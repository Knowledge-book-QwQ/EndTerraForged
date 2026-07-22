package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

class EndDensitySettingsClassifierTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void identifiesRouterWithEndDensityPlaceholder() {
        assertTrue(EndDensitySettingsClassifier.containsEndDensity(
                routerWithFinalDensity(EndDensityFunction.INSTANCE)));
    }

    @Test
    void ignoresRouterWithoutEndDensityPlaceholder() {
        assertFalse(EndDensitySettingsClassifier.containsEndDensity(
                routerWithFinalDensity(DensityFunctions.zero())));
    }

    private static NoiseRouter routerWithFinalDensity(DensityFunction finalDensity) {
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(
                zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero,
                zero, finalDensity, zero, zero, zero);
    }
}
