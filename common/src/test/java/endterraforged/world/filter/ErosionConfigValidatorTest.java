package endterraforged.world.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

/**
 * Contract tests for {@link ErosionConfigValidator}: the pure-logic
 * validator that enforces the hydrology simulation's physical constraints
 * on {@link ErosionConfig}'s six numeric fields.
 *
 * <p><b>Why these tests exist.</b> Without validation, a malformed preset
 * file (e.g. {@code "droplets_per_chunk": -1}) would decode cleanly and
 * then crash the worldgen bootstrap with a vanilla
 * {@link IllegalArgumentException} from deep inside the hydrology
 * simulation — confusing for the user, who sees only "world creation
 * failed". The validator surfaces each violation as a clean
 * {@link DataResult#error} at decode time, with a field-specific message
 * that points the user at the offending preset field.</p>
 *
 * <p><b>What's pinned.</b></p>
 * <ul>
 *   <li>Each of the six fields has a "valid" test (a value at the boundary
 *       of the allowed range passes) and a "violated" test (a value
 *       just outside the boundary fails with the right message).</li>
 *   <li>The {@link ErosionConfig#DEFAULT} constant passes validation
 *       (continuity guard — defaults must always be valid).</li>
 *   <li>The validator returns the same instance on success (no defensive
 *       copy — {@link ErosionConfig} is already immutable).</li>
 *   <li>A {@code null} argument throws {@link NullPointerException}
 *       (fail-fast for programmer error — the codec never passes null
 *       here, but if it did, an NPE at the call site is clearer than a
 *       misleading "field must be >= 0" error on a null field).</li>
 *   <li>Fail-fast philosophy: only the first violation is reported
 *       (not a list of all violations).</li>
 * </ul>
 */
class ErosionConfigValidatorTest {

    // ------------------------------------------------------------------
    //  DEFAULT passes (continuity guard)
    // ------------------------------------------------------------------

    @Test
    void defaultErosionConfigPassesValidation() {
        // ErosionConfig.DEFAULT must always be valid — a future change
        // to the defaults that violates a constraint would break every
        // fresh world load. Pin it so the constraint is caught at the
        // validator level, not at worldgen bootstrap.
        DataResult<ErosionConfig> result =
                ErosionConfigValidator.validate(ErosionConfig.DEFAULT);
        assertTrue(result.isSuccess(),
                "ErosionConfig.DEFAULT must pass validation");
    }

    // ------------------------------------------------------------------
    //  dropletsPerChunk: >= 0
    // ------------------------------------------------------------------

    @Test
    void dropletsPerChunkZeroIsValid() {
        // 0 is the boundary — a preset with 0 droplets per chunk is
        // nonsensical (no erosion simulated) but not physically invalid;
        // the user might want to disable erosion entirely. Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(0, 32, 1.0F, 1.0F, 0.5F, 0.5F));
        assertTrue(result.isSuccess(),
                "droplets_per_chunk = 0 must be valid (disable-erosion case)");
    }

