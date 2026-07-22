package endterraforged.world.preview;

import java.util.Objects;

import endterraforged.world.climate.EndClimate;
import endterraforged.world.cell.Cell;
import endterraforged.world.cave.EndCaveField;
import endterraforged.world.cave.EndCaveGraphPreviewMask;
import endterraforged.world.cave.EndCavePreviewMask;
import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.filter.Erosion;
import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.filter.ErosionFactory;
import endterraforged.world.filter.Filterable;
import endterraforged.world.filter.Size;
import endterraforged.world.heightmap.EndTerrainBlend;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLandmassVolume;
import endterraforged.world.heightmap.EndLandmassSignalBuffer;
import endterraforged.world.heightmap.EndSubsurface;
import endterraforged.world.heightmap.EndTerrainLayer;
import endterraforged.world.continent.EndCentralRegionPolicy;

/**
 * CPU-side preview sampler shared by the GUI and tests.
 */
public final class TerrainPreviewSampler {

    public static final int DEFAULT_SEED = 440011;
    public static final int DEFAULT_SIZE = 96;
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 24;

    private static final int MIN_INTERACTIVE_SIZE = 32;
    private static final int MAX_INTERACTIVE_SIZE = 96;
    private static final int MAX_SIZE = 512;
    private static final int DEFAULT_WORLD_SPAN = DEFAULT_SIZE * DEFAULT_BLOCKS_PER_PIXEL;
    private static final int CAVE_DEPTH_SAMPLES = 12;
    private static final int OUTER_PREVIEW_SEARCH_RADIUS = 16384;
    private static final int OUTER_PREVIEW_SEARCH_STEP = 1024;

    private TerrainPreviewSampler() {
    }

    public static TerrainPreview sample(EndPreset preset) {
        return sample(preset, DEFAULT_SEED, DEFAULT_SIZE, DEFAULT_BLOCKS_PER_PIXEL,
                TerrainPreviewSettings.DEFAULT);
    }

    /**
     * Samples a preview sized for an on-screen viewport while keeping the
     * represented world span close to {@link #DEFAULT_SIZE} *
     * {@link #DEFAULT_BLOCKS_PER_PIXEL}. This gives small widgets cheap
     * redraws without changing the geographic area being previewed.
     */
    public static TerrainPreview sampleForViewport(EndPreset preset, int viewportPixels) {
        return sampleForViewport(preset, viewportPixels, TerrainPreviewMode.COMBINED);
    }

    public static TerrainPreview sampleForViewport(EndPreset preset, int viewportPixels,
                                                   TerrainPreviewMode mode) {
        return sampleForViewport(preset, viewportPixels,
                TerrainPreviewSettings.DEFAULT.withMode(mode));
    }

    public static TerrainPreview sampleForViewport(EndPreset preset, int viewportPixels,
                                                   TerrainPreviewSettings settings) {
        Objects.requireNonNull(settings, "settings");
        int sampleSize = sampleSizeForViewport(viewportPixels);
        return sample(preset, DEFAULT_SEED, sampleSize, blocksPerPixelForPreset(preset, sampleSize, settings.scale()),
                settings);
    }

    public static TerrainPreview sample(EndPreset preset, int seed, int size, int blocksPerPixel) {
        return sample(preset, seed, size, blocksPerPixel, TerrainPreviewSettings.DEFAULT);
    }

    public static TerrainPreview sample(EndPreset preset, int seed, int size, int blocksPerPixel,
                                        TerrainPreviewMode mode) {
        return sample(preset, seed, size, blocksPerPixel,
                TerrainPreviewSettings.DEFAULT.withMode(mode));
    }

    public static TerrainPreview sample(EndPreset preset, int seed, int size, int blocksPerPixel,
                                        TerrainPreviewSettings settings) {
        return sample(preset, seed, size, blocksPerPixel, settings, biomeLayoutConfig(preset));
    }

