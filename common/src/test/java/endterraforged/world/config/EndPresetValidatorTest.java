package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import endterraforged.world.filter.ErosionConfig;

/**
 * Contract tests for {@link EndPresetValidator}: the pure-logic validator
 * that enforces vanilla {@code DimensionType} / {@code NoiseSettings}
 * height constraints on {@link EndPreset}'s seven world-shape fields
 * and delegates the embedded {@link ErosionConfig} to
 * {@link endterraforged.world.filter.ErosionConfigValidator}.
 *
 * <p><b>Why these tests exist.</b> Without validation, a malformed preset
 * file (e.g. {@code "world_height": 100}) would decode cleanly and then
 * crash the worldgen bootstrap with a vanilla
 * {@link IllegalArgumentException} from inside {@code DimensionType} —
 * too late for the user to know which field was wrong. The validator
 * surfaces each violation as a {@link DataResult#error} at decode time,
 * with a field-specific message that points the user at the offending
 * preset field.</p>
 *
 * <p><b>What's pinned.</b></p>
 * <ul>
 *   <li>{@link EndPreset#defaults()} passes validation (continuity guard
 *       — a fresh world must always be valid).</li>
 *   <li>A fully-custom valid preset passes (all fields at non-boundary
 *       values).</li>
 *   <li>Each constraint has a "boundary passes" test (value at the edge
 *       of the allowed range) and a "violated" test (value just past the
 *       boundary fails with the right message).</li>
 *   <li>Cross-field constraint: {@code minY + worldHeight <= 2032}.</li>
 *   <li>Embedded {@link ErosionConfig} delegation: an invalid erosion
 *       config surfaces from the EndPreset validator with an "erosion
 *       config invalid" prefix.</li>
 *   <li>Fail-fast: only the first violation is reported.</li>
 *   <li>Null arg throws NPE (fail-fast for programmer error).</li>
 * </ul>
 */
class EndPresetValidatorTest {

    // ------------------------------------------------------------------
    //  DEFAULT passes (continuity guard)
    // ------------------------------------------------------------------

    @Test
    void defaultPresetPassesValidation() {
        // EndPreset.defaults() must always be valid — a future change
        // to the defaults that violates a constraint would break every
        // fresh world load. Pin it so the constraint is caught at the
        // validator level, not at worldgen bootstrap.
        DataResult<EndPreset> result =
                EndPresetValidator.validate(EndPreset.defaults());
        assertTrue(result.isSuccess(),
                "EndPreset.defaults() must pass validation");
    }

    // ------------------------------------------------------------------
    //  Fully custom valid preset
    // ------------------------------------------------------------------

    @Test
    void fullyCustomValidPresetPasses() {
        // A preset where every field differs from defaults() and is at
        // non-boundary values — guards against a swapped-field bug in
        // the validator (e.g. checking seaLevelY against the
        // islandBaselineY range).
        EndPreset custom = fullyCustomPreset();
        DataResult<EndPreset> result = EndPresetValidator.validate(custom);
        assertTrue(result.isSuccess(),
                "a fully-custom valid preset must pass: " + custom);
    }

    @Test
    void successReturnsSameInstance() {
        // The validator must not copy / wrap the input — EndPreset is
        // already an immutable record, so a defensive copy would be
        // wasteful and would break identity equality for callers.
        EndPreset custom = fullyCustomPreset();
        DataResult<EndPreset> result = EndPresetValidator.validate(custom);
        assertTrue(result.isSuccess());
        assertSame(custom, result.result().orElseThrow(),
                "validate() must return the same instance on success");
    }

    // ------------------------------------------------------------------
    //  worldHeight constraints
    // ------------------------------------------------------------------

