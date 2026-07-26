/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged R9.3.6/R9.6 hydraulic Erosion (MIT) into
 * EndTerraForged (LGPL-3.0-or-later). The droplet gradient, inertia,
 * capacity, erosion/deposition, evaporation, brush and RNG ordering are
 * retained. ETF uses primitive SoA buffers, immutable source paths, bounded
 * transfer budgets and world-space tile sources to make the candidate
 * deterministic across independently built tiles.
 */
package endterraforged.world.erosion;

import java.util.Objects;

import endterraforged.util.FastRandom;
import endterraforged.util.NoiseUtil;

/** Immutable test-only RTF-derived hydraulic erosion candidate. */
final class EndHydraulicErosionRuntime {

    static final long ALGORITHM_ID = 0x4554465F48594452L;
    static final int ALGORITHM_VERSION = 1;
    static final int SOURCE_CHUNK_SAMPLES = 16;
    static final int DROPLETS_PER_SOURCE_CHUNK = 135;
    static final int DROPLET_LIFETIME = 12;
    static final int BRUSH_RADIUS_SAMPLES = 4;
    static final int REQUIRED_HALO_SAMPLES = DROPLET_LIFETIME + BRUSH_RADIUS_SAMPLES;
    static final float INITIAL_WATER = 0.7F;
    static final float INITIAL_SPEED = 0.7F;
    static final float EROSION_RATE = 0.5F;
    static final float DEPOSITION_RATE = 0.5F;
    static final float INERTIA = 0.05F;
    static final float GRADIENT_WEIGHT = 0.95F;
    static final float CAPACITY_FACTOR = 4.0F;
    static final float MIN_CAPACITY = 0.01F;
    static final float GRAVITY = 3.0F;
    static final float EVAPORATION = 0.01F;
    static final float REFERENCE_HEIGHT_BLOCKS = 256.0F;
    static final float MAX_TRANSFER_BLOCKS = 12.0F;
    static final float MIN_LANDNESS = 0.12F;
    static final float MIN_THICKNESS_BLOCKS = 8.0F;
    static final float DRAINAGE_FLUX_SCALE = 4.0F;
    static final int SEED_OFFSET = 12768;

    private final int[] brushOffsetX;
    private final int[] brushOffsetZ;
    private final float[] brushWeight;

    EndHydraulicErosionRuntime() {
        int entries = brushEntryCount();
        this.brushOffsetX = new int[entries];
        this.brushOffsetZ = new int[entries];
        this.brushWeight = new float[entries];
        float weightSum = 0.0F;
        int entry = 0;
        for (int z = -BRUSH_RADIUS_SAMPLES; z <= BRUSH_RADIUS_SAMPLES; z++) {
            for (int x = -BRUSH_RADIUS_SAMPLES; x <= BRUSH_RADIUS_SAMPLES; x++) {
                float distanceSquared = x * x + z * z;
                if (distanceSquared >= BRUSH_RADIUS_SAMPLES * BRUSH_RADIUS_SAMPLES) {
                    continue;
                }
                float weight = 1.0F
                        - (float) Math.sqrt(distanceSquared) / BRUSH_RADIUS_SAMPLES;
                this.brushOffsetX[entry] = x;
                this.brushOffsetZ[entry] = z;
                this.brushWeight[entry] = weight;
                weightSum += weight;
                entry++;
            }
        }
        for (int index = 0; index < this.brushWeight.length; index++) {
            this.brushWeight[index] /= weightSum;
        }
    }

    long primitiveBytes() {
        return (long) this.brushWeight.length
                * (Integer.BYTES * 2L + Float.BYTES);
    }

    int brushEntries() {
        return this.brushWeight.length;
    }

    EndHydraulicErosionTile apply(EndErosionTile input,
                                  EndHydraulicErosionScratch scratch,
                                  float[] finalTopBlocks,
                                  float[] deltaBlocks,
                                  float[] erosionStrength,
                                  float[] drainagePotential,
                                  float[] activation) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(scratch, "scratch");
        EndErosionTileKey key = input.key();
        validateGeometry(key, scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation);
        scratch.clear();
        initialiseBudgets(input, scratch, activation);

