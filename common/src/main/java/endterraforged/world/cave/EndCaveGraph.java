package endterraforged.world.cave;

import java.lang.ref.WeakReference;
import java.util.Objects;

import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.noise.NoiseMath;

/**
 * Deterministic region graph for landmark chambers and coarse cave corridors.
 *
 * <p>This is deliberately derived from existing scalar configs instead of
 * adding another preset object. The graph gives the 3D cave field a stable
 * structural skeleton while later work can add explicit rift/river node types.</p>
 *
 * <p><b>Thread safety.</b> Config-derived fields are immutable and graph
 * geometry is cached per worker thread. No mutable node state is shared by
 * parallel chunk-generation workers.</p>
 */
final class EndCaveGraph {

    static final EndCaveGraph DISABLED =
            new EndCaveGraph(CaveSystemConfig.DISABLED, CaveNetworkConfig.DEFAULT,
                    CaveChamberConfig.DEFAULT, 0);

    private static final int SEARCH_RADIUS = 1;
    private static final int NODE_GRID_SIZE = SEARCH_RADIUS * 2 + 2;
    private static final int NODE_GRID_CELL_COUNT = NODE_GRID_SIZE * NODE_GRID_SIZE;
    private static final int COLUMN_CACHE_SIZE = 64;
    private static final int COLUMN_CACHE_MASK = COLUMN_CACHE_SIZE - 1;
    private static final ThreadLocal<ColumnCache> COLUMNS = ThreadLocal.withInitial(ColumnCache::new);

    private final CaveSystemConfig system;
    private final CaveNetworkConfig network;
    private final CaveChamberConfig chambers;
    private final int seed;
    private final int cellSize;
    private final float minLandness;
    private final float activationChance;
    private final float connectionChance;
    private final float corridorRadiusAlpha;
    private final float riftChance;
    private final float flowChance;

    private EndCaveGraph(CaveSystemConfig system,
                         CaveNetworkConfig network,
                         CaveChamberConfig chambers,
                         int seed) {
        this.system = system;
        this.network = network;
        this.chambers = chambers;
        this.seed = seed + system.seedOffset();
        this.cellSize = Math.max(32, network.chamberSpacing());
        this.minLandness = network.minLandness();
        this.activationChance = NoiseMath.clamp(0.08F
                + chambers.chamberProbability() * 0.58F
                + network.networkDensity() * 0.18F
                + system.spectacleBias() * 0.16F,
                0.0F, 0.98F);
        this.connectionChance = NoiseMath.clamp(0.25F
                + network.networkDensity() * 0.35F
                + system.connectivity() * 0.25F
                + network.loopChance() * 0.15F,
                0.0F, 1.0F);
        this.corridorRadiusAlpha = NoiseMath.clamp(0.18F
                + system.connectivity() * 0.18F
                + network.branchingFactor() / 48.0F,
                0.12F, 0.45F);
        this.riftChance = NoiseMath.clamp(0.08F + system.spectacleBias() * 0.22F,
                0.0F, 0.4F);
        this.flowChance = NoiseMath.clamp(0.06F + network.loopChance() * 0.28F
                + system.connectivity() * 0.12F, 0.0F, 0.38F);
    }

    static EndCaveGraph fromConfig(SubsurfaceConfig config, int seed) {
        Objects.requireNonNull(config, "config");
        CaveSystemConfig system = config.caveSystemConfig();
        if (!system.enabled()) {
            return DISABLED;
        }
        return new EndCaveGraph(system, config.caveNetworkConfig(),
                config.caveChamberConfig(), seed);
    }

    boolean enabled() {
        return system.enabled();
    }

