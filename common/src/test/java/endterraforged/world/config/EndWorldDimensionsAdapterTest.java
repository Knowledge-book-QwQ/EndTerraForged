package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.OptionalLong;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndWorldDimensionsAdapterTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dimensionTypeCopiesSemanticsAndReplacesOnlyVerticalBounds() {
        DimensionType.MonsterSettings monsters = new DimensionType.MonsterSettings(
                false, false, UniformInt.of(0, 7), 0);
        DimensionType source = new DimensionType(
                OptionalLong.of(6000L), false, false, false, false, 1.0D,
                false, false, -256, 512, 256, BlockTags.INFINIBURN_END,
                BuiltinDimensionTypes.END_EFFECTS, 0.0F, monsters);
        WorldVerticalBounds bounds = new WorldVerticalBounds(-512, 1536);

        DimensionType resized = EndWorldDimensionsAdapter.resizeDimensionType(source, bounds);

        assertEquals(-512, resized.minY());
        assertEquals(1536, resized.height());
        assertEquals(256, resized.logicalHeight());
        assertEquals(source.fixedTime(), resized.fixedTime());
        assertEquals(source.effectsLocation(), resized.effectsLocation());
        assertSame(monsters, resized.monsterSettings());
    }

    @Test
    void logicalHeightIsClampedWhenCreatingAShorterEnvelope() {
        DimensionType source = new DimensionType(
                OptionalLong.empty(), false, false, false, false, 1.0D,
                false, false, -256, 512, 256, BlockTags.INFINIBURN_END,
                BuiltinDimensionTypes.END_EFFECTS, 0.0F,
                new DimensionType.MonsterSettings(false, false, UniformInt.of(0, 7), 0));

        DimensionType resized = EndWorldDimensionsAdapter.resizeDimensionType(
                source, new WorldVerticalBounds(0, 16));

        assertEquals(16, resized.logicalHeight());
    }

    @Test
    void noiseSettingsUseTheSameExactEnvelopeAndKeepCellSizes() {
        NoiseSettings source = NoiseSettings.create(-256, 512, 2, 1);
        WorldVerticalBounds bounds = new WorldVerticalBounds(-512, 1536);

        NoiseSettings resized = EndWorldDimensionsAdapter.resizeNoiseSettings(source, bounds);

        assertEquals(-512, resized.minY());
        assertEquals(1536, resized.height());
        assertEquals(2, resized.noiseSizeHorizontal());
        assertEquals(1, resized.noiseSizeVertical());
    }

    @Test
    void generatorSettingsKeepEveryNonHeightField() {
        NoiseGeneratorSettings source = NoiseGeneratorSettings.dummy();
        WorldVerticalBounds bounds = new WorldVerticalBounds(-512, 1536);

        NoiseGeneratorSettings resized =
                EndWorldDimensionsAdapter.resizeGeneratorSettings(source, bounds);

        assertEquals(bounds.minY(), resized.noiseSettings().minY());
        assertEquals(bounds.height(), resized.noiseSettings().height());
        assertSame(source.defaultBlock(), resized.defaultBlock());
        assertSame(source.defaultFluid(), resized.defaultFluid());
        assertSame(source.noiseRouter(), resized.noiseRouter());
        assertSame(source.surfaceRule(), resized.surfaceRule());
        assertSame(source.spawnTarget(), resized.spawnTarget());
        assertEquals(source.seaLevel(), resized.seaLevel());
        assertEquals(source.aquifersEnabled(), resized.aquifersEnabled());
    }

    @Test
    void resizedDirectHoldersRoundTripThroughVanillaRegistryFileCodecs() {
        WorldVerticalBounds bounds = new WorldVerticalBounds(-512, 1536);
        DimensionType sourceType = new DimensionType(
                OptionalLong.of(6000L), false, false, false, false, 1.0D,
                false, false, -256, 512, 256, BlockTags.INFINIBURN_END,
                BuiltinDimensionTypes.END_EFFECTS, 0.0F,
                new DimensionType.MonsterSettings(false, false, UniformInt.of(0, 7), 0));
        DimensionType resizedType =
                EndWorldDimensionsAdapter.resizeDimensionType(sourceType, bounds);
        NoiseGeneratorSettings resizedSettings = EndWorldDimensionsAdapter.resizeGeneratorSettings(
                NoiseGeneratorSettings.dummy(), bounds);

        Holder<DimensionType> decodedType = roundTrip(
                DimensionType.CODEC, Holder.direct(resizedType));
        Holder<NoiseGeneratorSettings> decodedSettings = roundTrip(
                NoiseGeneratorSettings.CODEC, Holder.direct(resizedSettings));

        assertEquals(bounds.minY(), decodedType.value().minY());
        assertEquals(bounds.height(), decodedType.value().height());
        assertEquals(bounds.minY(), decodedSettings.value().noiseSettings().minY());
        assertEquals(bounds.height(), decodedSettings.value().noiseSettings().height());
    }

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, value);
        JsonElement json = encoded.result().orElseThrow(() -> new AssertionError(
                "encode failed: " + encoded.error().map(error -> error.message()).orElse("?")));
        DataResult<T> decoded = codec.parse(JsonOps.INSTANCE, json);
        return decoded.result().orElseThrow(() -> new AssertionError(
                "decode failed: " + decoded.error().map(error -> error.message()).orElse("?")));
    }
}
