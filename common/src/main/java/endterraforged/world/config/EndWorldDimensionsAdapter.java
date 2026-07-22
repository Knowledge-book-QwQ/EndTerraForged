package endterraforged.world.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.Holder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;

/**
 * Applies an ETF vertical specification to the selected End dimension only.
 *
 * <p>This adapter copies immutable vanilla records and preserves every non-height field.
 * It neither mutates dynamic registries nor replaces the Overworld, Nether, or dimensions
 * supplied by other mods. Direct holders are intentional: Minecraft's registry file codecs
 * encode unregistered values inline when the final dimension registry is baked.</p>
 *
 * <p><b>Thread safety:</b> stateless. Returned objects are immutable and safe to hand to the
 * create-world pipeline.</p>
 */
public final class EndWorldDimensionsAdapter {

    private EndWorldDimensionsAdapter() {
    }

    /** Returns a dimension set with only {@link LevelStem#END} resized. */
    public static WorldDimensions withEndBounds(
            WorldDimensions dimensions, WorldVerticalBounds bounds) {
        Objects.requireNonNull(dimensions, "dimensions");
        Objects.requireNonNull(bounds, "bounds");

        LevelStem currentEnd = dimensions.get(LevelStem.END).orElseThrow(
                () -> new IllegalStateException("selected world dimensions do not contain the End"));
        if (!(currentEnd.generator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("selected End generator is not noise-based: "
                    + currentEnd.generator().getClass().getName());
        }

        DimensionType resizedType = resizeDimensionType(currentEnd.type().value(), bounds);
        NoiseGeneratorSettings resizedSettings = resizeGeneratorSettings(
                generator.generatorSettings().value(), bounds);
        NoiseBasedChunkGenerator resizedGenerator = new NoiseBasedChunkGenerator(
                generator.getBiomeSource(), Holder.direct(resizedSettings));
        LevelStem resizedEnd = new LevelStem(Holder.direct(resizedType), resizedGenerator);

        Map<net.minecraft.resources.ResourceKey<LevelStem>, LevelStem> resized =
                new LinkedHashMap<>(dimensions.dimensions());
        resized.put(LevelStem.END, resizedEnd);
        return new WorldDimensions(Collections.unmodifiableMap(resized));
    }

    static DimensionType resizeDimensionType(
            DimensionType source, WorldVerticalBounds bounds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(bounds, "bounds");
        int logicalHeight = Math.min(source.logicalHeight(), bounds.height());
        return new DimensionType(
                source.fixedTime(), source.hasSkyLight(), source.hasCeiling(), source.ultraWarm(),
                source.natural(), source.coordinateScale(), source.bedWorks(),
                source.respawnAnchorWorks(), bounds.minY(), bounds.height(), logicalHeight,
                source.infiniburn(), source.effectsLocation(), source.ambientLight(),
                source.monsterSettings());
    }

    static NoiseSettings resizeNoiseSettings(
            NoiseSettings source, WorldVerticalBounds bounds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(bounds, "bounds");
        return NoiseSettings.create(bounds.minY(), bounds.height(),
                source.noiseSizeHorizontal(), source.noiseSizeVertical());
    }

    @SuppressWarnings("deprecation")
    static NoiseGeneratorSettings resizeGeneratorSettings(
            NoiseGeneratorSettings source, WorldVerticalBounds bounds) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(bounds, "bounds");
        return new NoiseGeneratorSettings(
                resizeNoiseSettings(source.noiseSettings(), bounds),
                source.defaultBlock(), source.defaultFluid(), source.noiseRouter(),
                source.surfaceRule(), source.spawnTarget(), source.seaLevel(),
                // The record constructor still requires this preserved field in 1.21.1.
                source.disableMobGeneration(), source.aquifersEnabled(), source.oreVeinsEnabled(),
                source.useLegacyRandomSource());
    }
}
