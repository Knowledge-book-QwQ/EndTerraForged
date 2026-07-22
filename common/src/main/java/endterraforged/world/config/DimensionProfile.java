/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Replaces RTF's implicit
 * "this is the overworld" assumption (MIT) with an explicit, per-dimension
 * profile so the End can be configured independently.
 */
package endterraforged.world.config;

/**
 * Read-only description of a dimension's worldgen shape.
 *
 * <p>This is the single seam through which sea level, height range, topology
 * and sea-floor behaviour reach the worldgen pipeline. Centralising them here
 * (rather than scattering {@code seaLevel} literals through {@code Levels},
 * {@code Heightmap}, etc. as RTF does) is what lets the same algorithm run
 * unchanged against an End with a sea, an End with no sea, or an End whose
 * sea has no floor.</p>
 *
 * <p><b>Thread safety:</b> implementations should be effectively immutable
 * after construction; the preset layer may rebuild a profile on config change
 * but never mutates one in place. Safe to share across parallel tile
 * generators.</p>
 */
public interface DimensionProfile {

    /** Vertical size of the world in blocks, e.g. 512 for the standard -256..255 End. */
    int worldHeight();

    /** World Y of the lowest block, e.g. -256 for the Standard specification. */
    int minY();

    /**
     * World Y of the sea level. Meaningful only when {@link #seaMode()}
     * {@link SeaMode#hasSea()}; otherwise callers should treat
     * {@link #islandBaselineY()} as the reference surface instead.
     */
    int seaLevelY();

    /**
     * World Y of the reference surface used when there is no sea — the
     * "ground floor" of floating islands. Erosion anchors to this in
     * {@link SeaMode#NONE}. In sea modes it is typically equal to
     * {@link #seaLevelY()}.
     */
    int islandBaselineY();

    SeaMode seaMode();

    TopologyMode topologyMode();

    /** Whether extra standalone floating islands are layered on top of the macro terrain. */
    boolean floatingIslandsEnabled();

    /**
     * Fine-grained macro-landmass tuning. Older tests and lightweight profile
     * implementations inherit compatibility defaults so adding new terrain
     * controls does not force every caller to copy boilerplate or silently
     * change legacy column tests into finite shelves.
     */
    default ContinentConfig continentConfig() {
        return ContinentConfig.legacyDefaults();
    }

    /** Global terrain shaping controls applied after macro landness is sampled. */
    default TerrainConfig terrainConfig() {
        return TerrainConfig.DEFAULT;
    }

    /** Underground terrain modifier controls. Defaults keep legacy terrain unchanged. */
    default SubsurfaceConfig subsurfaceConfig() {
        return SubsurfaceConfig.DEFAULT;
    }

    /**
     * Convenience: the Y that worldgen should treat as the primary surface
     * reference. This is {@link #islandBaselineY()} in {@link SeaMode#NONE}
     * and {@link #seaLevelY()} otherwise, so algorithm code does not need to
     * branch on sea mode at every call site.
     */
    default int surfaceY() {
        return seaMode().hasSea() ? seaLevelY() : islandBaselineY();
    }

    /** Exclusive world Y immediately above the dimension's highest block. */
    default int maxY() {
        return minY() + worldHeight();
    }
}
