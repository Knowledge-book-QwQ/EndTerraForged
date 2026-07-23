package endterraforged.world.preview;

import java.util.Objects;

import endterraforged.world.heightmap.EndCoastBand;
import endterraforged.world.heightmap.EndCoastBands;
import endterraforged.world.heightmap.EndTerrainBlend;
import endterraforged.world.heightmap.EndTerrainLayer;
import endterraforged.world.level.biome.BiomeClimateBands;
import endterraforged.world.level.biome.EndBiomeLayout;
import endterraforged.world.noise.NoiseMath;

/**
 * Color mapping for CPU terrain preview modes.
 */
public final class TerrainPreviewPalette {
    public static final float VISIBLE_LAND_THRESHOLD = 0.015F;

    private static final int VOID_COLOR = 0xFF160B23;
    private static final int NO_LAYER_COLOR = 0xFF4A435A;
    private static final int LOW_COLOR = 0xFF334B64;
    private static final int MID_COLOR = 0xFF6E8156;
    private static final int HIGH_COLOR = 0xFFD6D2B0;
    private static final int LAND_COLOR = 0xFF9DD8A1;
    private static final int ARCHIPELAGO_MAINLAND = 0xFF355B50;
    private static final int ARCHIPELAGO_SHELF = 0xFF3D8293;
    private static final int ARCHIPELAGO_COAST = 0xFF77AA78;
    private static final int ARCHIPELAGO_INLAND = 0xFFD2C46F;
    private static final int ARCHIPELAGO_BRIDGE = 0xFFE48C61;
    private static final int VOLUME_LEGACY = 0xFF8D6079;
    private static final int VOLUME_THIN = 0xFF4A8D9B;
    private static final int VOLUME_THICK = 0xFFE0C06C;
    private static final int PLAINS_TINT = 0xFF7EA66A;
    private static final int HILLS_TINT = 0xFFB99B5D;
    private static final int PLATEAU_TINT = 0xFF7A8A9F;
    private static final int MOUNTAINS_TINT = 0xFF9A7B86;
    private static final int VOLCANO_TINT = 0xFFC85C4A;
    private static final int ABYSS_RIM = 0xFF1B2638;
    private static final int ABYSS_SHELF = 0xFF27607A;
    private static final int ABYSS_CORE = 0xFF9EF6FF;
    private static final int CAVE_LOW = 0xFF31424B;
    private static final int CAVE_PASSAGE = 0xFF6F7C63;
    private static final int CAVE_CHAMBER = 0xFFE9D88D;
    private static final int CAVE_WATER = 0xFF35B9D6;
    private static final int CAVE_LAVA = 0xFFFF7A32;
    private static final int TEMPERATURE_COLD = 0xFF365B9B;
    private static final int TEMPERATURE_HOT = 0xFFD9694C;
    private static final int MOISTURE_DRY = 0xFFB6844A;
    private static final int MOISTURE_WET = 0xFF39A9A5;
    private static final int WIND_CALM = 0xFF44546A;
    private static final int WIND_STRONG = 0xFFE5E0FF;
    private static final BiomeClimateBands.Temperature[] TEMPERATURE_BANDS =
            BiomeClimateBands.Temperature.values();
    private static final BiomeClimateBands.Moisture[] MOISTURE_BANDS =
            BiomeClimateBands.Moisture.values();
    private static final int[] BIOME_COLORS = {
            0xFF88B060,
            0xFFD7B45D,
            0xFF5BA7A0,
            0xFF8F76B8,
            0xFFC65F55,
            0xFF5F8CC8,
            0xFF9FBD6B,
            0xFFB6844A
    };
    private static final float LAYER_TINT_ALPHA = 0.28F;

    private TerrainPreviewPalette() {
    }

    public static boolean isVisibleLand(float landness) {
        return NoiseMath.clamp(landness, 0.0F, 1.0F) > VISIBLE_LAND_THRESHOLD;
    }

    public static int color(float landness, float height, EndTerrainLayer layer) {
        return color(landness, height, layer, TerrainPreviewMode.COMBINED);
    }