    @Test
    void dropletsPerChunkNegativeIsInvalid() {
        // -1 is just past the boundary — would cause the droplet-sim loop
        // to never terminate (loop guard is i < dropletsPerChunk).
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(-1, 32, 1.0F, 1.0F, 0.5F, 0.5F));
        assertFalse(result.isSuccess(),
                "droplets_per_chunk = -1 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplets_per_chunk"),
                "error message must name the offending field");
        assertTrue(msg.contains(">= 0"),
                "error message must state the constraint");
        assertTrue(msg.contains("-1"),
                "error message must include the offending value");
    }

    // ------------------------------------------------------------------
    //  dropletLifetime: >= 1
    // ------------------------------------------------------------------

    @Test
    void dropletLifetimeOneIsValid() {
        // 1 is the boundary — a droplet that takes exactly 1 step. Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 1, 1.0F, 1.0F, 0.5F, 0.5F));
        assertTrue(result.isSuccess(),
                "droplet_lifetime = 1 must be valid (single-step droplet)");
    }

    @Test
    void dropletLifetimeZeroIsInvalid() {
        // 0 means the droplet expires before its first step — simulates
        // nothing. Reject so the user notices the misconfiguration.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 0, 1.0F, 1.0F, 0.5F, 0.5F));
        assertFalse(result.isSuccess(),
                "droplet_lifetime = 0 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplet_lifetime"),
                "error message must name the offending field");
        assertTrue(msg.contains(">= 1"),
                "error message must state the constraint");
        assertTrue(msg.contains("0"),
                "error message must include the offending value");
    }

    @Test
    void dropletLifetimeNegativeIsInvalid() {
        // Negative is nonsensical — pinned for symmetry with the
        // dropletsPerChunk negative test.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, -1, 1.0F, 1.0F, 0.5F, 0.5F));
        assertFalse(result.isSuccess(),
                "droplet_lifetime = -1 must be rejected");
    }

    // ------------------------------------------------------------------
    //  dropletVolume: >= 0.0
    // ------------------------------------------------------------------

    @Test
    void dropletVolumeZeroIsValid() {
        // 0.0 is the boundary — a droplet with 0 volume carries no
        // sediment, so erosion is disabled (but the simulation still
        // runs and deposits nothing). Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 0.0F, 1.0F, 0.5F, 0.5F));
        assertTrue(result.isSuccess(),
                "droplet_volume = 0.0 must be valid (no-sediment case)");
    }

    @Test
    void dropletVolumeNegativeIsInvalid() {
        // Negative volume is physically meaningless — capacity =
        // volume * (1 - velocity) would be negative.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, -0.1F, 1.0F, 0.5F, 0.5F));
        assertFalse(result.isSuccess(),
                "droplet_volume = -0.1 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplet_volume"),
                "error message must name the offending field");
        assertTrue(msg.contains(">= 0.0"),
                "error message must state the constraint");
        assertTrue(msg.contains("-0.1"),
                "error message must include the offending value");
    }

    // ------------------------------------------------------------------
    //  dropletVelocity: >= 0.0
    // ------------------------------------------------------------------

    @Test
    void dropletVelocityZeroIsValid() {
        // 0.0 is the boundary — a droplet with 0 initial velocity still
        // accelerates downhill (gravity), so the simulation is valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 0.0F, 0.5F, 0.5F));
        assertTrue(result.isSuccess(),
                "droplet_velocity = 0.0 must be valid (gravity-driven case)");
    }

    @Test
    void dropletVelocityNegativeIsInvalid() {
        // Negative velocity is physically meaningless — velocity^2 is
        // used for erosive force, so the sign is irrelevant in the math
        // but the value signals a misconfigured preset.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, -0.1F, 0.5F, 0.5F));
        assertFalse(result.isSuccess(),
                "droplet_velocity = -0.1 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplet_velocity"),
                "error message must name the offending field");
        assertTrue(msg.contains(">= 0.0"),
                "error message must state the constraint");
    }

    // ------------------------------------------------------------------
    //  erosionRate: in [0.0, 1.0]
    // ------------------------------------------------------------------

    @Test
    void erosionRateZeroIsValid() {
        // 0.0 is the lower boundary — erosionRate = 0 means no erosion
        // happens (deposition still does). Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 0.0F, 0.5F));
        assertTrue(result.isSuccess(),
                "erosion_rate = 0.0 must be valid (no-erosion case)");
    }

    @Test
    void erosionRateOneIsValid() {
        // 1.0 is the upper boundary — erosionRate = 1 means the full
        // excess capacity is removed per step. Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 1.0F, 0.5F));
        assertTrue(result.isSuccess(),
                "erosion_rate = 1.0 must be valid (full-erosion case)");
    }

    @Test
    void erosionRateNegativeIsInvalid() {
        // < 0: anti-erosion (would deposit sediment on the downhill
        // path, reversing the simulation's intent).
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, -0.01F, 0.5F));
        assertFalse(result.isSuccess(),
                "erosion_rate = -0.01 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("erosion_rate"),
                "error message must name the offending field");
        assertTrue(msg.contains("[0.0, 1.0]"),
                "error message must state the constraint range");
        assertTrue(msg.contains("-0.01"),
                "error message must include the offending value");
    }

    @Test
    void erosionRateAboveOneIsInvalid() {
        // > 1: over-erodes (removes more than the full excess capacity
        // per step, over-carving the terrain).
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 1.01F, 0.5F));
        assertFalse(result.isSuccess(),
                "erosion_rate = 1.01 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("1.01"),
                "error message must include the offending value");
    }

    // ------------------------------------------------------------------
    //  depositRate: in [0.0, 1.0]
    // ------------------------------------------------------------------

    @Test
    void depositRateZeroIsValid() {
        // 0.0 is the lower boundary — depositRate = 0 means no
        // deposition happens (erosion still does). Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 0.5F, 0.0F));
        assertTrue(result.isSuccess(),
                "deposit_rate = 0.0 must be valid (no-deposit case)");
    }

    @Test
    void depositRateOneIsValid() {
        // 1.0 is the upper boundary — depositRate = 1 means the full
        // oversaturated sediment is deposited per step. Valid.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 0.5F, 1.0F));
        assertTrue(result.isSuccess(),
                "deposit_rate = 1.0 must be valid (full-deposit case)");
    }

    @Test
    void depositRateNegativeIsInvalid() {
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 0.5F, -0.01F));
        assertFalse(result.isSuccess(),
                "deposit_rate = -0.01 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("deposit_rate"),
                "error message must name the offending field");
        assertTrue(msg.contains("[0.0, 1.0]"),
                "error message must state the constraint range");
    }

    @Test
    void depositRateAboveOneIsInvalid() {
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(128, 32, 1.0F, 1.0F, 0.5F, 1.01F));
        assertFalse(result.isSuccess(),
                "deposit_rate = 1.01 must be rejected");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("1.01"),
                "error message must include the offending value");
    }

    // ------------------------------------------------------------------
    //  Fail-fast: first violation only (not a list)
    // ------------------------------------------------------------------

    @Test
    void firstViolationIsReportedNotAll() {
        // A config with multiple violations (negative dropletsPerChunk
        // AND out-of-range erosionRate) should report only the first
        // — keeping the error message focused on one fix at a time.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(-1, 0, -1.0F, -1.0F, 2.0F, 2.0F));
        assertFalse(result.isSuccess(),
                "a config with multiple violations must still fail");
        String msg = result.error().orElseThrow().message();
        // Only the first violation (droplets_per_chunk) should appear —
        // the other five bad fields should not be mentioned in this
        // error message (they'll surface one at a time as the user
        // fixes each and re-decodes).
        assertTrue(msg.contains("droplets_per_chunk"),
                "the first violation (droplets_per_chunk) must be reported");
        assertFalse(msg.contains("erosion_rate"),
                "later violations must not appear in the same error message");
    }

    // ------------------------------------------------------------------
    //  Returns same instance on success (no defensive copy)
    // ------------------------------------------------------------------

    @Test
    void successReturnsSameInstance() {
        // The validator must not copy / wrap the input — ErosionConfig
        // is already immutable, so a defensive copy would be wasteful
        // and would break identity equality for callers that rely on
        // the decoded instance being the same one they passed in.
        ErosionConfig config = new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F);
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(config);
        assertTrue(result.isSuccess());
        assertSame(config, result.result().orElseThrow(),
                "validate() must return the same instance on success");
    }

    // ------------------------------------------------------------------
    //  Null-arg fails fast (NPE, not silent return)
    // ------------------------------------------------------------------

    @Test
    void nullArgThrowsNpe() {
        // null config is a programmer error — the codec never passes
        // null here (ErosionConfig.CODEC decodes to a non-null instance
        // before validation runs), but if it did, an NPE at the call
        // site is clearer than a misleading "field must be >= 0" error
        // on a null field access.
        assertThrows(NullPointerException.class,
                () -> ErosionConfigValidator.validate(null));
    }

    // ------------------------------------------------------------------
    //  Comprehensive valid config (all fields within bounds)
    // ------------------------------------------------------------------

    @Test
    void fullyCustomValidConfigPasses() {
        // A config where every field differs from DEFAULT and is at a
        // non-boundary value — guards against a swapped-field bug in
        // the validator (e.g. checking dropletLifetime against the
        // dropletsPerChunk constraint).
        ErosionConfig custom = new ErosionConfig(256, 48, 1.5F, 1.2F, 0.7F, 0.3F);
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(custom);
        assertTrue(result.isSuccess(),
                "a fully-custom valid config must pass: " + custom);
        assertSame(custom, result.result().orElseThrow());
    }

    @Test
    void largeDropletsPerChunkIsValid() {
        // A large droplet count is expensive but valid — the GUI slider
        // goes up to 1024, and a user who wants high-quality erosion
        // should be able to set it. Pin so a future "cap at 256" change
        // to the validator is caught.
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(
                new ErosionConfig(1024, 32, 1.0F, 1.0F, 0.5F, 0.5F));
        assertTrue(result.isSuccess(),
                "droplets_per_chunk = 1024 (GUI slider max) must be valid");
    }
}
