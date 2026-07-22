/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's UnifiedTerrainRegionLayout and RegionEdgeValue (MIT):
 * raccoonman.reterraforged.world.worldgen.cell.terrain.region.UnifiedTerrainRegionLayout
 * raccoonman.reterraforged.world.worldgen.cell.terrain.region.RegionEdgeValue
 *
 * EndTerraForged changes:
 * - removes Cell, registry, populator and object-pool coupling
 * - uses caller-owned primitive sampling buffers
 * - applies explicit weight / region-size-squared area compensation
 * - restricts macro ownership to AREA terrain families
 */
package endterraforged.world.terrain;

import java.util.List;
import java.util.Objects;

import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.NoiseMath.Vec2f;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * Immutable, bounded macro terrain ownership sampler.
 *
 * <p>Every coordinate receives exactly one AREA ownership region from the
 * supplied catalog. Finite RIDGE and COMPACT features use independent anchor
 * layouts and never purchase or label flat macro regions.</p>
 */
public final class TerrainRegionLayout {

    public static final int MAX_ENTRIES = 16;

    private static final int SEARCH_RADIUS = 2;
    static final int MAX_SEARCH_CANDIDATES = MAX_ENTRIES
            * (SEARCH_RADIUS * 2 + 1) * (SEARCH_RADIUS * 2 + 1);
    private static final float CELL_JITTER = 0.7F;
    private static final int GRID_SEED_SALT = 0x2F6E2B1D;
    private static final int REGION_SEED_SALT = 0x6C8E9CF5;
    private static final int GRID_ROTATION_SALT = 0x045D9F3B;
    private static final int FEATURE_ROTATION_SALT = 0x71A4C953;
    private static final float MAX_FEATURE_ROTATION_OFFSET = NoiseMath.PI2 * 0.0625F;
    private static final float BLEND_SCORE_RATIO_START = 0.72F;

    private final Domain warp;
    private final RuntimeEntry[] entries;

    /**
     * Builds a fixed runtime layout with a bounded RTF-style domain warp.
     *
     * @param seed stable world seed namespace for this layout
     * @param warpScale positive world-space noise scale
     * @param warpStrength maximum signed warp displacement
     * @param source immutable terrain ownership catalog
     */
    public TerrainRegionLayout(int seed, int warpScale, float warpStrength,
                               List<TerrainRegionEntry> source) {
        Objects.requireNonNull(source, "source");
        if (warpScale <= 0) {
            throw new IllegalArgumentException("warpScale must be > 0, got " + warpScale);
        }
        if (!Float.isFinite(warpStrength) || warpStrength < 0.0F) {
            throw new IllegalArgumentException("warpStrength must be finite and >= 0, got " + warpStrength);
        }
        if (source.isEmpty() || source.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "terrain region catalog size must be in [1, " + MAX_ENTRIES + "]");
        }

        RuntimeEntry[] active = new RuntimeEntry[source.size()];
        boolean[] ids = new boolean[MAX_ENTRIES];
        int activeCount = 0;

        for (TerrainRegionEntry entry : source) {
            validateEntry(entry, ids);
            RuntimeEntry runtime = new RuntimeEntry(seed, entry);
            active[activeCount++] = runtime;
        }

