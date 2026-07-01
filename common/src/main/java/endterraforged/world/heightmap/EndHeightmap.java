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
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;
import endterraforged.world.river.EndRiverMap;

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
     * Optional river post-processor. When set, {@link #getHeight} runs the
     * river carver over the raw terrain. May be {@code null}.
     */
    private final EndRiverMap riverMap;

    /**
     * Optional lake post-processor. Runs after the river carver in
     * {@link #getHeight}, so a lake basin is carved on top of any river
     * valley — a river running through a lake carves into the lake floor, not
     * the raw terrain. May be {@code null}.
     */
    private final EndLakeMap lakeMap;

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
        this(profile, seed, EndMountains.mountains2(seed), null, null);
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
        this(profile, seed, mountains, null, null);
    }

    /**
     * Full constructor with optional river + lake post-processors. All other
     * constructors delegate here. Package-private: the public seams for adding
     * post-processors are {@link #withRivers(EndRiverMap)} and
     * {@link #withLakes(EndLakeMap)}.
     */
    EndHeightmap(DimensionProfile profile, int seed, Noise mountains,
                 EndRiverMap riverMap, EndLakeMap lakeMap) {
        this.levels = new EndLevels(profile);
        this.continent = buildContinent(profile.topologyMode(), seed);
        this.mountains = mountains;
        this.terrain = Noises.mul(continent, mountains);
        this.seaMode = profile.seaMode();
        this.riverMap = riverMap;
        this.lakeMap = lakeMap;
    }

    /**
     * Returns a new EndHeightmap with the same continent / mountains / levels
     * and the same lake post-processor, but with the given river post-processor
     * attached. The original is unchanged (immutable). Pass {@code null} to
     * detach rivers.
     *
     * <p>This is the public seam through which stage-4.2 wires the river
     * network into the height field. After this call, {@link #getHeight}
     * returns carved heights; downstream consumers (EndDensity, stage-3
     * DensityFunction bridge) automatically pick up the river valleys.</p>
     */
    public EndHeightmap withRivers(EndRiverMap riverMap) {
        return new EndHeightmap(this.levels, this.continent, this.mountains,
                this.terrain, this.seaMode, riverMap, this.lakeMap);
    }

    /**
     * Returns a new EndHeightmap with the same continent / mountains / levels
     * and the same river post-processor, but with the given lake post-processor
     * attached. The original is unchanged (immutable). Pass {@code null} to
     * detach lakes. Lakes run after rivers in {@link #getHeight}.
     */
    public EndHeightmap withLakes(EndLakeMap lakeMap) {
        return new EndHeightmap(this.levels, this.continent, this.mountains,
                this.terrain, this.seaMode, this.riverMap, lakeMap);
    }

    private EndHeightmap(EndLevels levels, Continent continent, Noise mountains,
                         Noise terrain, SeaMode seaMode,
                         EndRiverMap riverMap, EndLakeMap lakeMap) {
        this.levels = levels;
        this.continent = continent;
        this.mountains = mountains;
        this.terrain = terrain;
        this.seaMode = seaMode;
        this.riverMap = riverMap;
        this.lakeMap = lakeMap;
    }

    /**
     * Final terrain height at {@code (x, z)}, with rivers then lakes applied
     * if attached via {@link #withRivers} / {@link #withLakes}.
     *
     * <p>Post-processors chain: raw terrain → river carver → lake carver. A
     * lake therefore carves on top of any river valley at the same location,
     * which is the desired semantics (a river through a lake cuts into the
     * lake floor). When neither is attached, this is identical to
     * {@link #getTerrainHeight}.</p>
     *
     * <p>This is the value downstream consumers should query: EndDensity uses
     * it to decide column solidity, and the stage-3 DensityFunction bridge
     * will expose it as the dimension's {@code final_density}.</p>
     *
     * @return normalised height in {@code [surface, 1]} (post-river, post-lake)
     */
    public float getHeight(float x, float z, int seed) {
        float h = getTerrainHeight(x, z, seed);
        if (this.riverMap != null) {
            h = this.riverMap.modifyHeight(x, z, seed, this, h);
        }
        if (this.lakeMap != null) {
            h = this.lakeMap.modifyHeight(x, z, seed, this, h);
        }
        return h;
    }

    /**
     * Raw terrain height at {@code (x, z)} — continent × mountains scaled to
     * world height, with <em>no</em> river carving. Used internally by
     * {@link EndRiverMap#modifyHeight} to sample source / terrain heights
     * without recursing through the carving step.
     *
     * <p>External callers should normally use {@link #getHeight}; this is
     * exposed for the river carver and for diagnostics that want to see the
     * pre-river shape.</p>
     *
     * @return normalised height in {@code [surface, 1]} (pre-river)
     */
    public float getTerrainHeight(float x, float z, int seed) {
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
