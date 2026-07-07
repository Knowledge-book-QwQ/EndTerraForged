/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Pure-logic validator
 * for {@link ErosionConfig}: enforces the hydrology simulation's physical
 * constraints (non-negative droplet counts, rates in [0,1]) and surfaces
 * violations as a {@link com.mojang.serialization.DataResult#error} so a
 * malformed preset file fails decode cleanly rather than crashing the
 * worldgen bootstrap with a vanilla {@link IllegalArgumentException}.
 *
 * <p>RTF (the MIT-licensed upstream) has no equivalent validator — its
 * {@code FilterSettings.Erosion} codec uses bare {@code Codec.INT} /
 * {@code Codec.FLOAT} for every field, deferring all checks to vanilla's
 * worldgen pipeline where errors surface as confusing late-stage crashes.
 * EndTerraForged closes that gap by validating at decode time, following
 * the project's own {@code SEA_MODE_CODEC} {@code flatXmap} precedent.</p>
 */
package endterraforged.world.filter;

import com.mojang.serialization.DataResult;

/**
 * Pure-logic validator for {@link ErosionConfig}'s six numeric fields.
 *
 * <p><b>The problem.</b> Without validation, a preset file with
 * {@code "droplets_per_chunk": -1} or {@code "erosion_rate": 1.5} decodes
 * cleanly and then breaks the hydrology simulation at runtime:
 * negative droplet counts cause infinite loops (the loop guard
 * {@code i < dropletsPerChunk} never exits when {@code dropletsPerChunk}
 * is negative), and rates outside {@code [0, 1]} produce nonsensical
 * erosion/deposit magnitudes (e.g. {@code erosionRate = 2.0} removes
 * twice the excess capacity per step, over-carving the terrain). These
 * failures surface as a vanilla {@link IllegalArgumentException} from
 * deep inside the worldgen pipeline — confusing for the user, who
 * sees only "world creation failed" with no pointer to the offending
 * preset field.</p>
 *
 * <p><b>The fix.</b> This validator runs at codec decode time (wired in
 * via {@code ErosionConfig.CODEC.flatXmap(ErosionConfigValidator::validate,
 * DataResult::success)}) and surfaces each constraint violation as a
 * {@link DataResult#error} with a field-specific message. DFU reports
 * these errors cleanly to the user ("Failed to decode preset: erosion_rate
 * must be in [0, 1], got 1.5") instead of letting the bad value reach
 * the worldgen bootstrap.</p>
 *
 * <p><b>Constraints enforced.</b></p>
 * <ul>
 *   <li>{@code dropletsPerChunk >= 0} — negative counts would never
 *       terminate the droplet-simulation loop.</li>
 *   <li>{@code dropletLifetime >= 1} — a lifetime of 0 means the
 *       droplet expires before its first step, simulating nothing;
 *       negative is nonsensical.</li>
 *   <li>{@code dropletVolume >= 0.0} — negative water volume is
 *       physically meaningless (the simulation multiplies capacity by
 *       volume to compute sediment carrying capacity).</li>
 *   <li>{@code dropletVelocity >= 0.0} — negative velocity is
 *       physically meaningless (velocity is squared and used to compute
 *       erosive force).</li>
 *   <li>{@code erosionRate in [0.0, 1.0]} — the rate is a fraction of
 *       excess capacity removed per step; outside {@code [0, 1]} it
 *       over-carves (rate {@code > 1}) or anti-erodes (rate {@code < 0}).</li>
 *   <li>{@code depositRate in [0.0, 1.0]} — same logic: a fraction of
 *       oversaturated sediment deposited per step.</li>
 * </ul>
 *
 * <p><b>Design — why a separate class.</b> Keeping the validation logic
 * out of {@link ErosionConfig} itself lets it be unit-tested in
 * isolation (the {@link ErosionConfig} POJO has no test surface beyond
 * its {@code equals} / {@code hashCode}). It also lets
 * {@link endterraforged.world.config.EndPresetValidator} delegate to
 * this class for the embedded erosion config, so cross-field constraints
 * on the parent preset don't duplicate the erosion-field checks.</p>
 *
 * <p><b>Fail-fast philosophy.</b> Returns {@link DataResult#error} on
 * the first violation encountered (not a list of all violations).
 * Reporting one error at a time keeps the error message focused and
 * avoids overwhelming the user; a preset with multiple bad fields will
 * surface each error in turn as they're fixed and re-decoded. This
 * matches the project's existing {@code SEA_MODE_CODEC} {@code flatXmap}
 * pattern.</p>
 *
 * <p><b>Thread-safety.</b> Stateless — all methods are static and
 * operate only on their arguments. Safe to call from any thread.</p>
 */
public final class ErosionConfigValidator {

    /**
     * Lower bound for {@code dropletVolume} and {@code dropletVelocity}.
     * The hydrology simulation multiplies these by capacity/velocity
     * magnitudes, so negative values would produce nonsensical results.
     */
    private static final float MIN_FLOAT_FIELD = 0.0f;

    /**
     * Lower bound for {@code erosionRate} and {@code depositRate}.
     * Both are fractions (of excess capacity / oversaturated sediment
     * respectively), so the lower bound is {@code 0.0}.
     */
    private static final float MIN_RATE = 0.0f;

    /**
     * Upper bound for {@code erosionRate} and {@code depositRate}.
     * A fraction cannot exceed {@code 1.0} — a rate of {@code 1.0}
     * means "remove / deposit the full excess per step", and values
     * above {@code 1.0} would over-apply the operation.
     */
    private static final float MAX_RATE = 1.0f;

    private ErosionConfigValidator() {
        // Utility class — no instances.
    }

    /**
     * Validates the given {@link ErosionConfig}.
     *
     * <p>Returns {@link DataResult#success(Object)} if all six field
     * constraints are satisfied, or {@link DataResult#error} with a
     * field-specific message describing the first violation.</p>
     *
     * @param config the erosion config to validate; must not be {@code null}
     * @return a successful {@link DataResult} carrying the config if valid,
     *         or an error {@link DataResult} describing the violation
     * @throws NullPointerException if {@code config} is {@code null}
     *         (programmer error — the codec should never pass null here
     *         because {@code ErosionConfig.CODEC} decodes to a non-null
     *         instance before validation runs)
     */
    public static DataResult<ErosionConfig> validate(ErosionConfig config) {
        // dropletsPerChunk: >= 0 (negative would never terminate the
        // droplet-simulation loop — the loop guard is i < dropletsPerChunk).
        if (config.dropletsPerChunk < 0) {
            return DataResult.error(() ->
                    "erosion.droplets_per_chunk must be >= 0, got "
                            + config.dropletsPerChunk);
        }
        // dropletLifetime: >= 1 (a lifetime of 0 means the droplet expires
        // before its first step, simulating nothing).
        if (config.dropletLifetime < 1) {
            return DataResult.error(() ->
                    "erosion.droplet_lifetime must be >= 1, got "
                            + config.dropletLifetime);
        }
        // dropletVolume: >= 0.0 (negative water volume is physically
        // meaningless — capacity = volume * (1 - velocity) would be negative).
        if (config.dropletVolume < MIN_FLOAT_FIELD) {
            return DataResult.error(() ->
                    "erosion.droplet_volume must be >= 0.0, got "
                            + config.dropletVolume);
        }
        // dropletVelocity: >= 0.0 (negative velocity is physically
        // meaningless — velocity^2 is used for erosive force).
        if (config.dropletVelocity < MIN_FLOAT_FIELD) {
            return DataResult.error(() ->
                    "erosion.droplet_velocity must be >= 0.0, got "
                            + config.dropletVelocity);
        }
        // erosionRate: in [0, 1] (fraction of excess capacity removed
        // per step; outside [0, 1] over-carves or anti-erodes).
        if (config.erosionRate < MIN_RATE || config.erosionRate > MAX_RATE) {
            return DataResult.error(() ->
                    "erosion.erosion_rate must be in [0.0, 1.0], got "
                            + config.erosionRate);
        }
        // depositRate: in [0, 1] (fraction of oversaturated sediment
        // deposited per step).
        if (config.depositRate < MIN_RATE || config.depositRate > MAX_RATE) {
            return DataResult.error(() ->
                    "erosion.deposit_rate must be in [0.0, 1.0], got "
                            + config.depositRate);
        }
        return DataResult.success(config);
    }
}