        MutableStats stats = new MutableStats();
        runDroplets(input, scratch, stats);
        finish(input, scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation);
        return EndHydraulicErosionTile.fromOwnedArrays(
                key, finalTopBlocks, deltaBlocks, erosionStrength,
                drainagePotential, activation, stats.snapshot());
    }

    private void runDroplets(EndErosionTile input,
                             EndHydraulicErosionScratch scratch,
                             MutableStats stats) {
        EndErosionTileKey key = input.key();
        int sampleDistance = key.sampleDistanceBlocks();
        int sampleOriginX = Math.toIntExact(key.sampleOriginBlockX() / sampleDistance);
        int sampleOriginZ = Math.toIntExact(key.sampleOriginBlockZ() / sampleDistance);
        int minChunkX = Math.floorDiv(sampleOriginX, SOURCE_CHUNK_SAMPLES);
        int minChunkZ = Math.floorDiv(sampleOriginZ, SOURCE_CHUNK_SAMPLES);
        int maxChunkX = Math.floorDiv(
                sampleOriginX + key.sampleWidth() - 1, SOURCE_CHUNK_SAMPLES);
        int maxChunkZ = Math.floorDiv(
                sampleOriginZ + key.sampleHeight() - 1, SOURCE_CHUNK_SAMPLES);
        int seed = Long.hashCode(key.worldSeed()) + SEED_OFFSET;
        FastRandom random = new FastRandom();

        for (int iteration = 0; iteration < DROPLETS_PER_SOURCE_CHUNK; iteration++) {
            long iterationSeed = NoiseUtil.seed(seed, iteration);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    long chunkSeed = NoiseUtil.seed(chunkX, chunkZ);
                    random.seed(chunkSeed, iterationSeed);
                    int sourceX = chunkX * SOURCE_CHUNK_SAMPLES
                            + random.nextInt(SOURCE_CHUNK_SAMPLES);
                    int sourceZ = chunkZ * SOURCE_CHUNK_SAMPLES
                            + random.nextInt(SOURCE_CHUNK_SAMPLES);
                    float localX = sourceX - sampleOriginX;
                    float localZ = sourceZ - sampleOriginZ;
                    if (localX < 1.0F || localX >= key.sampleWidth() - 1.0F
                            || localZ < 1.0F || localZ >= key.sampleHeight() - 1.0F) {
                        continue;
                    }
                    stats.droplets++;
                    applyDroplet(sourceX, sourceZ, sampleOriginX, sampleOriginZ,
                            input, scratch, stats);
                }
            }
        }
    }

    private void applyDroplet(float positionX,
                              float positionZ,
                              int sampleOriginX,
                              int sampleOriginZ,
                              EndErosionTile input,
                              EndHydraulicErosionScratch scratch,
                              MutableStats stats) {
        int width = input.key().sampleWidth();
        int height = input.key().sampleHeight();
        float directionX = 0.0F;
        float directionZ = 0.0F;
        float sediment = 0.0F;
        float speed = INITIAL_SPEED;
        float water = INITIAL_WATER;

        for (int lifetime = 0; lifetime < DROPLET_LIFETIME; lifetime++) {
            EndHydraulicErosionScratch.Sample current = scratch.currentSample();
            sample(input, positionX, positionZ, sampleOriginX, sampleOriginZ, current);
            directionX = directionX * INERTIA - current.gradientX * GRADIENT_WEIGHT;
            directionZ = directionZ * INERTIA - current.gradientZ * GRADIENT_WEIGHT;
            float directionLength = (float) Math.sqrt(
                    directionX * directionX + directionZ * directionZ);
            if (Float.isNaN(directionLength)) {
                directionLength = 0.0F;
            }
            if (directionLength != 0.0F) {
                directionX /= directionLength;
                directionZ /= directionLength;
            }
            if (directionX == 0.0F && directionZ == 0.0F) {
                stats.stationaryStops++;
                stats.exportedSedimentBlocks += sediment * REFERENCE_HEIGHT_BLOCKS;
                return;
            }

            addFlux(scratch.flux(), width, current, water);
            stats.steps++;
            if (inCore(current.nodeX, current.nodeZ, input.key())) {
                stats.coreSteps++;
            } else {
                stats.haloSteps++;
            }

            positionX += directionX;
            positionZ += directionZ;
            if (positionX < sampleOriginX
                    || positionX >= sampleOriginX + width - 1.0F
                    || positionZ < sampleOriginZ
                    || positionZ >= sampleOriginZ + height - 1.0F) {
                stats.boundaryStops++;
                stats.exportedSedimentBlocks += sediment * REFERENCE_HEIGHT_BLOCKS;
                return;
            }

            EndHydraulicErosionScratch.Sample next = scratch.nextSample();
            sample(input, positionX, positionZ, sampleOriginX, sampleOriginZ, next);
            float deltaHeight = next.height - current.height;
            float capacity = Math.max(
                    -deltaHeight * speed * water * CAPACITY_FACTOR, MIN_CAPACITY);
            if (sediment > capacity || deltaHeight > 0.0F) {
                float requested = deltaHeight > 0.0F
                        ? Math.min(deltaHeight, sediment)
                        : (sediment - capacity) * DEPOSITION_RATE;
                float deposited = deposit(current, requested, input, scratch);
                sediment = Math.max(0.0F, sediment - deposited);
                stats.depositedBlocks += deposited * REFERENCE_HEIGHT_BLOCKS;
            } else {
                float requested = Math.min(
                        (capacity - sediment) * EROSION_RATE, -deltaHeight);
                float eroded = erode(current.nodeX, current.nodeZ,
                        requested, input, scratch, stats);
                sediment += eroded;
                stats.erodedBlocks += eroded * REFERENCE_HEIGHT_BLOCKS;
            }

            speed = (float) Math.sqrt(speed * speed + deltaHeight * GRAVITY);
            water *= 1.0F - EVAPORATION;
            if (Float.isNaN(speed)) {
                speed = 0.0F;
            }
        }
        stats.lifetimeStops++;
        stats.exportedSedimentBlocks += sediment * REFERENCE_HEIGHT_BLOCKS;
    }

    private float erode(int centerX,
                        int centerZ,
                        float amount,
                        EndErosionTile input,
                        EndHydraulicErosionScratch scratch,
                        MutableStats stats) {
        if (amount <= 0.0F) {
            return 0.0F;
        }
        int width = input.key().sampleWidth();
        int height = input.key().sampleHeight();
        float eroded = 0.0F;
        for (int brush = 0; brush < this.brushWeight.length; brush++) {
            int x = centerX + this.brushOffsetX[brush];
            int z = centerZ + this.brushOffsetZ[brush];
            if (x < 0 || x >= width || z < 0 || z >= height) {
                continue;
            }
            int index = z * width + x;
            float transfer = amount * this.brushWeight[brush] * transferFactor(scratch, index);
            float applied = transfer;
            if (applied <= 0.0F) {
                continue;
            }
            scratch.deltaUnits()[index] -= applied;
            eroded += applied;
            stats.brushWrites++;
        }
        return eroded;
    }

    private static float deposit(EndHydraulicErosionScratch.Sample sample,
                                 float amount,
                                 EndErosionTile input,
                                 EndHydraulicErosionScratch scratch) {
        if (amount <= 0.0F) {
            return 0.0F;
        }
        int width = input.key().sampleWidth();
        float deposited = 0.0F;
        deposited += depositAt(sample.nodeZ * width + sample.nodeX,
                amount * (1.0F - sample.offsetX) * (1.0F - sample.offsetZ), scratch);
        deposited += depositAt(sample.nodeZ * width + sample.nodeX + 1,
                amount * sample.offsetX * (1.0F - sample.offsetZ), scratch);
        deposited += depositAt((sample.nodeZ + 1) * width + sample.nodeX,
                amount * (1.0F - sample.offsetX) * sample.offsetZ, scratch);
        deposited += depositAt((sample.nodeZ + 1) * width + sample.nodeX + 1,
                amount * sample.offsetX * sample.offsetZ, scratch);
        return deposited;
    }

    private static float depositAt(int index,
                                   float amount,
                                   EndHydraulicErosionScratch scratch) {
        float transfer = amount * transferFactor(scratch, index);
        float applied = transfer;
        if (applied <= 0.0F) {
            return 0.0F;
        }
        scratch.deltaUnits()[index] += applied;
        return applied;
    }

    private static void addFlux(float[] flux,
                                int width,
                                EndHydraulicErosionScratch.Sample sample,
                                float water) {
        int northWest = sample.nodeZ * width + sample.nodeX;
        flux[northWest] += water * (1.0F - sample.offsetX) * (1.0F - sample.offsetZ);
        flux[northWest + 1] += water * sample.offsetX * (1.0F - sample.offsetZ);
        flux[northWest + width] += water * (1.0F - sample.offsetX) * sample.offsetZ;
        flux[northWest + width + 1] += water * sample.offsetX * sample.offsetZ;
    }

    private static void sample(EndErosionTile input,
                               float x,
                               float z,
                               int sampleOriginX,
                               int sampleOriginZ,
                               EndHydraulicErosionScratch.Sample output) {
        int globalNodeX = floor(x);
        int globalNodeZ = floor(z);
        int nodeX = globalNodeX - sampleOriginX;
        int nodeZ = globalNodeZ - sampleOriginZ;
        float offsetX = x - globalNodeX;
        float offsetZ = z - globalNodeZ;
        int width = input.key().sampleWidth();
        int northWest = nodeZ * width + nodeX;
        float heightNorthWest = input.sourceTopBlocks(northWest) / REFERENCE_HEIGHT_BLOCKS;
        float heightNorthEast = input.sourceTopBlocks(northWest + 1) / REFERENCE_HEIGHT_BLOCKS;
        float heightSouthWest = input.sourceTopBlocks(northWest + width) / REFERENCE_HEIGHT_BLOCKS;
        float heightSouthEast = input.sourceTopBlocks(northWest + width + 1)
                / REFERENCE_HEIGHT_BLOCKS;
        float gradientX = (heightNorthEast - heightNorthWest) * (1.0F - offsetZ)
                + (heightSouthEast - heightSouthWest) * offsetZ;
        float gradientZ = (heightSouthWest - heightNorthWest) * (1.0F - offsetX)
                + (heightSouthEast - heightNorthEast) * offsetX;
        float height = heightNorthWest * (1.0F - offsetX) * (1.0F - offsetZ)
                + heightNorthEast * offsetX * (1.0F - offsetZ)
                + heightSouthWest * (1.0F - offsetX) * offsetZ
                + heightSouthEast * offsetX * offsetZ;
        output.nodeX = nodeX;
        output.nodeZ = nodeZ;
        output.offsetX = offsetX;
        output.offsetZ = offsetZ;
        output.height = height;
        output.gradientX = gradientX;
        output.gradientZ = gradientZ;
    }

    private static void initialiseBudgets(EndErosionTile input,
                                          EndHydraulicErosionScratch scratch,
                                          float[] activation) {
        for (int index = 0; index < input.key().cellCount(); index++) {
            float gate = activation(input, index);
            activation[index] = gate;
            float factor = gate * (1.0F - Math.clamp(
                    input.erosionResistance(index), 0.0F, 1.0F));
            float cutBlocks = Math.min(MAX_TRANSFER_BLOCKS,
                    Math.max(0.0F, input.availableThicknessBlocks(index)) * 0.25F);
            scratch.cutBudgetUnits()[index] = cutBlocks * factor / REFERENCE_HEIGHT_BLOCKS;
            scratch.transferFactor()[index] = factor;
        }
    }

    private static void finish(EndErosionTile input,
                               EndHydraulicErosionScratch scratch,
                               float[] finalTopBlocks,
                               float[] deltaBlocks,
                               float[] erosionStrength,
                               float[] drainagePotential,
                               float[] activation) {
        for (int index = 0; index < input.key().cellCount(); index++) {
            float boundedUnits = Math.clamp(scratch.deltaUnits()[index],
                    -scratch.cutBudgetUnits()[index], MAX_TRANSFER_BLOCKS
                            * scratch.transferFactor()[index] / REFERENCE_HEIGHT_BLOCKS);
            float delta = boundedUnits * REFERENCE_HEIGHT_BLOCKS;
            deltaBlocks[index] = delta;
            finalTopBlocks[index] = input.sourceTopBlocks(index) + delta;
            erosionStrength[index] = Math.clamp(
                    Math.max(0.0F, -delta) / MAX_TRANSFER_BLOCKS, 0.0F, 1.0F);
            float flux = scratch.flux()[index];
            drainagePotential[index] = activation[index] * flux / (flux + DRAINAGE_FLUX_SCALE);
        }
    }

    private static float activation(EndErosionTile input, int index) {
        if (input.erosionProtected(index) || input.archipelagoDominant(index)
                || input.areaFamily(index) == 0
                || input.outerActivation(index) <= 0.0F
                || input.landness(index) <= MIN_LANDNESS
                || input.availableThicknessBlocks(index) < MIN_THICKNESS_BLOCKS) {
            return 0.0F;
        }
        return Math.clamp(input.outerActivation(index), 0.0F, 1.0F)
                * Math.clamp(input.landness(index), 0.0F, 1.0F)
                * Math.clamp(input.inlandness(index), 0.0F, 1.0F);
    }

    private static float transferFactor(EndHydraulicErosionScratch scratch, int index) {
        return scratch.transferFactor()[index];
    }

    private static boolean inCore(int x, int z, EndErosionTileKey key) {
        int halo = key.haloSamples();
        return x >= halo && x < key.sampleWidth() - halo
                && z >= halo && z < key.sampleHeight() - halo;
    }

    private static void validateGeometry(EndErosionTileKey key,
                                         EndHydraulicErosionScratch scratch,
                                         float[]... outputs) {
        if (key.algorithmId() != ALGORITHM_ID || key.algorithmVersion() != ALGORITHM_VERSION) {
            throw new IllegalArgumentException("tile key does not identify hydraulic algorithm v1");
        }
        if (key.haloSamples() != REQUIRED_HALO_SAMPLES) {
            throw new IllegalArgumentException("hydraulic halo must be " + REQUIRED_HALO_SAMPLES);
        }
        if (key.sampleWidth() % SOURCE_CHUNK_SAMPLES != 0
                || key.sampleHeight() % SOURCE_CHUNK_SAMPLES != 0) {
            throw new IllegalArgumentException("hydraulic sample dimensions must align to source chunks");
        }
        if (key.sampleOriginBlockX() % key.sampleDistanceBlocks() != 0L
                || key.sampleOriginBlockZ() % key.sampleDistanceBlocks() != 0L) {
            throw new IllegalArgumentException("hydraulic sample origin must align to sample distance");
        }
        long sampleOriginX = key.sampleOriginBlockX() / key.sampleDistanceBlocks();
        long sampleOriginZ = key.sampleOriginBlockZ() / key.sampleDistanceBlocks();
        if (Math.floorMod(sampleOriginX, SOURCE_CHUNK_SAMPLES) != 0L
                || Math.floorMod(sampleOriginZ, SOURCE_CHUNK_SAMPLES) != 0L) {
            throw new IllegalArgumentException("hydraulic sample origin must align to source chunks");
        }
        if (scratch.capacity() != key.cellCount()) {
            throw new IllegalArgumentException("scratch capacity does not match tile geometry");
        }
        for (float[] output : outputs) {
            Objects.requireNonNull(output, "output");
            if (output.length != key.cellCount()) {
                throw new IllegalArgumentException("output length does not match tile geometry");
            }
        }
    }

    private static int brushEntryCount() {
        int count = 0;
        for (int z = -BRUSH_RADIUS_SAMPLES; z <= BRUSH_RADIUS_SAMPLES; z++) {
            for (int x = -BRUSH_RADIUS_SAMPLES; x <= BRUSH_RADIUS_SAMPLES; x++) {
                if (x * x + z * z < BRUSH_RADIUS_SAMPLES * BRUSH_RADIUS_SAMPLES) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int floor(float value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static final class MutableStats {
        private int droplets;
        private int steps;
        private int brushWrites;
        private int coreSteps;
        private int haloSteps;
        private int stationaryStops;
        private int boundaryStops;
        private int lifetimeStops;
        private double erodedBlocks;
        private double depositedBlocks;
        private double exportedSedimentBlocks;

        private EndHydraulicErosionTile.Stats snapshot() {
            return new EndHydraulicErosionTile.Stats(
                    this.droplets, this.steps, this.brushWrites,
                    this.coreSteps, this.haloSteps, this.stationaryStops,
                    this.boundaryStops, this.lifetimeStops, (float) this.erodedBlocks,
                    (float) this.depositedBlocks, (float) this.exportedSedimentBlocks);
        }
    }
}