    public static int color(float landness, float height,
                            EndTerrainLayer layer, TerrainPreviewMode mode) {
        return color(landness, height, EndTerrainBlend.single(layer), mode);
    }

    public static int color(float landness, float height,
                            EndTerrainBlend blend, TerrainPreviewMode mode) {
        return color(landness, height, blend, mode, 0.0F, 0.0F, 0.0F);
    }

    public static int color(float landness, float height,
                            EndTerrainBlend blend, TerrainPreviewMode mode,
                            float temperature, float moisture, float wind) {
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case COMBINED -> combinedColor(landness, height, blend);
            case HEIGHT -> heightColor(landness, height);
            case LANDNESS -> landnessColor(landness);
            case ARCHIPELAGO -> archipelagoColor(landness, 0.0F, 0.0F);
            case VOLUME -> volumeColor(landness, false, 0.0F);
            case LAYERS -> layerMapColor(landness, blend);
            case BIOMES -> biomeColor(landness, 0);
            case BIOME_CLIMATE -> biomeClimateColor(landness, EndBiomeLayout.Ring.END, temperature, moisture);
            case CAVES -> caveColor(landness, 0.0F);
            case CAVE_CHAMBERS -> caveColor(landness, 0.0F);
            case CAVE_NETWORK -> caveColor(landness, 0.0F);
            case CAVE_RIFTS -> caveColor(landness, 0.0F);
            case CAVE_FLOWS -> caveColor(landness, 0.0F);
            case CAVE_WATER -> caveWaterColor(landness, 0.0F);
            case CAVE_LAVA -> caveLavaColor(landness, 0.0F);
            case CAVE_DEPTH -> caveDepthColor(landness, 0.0F, 0.0F);
            case ABYSS -> abyssColor(landness, 0.0F);
            case TEMPERATURE -> channelColor(temperature, TEMPERATURE_COLD, TEMPERATURE_HOT);
            case MOISTURE -> channelColor(moisture, MOISTURE_DRY, MOISTURE_WET);
            case WIND -> channelColor(wind, WIND_CALM, WIND_STRONG);
        };
    }

    public static int layerColor(EndTerrainLayer layer) {
        return switch (Objects.requireNonNull(layer, "layer")) {
            case PLAINS -> PLAINS_TINT;
            case HILLS -> HILLS_TINT;
            case PLATEAU -> PLATEAU_TINT;
            case MOUNTAINS -> MOUNTAINS_TINT;
            case VOLCANO -> VOLCANO_TINT;
            case NONE -> 0;
        };
    }

    public static int layerColor(EndTerrainBlend blend) {
        Objects.requireNonNull(blend, "blend");
        int fromColor = layerColor(blend.from());
        int toColor = layerColor(blend.to());
        if (fromColor == 0 && toColor == 0) {
            return 0;
        }
        if (fromColor == 0) {
            return toColor;
        }
        if (toColor == 0) {
            return fromColor;
        }
        return lerp(fromColor, toColor, blend.alpha());
    }

    public static int biomeColor(float landness, int biomeIndex) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        return BIOME_COLORS[Math.floorMod(biomeIndex, BIOME_COLORS.length)];
    }

    public static int biomeRingColor(EndBiomeLayout.Ring ring) {
        return switch (Objects.requireNonNull(ring, "ring")) {
            case END -> BIOME_COLORS[0];
            case HIGHLANDS -> BIOME_COLORS[1];
            case MIDLANDS -> BIOME_COLORS[2];
            case ISLANDS -> BIOME_COLORS[3];
            case BARRENS -> BIOME_COLORS[4];
        };
    }

    public static int biomeRingBlendColor(EndBiomeLayout.Ring ring, float fracX, float fracZ) {
        int base = biomeRingColor(ring);
        int accent = BIOME_COLORS[Math.floorMod(ring.ordinal() + 3, BIOME_COLORS.length)];
        float contrast = Math.abs(NoiseMath.clamp(fracX, 0.0F, 1.0F)
                - NoiseMath.clamp(fracZ, 0.0F, 1.0F));
        return lerp(base, accent, 0.12F + contrast * 0.28F);
    }

    public static int biomeClimateColor(float landness, EndBiomeLayout.Ring ring,
                                        float temperature, float moisture) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        int base = biomeRingColor(ring);
        int temperatureTint = channelColor(temperatureBandAlpha(temperature),
                TEMPERATURE_COLD, TEMPERATURE_HOT);
        int moistureTint = channelColor(moistureBandAlpha(moisture),
                MOISTURE_DRY, MOISTURE_WET);
        return lerp(lerp(base, temperatureTint, 0.34F), moistureTint, 0.24F);
    }

    public static int abyssColor(float landness, float strength) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        float mask = NoiseMath.clamp(strength, 0.0F, 1.0F);
        if (mask <= 0.0F) {
            return lerp(LOW_COLOR, LAND_COLOR, 0.35F);
        }
        if (mask < 0.55F) {
            return lerp(LAND_COLOR, ABYSS_RIM, mask / 0.55F);
        }
        if (mask < 0.85F) {
            return lerp(ABYSS_RIM, ABYSS_SHELF, (mask - 0.55F) / 0.30F);
        }
        return lerp(ABYSS_SHELF, ABYSS_CORE, (mask - 0.85F) / 0.15F);
    }

    /** Colors the runtime archipelago bands and mainland-overlap bridge candidates. */
    public static int archipelagoColor(float mainlandLandness, float mask,
                                       float archipelagoLandness) {
        float featureMask = NoiseMath.clamp(mask, 0.0F, 1.0F);
        if (featureMask <= 0.001F) {
            return isVisibleLand(mainlandLandness) ? ARCHIPELAGO_MAINLAND : VOID_COLOR;
        }
        if (isVisibleLand(mainlandLandness) && isVisibleLand(archipelagoLandness)) {
            return ARCHIPELAGO_BRIDGE;
        }
        EndCoastBand band = EndCoastBands.band(featureMask);
        return switch (band) {
            case VOID_EDGE, SHELF -> lerp(VOID_COLOR, ARCHIPELAGO_SHELF,
                    Math.max(0.18F, featureMask));
            case COAST -> lerp(ARCHIPELAGO_SHELF, ARCHIPELAGO_COAST, featureMask);
            case INLAND -> lerp(ARCHIPELAGO_COAST, ARCHIPELAGO_INLAND, featureMask);
        };
    }

    /**
     * Colors the finite landmass thickness used by the runtime density field.
     * Legacy columns deliberately receive a distinct color so the preview does
     * not imply that they already have a bounded underside.
     */
    public static int volumeColor(float landness, boolean finite, float thickness) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        if (!finite) {
            return VOLUME_LEGACY;
        }
        return lerp(VOLUME_THIN, VOLUME_THICK,
                NoiseMath.clamp(thickness / 0.5F, 0.0F, 1.0F));
    }

    /** Colors a vertical macro-landmass slice after runtime density sampling. */
    public static int landmassSliceColor(boolean solid, float verticalAlpha) {
        if (!solid) {
            return VOID_COLOR;
        }
        return lerp(VOLUME_THIN, VOLUME_THICK, NoiseMath.clamp(verticalAlpha, 0.0F, 1.0F));
    }

    public static int caveColor(float landness, float strength) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        float mask = NoiseMath.clamp(strength, 0.0F, 1.0F);
        if (mask <= 0.0F) {
            return lerp(LOW_COLOR, LAND_COLOR, 0.42F);
        }
        if (mask < 0.5F) {
            return lerp(LAND_COLOR, CAVE_LOW, mask / 0.5F);
        }
        if (mask < 0.82F) {
            return lerp(CAVE_LOW, CAVE_PASSAGE, (mask - 0.5F) / 0.32F);
        }
        return lerp(CAVE_PASSAGE, CAVE_CHAMBER, (mask - 0.82F) / 0.18F);
    }

    public static int caveDepthColor(float landness, float strength, float depthAlpha) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        float mask = NoiseMath.clamp(strength, 0.0F, 1.0F);
        if (mask <= 0.0F) {
            return lerp(LOW_COLOR, LAND_COLOR, 0.42F);
        }
        float depth = NoiseMath.clamp(depthAlpha, 0.0F, 1.0F);
        int depthTint = depth < 0.5F
                ? lerp(CAVE_CHAMBER, CAVE_PASSAGE, depth * 2.0F)
                : lerp(CAVE_PASSAGE, ABYSS_CORE, (depth - 0.5F) * 2.0F);
        return lerp(lerp(LOW_COLOR, LAND_COLOR, 0.42F), depthTint,
                0.35F + mask * 0.65F);
    }

    public static int caveWaterColor(float landness, float strength) {
        return caveLiquidColor(landness, strength, CAVE_WATER);
    }

    public static int caveLavaColor(float landness, float strength) {
        return caveLiquidColor(landness, strength, CAVE_LAVA);
    }

    private static int caveLiquidColor(float landness, float strength, int liquidColor) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        float mask = NoiseMath.clamp(strength, 0.0F, 1.0F);
        int base = lerp(LOW_COLOR, LAND_COLOR, 0.42F);
        if (mask <= 0.0F) {
            return base;
        }
        return lerp(base, liquidColor, 0.28F + mask * 0.72F);
    }

    private static int combinedColor(float landness, float height, EndTerrainBlend blend) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        int shaded = heightColor(landness, height);
        int tint = layerColor(blend);
        if (tint == 0) {
            return shaded;
        }
        return lerp(shaded, tint, LAYER_TINT_ALPHA);
    }

    private static int heightColor(float landness, float height) {
        float land = NoiseMath.clamp(landness, 0.0F, 1.0F);
        if (land <= VISIBLE_LAND_THRESHOLD) {
            return VOID_COLOR;
        }
        float h = NoiseMath.clamp(height, 0.0F, 1.0F);
        int terrain = h < 0.5F
                ? lerp(LOW_COLOR, MID_COLOR, h * 2.0F)
                : lerp(MID_COLOR, HIGH_COLOR, (h - 0.5F) * 2.0F);
        return lerp(VOID_COLOR, terrain, Math.min(1.0F, 0.25F + land * 0.75F));
    }

    private static int landnessColor(float landness) {
        float land = NoiseMath.clamp(landness, 0.0F, 1.0F);
        if (land <= VISIBLE_LAND_THRESHOLD) {
            return VOID_COLOR;
        }
        return lerp(VOID_COLOR, LAND_COLOR, land);
    }

    private static int layerMapColor(float landness, EndTerrainBlend blend) {
        if (!isVisibleLand(landness)) {
            return VOID_COLOR;
        }
        int layerColor = layerColor(blend);
        if (layerColor == 0) {
            return NO_LAYER_COLOR;
        }
        return layerColor;
    }

    private static int channelColor(float value, int low, int high) {
        return lerp(low, high, NoiseMath.clamp(value, 0.0F, 1.0F));
    }

    private static float temperatureBandAlpha(float value) {
        float clamped = NoiseMath.clamp(value, 0.0F, 1.0F);
        for (BiomeClimateBands.Temperature band : TEMPERATURE_BANDS) {
            if (band.contains(clamped)) {
                return band.ordinal() / (float) (TEMPERATURE_BANDS.length - 1);
            }
        }
        return clamped;
    }

    private static float moistureBandAlpha(float value) {
        float clamped = NoiseMath.clamp(value, 0.0F, 1.0F);
        for (BiomeClimateBands.Moisture band : MOISTURE_BANDS) {
            if (band.contains(clamped)) {
                return band.ordinal() / (float) (MOISTURE_BANDS.length - 1);
            }
        }
        return clamped;
    }

    private static int lerp(int a, int b, float alpha) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * alpha);
        int g = Math.round(ag + (bg - ag) * alpha);
        int bl = Math.round(ab + (bb - ab) * alpha);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
