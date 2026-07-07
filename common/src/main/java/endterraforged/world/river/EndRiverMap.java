/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * rivermap / UpliftRiverCarver (MIT) — lineage TerraForged (dags) ->
 * ReTerraForged (raccoonman) -> EndTerraForged — but the water-level model
 * is rewritten: RTF anchors water level to sea level and routes rivers to
 * the ocean; End has no sea in SeaMode.NONE, so the riverbed descends from
 * a per-cell source height down to the dimension's reference surface (the
 * void threshold), letting water spill off the island edge into the void.
 */
package endterraforged.world.river;

import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;
import endterraforged.world.noise.NoiseMath;

/**
 * The End's river network: a post-processor that carves river valleys into an
 * {@link EndHeightmap}'s terrain field.
 *
 * <p><b>Network layout.</b> A worley cell grid defines candidate river reaches.
 * Each cell hosts a river with probability {@link #riverChance}; the river is a
 * 2D {@link River} segment running from the cell centre (the approximate
 * source / peak) outward in a hash-derived direction toward the cell edge (the
 * approximate island rim / void). A 3×3 neighbourhood scan per sample finds the
 * nearest reach segment, so rivers from adjacent cells can overlap at borders —
 * that is what makes rivers feel connected rather than confined to one cell.</p>
 *
 * <p><b>Fork branches (stage 4.6).</b> A cell's main reach may fork partway along
 * its path: at {@code t=forkPoint} a branch segment splits off, heading in a
 * hash-derived deviation from the main direction. Each cell can therefore host
 * up to two segments (main + optional fork). The scan finds the nearest of all
 * segments, so a sample can sit inside a fork valley even when the main reach
 * is far away. This produces a tree-like network without the combinatorial
 * explosion of a full recursive fork tree (limited to one level per cell).</p>
 *
 * <p><b>Zone carving.</b> Borrowed from RTF's {@code UpliftRiverCarver}: the
 * perpendicular distance to the reach segment is mapped through a hermite
 * falloff. Inside {@link #bedWidth} the carver has full strength (riverbed);
 * between {@code bedWidth} and {@link #valleyWidth} it tapers to zero (banks →
 * valley floor → undisturbed terrain). The carver only <em>lowers</em> terrain
 * ({@code lerp(terrain, bed, riverness)} with {@code bed < terrain}), never
 * raises it.</p>
 *
 * <p><b>Water level — the End difference.</b> RTF sets the riverbed level from
 * a sea-anchored {@code ContinentalHydrology} step function. The End has no
 * sea in {@link endterraforged.world.config.SeaMode#NONE}, so the bed level
 * interpolates along the reach: {@code lerp(sourceHeight, surface, t) - bedDepth},
 * where {@code sourceHeight} is the terrain height at the reach start and
 * {@code surface} is the dimension's reference surface (island baseline in
 * NONE, sea level otherwise). At {@code t=1} (island edge) the bed meets the
 * surface, so water naturally spills into the void — no sea required.</p>
 *
 * <p><b>What this class does NOT do.</b> It does not place water blocks (that
 * is a stage-4.7 surface-system concern requiring MC chunk access). It does
 * not cache river networks per tile (stage 4 perf optimisation). It is the
 * minimal valley-carving kernel that produces the "water runs off the island"
 * shape.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable primitives set at
 * construction; the backing {@link EndHeightmap} is immutable. Safe to query
 * from parallel chunk-gen threads.</p>
 *
 * @param cellSize       worley cell size in blocks; smaller = denser river grid
 * @param riverChance    fraction of cells that host a river, in {@code [0,1]};
 *                       {@code 0} = no rivers, {@code 1} = every cell has one
 * @param bedWidth       riverbed half-width in blocks (full-strength carve zone)
 * @param valleyWidth    total valley half-width in blocks (carve tapers to 0 here)
 * @param bedDepth       riverbed depth below the along-reach water level, as a
 *                       fraction of {@link EndLevels#elevationRange}
 * @param forkChance     fraction of rivers that fork, in {@code [0,1]};
 *                       {@code 0} = no forks, {@code 1} = every river forks
 * @param forkPoint      where along the main reach the fork starts, in {@code [0,1]};
 *                       {@code 0.3} = fork near the source, {@code 0.7} = near the outlet
 * @param forkAngleMax   maximum deviation angle from the main direction, in degrees
 * @param forkLengthFactor length of the fork relative to the main reach, in {@code [0,1]}
 */
