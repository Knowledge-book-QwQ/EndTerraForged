package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClimateConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(ClimateConfig.DEFAULT, new ClimateConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        ClimateConfig source = new ClimateConfig(6000.0F, 900, 901,
                1200, 1201, 1500, 0.4F,
                2.5F, 0.15F, 0.85F, 0.2F,
                3.5F, 0.1F, 0.9F, -0.15F);
        assertEquals(source, new ClimateConfigBuilder(source).build());
    }

    @Test
    void settersStoreValues() {
        ClimateConfig built = new ClimateConfigBuilder()
                .climateRadius(8000.0F)
                .temperatureScale(1000)
                .temperatureSeedOffset(1001)
                .moistureScale(1400)
                .moistureSeedOffset(1401)
                .windScale(1800)
                .perturbation(0.35F)
                .temperatureFalloff(2.5F)
                .temperatureMin(0.2F)
                .temperatureMax(0.8F)
                .temperatureBias(0.15F)
                .moistureFalloff(3.5F)
                .moistureMin(0.1F)
                .moistureMax(0.9F)
                .moistureBias(-0.25F)
                .build();

        assertEquals(new ClimateConfig(8000.0F, 1000, 1001,
                1400, 1401, 1800, 0.35F,
                2.5F, 0.2F, 0.8F, 0.15F,
                3.5F, 0.1F, 0.9F, -0.25F), built);
    }

    @Test
    void resetRestoresDefaults() {
        ClimateConfigBuilder builder = new ClimateConfigBuilder()
                .climateRadius(8000.0F)
                .temperatureScale(1000)
                .moistureScale(1400)
                .windScale(1800)
                .perturbation(0.35F);

        builder.reset();

        assertEquals(ClimateConfig.DEFAULT, builder.build());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ClimateConfigBuilder()
                        .climateRadius(0.0F)
                        .build());

        assertTrue(e.getMessage().contains("climate_radius"));
    }
}
