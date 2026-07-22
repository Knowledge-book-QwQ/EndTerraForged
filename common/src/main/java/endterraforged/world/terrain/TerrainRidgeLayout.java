/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's TerrainAnchorLayout (MIT):
 * raccoonman.reterraforged.world.worldgen.cell.terrain.placement.TerrainAnchorLayout
 *
 * EndTerraForged changes:
 * - specializes the layout for one internal RIDGE spacing band
 * - writes Top-3 candidates into a caller-owned primitive buffer
 * - removes Cell, catalog, registry, pooling and executor coupling
 */
package endterraforged.world.terrain;

import java.util.Objects;

import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.NoiseMath.Vec2f;

/** Immutable, allocation-free anchor layout for finite ridge overlays. */
public final class TerrainRidgeLayout {
    static final float CELL_JITTER = 0.7F;
    static final int MAX_SEARCH_RADIUS = 6;

    private static final double SEARCH_BOUND_EPSILON = 1.0E-6D;
    private static final int CENTER_SEED_OFFSET = 7;
    private static final int CANDIDATE_SEED_OFFSET = 29;
    private static final int ROTATION_SEED_OFFSET = 43;

    private final int centerSeed;
    private final int candidateSeed;
    private final int rotationSeed;
    private final int spacing;
    private final float frequency;
    private final float maxReachSquared;
    private final int searchRadius;
    private final Evaluator evaluator;

    public TerrainRidgeLayout(int seed, int spacing, float maxReach, Evaluator evaluator) {
        if (spacing < 128 || spacing > 32768) {
            throw new IllegalArgumentException("ridge spacing must be in [128, 32768]");
        }
        if (!Float.isFinite(maxReach) || maxReach <= 0.0F) {
            throw new IllegalArgumentException("ridge reach must be finite and > 0");
        }
        int radius = calculateSearchRadius(spacing, maxReach);
        if (radius > MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("ridge reach exceeds the bounded search contract");
        }
        this.centerSeed = seed + CENTER_SEED_OFFSET;
        this.candidateSeed = seed + CANDIDATE_SEED_OFFSET;
        this.rotationSeed = seed + ROTATION_SEED_OFFSET;
        this.spacing = spacing;
        this.frequency = 1.0F / spacing;
        this.maxReachSquared = maxReach * maxReach;
        this.searchRadius = radius;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /** Samples all reachable anchors and retains the strongest three. */
    public void sample(float x, float z, TerrainRidgeBuffer output) {
        Objects.requireNonNull(output, "output");
        output.clear();
        if (!Float.isFinite(x) || !Float.isFinite(z)) {
            return;
        }
        float gridX = x * this.frequency;
        float gridZ = z * this.frequency;
        int originX = floor(gridX);
        int originZ = floor(gridZ);
        for (int dz = -this.searchRadius; dz <= this.searchRadius; dz++) {
            for (int dx = -this.searchRadius; dx <= this.searchRadius; dx++) {
                int cellX = originX + dx;
                int cellZ = originZ + dz;
                Vec2f jitter = NoiseMath.cell(this.centerSeed, cellX, cellZ);
                float centerX = (cellX + jitter.x() * CELL_JITTER) * this.spacing;
                float centerZ = (cellZ + jitter.y() * CELL_JITTER) * this.spacing;
                float deltaX = centerX - x;
                float deltaZ = centerZ - z;
                float distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (!Float.isFinite(distanceSquared) || distanceSquared >= this.maxReachSquared) {
                    continue;
                }
                int anchorSeed = NoiseMath.hash2D(this.candidateSeed, cellX, cellZ);
                float rotation = unitFloat(NoiseMath.hash2D(this.rotationSeed, cellX, cellZ))
                        * NoiseMath.PI2;
                float rotationCos = (float) Math.cos(rotation);
                float rotationSin = (float) Math.sin(rotation);
                float influence = this.evaluator.influence(anchorSeed, centerX, centerZ,
                        rotationCos, rotationSin, x, z);
                if (!Float.isFinite(influence) || influence <= 0.0F) {
                    continue;
                }
                output.insert(packCell(cellX, cellZ), anchorSeed, centerX, centerZ,
                        rotationCos, rotationSin, distanceSquared, Math.min(1.0F, influence));
            }
        }
    }

    public int searchRadius() {
        return this.searchRadius;
    }

    public int spacing() {
        return this.spacing;
    }

    public static int calculateSearchRadius(int spacing, float maxReach) {
        return Math.max(1, (int) Math.ceil(
                maxReach / spacing + CELL_JITTER + SEARCH_BOUND_EPSILON));
    }

    private static int floor(float value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static float unitFloat(int hash) {
        return (hash >>> 8) * 0x1.0P-24F;
    }

    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /** Thread-safe normalized influence evaluator for one ridge runtime. */
    @FunctionalInterface
    public interface Evaluator {
        float influence(int anchorSeed, float centerX, float centerZ,
                        float rotationCos, float rotationSin, float sampleX, float sampleZ);
    }
}
