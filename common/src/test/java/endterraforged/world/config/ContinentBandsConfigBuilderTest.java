package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContinentBandsConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtCurrentBands() {
        assertEquals(ContinentBandsConfig.DEFAULT, new ContinentBandsConfigBuilder().build());
    }

    @Test
    void sourceRoundTripIsIdentity() {
        ContinentBandsConfig config = new ContinentBandsConfig(
                true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F);
        assertEquals(config, new ContinentBandsConfigBuilder(config).build());
    }

    @Test
    void resetRestoresCurrentBands() {
        ContinentBandsConfigBuilder builder = new ContinentBandsConfigBuilder()
                .voidOuterThreshold(0.05F)
                .shelfThreshold(0.18F)
                .rimThreshold(0.30F)
                .coastThreshold(0.44F)
                .inlandThreshold(0.62F);
        assertNotEquals(ContinentBandsConfig.DEFAULT, builder.build());
        builder.reset();
        assertEquals(ContinentBandsConfig.DEFAULT, builder.build());
    }

    @Test
    void settersStoreEveryValue() {
        ContinentBandsConfig built = new ContinentBandsConfigBuilder()
                .enabled(true)
                .voidOuterThreshold(0.08F)
                .shelfThreshold(0.22F)
                .rimThreshold(0.34F)
                .coastThreshold(0.46F)
                .inlandThreshold(0.58F)
                .build();

        assertEquals(new ContinentBandsConfig(true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F), built);
    }

    @Test
    void invalidStateFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ContinentBandsConfigBuilder()
                        .voidOuterThreshold(0.30F)
                        .shelfThreshold(0.20F)
                        .build());
        assertTrue(error.getMessage().contains("void_outer < shelf"));
    }
}
