/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later).
 */
package endterraforged.world.level.biome;

/**
 * Shared unit-interval climate bands for End biome-variant presets.
 *
 * <p>These bands are intentionally pure scalar helpers: they do not reference
 * biome holders, registry objects, codecs, or preset JSON. The biome-source
 * data layer can use them to keep temperature/moisture variant ranges readable
 * while {@link BiomeVariant} remains the serializable holder-bound type.</p>
 */
public final class BiomeClimateBands {

    private BiomeClimateBands() {
    }

    public enum Temperature {
        FROZEN(0.0F, 0.2F),
        COLD(0.2F, 0.4F),
        TEMPERATE(0.4F, 0.6F),
        WARM(0.6F, 0.8F),
        HOT(0.8F, 1.0F);

        private final float min;
        private final float max;

        Temperature(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public float min() {
            return min;
        }

        public float max() {
            return max;
        }

        public boolean contains(float value) {
            return value >= min && value <= max;
        }
    }

    public enum Moisture {
        ARID(0.0F, 0.2F),
        DRY(0.2F, 0.4F),
        BALANCED(0.4F, 0.6F),
        WET(0.6F, 0.8F),
        SATURATED(0.8F, 1.0F);

        private final float min;
        private final float max;

        Moisture(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public float min() {
            return min;
        }

        public float max() {
            return max;
        }

        public boolean contains(float value) {
            return value >= min && value <= max;
        }
    }
}
