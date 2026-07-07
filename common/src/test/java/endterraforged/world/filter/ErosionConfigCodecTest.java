package endterraforged.world.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

/**
 * Round-trip, default, and validation tests for {@link ErosionConfig#CODEC}.
 *
 * <p>These pin the codec contract that the stage-5 GUI / data-pack preset
 * files rely on: {@link ErosionConfig#DEFAULT} round-trips losslessly, an
 * empty JSON object decodes to {@link ErosionConfig#DEFAULT}, and constraint
 * violations (wired in via {@link ErosionConfigValidator#validate} on the
 * decode side) surface as {@link DataResult#error} with field-specific
 * messages instead of silently producing values that would crash the
 * hydrology simulation later.</p>
 *
 * <p>The validator-level boundary tests live in
 * {@link ErosionConfigValidatorTest} — these tests focus on the codec
 * integration (JSON → {@link DataResult}) rather than re-pinning every
 * boundary value.</p>
 */
class ErosionConfigCodecTest {

    private static ErosionConfig decode(JsonElement json) {
        DataResult<ErosionConfig> result = ErosionConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(ErosionConfig config) {
        DataResult<JsonElement> result = ErosionConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }

    private static DataResult<ErosionConfig> parseResult(String json) {
        return ErosionConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    // ------------------------------------------------------------------
    //  Round-trip + defaults
    // ------------------------------------------------------------------

    @Test
    void defaultErosionConfigRoundTripsLosslessly() {
        ErosionConfig original = ErosionConfig.DEFAULT;
        ErosionConfig roundTripped = decode(encode(original));
        assertEquals(original, roundTripped,
                "ErosionConfig.DEFAULT must survive a codec round-trip unchanged");
    }

    @Test
    void customErosionConfigRoundTripsLosslessly() {
        // Every field differs from DEFAULT so a swapped-field codec bug
        // (e.g. erosionRate <-> depositRate) would be caught.
        ErosionConfig custom = new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F);
        ErosionConfig roundTripped = decode(encode(custom));
        assertEquals(custom, roundTripped,
                "a fully-custom ErosionConfig must survive a codec round-trip unchanged");
    }

    @Test
    void emptyObjectDecodesToDefault() {
        // Every codec field has a default, so {} must yield ErosionConfig.DEFAULT.
        ErosionConfig decoded = decode(new JsonObject());
        assertEquals(ErosionConfig.DEFAULT, decoded,
                "an empty erosion object must decode to the canonical defaults");
    }

    @Test
    void omittingOneFieldFallsBackToThatFieldDefault() {
        // A partial JSON with one field set must inherit defaults for the rest.
        JsonObject partial = new JsonObject();
        partial.addProperty("droplets_per_chunk", 256);
        ErosionConfig decoded = decode(partial);
        assertEquals(256, decoded.dropletsPerChunk,
                "the explicitly-set field must be honoured");
        assertEquals(ErosionConfig.DEFAULT.dropletLifetime, decoded.dropletLifetime,
                "omitted droplet_lifetime must fall back to default");
        assertEquals(ErosionConfig.DEFAULT.erosionRate, decoded.erosionRate,
                "omitted erosion_rate must fall back to default");
    }

    @Test
    void defaultEncodesCompactlyToEmptyObject() {
        // optionalFieldOf elides fields whose value equals the default, so
        // ErosionConfig.DEFAULT serialises to {} — pinning the compact form
        // so a future switch to a non-compact codec is caught.
        JsonObject json = encode(ErosionConfig.DEFAULT).getAsJsonObject();
        assertEquals(0, json.size(),
                "ErosionConfig.DEFAULT must encode to an empty object: " + json);
    }

    // ------------------------------------------------------------------
    //  Codec-level validation (ErosionConfigValidator via flatXmap)
    // ------------------------------------------------------------------

    @Test
    void decodeRejectsNegativeDropletsPerChunk() {
        // -1 would cause the droplet-sim loop to never terminate
        // (loop guard is i < dropletsPerChunk).
        DataResult<ErosionConfig> result = parseResult(
                "{\"droplets_per_chunk\": -1}");
        assertTrue(result.error().isPresent(),
                "droplets_per_chunk < 0 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplets_per_chunk"));
        assertTrue(msg.contains(">= 0"));
        assertTrue(msg.contains("-1"));
    }

    @Test
    void decodeRejectsZeroDropletLifetime() {
        // 0 means the droplet expires before its first step — simulates nothing.
        DataResult<ErosionConfig> result = parseResult(
                "{\"droplet_lifetime\": 0}");
        assertTrue(result.error().isPresent(),
                "droplet_lifetime = 0 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplet_lifetime"));
        assertTrue(msg.contains(">= 1"));
    }

    @Test
    void decodeRejectsNegativeDropletVolume() {
        // Negative water volume is physically meaningless.
        DataResult<ErosionConfig> result = parseResult(
                "{\"droplet_volume\": -0.5}");
        assertTrue(result.error().isPresent(),
                "droplet_volume < 0 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplet_volume"));
        assertTrue(msg.contains(">= 0.0"));
    }

    @Test
    void decodeRejectsErosionRateAboveOne() {
        // 1.5 is outside [0, 1] — would over-carve the terrain.
        DataResult<ErosionConfig> result = parseResult(
                "{\"erosion_rate\": 1.5}");
        assertTrue(result.error().isPresent(),
                "erosion_rate > 1 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("erosion_rate"));
        assertTrue(msg.contains("[0.0, 1.0]"));
    }

    @Test
    void decodeRejectsDepositRateBelowZero() {
        // -0.1 is outside [0, 1] — would anti-deposit sediment.
        DataResult<ErosionConfig> result = parseResult(
                "{\"deposit_rate\": -0.1}");
        assertTrue(result.error().isPresent(),
                "deposit_rate < 0 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("deposit_rate"));
        assertTrue(msg.contains("[0.0, 1.0]"));
    }

    @Test
    void decodeAcceptsBoundaryZeroDropletsPerChunk() {
        // 0 is the boundary — disable-erosion case, valid.
        DataResult<ErosionConfig> result = parseResult(
                "{\"droplets_per_chunk\": 0}");
        assertTrue(result.isSuccess(),
                "droplets_per_chunk = 0 (disable-erosion case) must be valid");
    }

    @Test
    void decodeAcceptsBoundaryRateOfOne() {
        // 1.0 is the boundary for both rates — valid.
        DataResult<ErosionConfig> result = parseResult(
                "{\"erosion_rate\": 1.0, \"deposit_rate\": 1.0}");
        assertTrue(result.isSuccess(),
                "rates at the [0, 1] boundary (1.0) must be valid");
    }

    @Test
    void decodeReportsOnlyFirstViolationWhenMultiplePresent() {
        // Multiple violations: bad droplets_per_chunk (-1) AND bad
        // erosion_rate (2.0). Fail-fast: only the first violation
        // (droplets_per_chunk) must be reported.
        DataResult<ErosionConfig> result = parseResult(
                "{\"droplets_per_chunk\": -1, \"erosion_rate\": 2.0}");
        assertTrue(result.error().isPresent());
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplets_per_chunk"),
                "the first violation must be reported");
        assertFalse(msg.contains("erosion_rate"),
                "later violations must not appear in the same error message");
    }
}
