package endterraforged.world.heightmap;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * Selects and evaluates optional End terrain layers around the primary
 * mountain spine.
 *
 * <p>The primary mountain layer remains owned by {@link EndHeightmap}; this
 * composer only handles the low-amplitude auxiliary families. Those families
 * are selected by weight from a stable low-frequency field so multiple enabled
 * layers produce broad terrain regions instead of stacking everywhere. When a
 * preset enables terrain blending, adjacent weighted regions cross-fade at
 * their shared boundary.</p>
 */
final class EndTerrainComposer {
    private static final float AUXILIARY_LAYER_SCALE = 0.05F;

    private final Noise selector;
    private final float globalHorizontalScale;
    private final float terrainRegionScale;
    private final float terrainBlendRange;
    private final Layer[] enabledLayers;
    private final float totalWeight;

    EndTerrainComposer(TerrainConfig config, int seed) {
        this(config, seed, Noises.clamp(
                Noises.perlin(seed + 240, config.terrainRegionSize(), 2),
                0.0F, 1.0F));
    }

    EndTerrainComposer(TerrainConfig config, int seed, Noise selector) {
        this.selector = Noises.clamp(selector, 0.0F, 1.0F);
        this.globalHorizontalScale = config.globalHorizontalScale();
        this.terrainRegionScale = config.terrainRegionSize()
                / (float) TerrainConfig.DEFAULT.terrainRegionSize();
        this.terrainBlendRange = config.terrainBlendRange();
        Layer[] layers = new Layer[] {
                new Layer(EndTerrainLayer.PLAINS, config.plains(), buildLayer(seed + 200, 900, 2)),
                new Layer(EndTerrainLayer.HILLS, config.hills(), buildLayer(seed + 210, 650, 3)),
                new Layer(EndTerrainLayer.PLATEAU, config.plateau(), buildLayer(seed + 220, 1100, 2)),
                new Layer(EndTerrainLayer.VOLCANO, config.volcano(), buildLayer(seed + 230, 500, 3))
        };
        this.enabledLayers = enabledLayers(layers);
        this.totalWeight = totalWeight(this.enabledLayers);
    }

    float auxiliaryContribution(float x, float z, int seed) {
        if (this.totalWeight <= 0.0F) {
            return 0.0F;
        }
        float selectedWeight = selectedWeight(x, z, seed);
        if (this.terrainBlendRange <= 0.0F || this.enabledLayers.length == 1) {
            return selectLayer(selectedWeight).contribution(
                    x, z, seed, this.globalHorizontalScale, this.terrainRegionScale);
        }
        return blendedContribution(selectedWeight, x, z, seed);
    }

    /**
     * Keeps R2 continent coasts gently varied while reserving full mountain,
     * plateau and volcanic amplitude for stable inland terrain.
     */
    float reliefEnvelope(float inlandness) {
        return 0.18F + 0.82F * smoothstep(inlandness);
    }

    EndTerrainLayer selectedLayer(float x, float z, int seed) {
        if (this.totalWeight <= 0.0F) {
            return EndTerrainLayer.NONE;
        }
        return selectLayer(selectedWeight(x, z, seed)).type();
    }

    EndTerrainBlend selectedBlend(float x, float z, int seed) {
        if (this.totalWeight <= 0.0F) {
            return EndTerrainBlend.NONE;
        }
        return layerBlend(selectedWeight(x, z, seed)).toPreviewBlend();
    }

    private float selectedWeight(float x, float z, int seed) {
        // terrainRegionSize is already baked into selector's noise scale.
        float selectorScale = Math.max(0.01F, this.globalHorizontalScale);
        return this.selector.compute(x / selectorScale, z / selectorScale, seed)
                * this.totalWeight;
    }

    private Layer selectLayer(float selectedWeight) {
        float cursor = 0.0F;
        Layer fallback = this.enabledLayers[this.enabledLayers.length - 1];
        for (Layer layer : this.enabledLayers) {
            float weight = layer.selectionWeight();
            cursor += weight;
            fallback = layer;
            if (selectedWeight <= cursor) {
                return layer;
            }
        }
        return fallback;
    }

    private LayerBlend layerBlend(float selectedWeight) {
        if (this.terrainBlendRange <= 0.0F || this.enabledLayers.length == 1) {
            Layer layer = selectLayer(selectedWeight);
            return LayerBlend.single(layer);
        }
        float cursor = 0.0F;
        for (int i = 0; i < this.enabledLayers.length; i++) {
            Layer current = this.enabledLayers[i];
            float start = cursor;
            float end = start + current.selectionWeight();
            if (selectedWeight < end || i == this.enabledLayers.length - 1) {
                return blendAtInterval(i, start, end, selectedWeight);
            }
            cursor = end;
        }
        Layer fallback = this.enabledLayers[this.enabledLayers.length - 1];
        return LayerBlend.single(fallback);
    }

    /**
     * Worldgen-only counterpart to {@link #layerBlend(float)}. The preview path
     * needs a value object to expose layer names and alpha, whereas density
     * sampling only needs the scalar contribution and must not allocate one
     * object per terrain column.
     */
    private float blendedContribution(float selectedWeight, float x, float z, int seed) {
        float cursor = 0.0F;
        for (int i = 0; i < this.enabledLayers.length; i++) {
            Layer current = this.enabledLayers[i];
            float start = cursor;
            float end = start + current.selectionWeight();
            if (selectedWeight < end || i == this.enabledLayers.length - 1) {
                return contributionAtInterval(i, start, end, selectedWeight, x, z, seed);
            }
            cursor = end;
        }
        return this.enabledLayers[this.enabledLayers.length - 1].contribution(
                x, z, seed, this.globalHorizontalScale, this.terrainRegionScale);
    }