    @Test
    void worldHeightAtMinimumIsValid() {
        // 16 is the boundary — smallest valid multiple of 16.
        EndPreset preset = withWorldHeight(EndPreset.defaults(), 16, 0);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "world_height = 16 (vanilla minimum) must be valid");
    }

    @Test
    void worldHeightAtMaximumIsValid() {
        // 4064 is the boundary — largest valid height (the EndTerraForged
        // default).
        EndPreset preset = withWorldHeight(EndPreset.defaults(), 4064, -2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "world_height = 4064 (vanilla maximum) must be valid");
    }

    @Test
    void worldHeightBelowMinimumIsInvalid() {
        // 15 — too small.
        EndPreset preset = withWorldHeight(EndPreset.defaults(), 15, 0);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "world_height = 15 (below vanilla minimum) must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("world_height"),
                "error message must name the offending field");
        assertTrue(msg.contains("[16, 4064]"),
                "error message must state the constraint range");
        assertTrue(msg.contains("15"),
                "error message must include the offending value");
    }

    @Test
    void worldHeightAboveMaximumIsInvalid() {
        // 4080 — too tall (above the 4064 cap).
        EndPreset preset = withWorldHeight(EndPreset.defaults(), 4080, -2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "world_height = 4080 (above vanilla maximum) must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("4080"));
    }

    @Test
    void worldHeightNotMultipleOf16IsInvalid() {
        // 100 — multiple-of-16 violation (would pass the range check
        // but fail the alignment check). Pin so the alignment constraint
        // is independently verified.
        EndPreset preset = withWorldHeight(EndPreset.defaults(), 100, 0);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "world_height = 100 (not a multiple of 16) must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("world_height"));
        assertTrue(msg.contains("multiple of 16"),
                "error message must state the alignment constraint");
        assertTrue(msg.contains("100"));
    }

    // ------------------------------------------------------------------
    //  minY constraints
    // ------------------------------------------------------------------

    @Test
    void minYAtMinimumIsValid() {
        // -2032 is the boundary — vanilla DimensionType lower bound.
        EndPreset preset = withMinY(EndPreset.defaults(), -2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "min_y = -2032 (vanilla minimum) must be valid");
    }

    @Test
    void minYAboveMinimumIsValid() {
        // 0 is well above the minimum — should be valid.
        EndPreset preset = withMinY(EndPreset.defaults(), 0);
        // Need to shrink worldHeight so minY + worldHeight <= 2032.
        preset = withWorldHeight(preset, 384, 0);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "min_y = 0 (well above minimum) must be valid");
    }

    @Test
    void minYBelowMinimumIsInvalid() {
        // -2048 — below the -2032 lower bound.
        EndPreset preset = withMinY(EndPreset.defaults(), -2048);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "min_y = -2048 (below vanilla minimum) must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y"));
        assertTrue(msg.contains(">= -2032"));
        assertTrue(msg.contains("-2048"));
    }

    @Test
    void minYNotMultipleOf16IsInvalid() {
        // -2031 is inside the range but not a multiple of 16.
        EndPreset preset = withMinY(EndPreset.defaults(), -2031);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "min_y = -2031 (not a multiple of 16) must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y"));
        assertTrue(msg.contains("multiple of 16"));
        assertTrue(msg.contains("-2031"));
    }

    // ------------------------------------------------------------------
    //  Cross-field constraint: minY + worldHeight <= 2032
    // ------------------------------------------------------------------

    @Test
    void worldTopAtMaximumIsValid() {
        // -2032 + 4064 = 2032 — exactly the vanilla top limit.
        // This is the EndTerraForged default.
        EndPreset preset = new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ErosionConfig.DEFAULT);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "min_y + world_height = 2032 (exactly at vanilla top) must be valid");
    }

    @Test
    void worldTopAboveMaximumIsInvalid() {
        // minY=0 + worldHeight=4064 = 4064 > 2032 — violates the top
        // constraint even though worldHeight alone is in range.
        EndPreset preset = new EndPreset(4064, 0, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ErosionConfig.DEFAULT);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "min_y + world_height > 2032 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y + world_height"));
        assertTrue(msg.contains("<= 2032"));
        assertTrue(msg.contains("4064"));
        assertTrue(msg.contains("0 + 4064"),
                "error message must show the breakdown");
    }

    // ------------------------------------------------------------------
    //  seaLevelY constraints
    // ------------------------------------------------------------------

    @Test
    void seaLevelYAtLowerBoundIsValid() {
        // seaLevelY = minY = -2032 — boundary, valid.
        EndPreset preset = withSeaLevelY(EndPreset.defaults(), -2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "sea_level_y = minY (lower bound) must be valid");
    }

    @Test
    void seaLevelYAtUpperBoundIsValid() {
        // seaLevelY = minY + worldHeight - 1 = -2032 + 4064 - 1 = 2031.
        EndPreset preset = withSeaLevelY(EndPreset.defaults(), 2031);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "sea_level_y = maxY (upper bound) must be valid");
    }

    @Test
    void seaLevelYBelowLowerBoundIsInvalid() {
        // seaLevelY = -2033 — below minY=-2032.
        EndPreset preset = withSeaLevelY(EndPreset.defaults(), -2033);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "sea_level_y below minY must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("sea_level_y"));
        assertTrue(msg.contains("[-2032, 2031]"));
        assertTrue(msg.contains("-2033"));
    }

    @Test
    void seaLevelYAboveUpperBoundIsInvalid() {
        // seaLevelY = 2032 — equal to minY + worldHeight, which is
        // outside the bounds (max valid is 2031).
        EndPreset preset = withSeaLevelY(EndPreset.defaults(), 2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "sea_level_y >= minY + worldHeight must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("sea_level_y"));
        assertTrue(msg.contains("2032"));
    }

    // ------------------------------------------------------------------
    //  islandBaselineY constraints
    // ------------------------------------------------------------------

    @Test
    void islandBaselineYAtLowerBoundIsValid() {
        EndPreset preset = withIslandBaselineY(EndPreset.defaults(), -2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "island_baseline_y = minY (lower bound) must be valid");
    }

    @Test
    void islandBaselineYAtUpperBoundIsValid() {
        EndPreset preset = withIslandBaselineY(EndPreset.defaults(), 2031);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertTrue(result.isSuccess(),
                "island_baseline_y = maxY (upper bound) must be valid");
    }

    @Test
    void islandBaselineYBelowLowerBoundIsInvalid() {
        EndPreset preset = withIslandBaselineY(EndPreset.defaults(), -2033);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "island_baseline_y below minY must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("island_baseline_y"));
        assertTrue(msg.contains("[-2032, 2031]"));
    }

    @Test
    void islandBaselineYAboveUpperBoundIsInvalid() {
        EndPreset preset = withIslandBaselineY(EndPreset.defaults(), 2032);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "island_baseline_y >= minY + worldHeight must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("island_baseline_y"));
    }

    // ------------------------------------------------------------------
    //  ErosionConfig delegation
    // ------------------------------------------------------------------

    @Test
    void invalidErosionConfigSurfacesFromEndPresetValidator() {
        // An EndPreset whose embedded ErosionConfig is invalid must
        // fail validation via delegation, with the error prefixed
        // "erosion config invalid:" so the user knows it's the embedded
        // config, not a top-level preset field.
        EndPreset preset = new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                new ErosionConfig(-1, 32, 1.0F, 1.0F, 0.5F, 0.5F));
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess(),
                "an EndPreset with an invalid ErosionConfig must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("erosion config invalid"),
                "error message must indicate the erosion sub-config");
        // The inner validator's message must be propagated (so the user
        // knows which erosion field is wrong).
        assertTrue(msg.contains("droplets_per_chunk"),
                "inner error message must be preserved");
    }

    @Test
    void invalidErosionRateSurfacesFromEndPresetValidator() {
        // Same delegation, different field — guards against the
        // validator skipping the erosion check on success of the
        // parent fields.
        EndPreset preset = new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                new ErosionConfig(128, 32, 1.0F, 1.0F, 1.5F, 0.5F));
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess());
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("erosion_rate"));
    }

    // ------------------------------------------------------------------
    //  Fail-fast: first violation only (not a list)
    // ------------------------------------------------------------------

    @Test
    void firstViolationIsReportedNotAll() {
        // A preset with multiple violations (bad worldHeight AND bad
        // seaLevelY AND invalid erosion) should report only the first
        // — keeping the error message focused on one fix at a time.
        EndPreset preset = new EndPreset(15, -2033, 99999, -99999,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                new ErosionConfig(-1, 0, -1.0F, -1.0F, 2.0F, 2.0F));
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        assertFalse(result.isSuccess());
        String msg = result.error().orElseThrow().message();
        // world_height is the first check, so it must be reported.
        assertTrue(msg.contains("world_height"),
                "the first violation (world_height) must be reported");
        // The other violations must NOT appear in this error message.
        assertFalse(msg.contains("sea_level_y"),
                "later violations must not appear in the same error message");
        assertFalse(msg.contains("island_baseline_y"));
        assertFalse(msg.contains("erosion"));
    }

    // ------------------------------------------------------------------
    //  Null-arg fails fast (NPE)
    // ------------------------------------------------------------------

    @Test
    void nullArgThrowsNpe() {
        // null preset is a programmer error — the codec never passes
        // null here (EndPreset.CODEC decodes to a non-null instance
        // before validation runs), but if it did, an NPE at the call
        // site is clearer than a misleading "field must be" error on
        // a null field access.
        assertThrows(NullPointerException.class,
                () -> EndPresetValidator.validate(null));
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    /**
     * Returns a preset where every field differs from
     * {@link EndPreset#defaults()} — used by tests that need to verify
     * the validator accepts a non-default preset (catches swapped-field
     * bugs in the validator).
     */
    private static EndPreset fullyCustomPreset() {
        return new EndPreset(384, -64, 63, 100,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F));
    }

    /**
     * Returns a copy of {@code base} with {@code worldHeight} overridden.
     * Also adjusts {@code seaLevelY} and {@code islandBaselineY} to keep
     * them within the new bounds (callers passing in-bounds values don't
     * need to worry about the bounds check).
     */
    private static EndPreset withWorldHeight(EndPreset base, int worldHeight, int minY) {
        return new EndPreset(worldHeight, minY, base.seaLevelY(), base.islandBaselineY(),
                base.seaMode(), base.topologyMode(), base.floatingIslandsEnabled(),
                base.erosionConfig());
    }

    private static EndPreset withMinY(EndPreset base, int minY) {
        return new EndPreset(base.worldHeight(), minY, base.seaLevelY(), base.islandBaselineY(),
                base.seaMode(), base.topologyMode(), base.floatingIslandsEnabled(),
                base.erosionConfig());
    }

    private static EndPreset withSeaLevelY(EndPreset base, int seaLevelY) {
        return new EndPreset(base.worldHeight(), base.minY(), seaLevelY, base.islandBaselineY(),
                base.seaMode(), base.topologyMode(), base.floatingIslandsEnabled(),
                base.erosionConfig());
    }

    private static EndPreset withIslandBaselineY(EndPreset base, int islandBaselineY) {
        return new EndPreset(base.worldHeight(), base.minY(), base.seaLevelY(), islandBaselineY,
                base.seaMode(), base.topologyMode(), base.floatingIslandsEnabled(),
                base.erosionConfig());
    }
}