public record EndRiverMap(float cellSize, float riverChance, float bedWidth,
                          float valleyWidth, float bedDepth,
                          float forkChance, float forkPoint,
                          float forkAngleMax, float forkLengthFactor) {

    /** End-tuned defaults: sparse rivers, narrow beds, shallow valleys, occasional forks. */
    public static EndRiverMap defaults() {
        return new EndRiverMap(380.0F, 0.35F, 12.0F, 90.0F, 0.04F,
                0.25F, 0.45F, 35.0F, 0.6F);
    }

    /** Legacy compact constructor for tests / presets that don't need forks (no-fork mode). */
    public EndRiverMap(float cellSize, float riverChance, float bedWidth, float valleyWidth, float bedDepth) {
        this(cellSize, riverChance, bedWidth, valleyWidth, bedDepth, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * Post-processes the height at {@code (x, z)}: returns the river-carved
     * height, or {@code inputHeight} unchanged if no river is near.
     *
     * <p>This is a <em>chained</em> post-processor: {@code inputHeight} is the
     * height produced by upstream post-processors (or
     * {@link EndHeightmap#getTerrainHeight} if this is the first). Rivers carve
     * relative to the upstream height, so a lake placed upstream of a river
     * correctly lowers the terrain the river then carves through. The source
     * height (reach start, the "peak" the river descends from) is sampled from
     * the raw terrain field to avoid recursion — a lake at the exact source
     * point is a known minor approximation, acceptable because bed level
     * descends to {@code surface} by {@code t=1} regardless.</p>
     *
     * <p>Void (landness 0) passes {@code inputHeight} through untouched —
     * rivers only carve land.</p>
     *
     * @param x            world X
     * @param z            world Z
     * @param seed         world seed (must match the heightmap's seed)
     * @param heightmap    the source terrain field (for landness, levels, raw
     *                     source sampling)
     * @param inputHeight  the upstream height at {@code (x, z)} (raw terrain if
     *                     this is the first post-processor)
     * @return normalised height in {@code [surface, 1]}, carved where a river
     *         passes through; unchanged {@code inputHeight} otherwise
     */
    public float modifyHeight(float x, float z, int seed, EndHeightmap heightmap, float inputHeight) {
        // Degenerate config guard: cellSize<=0 would make invCell=Inf and
        // poison the worley scan with NaN. No-op instead.
        if (cellSize <= 0.0F) {
            return inputHeight;
        }
        // Rivers only exist on land — void stays void.
        float landness = heightmap.getLandness(x, z, seed);
        if (landness <= 0.0F) {
            return inputHeight;
        }

        RiverSample sample = sampleNearestRiver(x, z, seed);
        if (sample == null) {
            return inputHeight;
        }

        float riverness = riverness(sample.distance);
        if (riverness <= 0.0F) {
            return inputHeight;
        }

        EndLevels levels = heightmap.levels();
        // Carve relative to the upstream height — this is what makes the river
        // chain correctly with climate / lakes / other post-processors placed
        // before it.
        float terrainHeight = inputHeight;
        // Source height: raw terrain at the MAIN reach's start (cell centre).
        // Both main and fork segments carry mainStartX/mainStartZ, so forks
        // use the same cell-centre terrain as the main reach — this makes the
        // fork's water level continuous with the main reach at the fork point
        // (no vertical step). Sampled from getTerrainHeight (raw) to avoid
        // recursion through the post-process chain.
        float sourceHeight = heightmap.getTerrainHeight(
                sample.segment.mainStartX(), sample.segment.mainStartZ(), seed);
        float tNormalized = sample.tNormalized;
        // Bed descends from sourceHeight (tNormalized=0 for main, forkPoint for
        // fork) to surface (tNormalized=1, island edge / void threshold), then
        // drops by bedDepth so water sits in a channel. Clamped to >= surface
        // so the bed never punches below the void threshold (in SeaMode.NONE
        // below-surface is void, so an unclamped bed would carve a void trench
        // instead of a water surface).
        float bedLevel = NoiseMath.lerp(sourceHeight, levels.surface, tNormalized)
                - bedDepth * levels.elevationRange;
        bedLevel = Math.max(bedLevel, levels.surface);

        // Rivers only lower terrain, never raise it. Where the bed sits above
        // the upstream height (e.g. a low spot near a high-altitude source),
        // the min() leaves the terrain untouched — faithful to RTF's
        // `if (finalHeight < cell.height) cell.height = finalHeight` guard.
        float carved = NoiseMath.lerp(terrainHeight, bedLevel, riverness);
        return Math.min(terrainHeight, carved);
    }

    /**
     * The river's water-surface level at {@code (x, z)} in normalised
     * {@code [0,1]} height units, or {@code NaN} if no river is near or the
     * column is void.
     *
     * <p>This is the <em>rim</em> level — the height water would sit at
     * <em>before</em> {@link #bedDepth} carves the channel floor. It descends
     * along the reach from the source height (cell-centre terrain) down to the
     * dimension's reference {@link EndLevels#surface}, so at {@code t=1}
     * (island edge) the water surface meets the void threshold and spills off
     * the island — no sea required. The stage-4.7 water placer consumes this
     * to decide where to fill water blocks.</p>
     *
     * <p>Returns {@code NaN} when no river is in range or the column is void;
     * callers should treat {@code NaN} as "no water here".</p>
     */
    public float waterLevel(float x, float z, int seed, EndHeightmap heightmap) {
        if (cellSize <= 0.0F) {
            return Float.NaN;
        }
        if (heightmap.getLandness(x, z, seed) <= 0.0F) {
            return Float.NaN;
        }
        RiverSample sample = sampleNearestRiver(x, z, seed);
        if (sample == null) {
            return Float.NaN;
        }
        if (riverness(sample.distance) <= 0.0F) {
            return Float.NaN;
        }
        // Same along-reach descent as modifyHeight's bedLevel, but WITHOUT the
        // bedDepth subtraction: this is the surface the water sits at, not the
        // carved floor. Source sampled from raw terrain (getTerrainHeight) so
        // a lake placed upstream of a river does not recurse through carving.
        float sourceHeight = heightmap.getTerrainHeight(
                sample.segment.mainStartX(), sample.segment.mainStartZ(), seed);
        EndLevels levels = heightmap.levels();
        return NoiseMath.lerp(sourceHeight, levels.surface, sample.tNormalized);
    }

    /**
     * River intensity (riverness) at {@code (x, z)}: {@code 1} inside the bed,
     * tapering to {@code 0} at the valley edge via a hermite curve; {@code 0}
     * if no river is near or the column is void.
     *
     * <p>This is the world-coordinate counterpart of the package-private
     * {@link #riverness(float)} distance falloff: it locates the nearest reach
     * and evaluates the falloff at the sample's perpendicular distance. The
     * stage-4.7 water placer uses it to tell bed-centre columns (where water
     * is placed) from dry carved banks.</p>
     */
    public float rivernessAt(float x, float z, int seed, EndHeightmap heightmap) {
        if (cellSize <= 0.0F) {
            return 0.0F;
        }
        if (heightmap.getLandness(x, z, seed) <= 0.0F) {
            return 0.0F;
        }
        RiverSample sample = sampleNearestRiver(x, z, seed);
        if (sample == null) {
            return 0.0F;
        }
        return riverness(sample.distance);
    }

    /**
     * River intensity at a given perpendicular distance from the reach.
     * Returns {@code 1} inside the bed, tapers to {@code 0} at the valley edge
     * via a hermite curve for smooth banks.
     */
    float riverness(float distance) {
        if (distance >= valleyWidth) {
            return 0.0F;
        }
        if (distance <= bedWidth) {
            return 1.0F;
        }
        float t = (distance - bedWidth) / (valleyWidth - bedWidth);
        return 1.0F - NoiseMath.interpHermite(t);
    }

    /**
     * Scans the 3×3 worley neighbourhood of {@code (x, z)} for the nearest
     * river reach segment (including forks). Returns the segment and the
     * sample's perpendicular distance / along-reach projection, or {@code null}
     * if no cell in the neighbourhood hosts a river.
     *
     * <p>One coherent scan (like {@code IslandsContinent}'s single worley
     * pass) ensures the nearest reach is found without the desynchronisation
     * that separate per-module scans would introduce. Fork segments are
     * treated as separate candidates, so a sample can be closest to a fork
     * valley even when the main reach is farther away.</p>
     */
    private RiverSample sampleNearestRiver(float x, float z, int seed) {
        float invCell = 1.0F / cellSize;
        int cellX = NoiseMath.floor(x * invCell);
        int cellZ = NoiseMath.floor(z * invCell);

        RiverSegment nearest = null;
        float nearestDist = Float.MAX_VALUE;
        float nearestT = 0.0F;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (!hasRiver(seed, cx, cz)) {
                    continue;
                }
                RiverSegment[] segments = buildRiver(seed, cx, cz);
                for (RiverSegment seg : segments) {
                    float dist = seg.river().distanceTo(x, z);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = seg;
                        nearestT = seg.river().projection(x, z);
                    }
                }
            }
        }

        if (nearest == null) {
            return null;
        }
        float tNormalized = nearest.normaliseT(nearestT);
        return new RiverSample(nearest, nearestDist, nearestT, tNormalized);
    }

    /**
     * Whether the worley cell at {@code (cx, cz)} hosts a river, derived from
     * a per-cell hash. {@code riverChance=0} → never; {@code 1} → always.
     */
    private boolean hasRiver(int seed, int cx, int cz) {
        float h = NoiseMath.clamp((NoiseMath.valCoord2D(seed, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F);
        return h < riverChance;
    }

    /**
     * Builds the reach segment(s) for cell {@code (cx, cz)}: starts at the cell
     * centre (jittered), runs outward in a hash-derived direction for one cell
     * length. May optionally fork at {@link #forkPoint} with a deviation angle
     * up to {@link #forkAngleMax}. Returns an array of 1 (main only) or 2
     * (main + fork) segments with fork metadata. The jitter, direction, and
     * fork are deterministic per cell so the same river(s) are reconstructed on
     * every sample.
     */
    private RiverSegment[] buildRiver(int seed, int cx, int cz) {
        // Cell centre in world space, with a small hash-driven jitter so
        // sources don't sit on a perfect grid.
        float jitterX = (NoiseMath.valCoord2D(seed + 0x9E3779B1, cx, cz)) * 0.15F * cellSize;
        float jitterZ = (NoiseMath.valCoord2D(seed + 0x85EBCA77, cx, cz)) * 0.15F * cellSize;
        float startX = (cx + 0.5F) * cellSize + jitterX;
        float startZ = (cz + 0.5F) * cellSize + jitterZ;

        // Hash-derived direction: a full 2π angle so rivers radiate in all
        // directions across the grid.
        float angle = NoiseMath.clamp(
                (NoiseMath.valCoord2D(seed + 0xC2B2AE3D, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F)
                * (float) (Math.PI * 2.0);
        float mainEndX = startX + (float) Math.cos(angle) * cellSize;
        float mainEndZ = startZ + (float) Math.sin(angle) * cellSize;

        River main = River.of(startX, startZ, mainEndX, mainEndZ);
        RiverSegment mainSeg = new RiverSegment(main, false, 0.0F, 0.0F, startX, startZ);

        // Fork chance: only fork if forkChance > 0 and hash clears the gate.
        if (forkChance <= 0.0F) {
            return new RiverSegment[] { mainSeg };
        }
        float forkGate = NoiseMath.clamp(
                (NoiseMath.valCoord2D(seed + 0xD4A2E1F3, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F);
        if (forkGate >= forkChance) {
            return new RiverSegment[] { mainSeg };
        }

        // Fork starts at forkPoint along the main reach.
        float forkStartX = startX + (mainEndX - startX) * forkPoint;
        float forkStartZ = startZ + (mainEndZ - startZ) * forkPoint;

        // Fork direction: main angle + hash-derived deviation (±forkAngleMax).
        float angleOffsetRaw = NoiseMath.clamp(
                (NoiseMath.valCoord2D(seed + 0xA3D5E7F9, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F);
        // Map [0,1] → [-max, +max] degrees, then to radians.
        float forkAngleRad = (float) Math.toRadians(forkAngleMax);
        float deviation = (angleOffsetRaw - 0.5F) * 2.0F * forkAngleRad;  // [-forkAngleMax, +forkAngleMax]
        float forkAngle = angle + deviation;

        float forkLength = cellSize * forkLengthFactor * (1.0F - forkPoint);
        float forkEndX = forkStartX + (float) Math.cos(forkAngle) * forkLength;
        float forkEndZ = forkStartZ + (float) Math.sin(forkAngle) * forkLength;

        River fork = River.of(forkStartX, forkStartZ, forkEndX, forkEndZ);
        // Fork carries the MAIN reach's start point (startX, startZ), not the
        // fork's own start, so source-height sampling uses the same cell-centre
        // terrain as the main reach. This makes the fork's water level
        // continuous with the main reach's level at the fork point — no
        // vertical step where the fork branches off.
        RiverSegment forkSeg = new RiverSegment(fork, true, forkPoint, forkLengthFactor, startX, startZ);
        return new RiverSegment[] { mainSeg, forkSeg };
    }

    /** A river segment with metadata: whether it's a fork, the fork parameters needed to normalise t, and the main reach's start point (for source-height sampling). */
    private record RiverSegment(River river, boolean isFork, float forkPoint,
                                float forkLengthFactor, float mainStartX, float mainStartZ) {
        /**
         * Normalises the projection t onto the main reach's t range.
         * For main: tNormalized = t.
         * For fork: tNormalized = forkPoint + t * forkLengthFactor * (1 - forkPoint).
         */
        float normaliseT(float t) {
            if (!isFork) {
                return t;
            }
            // Fork t=0 → forkPoint, t=1 → forkPoint + forkLengthFactor*(1-forkPoint)
            return forkPoint + t * forkLengthFactor * (1.0F - forkPoint);
        }
    }

    /** A sampled river reach: the segment, perpendicular distance, projection, and normalised projection. */
    private record RiverSample(RiverSegment segment, float distance, float t, float tNormalized) {
    }
}
