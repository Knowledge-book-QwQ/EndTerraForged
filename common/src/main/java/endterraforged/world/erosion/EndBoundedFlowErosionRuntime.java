package endterraforged.world.erosion;

import java.util.Objects;

/** Immutable test-only bounded Priority-Flood and stream-power candidate. */
final class EndBoundedFlowErosionRuntime {

    static final long ALGORITHM_ID = 0x4554465F464C4F57L;
    static final int ALGORITHM_VERSION = 1;
    static final int PRIORITY_RADIUS_SAMPLES = 3;
    static final int FLOW_STEPS = 12;
    static final int ROUTING_RADIUS_SAMPLES = 1;
    static final int REQUIRED_HALO_SAMPLES =
            PRIORITY_RADIUS_SAMPLES + FLOW_STEPS + ROUTING_RADIUS_SAMPLES;
    static final float MAX_INCISION_BLOCKS = 10.0F;
    static final float MIN_LANDNESS = 0.12F;
    static final float MIN_THICKNESS_BLOCKS = 8.0F;
    static final float MAX_THICKNESS_FRACTION = 0.20F;
    static final float SPLIT_RATIO = 0.35F;
    static final float FLOW_SCALE_SAMPLES = 8.0F;
    static final float SLOPE_SCALE = 0.15F;

    private static final int FLOOD_SIDE = PRIORITY_RADIUS_SAMPLES * 2 + 1;
    private static final int FLOOD_CELLS = FLOOD_SIDE * FLOOD_SIDE;
    private static final float SQRT_TWO = 1.4142135623730951F;
    private static final long TIE_X = 0x9E3779B97F4A7C15L;
    private static final long TIE_Z = 0xC2B2AE3D27D4EB4FL;

    private final int[] directionX = {0, 0, 1, 1, 1, 0, -1, -1, -1};
    private final int[] directionZ = {0, -1, -1, 0, 1, 1, 1, 0, -1};
    private final float[] directionDistance = {
            0.0F, 1.0F, SQRT_TWO, 1.0F, SQRT_TWO,
            1.0F, SQRT_TWO, 1.0F, SQRT_TWO
    };

    long primitiveBytes() {
        return (long) this.directionX.length
                * (Integer.BYTES * 2L + Float.BYTES);
    }

    int floodCells() {
        return FLOOD_CELLS;
    }

    EndBoundedFlowErosionTile apply(EndErosionTile input,
                                    EndBoundedFlowErosionScratch scratch,
                                    float[] finalTopBlocks,
                                    float[] deltaBlocks,
                                    float[] erosionStrength,
                                    float[] drainagePotential,
                                    float[] activation,
                                    byte[] dominantDirection) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(scratch, "scratch");
        EndErosionTileKey key = input.key();
        validateGeometry(key, scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation, dominantDirection);
        scratch.clear();

