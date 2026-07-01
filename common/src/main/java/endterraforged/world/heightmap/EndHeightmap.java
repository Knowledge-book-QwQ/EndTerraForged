/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * Heightmap (MIT) — lineage TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged — but the composition is restructured:
 * RTF's Heightmap is a CellPopulator coupled to climate/rivers/biome and
 * packages vertical scale into each TerrainPopulator; here the continent is
 * a pure Noise node, the mountain layer is a pure [0,1] shape, and this class
 * owns the single composition + world-height scaling step.
 */
package endterraforged.world.heightmap;

import endterraforged.world.config.DimensionProfile;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.continent.Continent;
import endterraforged.world.continent.ContinentalShatteredContinent;
import endterraforged.world.continent.IslandsContinent;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * The End dimension's master height field: composes the continent landness
 * with a mountain shape layer and scales the result to world height.
 *
 * <p><b>Composition.</b> The terrain field is {@code continent × mountains},
 * both in {@code [0,1]}. Where the continent says "no land" (landness 0), the
 * product is 0 — and thanks to {@code Multiply}'s zero short-circuit the
 * mountain layer is not even sampled there. Where the continent is solid, the
 * mountain shape passes through unattenuated, so peaks and valleys are driven
 * purely by the mountain recipe ({@link EndMountains#mountains2} by default).</p>
 *
 * <p><b>Vertical scaling.</b> The {@code [0,1]} terrain field is mapped to
 * world height as {@code surface + elevationRange × terrain}, where
 * {@code surface} and {@code elevationRange} come from {@link EndLevels}.
 * This puts the terrain floor at the dimension's reference surface (sea level
 * or island baseline, depending on {@link endterraforged.world.config.SeaMode})
 * and lets mountains rise to the world top. Everything <em>below</em> the
 * surface — seabed, void, NO_FLOOR carving — is a stage-2.6 post-process
 * concern; this class only owns the above-surface shape.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable after construction. The
 * continent and mountain noises are records / leaf types that are safe to
 * query from parallel chunk-gen threads.</p>
 */
public final class EndHeightmap {

    private final Continent continent;
    private final Noise mountains;
    private final Noise terrain;
    private final EndLevels levels;
    private final SeaMode seaMode;

    /**
     * Builds an EndHeightmap from a dimension profile, using
     * {@link EndMountains#mountains2} as the default mountain layer.
     *
     * @param profile the dimension shape (sea level, topology, height range)
     * @param seed    the world seed; drives both the mountain recipe and the
     *                continent cell layout. The same seed must be passed to
     *                {@link #getHeight} / {@link #getLandness} at query time.
     */
    public EndHeightmap(DimensionProfile profile, int seed) {
        this(profile, seed, EndMountains.mountains2(seed));
    }

    /**
     * Builds an EndHeightmap with an explicit mountain layer. Package-private:
     * lets tests (and future preset code) swap the mountain recipe without
     * touching the continent / levels wiring.
     *
     * @param profile   the dimension shape
     * @param seed      the world seed, used to build the continent warp
     * @param mountains a {@code [0,1]} mountain height-field (e.g.
     *                  {@link EndMountains#mountains1} or {@link EndMountains#mountains2})
     */
    EndHeightmap(DimensionProfile profile, int seed, Noise mountains) {
        this.levels = new EndLevels(profile);
        this.continent = buildContinent(profile.topologyMode(), seed);
        this.mountains = mountains;
        this.terrain = Noises.mul(continent, mountains);
        this.seaMode = profile.seaMode();
    }

    /**
     * Normalised terrain height in {@code [0,1]} world units at {@code (x, z)}.
     *
     * <p>Computed as {@code surface + elevationRange × (continent × mountains)}.
     * Returns exactly {@code surface} where the continent reports no land
     * (landness 0), so the terrain floor is the dimension's reference surface
     * and the consumer can treat "height == surface with landness 0" as void.</p>
     *
     * @param x    world X
     * @param z    world Z
     * @param seed the world seed (must match the seed passed at construction
     *             for the continent cell layout to be consistent)
     * @return normalised height in {@code [surface, 1]}
     */
    public float getHeight(float x, float z, int seed) {
        return this.levels.surface + this.levels.elevationRange * this.terrain.compute(x, z, seed);
    }

    /**
     * Continent landness at {@code (x, z)} in {@code [0,1]}.
     *
     * <p>{@code 1} = solid landmass, {@code 0} = void / open space. This is
     * the continent module's raw output, sampled independently of the mountain
     * layer. The chunk generator uses this to decide whether to place any
     * terrain at all (void vs land) independently of the height value.</p>
     *
     * @param x    world X
     * @param z    world Z
     * @param seed the world seed
     * @return landness in {@code [0,1]}
     */
    public float getLandness(float x, float z, int seed) {
        return this.continent.compute(x, z, seed);
    }

    /** The {@link EndLevels} backing this heightmap's vertical scaling. */
    public EndLevels levels() {
        return this.levels;
    }

    /** The {@link SeaMode} of the backing dimension profile. */
    public SeaMode seaMode() {
        return this.seaMode;
    }

    /** The composed terrain noise tree ({@code continent × mountains}). */
    public Noise terrain() {
        return this.terrain;
    }

    /** The continent module backing this heightmap. */
    public Continent continent() {
        return this.continent;
    }

    // ----- continent assembly ----------------------------------------------

    /**
     * Selects and wires the continent module for the dimension's topology.
     *
     * <p>Both topologies share a perlin-driven domain warp (scale 300, strength
     * 40) that deforms the cell grid so islands / rifts don't sit on a perfect
     * lattice. The warp drivers use seeds offset by 100 from the world seed to
     * stay independent of the mountain recipe's seed slots.</p>
     *
     * <p><b>Defaults.</b> The continent parameters (cell scale, island radius,
     * scatter, rift threshold / strength) are hardcoded End-tuned defaults.
     * Stage 5 (configurable UI) can lift these into the DimensionProfile; for
     * now they live here as the single source of truth.</p>
     */
    private static Continent buildContinent(TopologyMode mode, int seed) {
        Domain warp = Domains.domain(
                Noises.perlin(seed + 100, 300, 4),
                Noises.perlin(seed + 101, 300, 4),
                Noises.constant(40.0F));
        return switch (mode) {
            case ISLANDS -> new IslandsContinent(
                    1.0F / 400.0F, 1.0F, 0.6F, 0.5F, warp);
            case CONTINENTAL_SHATTERED -> new ContinentalShatteredContinent(
                    1.0F / 800.0F, 1.0F, 0.6F, 0.85F, warp);
        };
    }
}
