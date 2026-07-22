/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Pure-logic validator
 * for {@link EndPreset}: enforces vanilla {@code DimensionType} / {@code NoiseSettings}
 * height constraints (multiple-of-16, range bounds) and the End-specific
 * cross-field constraints (sea level / island baseline within world bounds).
 * Surfaces violations as a {@link com.mojang.serialization.DataResult#error}
 * so a malformed preset file fails decode cleanly rather than crashing the
 * worldgen bootstrap with a vanilla {@link IllegalArgumentException}.
 *
 * <p>RTF (the MIT-licensed upstream) has no equivalent validator — its
 * {@code WorldSettings} codec uses bare {@code Codec.INT} for every
 * numeric field, deferring all checks to vanilla's {@code DimensionType}
 * constructor where errors surface as confusing late-stage crashes.
 * EndTerraForged closes that gap by validating at decode time, following
 * the project's own {@code SEA_MODE_CODEC} {@code flatXmap} precedent.</p>
 */
package endterraforged.world.config;

import com.mojang.serialization.DataResult;

import endterraforged.world.filter.ErosionConfigValidator;

/**
 * Pure-logic validator for {@link EndPreset}'s seven world-shape fields
 * (plus the embedded {@link endterraforged.world.filter.ErosionConfig}).
 *
 * <p><b>The problem.</b> Without validation, a preset file with
 * {@code "world_height": 100} (not a multiple of 16) or
 * {@code "sea_level_y": 99999} (outside the world bounds) decodes cleanly
 * and then crashes worldgen at runtime when vanilla's
 * {@code DimensionType} / {@code NoiseSettings} constructor throws
 * {@link IllegalArgumentException} — too late for the user to know which
 * preset field was wrong. The validator surfaces each constraint violation
 * as a {@link DataResult#error} at decode time, with a field-specific
 * message that points the user at the offending preset field.</p>
 *
 * <p><b>Constraints enforced.</b></p>
 * <ul>
 *   <li>{@code worldHeight} in {@code [16, 4064]} and a multiple of 16 —
 *       vanilla {@code DimensionType} requires the height to be a positive
 *       multiple of 16, capped at 4064 (the largest height vanilla supports).</li>
 *   <li>{@code minY >= -2032} and a multiple of 16 — vanilla
 *       {@code DimensionType} lower bound on {@code min_y}.</li>
 *   <li>{@code minY + worldHeight <= 2032} — vanilla {@code DimensionType}
 *       requires {@code min_y + height <= 2032} (the world's top must not
 *       exceed y=2032). Combined with the previous two constraints, this
 *       means a maximally-tall world is {@code minY=-2032, worldHeight=4064}.
 *       That remains a valid explicit high-cost specification; the bundled
 *       EndTerraForged default is smaller.</li>
 *   <li>{@code seaLevelY} in {@code [minY, minY + worldHeight - 1]} — the
 *       sea level block Y must be inside the world's vertical bounds.
 *       Enforced unconditionally (not just when {@code seaMode != NONE})
 *       because {@code EndPreset.defaults()} has {@code seaLevelY = 0},
 *       which is always inside the default bounds, so a user who never
 *       touches the sea level will not be rejected. A user who sets a
 *       nonsensical {@code seaLevelY} for a {@code NONE} world should
 *       still get an error — the field is part of the preset regardless
 *       of whether it's used.</li>
 *   <li>{@code islandBaselineY} in {@code [minY, minY + worldHeight - 1]} —
 *       the island-baseline block Y must be inside the world's vertical
 *       bounds. Same reasoning: the field is stored even when
 *       {@code topologyMode != ISLANDS}, so its value must be valid.</li>
 *   <li>{@code erosionConfig} delegated to {@link ErosionConfigValidator} —
 *       checks the six sub-field constraints (non-negative droplet counts,
 *       rates in {@code [0, 1]}).</li>
 * </ul>
 *
 * <p><b>Design — why a separate class.</b> Keeping the validation logic
 * out of {@link EndPreset} itself lets it be unit-tested in isolation
 * (the record has no test surface beyond its codec) and lets the
 * embedded {@link endterraforged.world.filter.ErosionConfig} delegate
 * to its own validator without duplicating the sub-field checks.</p>
 *
 * <p><b>Fail-fast philosophy.</b> Returns {@link DataResult#error} on
 * the first violation encountered (not a list of all violations).
 * Reporting one error at a time keeps the error message focused and
 * avoids overwhelming the user; a preset with multiple bad fields will
 * surface each error in turn as they're fixed and re-decoded. This
 * matches the project's existing {@code SEA_MODE_CODEC} {@code flatXmap}
 * pattern and the {@link ErosionConfigValidator} pattern.</p>
 *
 * <p><b>Thread-safety.</b> Stateless — all methods are static and
 * operate only on their arguments. Safe to call from any thread.</p>
 */
public final class EndPresetValidator {

    /**
     * Minimum supported {@code worldHeight}. Vanilla {@code DimensionType}
     * requires a positive multiple of 16, and 16 is the smallest valid
     * value — anything smaller would be a degenerate "1-chunk-tall" world.
     */
    private static final int MIN_WORLD_HEIGHT = 16;

    /**
     * Maximum supported {@code worldHeight}. Vanilla {@code DimensionType}
     * caps height at 4064 — the largest height that fits within the
     * {@code min_y + height <= 2032} constraint when {@code minY = -2032}.
     */
    private static final int MAX_WORLD_HEIGHT = 4064;

    /**
     * Minimum supported {@code minY}. Vanilla {@code DimensionType} caps
     * {@code min_y} at {@code -2032} — anything lower would put the
     * world's bottom outside the supported range.
     */
    private static final int MIN_MIN_Y = -2032;

    /**
     * Maximum supported value of {@code minY + worldHeight}. Vanilla
     * {@code DimensionType} requires {@code min_y + height <= 2032} —
     * the world's top block must not exceed y=2032.
     */
    private static final int MAX_WORLD_TOP = 2032;

    /**
     * Alignment requirement for {@code worldHeight} and {@code minY}.
     * Vanilla {@code DimensionType} requires both to be multiples of 16
     * (a chunk-section height). Both the standard player envelope and any
     * supported larger creation-time specification satisfy this.
     */
    private static final int ALIGNMENT = 16;

    private EndPresetValidator() {
        // Utility class — no instances.
    }

    /**
     * Validates the given {@link EndPreset}.
     *
     * <p>Returns {@link DataResult#success(Object)} if all constraints are
     * satisfied, or {@link DataResult#error} with a field-specific message
     * describing the first violation. The embedded {@link endterraforged.world.filter.ErosionConfig}
     * is delegated to {@link ErosionConfigValidator} after the parent
     * preset's own fields are checked.</p>
     *
     * @param preset the preset to validate; must not be {@code null}
     * @return a successful {@link DataResult} carrying the preset if valid,
     *         or an error {@link DataResult} describing the violation
     * @throws NullPointerException if {@code preset} is {@code null}
     *         (programmer error — the codec should never pass null here
     *         because {@code EndPreset.CODEC} decodes to a non-null
     *         instance before validation runs)
     */
    public static DataResult<EndPreset> validate(EndPreset preset) {
        if (preset.formatVersion() < EndPreset.LEGACY_FORMAT_VERSION
                || preset.formatVersion() > EndPreset.CURRENT_FORMAT_VERSION) {
            return DataResult.error(() -> "format_version must be in ["
                    + EndPreset.LEGACY_FORMAT_VERSION + ", " + EndPreset.CURRENT_FORMAT_VERSION
                    + "], got " + preset.formatVersion());
        }
        if (preset.formatVersion() >= 3 && !preset.continentConfig().continentBands().enabled()) {
            return DataResult.error(() -> "format_version 3 presets must explicitly enable continent.bands; "
                    + "pre-v3 presets use legacy continent band passthrough");
        }
        if (preset.formatVersion() <= 3
                && preset.terrainConfig().terrainLayoutMode() != TerrainLayoutMode.LEGACY_SELECTOR) {
            return DataResult.error(() -> "terrain.terrain_layout_mode=REGION_PLANNED requires the future "
                    + "format_version 4 terrain catalog; v3 presets must retain LEGACY_SELECTOR");
        }
        // worldHeight: positive multiple of 16, within [16, 4064].
        // Vanilla DimensionType throws IllegalArgumentException on any
        // violation, surfacing as a confusing worldgen crash; catch it
        // here with a field-specific message instead.
        if (preset.worldHeight() < MIN_WORLD_HEIGHT
                || preset.worldHeight() > MAX_WORLD_HEIGHT) {
            return DataResult.error(() ->
                    "world_height must be in [" + MIN_WORLD_HEIGHT + ", "
                            + MAX_WORLD_HEIGHT + "], got " + preset.worldHeight());
        }
        if (preset.worldHeight() % ALIGNMENT != 0) {
            return DataResult.error(() ->
                    "world_height must be a multiple of " + ALIGNMENT
                            + ", got " + preset.worldHeight());
        }
        // minY: >= -2032 and a multiple of 16. Vanilla DimensionType
        // requires both; checking here lets us give a clearer error
        // message than vanilla's generic "min_y must be in [-2032, 2031]".
        if (preset.minY() < MIN_MIN_Y) {
            return DataResult.error(() ->
                    "min_y must be >= " + MIN_MIN_Y + ", got " + preset.minY());
        }
        if (preset.minY() % ALIGNMENT != 0) {
            return DataResult.error(() ->
                    "min_y must be a multiple of " + ALIGNMENT
                            + ", got " + preset.minY());
        }
        // min_y + world_height <= 2032 — vanilla DimensionType requires
        // the world's top block to not exceed y=2032. Combined with
        // minY >= -2032 and worldHeight <= 4064, this means the maximum
        // world is minY=-2032, worldHeight=4064 (top = 2032).
        int worldTop = preset.minY() + preset.worldHeight();
        if (worldTop > MAX_WORLD_TOP) {
            return DataResult.error(() ->
                    "min_y + world_height must be <= " + MAX_WORLD_TOP
                            + " (vanilla DimensionType top limit), got "
                            + worldTop + " (" + preset.minY() + " + "
                            + preset.worldHeight() + ")");
        }
        // seaLevelY: must be within the world's vertical bounds. Enforced
        // unconditionally — the field is stored regardless of seaMode, and
        // a nonsensical value (e.g. 99999) should be caught even when the
        // user intends SeaMode.NONE (the field would still be on disk
        // and could be re-loaded by a future preset that does use it).
        int seaLevelY = preset.seaLevelY();
        if (seaLevelY < preset.minY() || seaLevelY >= worldTop) {
            return DataResult.error(() ->
                    "sea_level_y must be in [" + preset.minY() + ", "
                            + (worldTop - 1) + "] (world vertical bounds), got "
                            + seaLevelY);
        }
        // islandBaselineY: same constraint as seaLevelY — must be within
        // the world's vertical bounds.
        int islandBaselineY = preset.islandBaselineY();
        if (islandBaselineY < preset.minY() || islandBaselineY >= worldTop) {
            return DataResult.error(() ->
                    "island_baseline_y must be in [" + preset.minY() + ", "
                            + (worldTop - 1) + "] (world vertical bounds), got "
                            + islandBaselineY);
        }
        DataResult<ContinentConfig> continentResult =
                ContinentConfigValidator.validate(preset.continentConfig());
        if (continentResult.error().isPresent()) {
            String innerMsg = continentResult.error().get().message();
            return DataResult.error(() -> "continent config invalid: " + innerMsg);
        }
        DataResult<TerrainConfig> terrainResult =
                TerrainConfigValidator.validate(preset.terrainConfig());
        if (terrainResult.error().isPresent()) {
            String innerMsg = terrainResult.error().get().message();
            return DataResult.error(() -> "terrain config invalid: " + innerMsg);
        }
        DataResult<ClimateConfig> climateResult =
                ClimateConfigValidator.validate(preset.climateConfig());
        if (climateResult.error().isPresent()) {
            String innerMsg = climateResult.error().get().message();
            return DataResult.error(() -> "climate config invalid: " + innerMsg);
        }
        DataResult<BiomeLayoutConfig> biomeLayoutResult =
                BiomeLayoutConfigValidator.validate(preset.biomeLayoutConfig());
        if (biomeLayoutResult.error().isPresent()) {
            String innerMsg = biomeLayoutResult.error().get().message();
            return DataResult.error(() -> "biome layout config invalid: " + innerMsg);
        }
        DataResult<SubsurfaceConfig> subsurfaceResult =
                SubsurfaceConfigValidator.validate(preset.subsurfaceConfig());
        if (subsurfaceResult.error().isPresent()) {
            String innerMsg = subsurfaceResult.error().get().message();
            return DataResult.error(() -> "subsurface config invalid: " + innerMsg);
        }
        // Delegate the embedded ErosionConfig to its own validator.
        // The sub-field checks (droplet counts, rates in [0, 1]) live in
        // ErosionConfigValidator so they can be reused if a future caller
        // wants to validate a standalone ErosionConfig without wrapping it
        // in an EndPreset.
        DataResult<endterraforged.world.filter.ErosionConfig> erosionResult =
                ErosionConfigValidator.validate(preset.erosionConfig());
        if (erosionResult.error().isPresent()) {
            // Prefix the erosion error with "erosion." so the user knows
            // it's the embedded config, not a top-level preset field.
            // The ErosionConfigValidator's own message already includes
            // the field name (e.g. "erosion.droplets_per_chunk must be..."),
            // but we re-prefix here for clarity when the error surfaces
            // from the EndPreset codec.
            String innerMsg = erosionResult.error().get().message();
            return DataResult.error(() ->
                    "erosion config invalid: " + innerMsg);
        }
        return DataResult.success(preset);
    }
}
