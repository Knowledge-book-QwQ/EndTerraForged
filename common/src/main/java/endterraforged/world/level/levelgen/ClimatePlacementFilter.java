/*
 * EndTerraForged original design (LGPL-3.0-or-later). A custom
 * PlacementModifier that gates feature placement on the End's custom
 * temperature + moisture fields.
 */
package endterraforged.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

import endterraforged.world.climate.ClimatePredicate;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;

/**
 * A {@link PlacementModifier} that passes a feature position through only
 * when the End's custom {@link EndClimate} temperature and moisture at the
 * candidate position both fall within their respective closed ranges.
 *
 * <p><b>Why this exists.</b> Vanilla placement modifiers can gate on biome
 * (via {@code BiomeFilter}), heightmap, surface water depth, etc., but not
 * on a custom climate field. EndTerraForged's {@link EndClimate} is
 * published via {@link EndClimateAccess} (volatile, cross-thread); this
 * modifier lets a {@code placed_feature} JSON gate feature placement on
 * that field — e.g. "place chorus plants only where temperature &gt; 0.6
 * and moisture &gt; 0.5".</p>
 *
 * <p><b>JSON usage.</b> Registered as
 * {@code endterraforged:climate_filter}:
 * <pre>{@code
 * {
 *   "type": "endterraforged:climate_filter",
 *   "min_temp": 0.6,
 *   "max_temp": 1.0,
 *   "min_moist": 0.5,
 *   "max_moist": 1.0
 * }
 * }</pre>
 *
 * <p><b>Fast-path.</b> When no End dimension is loaded
 * ({@link EndClimateAccess#get} returns {@code null}), the modifier
 * discards all positions — no features are placed. This preserves vanilla
 * behaviour for non-End dimensions.</p>
 *
 * <p><b>Thread safety.</b> Reads from {@link EndClimateAccess}
 * (volatile-published immutable record). Feature placement runs on the
 * server thread (single-threaded per chunk); no further synchronisation
 * is needed.</p>
 */
public final class ClimatePlacementFilter extends PlacementModifier {

    public static final String NAME = "climate_filter";

    private final float minTemp;
    private final float maxTemp;
    private final float minMoist;
    private final float maxMoist;

    public ClimatePlacementFilter(float minTemp, float maxTemp,
                                  float minMoist, float maxMoist) {
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.minMoist = minMoist;
        this.maxMoist = maxMoist;
    }

    public float minTemp() { return minTemp; }
    public float maxTemp() { return maxTemp; }
    public float minMoist() { return minMoist; }
    public float maxMoist() { return maxMoist; }

    public static final MapCodec<ClimatePlacementFilter> CODEC =
            RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                            Codec.FLOAT.optionalFieldOf("min_temp", 0.0F)
                                    .forGetter(ClimatePlacementFilter::minTemp),
                            Codec.FLOAT.optionalFieldOf("max_temp", 1.0F)
                                    .forGetter(ClimatePlacementFilter::maxTemp),
                            Codec.FLOAT.optionalFieldOf("min_moist", 0.0F)
                                    .forGetter(ClimatePlacementFilter::minMoist),
                            Codec.FLOAT.optionalFieldOf("max_moist", 1.0F)
                                    .forGetter(ClimatePlacementFilter::maxMoist)
                    ).apply(instance, instance.stable(ClimatePlacementFilter::new))
            );

    /**
     * The placement-modifier type — a simple lambda returning the codec.
     * Registered on {@link net.minecraft.core.registries.BuiltInRegistries#PLACEMENT_MODIFIER_TYPE}
     * during {@link endterraforged.EndTerraForged#bootstrap()}, following the
     * same pattern as the density-function and biome-source registrations.
     */
    public static final PlacementModifierType<ClimatePlacementFilter> TYPE = () -> CODEC;

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context,
                                         RandomSource random, BlockPos pos) {
        EndClimate climate = EndClimateAccess.get();
        if (ClimatePredicate.bothInRange(climate, pos.getX(), pos.getZ(),
                minTemp, maxTemp, minMoist, maxMoist)) {
            return Stream.of(pos);
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE;
    }
}