        this.entries = trim(active, activeCount);
        this.warp = Domains.domain(
                Noises.perlin(seed + GRID_SEED_SALT, warpScale, 2),
                Noises.perlin(seed + GRID_SEED_SALT + 1, warpScale, 2),
                Noises.constant(warpStrength));
    }

    /**
     * Samples one world position into a caller-owned buffer without allocating.
     */
    public void sample(float worldX, float worldZ, TerrainRegionBuffer output) {
        Objects.requireNonNull(output, "output");
        if (!Float.isFinite(worldX) || !Float.isFinite(worldZ)) {
            throw new IllegalArgumentException("terrain region coordinates must be finite");
        }

        float warpedX = worldX + this.warp.getOffsetX(worldX, worldZ, 0);
        float warpedZ = worldZ + this.warp.getOffsetZ(worldX, worldZ, 0);
        sampleCandidates(warpedX, warpedZ, this.entries, output.ownership);

        CandidateScratch owner = output.ownership;
        RuntimeEntry ownerEntry = owner.bestEntry;
        float edge = edgeValue(owner.bestScore, owner.secondScore);
        float blend = 1.0F - edge;

        output.setBase(
                packCell(owner.bestCellX, owner.bestCellZ),
                ownerEntry.entryId,
                ownerEntry.family,
                ownerEntry.family,
                edge,
                blend,
                0.0F,
                warpedX,
                warpedZ);
        populateBlendCandidates(owner, output);
    }

    /** Materialises a diagnostic snapshot outside density sampling. */
    public TerrainRegionPlan planAt(float worldX, float worldZ) {
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        sample(worldX, worldZ, buffer);
        return buffer.snapshot();
    }

    private static void validateEntry(TerrainRegionEntry entry, boolean[] ids) {
        Objects.requireNonNull(entry, "terrain region entry");
        if (entry.entryId() < 0 || entry.entryId() >= MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "terrain region entry id must be in [0, " + (MAX_ENTRIES - 1) + "]");
        }
        if (ids[entry.entryId()]) {
            throw new IllegalArgumentException("terrain region entry ids must be unique");
        }
        ids[entry.entryId()] = true;
        if (!Float.isFinite(entry.weight()) || entry.weight() <= 0.0F) {
            throw new IllegalArgumentException("terrain region weight must be finite and > 0");
        }
        if (entry.placement() != TerrainPlacementMode.AREA) {
            throw new IllegalArgumentException(
                    "macro terrain ownership only accepts AREA entries; "
                            + entry.placement() + " requires an independent feature layout");
        }
        if (entry.regionSize() < 128 || entry.regionSize() > 32768) {
            throw new IllegalArgumentException("terrain region size must be in [128, 32768]");
        }
        if (!Float.isFinite(entry.aspectRatio())
                || entry.aspectRatio() < 0.25F
                || entry.aspectRatio() > 4.0F) {
            throw new IllegalArgumentException("terrain region aspect ratio must be in [0.25, 4]");
        }
    }

    private static RuntimeEntry[] trim(RuntimeEntry[] source, int length) {
        RuntimeEntry[] result = new RuntimeEntry[length];
        System.arraycopy(source, 0, result, 0, length);
        return result;
    }

    private static void sampleCandidates(float warpedX, float warpedZ,
                                         RuntimeEntry[] entries, CandidateScratch output) {
        output.clear();
        for (RuntimeEntry entry : entries) {
            double gridX = (warpedX * entry.rotationCos + warpedZ * entry.rotationSin)
                    / entry.majorSpacing;
            double gridZ = (-warpedX * entry.rotationSin + warpedZ * entry.rotationCos)
                    / entry.minorSpacing;
            int originX = floor(gridX);
            int originZ = floor(gridZ);

            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                    int cellX = originX + dx;
                    int cellZ = originZ + dz;
                    Vec2f jitter = NoiseMath.cell(entry.gridSeed, cellX, cellZ);
                    double centerGridX = cellX + jitter.x() * CELL_JITTER;
                    double centerGridZ = cellZ + jitter.y() * CELL_JITTER;
                    double deltaX = centerGridX - gridX;
                    double deltaZ = centerGridZ - gridZ;
                    double localDeltaX = deltaX * entry.majorSpacing;
                    double localDeltaZ = deltaZ * entry.minorSpacing;
                    double worldDistance = localDeltaX * localDeltaX + localDeltaZ * localDeltaZ;
                    double score = worldDistance * entry.candidateDensity / entry.weight;

                    double localX = centerGridX * entry.majorSpacing;
                    double localZ = centerGridZ * entry.minorSpacing;
                    float centerX = (float) (localX * entry.rotationCos - localZ * entry.rotationSin);
                    float centerZ = (float) (localX * entry.rotationSin + localZ * entry.rotationCos);
                    output.consider(entry, cellX, cellZ, score, centerX, centerZ);
                }
            }
        }
        if (output.bestEntry == null || output.secondEntry == null || output.thirdEntry == null) {
            throw new IllegalStateException("terrain region candidate search did not produce three candidates");
        }
    }

    private static void populateBlendCandidates(CandidateScratch source, TerrainRegionBuffer output) {
        output.clearCandidates();
        addBlendCandidate(source.bestEntry, source.bestCellX, source.bestCellZ,
                source.bestScore, source.bestCenterX, source.bestCenterZ, source.bestScore, output);
        addBlendCandidate(source.secondEntry, source.secondCellX, source.secondCellZ,
                source.secondScore, source.secondCenterX, source.secondCenterZ, source.bestScore, output);
        addBlendCandidate(source.thirdEntry, source.thirdCellX, source.thirdCellZ,
                source.thirdScore, source.thirdCenterX, source.thirdCenterZ, source.bestScore, output);

        for (int index = 0; index < source.candidateCount; index++) {
            RuntimeEntry entry = source.candidateEntries[index];
            int cellX = source.candidateCellX[index];
            int cellZ = source.candidateCellZ[index];
            if (sameCandidate(entry, cellX, cellZ, source.bestEntry, source.bestCellX, source.bestCellZ)
                    || sameCandidate(entry, cellX, cellZ,
                            source.secondEntry, source.secondCellX, source.secondCellZ)
                    || sameCandidate(entry, cellX, cellZ,
                            source.thirdEntry, source.thirdCellX, source.thirdCellZ)) {
                continue;
            }
            float candidateBlend = 1.0F - edgeValue(source.bestScore, source.candidateScores[index]);
            if (candidateBlend <= 0.0F) {
                continue;
            }
            addBlendCandidate(entry, cellX, cellZ, source.candidateScores[index],
                    source.candidateCenterX[index], source.candidateCenterZ[index],
                    source.bestScore, output);
        }
        output.normalizeCandidateWeights();
    }

    private static void addBlendCandidate(RuntimeEntry entry,
                                          int cellX,
                                          int cellZ,
                                          double score,
                                          float centerX,
                                          float centerZ,
                                          double bestScore,
                                          TerrainRegionBuffer output) {
        float blend = 1.0F - edgeValue(bestScore, score);
        output.addCandidate(packCell(cellX, cellZ), entry.entryId, entry.family, entry.placement,
                blend, centerX, centerZ, orientation(entry, cellX, cellZ));
    }

    private static boolean sameCandidate(RuntimeEntry leftEntry,
                                         int leftCellX,
                                         int leftCellZ,
                                         RuntimeEntry rightEntry,
                                         int rightCellX,
                                         int rightCellZ) {
        return leftEntry == rightEntry && leftCellX == rightCellX && leftCellZ == rightCellZ;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static long packCell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static float edgeValue(double bestScore, double secondScore) {
        if (!Double.isFinite(bestScore) || !Double.isFinite(secondScore) || secondScore <= 0.0D) {
            return 0.0F;
        }
        float ratio = (float) (bestScore / secondScore);
        float alpha = Math.clamp(
                (ratio - BLEND_SCORE_RATIO_START) / (1.0F - BLEND_SCORE_RATIO_START),
                0.0F, 1.0F);
        float blend = alpha * alpha * (3.0F - 2.0F * alpha);
        return 1.0F - blend;
    }

    private static float orientation(RuntimeEntry entry, int cellX, int cellZ) {
        float unit = unitFloat(NoiseMath.hash2D(
                FEATURE_ROTATION_SALT, NoiseMath.hash2D(entry.regionSeed, cellX, cellZ), entry.entryId));
        float offset = (unit - 0.5F) * 2.0F * MAX_FEATURE_ROTATION_OFFSET;
        float result = entry.gridRotation + offset;
        return result < NoiseMath.PI2 ? result : result - NoiseMath.PI2;
    }

    private static float unitFloat(int value) {
        return (value & Integer.MAX_VALUE) / (float) Integer.MAX_VALUE;
    }

    static final class CandidateScratch {
        private final RuntimeEntry[] candidateEntries = new RuntimeEntry[MAX_SEARCH_CANDIDATES];
        private final double[] candidateScores = new double[MAX_SEARCH_CANDIDATES];
        private final int[] candidateCellX = new int[MAX_SEARCH_CANDIDATES];
        private final int[] candidateCellZ = new int[MAX_SEARCH_CANDIDATES];
        private final float[] candidateCenterX = new float[MAX_SEARCH_CANDIDATES];
        private final float[] candidateCenterZ = new float[MAX_SEARCH_CANDIDATES];
        private int candidateCount;
        private RuntimeEntry bestEntry;
        private RuntimeEntry secondEntry;
        private RuntimeEntry thirdEntry;
        private double bestScore;
        private double secondScore;
        private double thirdScore;
        private int bestCellX;
        private int bestCellZ;
        private int secondCellX;
        private int secondCellZ;
        private int thirdCellX;
        private int thirdCellZ;
        private float bestCenterX;
        private float bestCenterZ;
        private float secondCenterX;
        private float secondCenterZ;
        private float thirdCenterX;
        private float thirdCenterZ;

        private void clear() {
            candidateCount = 0;
            bestEntry = null;
            secondEntry = null;
            thirdEntry = null;
            bestScore = Double.POSITIVE_INFINITY;
            secondScore = Double.POSITIVE_INFINITY;
            thirdScore = Double.POSITIVE_INFINITY;
            bestCellX = 0;
            bestCellZ = 0;
            secondCellX = 0;
            secondCellZ = 0;
            thirdCellX = 0;
            thirdCellZ = 0;
            bestCenterX = 0.0F;
            bestCenterZ = 0.0F;
            secondCenterX = 0.0F;
            secondCenterZ = 0.0F;
            thirdCenterX = 0.0F;
            thirdCenterZ = 0.0F;
        }

        private void consider(RuntimeEntry entry, int cellX, int cellZ,
                              double score, float centerX, float centerZ) {
            int candidateIndex = this.candidateCount++;
            this.candidateEntries[candidateIndex] = entry;
            this.candidateScores[candidateIndex] = score;
            this.candidateCellX[candidateIndex] = cellX;
            this.candidateCellZ[candidateIndex] = cellZ;
            this.candidateCenterX[candidateIndex] = centerX;
            this.candidateCenterZ[candidateIndex] = centerZ;
            if (better(entry, cellX, cellZ, score, bestEntry, bestCellX, bestCellZ, bestScore)) {
                thirdEntry = secondEntry;
                thirdScore = secondScore;
                thirdCellX = secondCellX;
                thirdCellZ = secondCellZ;
                thirdCenterX = secondCenterX;
                thirdCenterZ = secondCenterZ;
                secondEntry = bestEntry;
                secondScore = bestScore;
                secondCellX = bestCellX;
                secondCellZ = bestCellZ;
                secondCenterX = bestCenterX;
                secondCenterZ = bestCenterZ;
                bestEntry = entry;
                bestScore = score;
                bestCellX = cellX;
                bestCellZ = cellZ;
                bestCenterX = centerX;
                bestCenterZ = centerZ;
                return;
            }
            if (better(entry, cellX, cellZ, score,
                    secondEntry, secondCellX, secondCellZ, secondScore)) {
                thirdEntry = secondEntry;
                thirdScore = secondScore;
                thirdCellX = secondCellX;
                thirdCellZ = secondCellZ;
                thirdCenterX = secondCenterX;
                thirdCenterZ = secondCenterZ;
                secondEntry = entry;
                secondScore = score;
                secondCellX = cellX;
                secondCellZ = cellZ;
                secondCenterX = centerX;
                secondCenterZ = centerZ;
                return;
            }
            if (better(entry, cellX, cellZ, score,
                    thirdEntry, thirdCellX, thirdCellZ, thirdScore)) {
                thirdEntry = entry;
                thirdScore = score;
                thirdCellX = cellX;
                thirdCellZ = cellZ;
                thirdCenterX = centerX;
                thirdCenterZ = centerZ;
            }
        }

        private static boolean better(RuntimeEntry entry, int cellX, int cellZ, double score,
                                      RuntimeEntry current, int currentCellX, int currentCellZ,
                                      double currentScore) {
            int comparison = Double.compare(score, currentScore);
            if (comparison != 0) {
                return comparison < 0;
            }
            if (current == null) {
                return true;
            }
            if (entry.entryId != current.entryId) {
                return entry.entryId < current.entryId;
            }
            if (cellX != currentCellX) {
                return cellX < currentCellX;
            }
            return cellZ < currentCellZ;
        }
    }

    private static final class RuntimeEntry {
        private final int entryId;
        private final TerrainRegionFamily family;
        private final TerrainPlacementMode placement;
        private final int gridSeed;
        private final int regionSeed;
        private final float gridRotation;
        private final float rotationCos;
        private final float rotationSin;
        private final float majorSpacing;
        private final float minorSpacing;
        private final float weight;
        private final float candidateDensity;

        private RuntimeEntry(int seed, TerrainRegionEntry entry) {
            this.entryId = entry.entryId();
            this.family = entry.family();
            this.placement = entry.placement();
            this.weight = entry.weight();
            this.gridSeed = NoiseMath.hash2D(seed + GRID_SEED_SALT, this.entryId, 0);
            this.regionSeed = NoiseMath.hash2D(seed + REGION_SEED_SALT, this.entryId, 0);
            this.gridRotation = unitFloat(NoiseMath.hash2D(
                    seed + GRID_ROTATION_SALT, this.entryId, entry.regionSize())) * NoiseMath.PI2;
            this.rotationCos = NoiseMath.cos(this.gridRotation);
            this.rotationSin = NoiseMath.sin(this.gridRotation);
            float aspect = this.placement == TerrainPlacementMode.AREA ? 1.0F : entry.aspectRatio();
            float axisScale = (float) Math.sqrt(aspect);
            this.majorSpacing = entry.regionSize() * axisScale;
            this.minorSpacing = entry.regionSize() / axisScale;
            this.candidateDensity = 1.0F / (this.majorSpacing * this.minorSpacing);
        }
    }
}
