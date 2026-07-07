/*
 * EndTerraForged original design (LGPL-3.0-or-later). A custom
 * SurfaceRules.ConditionSource that gates surface rules on the End's
 * custom temperature field — vanilla's Temperature condition reads the
 * Climate.Sampler which is zeroed in the End's noise_settings.
 */
package endterraforged.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

import endterraforged.world.climate.ClimatePredicate;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;

/**
 * A {@link SurfaceRules.ConditionSource} that tests whether the End's custom
 * {@link EndClimate} temperature at the current block position falls within
 * a closed {@code [min, max]} range.
 *
 * <p><b>Why this exists.</b> Vanilla's {@code SurfaceRules.Temperature}
 * condition reads the {@code Climate.Sampler}'s temperature channel, which
 * is zeroed in the End's {@code noise_settings} (the End has no vanilla
 * climate router). EndTerraForged builds its own temperature field
 * ({@link EndClimate}) and publishes it via {@link EndClimateAccess}; this
 * condition lets {@code surface_rule} JSON gate rules on that field — e.g.
 * "place smooth basalt surface where temperature &lt; 0.3".</p>
 *
 * <p><b>JSON usage.</b> Registered as
 * {@code endterraforged:climate_temperature}:
 * <pre>{@code
 * {
 *   "type": "endterraforged:climate_temperature",
 *   "min": 0.0,
 *   "max": 0.3
 * }
 * }</pre>
 *
 * <p><b>Fast-path.</b> When no End dimension is loaded
 * ({@link EndClimateAccess#get} returns {@code null}), the condition
 * evaluates to {@code false} — the rule is skipped. This preserves vanilla
 * behaviour for non-End dimensions without any branching in the rule tree.</p>
 *
 * <p><b>Thread safety.</b> The condition reads from
 * {@link EndClimateAccess} (volatile-published immutable record) and the
 * {@link SurfaceRules.Context} block coordinates. Both are safe to read
 * from parallel chunk-gen surface-building threads.</p>
 *
 * @param min inclusive lower bound of the temperature range {@code [0,1]}
 * @param max inclusive upper bound of the temperature range {@code [0,1]}
 */
public record ClimateTemperatureCondition(float min, float max)
        implements SurfaceRules.ConditionSource {

    public static final String NAME = "climate_temperature";

    public static final KeyDispatchDataCodec<ClimateTemperatureCondition> CODEC =
            KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                            com.mojang.serialization.Codec.FLOAT.fieldOf("min")
                                    .forGetter(ClimateTemperatureCondition::min),
                            com.mojang.serialization.Codec.FLOAT.fieldOf("max")
                                    .forGetter(ClimateTemperatureCondition::max)
                    ).apply(instance, instance.stable(ClimateTemperatureCondition::new))
            ));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return () -> {
            EndClimate climate = EndClimateAccess.get();
            return ClimatePredicate.temperatureInRange(
                    climate, context.blockX, context.blockZ, min, max);
        };
    }
}
