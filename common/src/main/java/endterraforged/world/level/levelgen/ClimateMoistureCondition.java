/*
 * EndTerraForged original design (LGPL-3.0-or-later). A custom
 * SurfaceRules.ConditionSource that gates surface rules on the End's
 * custom moisture field — the End has no vanilla moisture router.
 */
package endterraforged.world.level.levelgen;

import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

import endterraforged.world.climate.ClimatePredicate;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;

/**
 * A {@link SurfaceRules.ConditionSource} that tests whether the End's custom
 * {@link EndClimate} moisture at the current block position falls within a
 * closed {@code [min, max]} range.
 *
 * <p>Companion to {@link ClimateTemperatureCondition} — same design, but
 * gates on the moisture channel. Together they let a {@code surface_rule}
 * JSON express dual-axis climate conditions by nesting
 * {@code if_true(if_true(temp_range, moist_range), block)} constructions.</p>
 *
 * <p><b>JSON usage.</b> Registered as
 * {@code endterraforged:climate_moisture}:
 * <pre>{@code
 * {
 *   "type": "endterraforged:climate_moisture",
 *   "min": 0.7,
 *   "max": 1.0
 * }
 * }</pre>
 *
 * <p><b>Fast-path.</b> When no End dimension is loaded
 * ({@link EndClimateAccess#get} returns {@code null}), the condition
 * evaluates to {@code false}.</p>
 *
 * @param min inclusive lower bound of the moisture range {@code [0,1]}
 * @param max inclusive upper bound of the moisture range {@code [0,1]}
 */
public record ClimateMoistureCondition(float min, float max)
        implements SurfaceRules.ConditionSource {

    public static final String NAME = "climate_moisture";

    public static final KeyDispatchDataCodec<ClimateMoistureCondition> CODEC =
            KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                            com.mojang.serialization.Codec.FLOAT.fieldOf("min")
                                    .forGetter(ClimateMoistureCondition::min),
                            com.mojang.serialization.Codec.FLOAT.fieldOf("max")
                                    .forGetter(ClimateMoistureCondition::max)
                    ).apply(instance, instance.stable(ClimateMoistureCondition::new))
            ));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return () -> {
            EndClimate climate = EndClimateAccess.get();
            return ClimatePredicate.moistureInRange(
                    climate, context.blockX, context.blockZ, min, max);
        };
    }
}
