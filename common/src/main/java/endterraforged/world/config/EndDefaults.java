/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Production default
 * DimensionProfile for the End — the stage-3.2 EndPreset will supersede
 * this once serialisation lands; for now this gives the MC-integration
 * layer a concrete profile to build EndHeightmap from.
 */
package endterraforged.world.config;

/**
 * The End's default production {@link DimensionProfile}: full RTF-matching
 * height range ({@code -2032..2032}, 4064 blocks), no sea, discrete
 * islands, no extra floating-islands layer.
 *
 * <p><b>Why a record and not a JSON-loaded preset.</b> Stage 3.2 will
 * introduce {@code EndPreset} + DFU codec so the profile is configurable
 * per-world. Until that lands the MC-integration Mixin needs a concrete
 * profile instance to build {@link endterraforged.world.heightmap.EndHeightmap}
 * from; this record is that instance. It mirrors the values
 * {@code TestProfile.defaultEnd()} uses in tests, so production and test
 * terrain match.</p>
 *
 * <p><b>Values.</b></p>
 * <ul>
 *   <li>{@code worldHeight = 4064} — full 1.21.1 vertical range upper bound</li>
 *   <li>{@code minY = -2032} — lowest legal {@code min_y} in 1.21.1</li>
 *   <li>{@code seaLevelY = 0} — unused in {@link SeaMode#NONE}, kept for future sea modes</li>
 *   <li>{@code islandBaselineY = 0} — islands float with their floor at Y=0</li>
 *   <li>{@code seaMode = NONE} — void below the surface, true floating islands</li>
 *   <li>{@code topologyMode = ISLANDS} — discrete islands separated by void</li>
 *   <li>{@code floatingIslandsEnabled = false} — no extra layer (stage 3.6)</li>
 * </ul>
 */
public record EndDefaults(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                          SeaMode seaMode, TopologyMode topologyMode,
                          boolean floatingIslandsEnabled) implements DimensionProfile {

    /** The End's canonical defaults: full height, no sea, discrete islands. */
    public static EndDefaults endDefaults() {
        return new EndDefaults(4064, -2032, 0, 0, SeaMode.NONE, TopologyMode.ISLANDS, false);
    }
}
