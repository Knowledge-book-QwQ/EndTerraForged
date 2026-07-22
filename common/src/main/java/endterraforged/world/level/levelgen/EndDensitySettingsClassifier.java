package endterraforged.world.level.levelgen;

import java.util.Objects;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * Identifies the noise router that carries EndTerraForged's runtime density
 * placeholder before a {@code RandomState} has been constructed.
 */
public final class EndDensitySettingsClassifier {

    private EndDensitySettingsClassifier() {
    }

    /**
     * Returns whether the router's final-density tree contains the ETF
     * placeholder that must be bound to a dimension-scoped {@code EndDensity}.
     */
    public static boolean containsEndDensity(NoiseRouter router) {
        Objects.requireNonNull(router, "router");
        DetectionVisitor visitor = new DetectionVisitor();
        router.finalDensity().mapAll(visitor);
        return visitor.found;
    }

    private static final class DetectionVisitor implements DensityFunction.Visitor {
        private boolean found;

        @Override
        public DensityFunction apply(DensityFunction function) {
            if (function instanceof EndDensityFunction) {
                found = true;
            }
            return function;
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            return noise;
        }
    }
}