        MutableStats stats = new MutableStats();
        initialise(input, scratch, activation);
        resolvePriorityFlood(input, scratch, activation, stats);
        resolveRouting(input, scratch, activation, stats);
        propagateFlow(input, scratch, activation, stats);
        finish(input, scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation,
                dominantDirection, stats);
        return EndBoundedFlowErosionTile.fromOwnedArrays(
                key, finalTopBlocks, deltaBlocks, erosionStrength,
                drainagePotential, activation, dominantDirection, stats.snapshot());
    }

    private static void initialise(EndErosionTile input,
                                   EndBoundedFlowErosionScratch scratch,
                                   float[] activation) {
        for (int index = 0; index < input.key().cellCount(); index++) {
            scratch.routingTopBlocks()[index] = input.sourceTopBlocks(index);
            activation[index] = activation(input, index);
        }
    }

    private void resolvePriorityFlood(EndErosionTile input,
                                      EndBoundedFlowErosionScratch scratch,
                                      float[] activation,
                                      MutableStats stats) {
        EndErosionTileKey key = input.key();
        int width = key.sampleWidth();
        int height = key.sampleHeight();
        int radius = PRIORITY_RADIUS_SAMPLES;
        for (int z = radius; z < height - radius; z++) {
            for (int x = radius; x < width - radius; x++) {
                int index = z * width + x;
                if (activation[index] <= 0.0F || !needsPriorityFlood(input, x, z)) {
                    continue;
                }
                long result = queryLocalSpill(input, scratch, x, z, stats);
                float spill = Float.intBitsToFloat((int) (result >>> 32));
                float source = input.sourceTopBlocks(index);
                float routingTop = Math.max(source, spill);
                float spillDepth = Math.max(0.0F, routingTop - source);
                scratch.routingTopBlocks()[index] = routingTop;
                scratch.spillDepthBlocks()[index] = spillDepth;
                scratch.escapeDirection()[index] = (byte) result;
                stats.maximumSpillDepthBlocks = Math.max(
                        stats.maximumSpillDepthBlocks, spillDepth);
            }
        }
    }

    private boolean needsPriorityFlood(EndErosionTile input, int x, int z) {
        int width = input.key().sampleWidth();
        float source = input.sourceTopBlocks(z * width + x);
        boolean hasDifferentNeighbor = false;
        for (byte direction = 1; direction <= 8; direction++) {
            int neighbor = (z + this.directionZ[direction]) * width
                    + x + this.directionX[direction];
            float neighborTop = input.sourceTopBlocks(neighbor);
            if (neighborTop < source) {
                return false;
            }
            hasDifferentNeighbor |= Float.floatToIntBits(neighborTop)
                    != Float.floatToIntBits(source);
        }
        return hasDifferentNeighbor;
    }

    private long queryLocalSpill(EndErosionTile input,
                                 EndBoundedFlowErosionScratch scratch,
                                 int centerX,
                                 int centerZ,
                                 MutableStats stats) {
        scratch.beginFlood();
        stats.priorityQueries++;
        EndErosionTileKey key = input.key();
        long sampleOriginX = key.sampleOriginBlockX() / key.sampleDistanceBlocks();
        long sampleOriginZ = key.sampleOriginBlockZ() / key.sampleDistanceBlocks();
        float[] best = scratch.floodBest();
        int[] parent = scratch.floodParent();
        long[] tie = scratch.floodTie();

        for (int localZ = 0; localZ < FLOOD_SIDE; localZ++) {
            for (int localX = 0; localX < FLOOD_SIDE; localX++) {
                int node = localZ * FLOOD_SIDE + localX;
                long globalX = sampleOriginX + centerX
                        + localX - PRIORITY_RADIUS_SAMPLES;
                long globalZ = sampleOriginZ + centerZ
                        + localZ - PRIORITY_RADIUS_SAMPLES;
                tie[node] = tieRank(key.worldSeed(), globalX, globalZ);
            }
        }

        int center = PRIORITY_RADIUS_SAMPLES * FLOOD_SIDE + PRIORITY_RADIUS_SAMPLES;
        best[center] = input.sourceTopBlocks(centerZ * key.sampleWidth() + centerX);
        heapInsertOrDecrease(scratch, center, stats);
        while (scratch.floodHeapSize() > 0) {
            int node = heapPop(scratch, stats);
            int localX = node % FLOOD_SIDE;
            int localZ = node / FLOOD_SIDE;
            if (localX == 0 || localX == FLOOD_SIDE - 1
                    || localZ == 0 || localZ == FLOOD_SIDE - 1) {
                int child = node;
                while (parent[child] >= 0 && parent[child] != center) {
                    child = parent[child];
                }
                byte direction = 0;
                if (parent[child] == center) {
                    direction = directionFromDelta(
                            child % FLOOD_SIDE - PRIORITY_RADIUS_SAMPLES,
                            child / FLOOD_SIDE - PRIORITY_RADIUS_SAMPLES);
                }
                return pack(best[node], direction);
            }

            for (byte direction = 1; direction <= 8; direction++) {
                int neighborX = localX + this.directionX[direction];
                int neighborZ = localZ + this.directionZ[direction];
                if (neighborX < 0 || neighborX >= FLOOD_SIDE
                        || neighborZ < 0 || neighborZ >= FLOOD_SIDE) {
                    continue;
                }
                int neighbor = neighborZ * FLOOD_SIDE + neighborX;
                if (scratch.floodHeapPositions()[neighbor] == -2) {
                    continue;
                }
                int tileX = centerX + neighborX - PRIORITY_RADIUS_SAMPLES;
                int tileZ = centerZ + neighborZ - PRIORITY_RADIUS_SAMPLES;
                float candidate = Math.max(
                        best[node], input.sourceTopBlocks(tileZ * key.sampleWidth() + tileX));
                int comparison = Float.compare(candidate, best[neighbor]);
                if (comparison > 0 || comparison == 0
                        && !betterParent(node, parent[neighbor], tie)) {
                    continue;
                }
                best[neighbor] = candidate;
                parent[neighbor] = node;
                heapInsertOrDecrease(scratch, neighbor, stats);
            }
        }
        return pack(input.sourceTopBlocks(centerZ * key.sampleWidth() + centerX), (byte) 0);
    }

    private void resolveRouting(EndErosionTile input,
                                EndBoundedFlowErosionScratch scratch,
                                float[] activation,
                                MutableStats stats) {
        EndErosionTileKey key = input.key();
        int width = key.sampleWidth();
        int radius = PRIORITY_RADIUS_SAMPLES;
        long sampleOriginX = key.sampleOriginBlockX() / key.sampleDistanceBlocks();
        long sampleOriginZ = key.sampleOriginBlockZ() / key.sampleDistanceBlocks();
        for (int z = radius; z < key.sampleHeight() - radius; z++) {
            for (int x = radius; x < width - radius; x++) {
                int index = z * width + x;
                if (activation[index] <= 0.0F) {
                    continue;
                }
                float firstScore = 0.0F;
                float secondScore = 0.0F;
                long firstTie = 0L;
                long secondTie = 0L;
                byte firstDirection = 0;
                byte secondDirection = 0;
                float currentTop = scratch.routingTopBlocks()[index];
                for (byte direction = 1; direction <= 8; direction++) {
                    int neighborX = x + this.directionX[direction];
                    int neighborZ = z + this.directionZ[direction];
                    int neighbor = neighborZ * width + neighborX;
                    float drop = currentTop - scratch.routingTopBlocks()[neighbor];
                    if (drop <= 0.0F) {
                        continue;
                    }
                    float score = drop / (this.directionDistance[direction]
                            * key.sampleDistanceBlocks());
                    long receiverTie = tieRank(key.worldSeed(),
                            sampleOriginX + neighborX, sampleOriginZ + neighborZ);
                    if (betterRoute(score, receiverTie, firstScore, firstTie, firstDirection)) {
                        secondScore = firstScore;
                        secondTie = firstTie;
                        secondDirection = firstDirection;
                        firstScore = score;
                        firstTie = receiverTie;
                        firstDirection = direction;
                    } else if (betterRoute(
                            score, receiverTie, secondScore, secondTie, secondDirection)) {
                        secondScore = score;
                        secondTie = receiverTie;
                        secondDirection = direction;
                    }
                }

                if (firstDirection == 0 && scratch.spillDepthBlocks()[index] > 0.0F) {
                    byte escape = scratch.escapeDirection()[index];
                    if (escape != 0 && acceptsFlatEscape(
                            key, scratch, x, z, index, escape, sampleOriginX, sampleOriginZ)) {
                        firstDirection = escape;
                        firstScore = scratch.spillDepthBlocks()[index]
                                / (PRIORITY_RADIUS_SAMPLES * key.sampleDistanceBlocks());
                    }
                }

                if (firstDirection == 0) {
                    continue;
                }
                scratch.dominantDirection()[index] = firstDirection;
                if (secondDirection != 0 && secondScore >= firstScore * SPLIT_RATIO) {
                    scratch.secondaryDirection()[index] = secondDirection;
                    scratch.dominantWeight()[index] = firstScore / (firstScore + secondScore);
                    stats.splitCells++;
                } else {
                    scratch.dominantWeight()[index] = 1.0F;
                }
                stats.routedCells++;
            }
        }
    }

    private boolean acceptsFlatEscape(EndErosionTileKey key,
                                      EndBoundedFlowErosionScratch scratch,
                                      int x,
                                      int z,
                                      int index,
                                      byte direction,
                                      long sampleOriginX,
                                      long sampleOriginZ) {
        int neighborX = x + this.directionX[direction];
        int neighborZ = z + this.directionZ[direction];
        int neighbor = neighborZ * key.sampleWidth() + neighborX;
        float current = scratch.routingTopBlocks()[index];
        float next = scratch.routingTopBlocks()[neighbor];
        if (next < current) {
            return true;
        }
        if (Float.floatToIntBits(next) != Float.floatToIntBits(current)) {
            return false;
        }
        long currentTie = tieRank(key.worldSeed(), sampleOriginX + x, sampleOriginZ + z);
        long nextTie = tieRank(
                key.worldSeed(), sampleOriginX + neighborX, sampleOriginZ + neighborZ);
        return Long.compareUnsigned(nextTie, currentTie) < 0;
    }

    private void propagateFlow(EndErosionTile input,
                               EndBoundedFlowErosionScratch scratch,
                               float[] activation,
                               MutableStats stats) {
        EndErosionTileKey key = input.key();
        int width = key.sampleWidth();
        int radius = PRIORITY_RADIUS_SAMPLES;
        float sampleArea = (float) key.sampleDistanceBlocks() * key.sampleDistanceBlocks();
        for (int z = radius; z < key.sampleHeight() - radius; z++) {
            for (int x = radius; x < width - radius; x++) {
                int index = z * width + x;
                float sourceArea = activation[index] * sampleArea;
                scratch.currentFlux()[index] = sourceArea;
                scratch.accumulatedFlux()[index] = sourceArea;
            }
        }

        for (int step = 0; step < FLOW_STEPS; step++) {
            scratch.clearNextFlux();
            for (int z = radius; z < key.sampleHeight() - radius; z++) {
                for (int x = radius; x < width - radius; x++) {
                    int index = z * width + x;
                    float area = scratch.currentFlux()[index];
                    if (area <= 0.0F) {
                        continue;
                    }
                    byte dominant = scratch.dominantDirection()[index];
                    if (dominant == 0) {
                        stats.terminalExportArea += area;
                        continue;
                    }
                    byte secondary = scratch.secondaryDirection()[index];
                    float dominantArea = secondary == 0
                            ? area : area * scratch.dominantWeight()[index];
                    routeArea(key, scratch, activation, x, z,
                            dominant, dominantArea, stats);
                    if (secondary != 0) {
                        routeArea(key, scratch, activation, x, z,
                                secondary, area - dominantArea, stats);
                    }
                }
            }
            scratch.swapFlux();
            for (int index = 0; index < key.cellCount(); index++) {
                scratch.accumulatedFlux()[index] += scratch.currentFlux()[index];
            }
        }
        for (float area : scratch.currentFlux()) {
            stats.truncatedArea += area;
        }
    }

    private void routeArea(EndErosionTileKey key,
                           EndBoundedFlowErosionScratch scratch,
                           float[] activation,
                           int x,
                           int z,
                           byte direction,
                           float area,
                           MutableStats stats) {
        if (area <= 0.0F) {
            return;
        }
        int neighborX = x + this.directionX[direction];
        int neighborZ = z + this.directionZ[direction];
        int neighbor = neighborZ * key.sampleWidth() + neighborX;
        if (activation[neighbor] <= 0.0F) {
            stats.terminalExportArea += area;
            return;
        }
        scratch.nextFlux()[neighbor] += area;
        stats.flowTransfers++;
    }

    private void finish(EndErosionTile input,
                        EndBoundedFlowErosionScratch scratch,
                        float[] finalTopBlocks,
                        float[] deltaBlocks,
                        float[] erosionStrength,
                        float[] drainagePotential,
                        float[] activation,
                        byte[] dominantDirection,
                        MutableStats stats) {
        EndErosionTileKey key = input.key();
        int width = key.sampleWidth();
        float sampleArea = (float) key.sampleDistanceBlocks() * key.sampleDistanceBlocks();
        for (int index = 0; index < key.cellCount(); index++) {
            float source = input.sourceTopBlocks(index);
            float gate = activation[index];
            float contributingSamples = scratch.accumulatedFlux()[index] / sampleArea;
            float upstreamSamples = Math.max(0.0F, contributingSamples - gate);
            float drainage = gate * upstreamSamples / (upstreamSamples + FLOW_SCALE_SAMPLES);
            byte direction = scratch.dominantDirection()[index];
            float slope = 0.0F;
            if (direction != 0) {
                int x = index % width;
                int z = index / width;
                int neighbor = (z + this.directionZ[direction]) * width
                        + x + this.directionX[direction];
                float rawDrop = Math.max(
                        0.0F, source - input.sourceTopBlocks(neighbor));
                slope = rawDrop / (this.directionDistance[direction]
                        * key.sampleDistanceBlocks());
                if (slope == 0.0F && scratch.spillDepthBlocks()[index] > 0.0F) {
                    slope = scratch.spillDepthBlocks()[index]
                            / (PRIORITY_RADIUS_SAMPLES * key.sampleDistanceBlocks());
                }
            }
            float slopeStrength = slope / (slope + SLOPE_SCALE);
            float power = (float) Math.sqrt(drainage) * slopeStrength;
            float factor = gate * (1.0F - Math.clamp(
                    input.erosionResistance(index), 0.0F, 1.0F));
            float budget = Math.min(MAX_INCISION_BLOCKS,
                    Math.max(0.0F, input.availableThicknessBlocks(index))
                            * MAX_THICKNESS_FRACTION);
            float roughnessFactor = 0.5F + 0.5F
                    * Math.clamp(input.roughness(index), 0.0F, 1.0F);
            float cut = Math.min(budget, MAX_INCISION_BLOCKS * power * roughnessFactor)
                    * factor;
            if (!Float.isFinite(cut) || cut < 0.0F) {
                cut = 0.0F;
            }
            finalTopBlocks[index] = source - cut;
            deltaBlocks[index] = cut == 0.0F ? 0.0F : -cut;
            erosionStrength[index] = Math.clamp(
                    cut / MAX_INCISION_BLOCKS, 0.0F, 1.0F);
            drainagePotential[index] = Math.clamp(drainage, 0.0F, 1.0F);
            dominantDirection[index] = direction;
            if (cut > 0.0F) {
                stats.incisionCells++;
                stats.totalCutBlocks += cut;
            }
        }
    }

    private void heapInsertOrDecrease(EndBoundedFlowErosionScratch scratch,
                                      int node,
                                      MutableStats stats) {
        int[] positions = scratch.floodHeapPositions();
        int position = positions[node];
        if (position == -2) {
            return;
        }
        if (position < 0) {
            position = scratch.floodHeapSize();
            scratch.floodHeapNodes()[position] = node;
            positions[node] = position;
            scratch.floodHeapSize(position + 1);
            stats.heapPushes++;
        }
        heapBubbleUp(scratch, position);
    }

    private int heapPop(EndBoundedFlowErosionScratch scratch, MutableStats stats) {
        int[] heap = scratch.floodHeapNodes();
        int[] positions = scratch.floodHeapPositions();
        int root = heap[0];
        int size = scratch.floodHeapSize() - 1;
        scratch.floodHeapSize(size);
        positions[root] = -2;
        if (size > 0) {
            int last = heap[size];
            heap[0] = last;
            positions[last] = 0;
            heapBubbleDown(scratch, 0);
        }
        stats.heapPops++;
        return root;
    }

    private void heapBubbleUp(EndBoundedFlowErosionScratch scratch, int position) {
        int[] heap = scratch.floodHeapNodes();
        int[] positions = scratch.floodHeapPositions();
        while (position > 0) {
            int parent = (position - 1) >>> 1;
            if (!heapLess(scratch, heap[position], heap[parent])) {
                return;
            }
            int node = heap[position];
            heap[position] = heap[parent];
            heap[parent] = node;
            positions[heap[position]] = position;
            positions[heap[parent]] = parent;
            position = parent;
        }
    }

    private void heapBubbleDown(EndBoundedFlowErosionScratch scratch, int position) {
        int[] heap = scratch.floodHeapNodes();
        int[] positions = scratch.floodHeapPositions();
        int size = scratch.floodHeapSize();
        while (true) {
            int left = position * 2 + 1;
            if (left >= size) {
                return;
            }
            int right = left + 1;
            int smallest = right < size && heapLess(scratch, heap[right], heap[left])
                    ? right : left;
            if (!heapLess(scratch, heap[smallest], heap[position])) {
                return;
            }
            int node = heap[position];
            heap[position] = heap[smallest];
            heap[smallest] = node;
            positions[heap[position]] = position;
            positions[heap[smallest]] = smallest;
            position = smallest;
        }
    }

    private static boolean heapLess(EndBoundedFlowErosionScratch scratch,
                                    int left,
                                    int right) {
        int comparison = Float.compare(
                scratch.floodBest()[left], scratch.floodBest()[right]);
        if (comparison != 0) {
            return comparison < 0;
        }
        return Long.compareUnsigned(
                scratch.floodTie()[left], scratch.floodTie()[right]) < 0;
    }

    private static boolean betterParent(int candidate,
                                        int current,
                                        long[] tie) {
        return current < 0 || Long.compareUnsigned(tie[candidate], tie[current]) < 0;
    }

    private static boolean betterRoute(float score,
                                       long tie,
                                       float currentScore,
                                       long currentTie,
                                       byte currentDirection) {
        int comparison = Float.compare(score, currentScore);
        return comparison > 0 || comparison == 0
                && (currentDirection == 0 || Long.compareUnsigned(tie, currentTie) < 0);
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

    private static long tieRank(long seed, long sampleX, long sampleZ) {
        long value = seed ^ sampleX * TIE_X ^ sampleZ * TIE_Z;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static byte directionFromDelta(int deltaX, int deltaZ) {
        int x = Integer.signum(deltaX);
        int z = Integer.signum(deltaZ);
        if (x == 0 && z < 0) {
            return 1;
        }
        if (x > 0 && z < 0) {
            return 2;
        }
        if (x > 0 && z == 0) {
            return 3;
        }
        if (x > 0) {
            return 4;
        }
        if (x == 0 && z > 0) {
            return 5;
        }
        if (x < 0 && z > 0) {
            return 6;
        }
        if (x < 0 && z == 0) {
            return 7;
        }
        if (x < 0) {
            return 8;
        }
        return 0;
    }

    private static long pack(float spill, byte direction) {
        return (long) Float.floatToRawIntBits(spill) << 32 | direction & 0xFFL;
    }

    private static void validateGeometry(EndErosionTileKey key,
                                         EndBoundedFlowErosionScratch scratch,
                                         float[] finalTopBlocks,
                                         float[] deltaBlocks,
                                         float[] erosionStrength,
                                         float[] drainagePotential,
                                         float[] activation,
                                         byte[] dominantDirection) {
        if (key.algorithmId() != ALGORITHM_ID || key.algorithmVersion() != ALGORITHM_VERSION) {
            throw new IllegalArgumentException("tile key does not identify bounded flow algorithm v1");
        }
        if (key.haloSamples() != REQUIRED_HALO_SAMPLES) {
            throw new IllegalArgumentException("bounded flow halo must be " + REQUIRED_HALO_SAMPLES);
        }
        if (key.sampleOriginBlockX() % key.sampleDistanceBlocks() != 0L
                || key.sampleOriginBlockZ() % key.sampleDistanceBlocks() != 0L) {
            throw new IllegalArgumentException("bounded flow origin must align to sample distance");
        }
        if (scratch.capacity() != key.cellCount() || scratch.floodCapacity() != FLOOD_CELLS) {
            throw new IllegalArgumentException("scratch does not match bounded flow geometry");
        }
        float[][] outputs = {
                finalTopBlocks, deltaBlocks, erosionStrength, drainagePotential, activation
        };
        for (float[] output : outputs) {
            Objects.requireNonNull(output, "output");
            if (output.length != key.cellCount()) {
                throw new IllegalArgumentException("output length does not match tile geometry");
            }
        }
        Objects.requireNonNull(dominantDirection, "dominantDirection");
        if (dominantDirection.length != key.cellCount()) {
            throw new IllegalArgumentException("direction length does not match tile geometry");
        }
    }

    private static final class MutableStats {
        private int priorityQueries;
        private int heapPushes;
        private int heapPops;
        private int routedCells;
        private int splitCells;
        private int flowTransfers;
        private int incisionCells;
        private double terminalExportArea;
        private double truncatedArea;
        private double totalCutBlocks;
        private float maximumSpillDepthBlocks;

        private EndBoundedFlowErosionTile.Stats snapshot() {
            return new EndBoundedFlowErosionTile.Stats(
                    this.priorityQueries, this.heapPushes, this.heapPops,
                    this.routedCells, this.splitCells, this.flowTransfers,
                    this.incisionCells, (float) this.terminalExportArea,
                    (float) this.truncatedArea, (float) this.totalCutBlocks,
                    this.maximumSpillDepthBlocks);
        }
    }
}