    float strength(float x, float z, float landness, float yNorm,
                   float terrainTopNorm, int worldHeight) {
        if (!enabled() || worldHeight <= 0 || yNorm > terrainTopNorm
                || landness < this.minLandness) {
            return 0.0F;
        }

        int cellX = floorDiv(x, cellSize);
        int cellZ = floorDiv(z, cellSize);
        float best = 0.0F;
        Column column = COLUMNS.get().column(this, cellX, cellZ);
        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                int gridX = dx + SEARCH_RADIUS;
                int gridZ = dz + SEARCH_RADIUS;
                Node node = column.nodeAt(gridX, gridZ);
                if (!node.active()) {
                    continue;
                }
                best = Math.max(best, nodeStrength(node, x, z,
                        yNorm, terrainTopNorm, worldHeight));
                best = Math.max(best, corridorStrength(node,
                        column.nodeAt(gridX + 1, gridZ), x, z,
                        yNorm, terrainTopNorm, worldHeight));
                best = Math.max(best, corridorStrength(node,
                        column.nodeAt(gridX, gridZ + 1), x, z,
                        yNorm, terrainTopNorm, worldHeight));
            }
        }
        return best;
    }

    float riftPreviewStrength(float x, float z, float landness) {
        return topDownStrength(x, z, landness, NodeKind.RIFT);
    }

    float flowPreviewStrength(float x, float z, float landness) {
        return topDownStrength(x, z, landness, NodeKind.FLOW);
    }

    private float topDownStrength(float x, float z, float landness, NodeKind kind) {
        if (!enabled() || landness < this.minLandness) {
            return 0.0F;
        }

        int cellX = floorDiv(x, cellSize);
        int cellZ = floorDiv(z, cellSize);
        float best = 0.0F;
        Column column = COLUMNS.get().column(this, cellX, cellZ);
        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                int gridX = dx + SEARCH_RADIUS;
                int gridZ = dz + SEARCH_RADIUS;
                Node node = column.nodeAt(gridX, gridZ);
                if (!node.active()) {
                    continue;
                }
                best = Math.max(best, nodeTopDownStrength(node, x, z, kind));
                if (kind == NodeKind.FLOW) {
                    best = Math.max(best, flowCorridorTopDownStrength(node,
                            column.nodeAt(gridX + 1, gridZ), x, z));
                    best = Math.max(best, flowCorridorTopDownStrength(node,
                            column.nodeAt(gridX, gridZ + 1), x, z));
                }
            }
        }

        float landGate = smoothstep(this.minLandness, 1.0F, landness);
        return NoiseMath.clamp(best * landGate, 0.0F, 1.0F);
    }

    private float nodeTopDownStrength(Node node, float x, float z, NodeKind kind) {
        if (node.kind() != kind) {
            return 0.0F;
        }
        return switch (kind) {
            case RIFT -> riftHorizontalStrength(node, x, z);
            case FLOW -> flowPocketHorizontalStrength(node, x, z);
            case CHAMBER -> chamberHorizontalStrength(node, x, z);
        };
    }

    private float nodeStrength(Node node, float x, float z, float yNorm,
                               float terrainTopNorm, int worldHeight) {
        return switch (node.kind()) {
            case RIFT -> riftStrength(node, x, z, yNorm, terrainTopNorm, worldHeight);
            case FLOW -> flowPocketStrength(node, x, z, yNorm, terrainTopNorm, worldHeight);
            case CHAMBER -> chamberStrength(node, x, z, yNorm, terrainTopNorm, worldHeight);
        };
    }

    private float chamberStrength(Node node, float x, float z, float yNorm,
                                  float terrainTopNorm, int worldHeight) {
        float horizontal = chamberHorizontalStrength(node, x, z);
        if (horizontal <= 0.0F) {
            return 0.0F;
        }
        return smoothstep(0.0F, 1.0F, horizontal)
                * verticalStrength(node.depth(), node.radius(), yNorm,
                        terrainTopNorm, worldHeight);
    }

    private float riftStrength(Node node, float x, float z, float yNorm,
                               float terrainTopNorm, int worldHeight) {
        float horizontal = riftHorizontalStrength(node, x, z);
        if (horizontal <= 0.0F) {
            return 0.0F;
        }
        float vertical = riftVerticalStrength(node.depth(), node.radius(),
                yNorm, terrainTopNorm, worldHeight);
        return smoothstep(0.0F, 1.0F, horizontal) * vertical;
    }

    private float flowPocketStrength(Node node, float x, float z, float yNorm,
                                     float terrainTopNorm, int worldHeight) {
        float horizontal = flowPocketHorizontalStrength(node, x, z);
        if (horizontal <= 0.0F) {
            return 0.0F;
        }
        float radius = Math.max(8.0F, node.radius() * 0.45F);
        float vertical = flowVerticalStrength(node.depth(), radius,
                yNorm, terrainTopNorm, worldHeight);
        return smoothstep(0.0F, 1.0F, horizontal) * vertical;
    }

    private float chamberHorizontalStrength(Node node, float x, float z) {
        float dx = x - node.x();
        float dz = z - node.z();
        return 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / node.radius();
    }

    private float riftHorizontalStrength(Node node, float x, float z) {
        float dx = Math.abs(x - node.x());
        float dz = Math.abs(z - node.z());
        float primary = Math.min(dx, dz);
        float secondary = Math.max(dx, dz);
        float width = Math.max(6.0F, node.radius() * 0.18F);
        float length = Math.max(width + 1.0F, node.radius() * 1.65F);
        return Math.min(1.0F - primary / width, 1.0F - secondary / length);
    }

    private float flowPocketHorizontalStrength(Node node, float x, float z) {
        float dx = x - node.x();
        float dz = z - node.z();
        float radius = Math.max(8.0F, node.radius() * 0.45F);
        return 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / radius;
    }

    private float flowCorridorTopDownStrength(Node a, Node b, float x, float z) {
        if (!b.active() || !connects(a, b)
                || (a.kind() != NodeKind.FLOW && b.kind() != NodeKind.FLOW)) {
            return 0.0F;
        }
        float abX = b.x() - a.x();
        float abZ = b.z() - a.z();
        float len2 = abX * abX + abZ * abZ;
        if (len2 <= 0.0F) {
            return 0.0F;
        }
        float alpha = NoiseMath.clamp(((x - a.x()) * abX + (z - a.z()) * abZ) / len2,
                0.0F, 1.0F);
        float lineX = a.x() + abX * alpha;
        float lineZ = a.z() + abZ * alpha;
        float dx = x - lineX;
        float dz = z - lineZ;
        float horizontal = 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / corridorRadius(a, b);
        return smoothstep(0.0F, 1.0F, horizontal);
    }

    private float corridorStrength(Node a, Node b, float x, float z, float yNorm,
                                   float terrainTopNorm, int worldHeight) {
        if (!b.active() || !connects(a, b)) {
            return 0.0F;
        }
        float abX = b.x() - a.x();
        float abZ = b.z() - a.z();
        float len2 = abX * abX + abZ * abZ;
        if (len2 <= 0.0F) {
            return 0.0F;
        }
        float alpha = NoiseMath.clamp(((x - a.x()) * abX + (z - a.z()) * abZ) / len2,
                0.0F, 1.0F);
        float lineX = a.x() + abX * alpha;
        float lineZ = a.z() + abZ * alpha;
        float dx = x - lineX;
        float dz = z - lineZ;
        float radius = corridorRadius(a, b);
        float horizontal = 1.0F - (float) Math.sqrt(dx * dx + dz * dz) / radius;
        if (horizontal <= 0.0F) {
            return 0.0F;
        }

        float depth = NoiseMath.lerp(a.depth(), b.depth(), alpha);
        float vertical = (a.kind() == NodeKind.FLOW || b.kind() == NodeKind.FLOW)
                ? flowVerticalStrength(depth, radius, yNorm, terrainTopNorm, worldHeight)
                : verticalStrength(depth, radius, yNorm, terrainTopNorm, worldHeight);
        return smoothstep(0.0F, 1.0F, horizontal) * vertical;
    }

    private float verticalStrength(float depthBlocks, float radius, float yNorm,
                                   float terrainTopNorm, int worldHeight) {
        float sampleDepth = (terrainTopNorm - yNorm) * worldHeight;
        float halfHeight = Math.max(4.0F, radius * chambers.verticalStretch() * 0.55F);
        float vertical = 1.0F - Math.abs(sampleDepth - depthBlocks) / halfHeight;
        return smoothstep(0.0F, 1.0F, vertical);
    }

    private float riftVerticalStrength(float depthBlocks, float radius, float yNorm,
                                       float terrainTopNorm, int worldHeight) {
        float sampleDepth = (terrainTopNorm - yNorm) * worldHeight;
        float top = Math.max(0.0F, depthBlocks - radius * (1.2F + system.spectacleBias()));
        float bottom = Math.min(system.depthEnd(), depthBlocks + radius * 1.8F);
        float topGate = smoothstep(top, top + Math.max(4.0F, radius * 0.18F), sampleDepth);
        float bottomGate = 1.0F - smoothstep(bottom - Math.max(4.0F, radius * 0.25F),
                bottom, sampleDepth);
        return topGate * bottomGate;
    }

    private float flowVerticalStrength(float depthBlocks, float radius, float yNorm,
                                       float terrainTopNorm, int worldHeight) {
        float sampleDepth = (terrainTopNorm - yNorm) * worldHeight;
        float halfHeight = Math.max(3.0F, radius * 0.28F);
        float vertical = 1.0F - Math.abs(sampleDepth - depthBlocks) / halfHeight;
        return smoothstep(0.0F, 1.0F, vertical);
    }

    private boolean connects(Node a, Node b) {
        float slope = Math.abs(a.depth() - b.depth())
                / Math.max(1.0F, distance(a.x(), a.z(), b.x(), b.z()));
        if (slope > network.maxSlope()) {
            return false;
        }
        return random(a.cellX(), a.cellZ(), b.cellX(), b.cellZ(), 71) <= connectionChance;
    }

    private float corridorRadius(Node a, Node b) {
        float base = Math.min(a.radius(), b.radius());
        return Math.max(6.0F, base * corridorRadiusAlpha);
    }

    private Node node(int cellX, int cellZ, Node target) {
        float activation = random(cellX, cellZ, 0, 0, 11);
        if (activation > this.activationChance) {
            return target.set(false, NodeKind.CHAMBER, cellX, cellZ,
                    0.0F, 0.0F, 0.0F, 0.0F);
        }

        float jitterX = random(cellX, cellZ, 0, 0, 23) - 0.5F;
        float jitterZ = random(cellX, cellZ, 0, 0, 29) - 0.5F;
        float x = (cellX + 0.5F + jitterX * 0.58F) * cellSize;
        float z = (cellZ + 0.5F + jitterZ * 0.58F) * cellSize;
        float radiusAlpha = NoiseMath.clamp(random(cellX, cellZ, 0, 0, 37)
                + system.spectacleBias() * 0.25F, 0.0F, 1.0F);
        float radius = NoiseMath.lerp(chambers.minRadius(), chambers.maxRadius(), radiusAlpha);
        float depthAlpha = NoiseMath.clamp(random(cellX, cellZ, 0, 0, 43)
                * (1.0F - chambers.floorBias() * 0.35F)
                + chambers.floorBias() * 0.35F, 0.0F, 1.0F);
        float depth = NoiseMath.lerp(system.depthStart(), system.depthEnd(), depthAlpha);
        return target.set(true, nodeKind(cellX, cellZ), cellX, cellZ, x, z, radius, depth);
    }

    private NodeKind nodeKind(int cellX, int cellZ) {
        float roll = random(cellX, cellZ, 0, 0, 53);
        if (roll < riftChance) {
            return NodeKind.RIFT;
        }
        if (roll < riftChance + flowChance) {
            return NodeKind.FLOW;
        }
        return NodeKind.CHAMBER;
    }

    private float random(int a, int b, int c, int d, int salt) {
        long hash = seed;
        hash ^= mix(a * 0x9E3779B97F4A7C15L);
        hash ^= mix(b * 0xC2B2AE3D27D4EB4FL);
        hash ^= mix(c * 0x165667B19E3779F9L);
        hash ^= mix(d * 0x85EBCA77C2B2AE63L);
        hash ^= mix(salt * 0x27D4EB2FL);
        return ((mix(hash) >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static int floorDiv(float value, int divisor) {
        return Math.floorDiv((int) Math.floor(value), divisor);
    }

    private static float distance(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float width = Math.max(0.0001F, edge1 - edge0);
        float alpha = NoiseMath.clamp((value - edge0) / width, 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private enum NodeKind {
        CHAMBER,
        RIFT,
        FLOW
    }

    /**
     * Per-worker cache for graph geometry. Node position, activation and depth
     * only depend on X/Z cells, while world generation evaluates many Y values
     * for that same column.
     */
    private static final class ColumnCache {
        private final Column[] columns = createColumns();
        private WeakReference<EndCaveGraph> owner = new WeakReference<>(null);

        private Column column(EndCaveGraph graph, int cellX, int cellZ) {
            if (this.owner.get() != graph) {
                this.owner = new WeakReference<>(graph);
                for (Column column : this.columns) {
                    column.invalidate();
                }
            }
            int index = cacheIndex(cellX, cellZ);
            Column column = this.columns[index];
            if (!column.matches(cellX, cellZ)) {
                column.refresh(graph, cellX, cellZ);
            }
            return column;
        }

        private static int cacheIndex(int cellX, int cellZ) {
            int hash = cellX;
            hash = 31 * hash + cellZ;
            hash ^= hash >>> 16;
            hash *= 0x7feb352d;
            hash ^= hash >>> 15;
            return hash & COLUMN_CACHE_MASK;
        }

        private static Column[] createColumns() {
            Column[] columns = new Column[COLUMN_CACHE_SIZE];
            for (int index = 0; index < columns.length; index++) {
                columns[index] = new Column();
            }
            return columns;
        }
    }

    private static final class Column {
        private final Node[] nodes = createNodes();
        private boolean initialized;
        private int cellX;
        private int cellZ;

        private boolean matches(int cellX, int cellZ) {
            return this.initialized && this.cellX == cellX && this.cellZ == cellZ;
        }

        private void invalidate() {
            this.initialized = false;
        }

        private void refresh(EndCaveGraph graph, int cellX, int cellZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            for (int gridZ = 0; gridZ < NODE_GRID_SIZE; gridZ++) {
                for (int gridX = 0; gridX < NODE_GRID_SIZE; gridX++) {
                    int nodeX = cellX - SEARCH_RADIUS + gridX;
                    int nodeZ = cellZ - SEARCH_RADIUS + gridZ;
                    graph.node(nodeX, nodeZ, this.nodes[gridZ * NODE_GRID_SIZE + gridX]);
                }
            }
            this.initialized = true;
        }

        private Node nodeAt(int gridX, int gridZ) {
            return this.nodes[gridZ * NODE_GRID_SIZE + gridX];
        }

        private static Node[] createNodes() {
            Node[] nodes = new Node[NODE_GRID_CELL_COUNT];
            for (int index = 0; index < nodes.length; index++) {
                nodes[index] = new Node();
            }
            return nodes;
        }
    }

    private static final class Node {
        private boolean active;
        private NodeKind kind;
        private int cellX;
        private int cellZ;
        private float x;
        private float z;
        private float radius;
        private float depth;

        private Node set(boolean active, NodeKind kind, int cellX, int cellZ,
                         float x, float z, float radius, float depth) {
            this.active = active;
            this.kind = kind;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.depth = depth;
            return this;
        }

        private boolean active() { return active; }
        private NodeKind kind() { return kind; }
        private int cellX() { return cellX; }
        private int cellZ() { return cellZ; }
        private float x() { return x; }
        private float z() { return z; }
        private float radius() { return radius; }
        private float depth() { return depth; }
    }
}