    static TerrainPreview sample(EndPreset preset, int seed, int size, int blocksPerPixel,
                                 TerrainPreviewSettings settings, BiomeLayoutConfig biomeLayoutConfig) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(settings, "settings");
        if (size <= 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1, " + MAX_SIZE + "], got " + size);
        }
        if (blocksPerPixel <= 0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0, got " + blocksPerPixel);
        }
        EndHeightmap heightmap = new EndHeightmap(preset, seed);
        EndLandmassVolume landmassVolume = heightmap.landmassVolume();
        EndClimate climate = preset.climateConfig().build(seed);
        int[] colors = new int[size * size];
        int[] layerCounts = new int[EndTerrainLayer.values().length];
        int blendedCount = 0;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        PreviewOrigin previewOrigin = previewOrigin(preset, heightmap, seed, size, blocksPerPixel);
        PreviewErosionGrid erosionGrid = heightMode(settings.mode())
                ? PreviewErosionGrid.create(preset, heightmap, seed, size, blocksPerPixel,
                        previewOrigin.minX(), previewOrigin.minZ())
                : PreviewErosionGrid.EMPTY;
        BiomePreviewLayout biomePreviewLayout = biomePreviewMode(settings.mode())
                ? BiomePreviewLayout.create(biomeLayoutConfig)
                : null;
        EndSubsurface subsurface = settings.mode() == TerrainPreviewMode.ABYSS
                ? preset.subsurfaceConfig().buildRuntime(seed)
                : null;
        EndCavePreviewMask caveMask = caveMode(settings.mode())
                ? preset.subsurfaceConfig().buildCavePreviewMask(seed)
                : null;
        EndCaveGraphPreviewMask caveGraphMask = caveGraphMode(settings.mode())
                ? preset.subsurfaceConfig().buildCaveGraphPreviewMask(seed)
                : null;
        EndCaveField caveField = settings.mode() == TerrainPreviewMode.CAVE_DEPTH
                ? EndCaveField.fromConfig(preset.subsurfaceConfig(), seed)
                : null;
        EndLandmassSignalBuffer landmassSignals = new EndLandmassSignalBuffer();
        for (int z = 0; z < size; z++) {
            float worldZ = previewOrigin.minZ() + z * blocksPerPixel;
            for (int x = 0; x < size; x++) {
                float worldX = previewOrigin.minX() + x * blocksPerPixel;
                heightmap.sampleLandmassSignals(worldX, worldZ, seed, landmassSignals);
                float landness = landmassSignals.landness();
                float height = erosionGrid.active()
                        ? erosionGrid.heightAt(x, z, size)
                        : heightmap.getTerrainHeight(worldX, worldZ, seed, landmassSignals);
                float terrainTop = heightmap.getHeight(worldX, worldZ, seed, landmassSignals);
                EndTerrainBlend blend = heightmap.auxiliaryTerrainBlendAt(worldX, worldZ, seed);
                EndTerrainLayer layer = blend.dominantLayer();
                float temperature = climate.getTemperature(worldX, worldZ, seed);
                float moisture = climate.getMoisture(worldX, worldZ, seed);
                float wind = climate.getWind(worldX, worldZ, seed);
                float uplift = settings.mode() == TerrainPreviewMode.UPLIFT
                        ? heightmap.getUplift(worldX, worldZ, seed)
                        : 0.0F;
                min = Math.min(min, height);
                max = Math.max(max, height);
                if (TerrainPreviewPalette.isVisibleLand(landness)) {
                    layerCounts[layer.ordinal()]++;
                    if (blend.isBlended()) {
                        blendedCount++;
                    }
                }
                colors[z * size + x] = color(
                        preset, landmassVolume, landness, height, terrainTop, blend, settings.mode(),
                        temperature, moisture, wind, worldX, worldZ,
                        biomePreviewLayout, subsurface, caveMask, caveGraphMask, caveField, uplift,
                        landmassSignals.mainlandLandness(), landmassSignals.archipelagoMask(),
                        landmassSignals.archipelagoLandness());
            }
        }
        return new TerrainPreview(size, colors, min, max,
                TerrainPreviewLayerStats.fromCounts(layerCounts, blendedCount));
    }

    /**
     * Chooses a representative exterior observation window for outer-continent
     * previews. Existing topologies retain the historic origin-centred view.
     */
    private static PreviewOrigin previewOrigin(EndPreset preset, EndHeightmap heightmap,
                                               int seed, int size, int blocksPerPixel) {
        float halfSpan = (size - 1) * blocksPerPixel * 0.5F;
        PreviewCenter center = representativeCenter(preset, heightmap, seed);
        return new PreviewOrigin(center.x() - halfSpan, center.z() - halfSpan);
    }

    /**
     * Deterministically chooses the centre used by previews that must inspect
     * ETF terrain rather than the protected vanilla End centre.
     */
    static PreviewCenter representativeCenter(EndPreset preset, EndHeightmap heightmap, int seed) {
        if (preset.topologyMode() != TopologyMode.OUTER_CONTINENTS) {
            return new PreviewCenter(0.0F, 0.0F);
        }

        int bestX = EndCentralRegionPolicy.OUTER_TERRAIN_RADIUS_BLOCKS + OUTER_PREVIEW_SEARCH_STEP;
        int bestZ = 0;
        float bestLandness = Float.NEGATIVE_INFINITY;
        for (int z = -OUTER_PREVIEW_SEARCH_RADIUS; z <= OUTER_PREVIEW_SEARCH_RADIUS;
             z += OUTER_PREVIEW_SEARCH_STEP) {
            for (int x = -OUTER_PREVIEW_SEARCH_RADIUS; x <= OUTER_PREVIEW_SEARCH_RADIUS;
                 x += OUTER_PREVIEW_SEARCH_STEP) {
                if (EndCentralRegionPolicy.outerActivation(x, z) < 1.0F) {
                    continue;
                }
                float landness = heightmap.getLandness(x, z, seed);
                if (landness > bestLandness) {
                    bestLandness = landness;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        for (int step = OUTER_PREVIEW_SEARCH_STEP / 2; step >= 64; step /= 2) {
            int refinedX = bestX;
            int refinedZ = bestZ;
            float refinedLandness = bestLandness;
            for (int dz = -step; dz <= step; dz += step) {
                for (int dx = -step; dx <= step; dx += step) {
                    int candidateX = bestX + dx;
                    int candidateZ = bestZ + dz;
                    if (EndCentralRegionPolicy.outerActivation(candidateX, candidateZ) < 1.0F) {
                        continue;
                    }
                    float landness = heightmap.getLandness(candidateX, candidateZ, seed);
                    if (landness > refinedLandness) {
                        refinedLandness = landness;
                        refinedX = candidateX;
                        refinedZ = candidateZ;
                    }
                }
            }
            bestX = refinedX;
            bestZ = refinedZ;
            bestLandness = refinedLandness;
        }
        return new PreviewCenter(bestX, bestZ);
    }

    private static int color(EndPreset preset, EndLandmassVolume landmassVolume,
                             float landness, float height,
                             float terrainTop, EndTerrainBlend blend,
                             TerrainPreviewMode mode, float temperature, float moisture, float wind,
                             float worldX, float worldZ,
                             BiomePreviewLayout biomePreviewLayout, EndSubsurface subsurface,
                             EndCavePreviewMask caveMask,
                             EndCaveGraphPreviewMask caveGraphMask,
                             EndCaveField caveField, float uplift,
                             float mainlandLandness, float archipelagoMask,
                             float archipelagoLandness) {
        if (mode == TerrainPreviewMode.BIOMES) {
            return biomePreviewLayout.color(worldX, worldZ);
        }
        if (mode == TerrainPreviewMode.BIOME_CLIMATE) {
            return biomePreviewLayout.climateColor(worldX, worldZ,
                    landness, temperature, moisture);
        }
        if (mode == TerrainPreviewMode.VOLUME) {
            float underside = landmassVolume.underside(worldX, worldZ, landness, terrainTop);
            return TerrainPreviewPalette.volumeColor(landness, landmassVolume.isFinite(),
                    terrainTop - underside);
        }
        if (mode == TerrainPreviewMode.ARCHIPELAGO) {
            return TerrainPreviewPalette.archipelagoColor(
                    mainlandLandness, archipelagoMask, archipelagoLandness);
        }
        if (mode == TerrainPreviewMode.CAVES) {
            return TerrainPreviewPalette.caveColor(landness,
                    caveMask.strength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_CHAMBERS) {
            return TerrainPreviewPalette.caveColor(landness,
                    caveMask.chamberStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_NETWORK) {
            return TerrainPreviewPalette.caveColor(landness,
                    caveMask.networkStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_RIFTS) {
            return TerrainPreviewPalette.caveColor(landness,
                    caveGraphMask.riftStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_FLOWS) {
            return TerrainPreviewPalette.caveColor(landness,
                    caveGraphMask.flowStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_WATER) {
            return TerrainPreviewPalette.caveWaterColor(landness,
                    caveGraphMask.waterCandidateStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_LAVA) {
            return TerrainPreviewPalette.caveLavaColor(landness,
                    caveGraphMask.lavaCandidateStrength(worldX, worldZ, landness));
        }
        if (mode == TerrainPreviewMode.CAVE_DEPTH) {
            CaveDepthSample sample = caveDepthSample(preset, caveField, worldX, worldZ,
                    landness, terrainTop);
            return TerrainPreviewPalette.caveDepthColor(landness,
                    sample.strength(), sample.depthAlpha());
        }
        if (mode == TerrainPreviewMode.ABYSS) {
            return TerrainPreviewPalette.abyssColor(landness,
                    subsurface.abyssStrength(worldX, worldZ, landness));
        }
        return TerrainPreviewPalette.color(landness, height, blend, mode,
                temperature, moisture, wind, uplift);
    }

    /**
     * Converts viewport pixels to a bounded raster size. Values are snapped to
     * 8-pixel buckets so minor layout changes do not force needless resamples.
     */
    public static int sampleSizeForViewport(int viewportPixels) {
        if (viewportPixels <= 0) {
            return MIN_INTERACTIVE_SIZE;
        }
        int clamped = Math.clamp(viewportPixels, MIN_INTERACTIVE_SIZE, MAX_INTERACTIVE_SIZE);
        return Math.max(MIN_INTERACTIVE_SIZE, (clamped / 8) * 8);
    }

    /**
     * Returns the block stride that preserves the default preview world span for
     * a given raster size.
     */
    public static int blocksPerPixelForSize(int sampleSize) {
        return blocksPerPixelForSize(sampleSize, TerrainPreviewScale.NORMAL);
    }

    public static int blocksPerPixelForSize(int sampleSize, TerrainPreviewScale scale) {
        Objects.requireNonNull(scale, "scale");
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be > 0, got " + sampleSize);
        }
        return Math.max(1, Math.round(scale.worldSpan(DEFAULT_WORLD_SPAN) / (float) sampleSize));
    }

    static int blocksPerPixelForPreset(EndPreset preset, int sampleSize, TerrainPreviewScale scale) {
        Objects.requireNonNull(preset, "preset");
        int span = scale.worldSpan(DEFAULT_WORLD_SPAN);
        if (preset.topologyMode() == TopologyMode.OUTER_CONTINENTS
                && scale == TerrainPreviewScale.WIDE) {
            int continentSpan = preset.continentConfig().continentAlgorithm().usesRtfTectonicScale()
                    ? preset.continentConfig().continentScale() * 8
                    : Math.round(preset.continentConfig().outerContinentScale() * 1.75F);
            span = Math.max(span, continentSpan);
        }
        return Math.max(1, Math.round(span / (float) sampleSize));
    }

    private static boolean heightMode(TerrainPreviewMode mode) {
        return mode == TerrainPreviewMode.COMBINED || mode == TerrainPreviewMode.HEIGHT;
    }

    private static boolean biomePreviewMode(TerrainPreviewMode mode) {
        return mode == TerrainPreviewMode.BIOMES || mode == TerrainPreviewMode.BIOME_CLIMATE;
    }

    private static boolean caveMode(TerrainPreviewMode mode) {
        return mode == TerrainPreviewMode.CAVES
                || mode == TerrainPreviewMode.CAVE_CHAMBERS
                || mode == TerrainPreviewMode.CAVE_NETWORK;
    }

    private static boolean caveGraphMode(TerrainPreviewMode mode) {
        return mode == TerrainPreviewMode.CAVE_RIFTS
                || mode == TerrainPreviewMode.CAVE_FLOWS
                || mode == TerrainPreviewMode.CAVE_WATER
                || mode == TerrainPreviewMode.CAVE_LAVA;
    }

    private static CaveDepthSample caveDepthSample(EndPreset preset, EndCaveField field,
                                                   float worldX, float worldZ,
                                                   float landness, float terrainTop) {
        if (field == null || !field.enabled()) {
            return CaveDepthSample.EMPTY;
        }
        int worldHeight = Math.max(1, preset.worldHeight());
        int start = Math.clamp(preset.subsurfaceConfig().caveSystemConfig().depthStart(),
                0, Math.max(0, worldHeight - 1));
        int end = Math.clamp(preset.subsurfaceConfig().caveSystemConfig().depthEnd(),
                start + 1, worldHeight);
        float bestStrength = 0.0F;
        float bestDepth = 0.0F;
        for (int i = 0; i < CAVE_DEPTH_SAMPLES; i++) {
            float alpha = CAVE_DEPTH_SAMPLES == 1 ? 0.0F
                    : i / (float) (CAVE_DEPTH_SAMPLES - 1);
            float depth = start + (end - start) * alpha;
            float yNorm = terrainTop - depth / worldHeight;
            float strength = field.strength(worldX, worldZ, landness,
                    yNorm, terrainTop, worldHeight);
            if (strength > bestStrength) {
                bestStrength = strength;
                bestDepth = alpha;
            }
        }
        return new CaveDepthSample(bestStrength, bestDepth);
    }

    private static BiomeLayoutConfig biomeLayoutConfig(EndPreset preset) {
        return preset.biomeLayoutConfig();
    }

    static record PreviewCenter(float x, float z) {
    }

    private record PreviewOrigin(float minX, float minZ) {
    }

    private record CaveDepthSample(float strength, float depthAlpha) {
        private static final CaveDepthSample EMPTY = new CaveDepthSample(0.0F, 0.0F);
    }

    /**
     * Preview-only raster that runs the same hydraulic erosion filter over a
     * temporary height grid. Production terrain still owns chunk/tile lifecycle;
     * this keeps GUI previews deterministic without coupling screens to filters.
     */
    private record PreviewErosionGrid(Cell[] cells, int size) {
        private static final PreviewErosionGrid EMPTY = new PreviewErosionGrid(null, 0);
        private static final int MIN_EROSION_SIZE = 16;
        private static final int CHUNK_SIZE = 16;

        private static PreviewErosionGrid create(EndPreset preset, EndHeightmap heightmap,
                                                  int seed, int sampleSize,
                                                  int blocksPerPixel, float sampleMinX, float sampleMinZ) {
            ErosionConfig config = preset.erosionConfig();
            if (sampleSize < MIN_EROSION_SIZE || config.dropletsPerChunk <= 0) {
                return EMPTY;
            }
            int erosionSize = erosionGridSize(sampleSize);
            Cell[] cells = sampleCells(heightmap, seed, erosionSize,
                    sampleWorldSpan(sampleSize, blocksPerPixel), sampleMinX, sampleMinZ);
            Erosion erosion = ErosionFactory.create(seed, erosionSize, config, preset);
            erosion.apply(new PreviewFilterable(cells, Size.make(erosionSize, 0),
                    Math.round(sampleMinX), Math.round(sampleMinZ)),
                    0, 0, config.dropletsPerChunk);
            return new PreviewErosionGrid(cells, erosionSize);
        }

        private boolean active() {
            return cells != null;
        }

        private float heightAt(int x, int z, int sampleSize) {
            if (sampleSize <= 1 || size <= 1) {
                return cellHeight(0, 0);
            }
            float scale = (size - 1) / (float) (sampleSize - 1);
            return bilinearHeight(x * scale, z * scale);
        }

        private float bilinearHeight(float x, float z) {
            int x0 = Math.clamp((int) x, 0, size - 1);
            int z0 = Math.clamp((int) z, 0, size - 1);
            int x1 = Math.min(size - 1, x0 + 1);
            int z1 = Math.min(size - 1, z0 + 1);
            float tx = x - x0;
            float tz = z - z0;
            float top = lerp(cellHeight(x0, z0), cellHeight(x1, z0), tx);
            float bottom = lerp(cellHeight(x0, z1), cellHeight(x1, z1), tx);
            return Math.clamp(lerp(top, bottom, tz), 0.0F, 1.0F);
        }

        private float cellHeight(int x, int z) {
            return cells[z * size + x].height;
        }

        private static int erosionGridSize(int sampleSize) {
            return Math.min(MAX_SIZE, ((sampleSize + CHUNK_SIZE - 1) / CHUNK_SIZE) * CHUNK_SIZE);
        }

        private static float sampleWorldSpan(int sampleSize, int blocksPerPixel) {
            return Math.max(1, sampleSize - 1) * blocksPerPixel;
        }

        private static Cell[] sampleCells(EndHeightmap heightmap, int seed,
                                           int size, float worldSpan,
                                           float minWorldX, float minWorldZ) {
            Cell[] cells = new Cell[size * size];
            float stride = size <= 1 ? 0.0F : worldSpan / (size - 1);
            for (int z = 0; z < size; z++) {
                float worldZ = minWorldZ + z * stride;
                for (int x = 0; x < size; x++) {
                    float worldX = minWorldX + x * stride;
                    Cell cell = new Cell();
                    float landness = heightmap.getLandness(worldX, worldZ, seed);
                    cell.height = heightmap.getTerrainHeight(worldX, worldZ, seed);
                    cell.erosionMask = !TerrainPreviewPalette.isVisibleLand(landness);
                    cells[z * size + x] = cell;
                }
            }
            return cells;
        }

        private static float lerp(float from, float to, float alpha) {
            return from + (to - from) * alpha;
        }
    }

    private record PreviewFilterable(Cell[] cells, Size blockSize, int blockX, int blockZ)
            implements Filterable {
        @Override
        public int getBlockX() {
            return blockX;
        }

        @Override
        public int getBlockZ() {
            return blockZ;
        }

        @Override
        public Size getBlockSize() {
            return blockSize;
        }

        @Override
        public Cell[] getBacking() {
            return cells;
        }

        @Override
        public Cell getCellRaw(int x, int z) {
            return cells[blockSize.indexOf(x, z)];
        }
    }

}