    private float contributionAtInterval(int index, float start, float end, float selectedWeight,
                                         float x, float z, int seed) {
        Layer current = this.enabledLayers[index];
        if (index > 0) {
            Layer previous = this.enabledLayers[index - 1];
            float width = blendWidth(previous, current);
            if (width > 0.0F && selectedWeight < start + width) {
                float alpha = smoothstep(0.5F + 0.5F * ((selectedWeight - start) / width));
                return lerp(previous.contribution(x, z, seed, this.globalHorizontalScale,
                                this.terrainRegionScale),
                        current.contribution(x, z, seed, this.globalHorizontalScale,
                                this.terrainRegionScale), alpha);
            }
        }
        if (index < this.enabledLayers.length - 1) {
            Layer next = this.enabledLayers[index + 1];
            float width = blendWidth(current, next);
            if (width > 0.0F && selectedWeight > end - width) {
                float alpha = smoothstep(0.5F * (1.0F - ((end - selectedWeight) / width)));
                return lerp(current.contribution(x, z, seed, this.globalHorizontalScale,
                                this.terrainRegionScale),
                        next.contribution(x, z, seed, this.globalHorizontalScale,
                                this.terrainRegionScale), alpha);
            }
        }
        return current.contribution(x, z, seed, this.globalHorizontalScale, this.terrainRegionScale);
    }

    private LayerBlend blendAtInterval(int index, float start, float end, float selectedWeight) {
        Layer current = this.enabledLayers[index];

        if (index > 0) {
            Layer previous = this.enabledLayers[index - 1];
            float width = blendWidth(previous, current);
            if (width > 0.0F && selectedWeight < start + width) {
                float alpha = smoothstep(0.5F + 0.5F * ((selectedWeight - start) / width));
                return new LayerBlend(previous, current, alpha);
            }
        }

        if (index < this.enabledLayers.length - 1) {
            Layer next = this.enabledLayers[index + 1];
            float width = blendWidth(current, next);
            if (width > 0.0F && selectedWeight > end - width) {
                float alpha = smoothstep(0.5F * (1.0F - ((end - selectedWeight) / width)));
                return new LayerBlend(current, next, alpha);
            }
        }

        return LayerBlend.single(current);
    }

    private float blendWidth(Layer left, Layer right) {
        float configuredWidth = this.totalWeight * this.terrainBlendRange * 0.5F;
        float maxStableWidth = Math.min(left.selectionWeight(), right.selectionWeight()) * 0.5F;
        return Math.min(configuredWidth, maxStableWidth);
    }

    private static Layer[] enabledLayers(Layer[] layers) {
        int count = 0;
        for (Layer layer : layers) {
            if (layer.selectionWeight() > 0.0F) {
                count++;
            }
        }
        Layer[] result = new Layer[count];
        int index = 0;
        for (Layer layer : layers) {
            if (layer.selectionWeight() > 0.0F) {
                result[index++] = layer;
            }
        }
        return result;
    }

    private static float totalWeight(Layer[] layers) {
        float result = 0.0F;
        for (Layer layer : layers) {
            result += layer.selectionWeight();
        }
        return result;
    }

    private static Noise buildLayer(int seed, int scale, int octaves) {
        return Noises.clamp(Noises.perlin(seed, scale, octaves), 0.0F, 1.0F);
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * clamp01(alpha);
    }

    private static float smoothstep(float value) {
        float alpha = clamp01(value);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record LayerBlend(Layer from, Layer to, float alpha) {
        static LayerBlend single(Layer layer) {
            return new LayerBlend(layer, layer, 0.0F);
        }

        LayerBlend {
            alpha = clamp01(alpha);
        }

        boolean isBlended() {
            return this.from != this.to && this.alpha > 0.0F;
        }

        EndTerrainBlend toPreviewBlend() {
            return new EndTerrainBlend(this.from.type(), this.to.type(), this.alpha);
        }
    }

    private record Layer(EndTerrainLayer type, TerrainLayerConfig config,
                         Noise noise, float selectionWeight) {

        Layer(EndTerrainLayer type, TerrainLayerConfig config, Noise noise) {
            this(type, config, noise, selectionWeight(config));
        }

        private static float selectionWeight(TerrainLayerConfig config) {
            if (config.weight() <= 0.0F
                    || config.baseScale() <= 0.0F
                    || config.verticalScale() <= 0.0F) {
                return 0.0F;
            }
            return config.weight();
        }

        float contribution(float x, float z, int seed,
                           float globalHorizontalScale, float terrainRegionScale) {
            float horizontalScale = globalHorizontalScale
                    * terrainRegionScale
                    * Math.max(0.01F, this.config.horizontalScale());
            float sample = this.noise.compute(x / horizontalScale, z / horizontalScale, seed);
            return sample
                    * this.config.weight()
                    * this.config.baseScale()
                    * this.config.verticalScale()
                    * AUXILIARY_LAYER_SCALE;
        }
    }
}
