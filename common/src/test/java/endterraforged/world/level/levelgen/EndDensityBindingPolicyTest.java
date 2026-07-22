package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EndDensityBindingPolicyTest {

    @Test
    void skipsNonEtfNoiseSettings() {
        assertEquals(EndDensityBindingPolicy.Decision.SKIP,
                EndDensityBindingPolicy.decide(false, false));
    }

    @Test
    void bindsEtfSettingsWhenVanillaFallbackCanBeBuilt() {
        assertEquals(EndDensityBindingPolicy.Decision.BIND,
                EndDensityBindingPolicy.decide(true, true));
    }

    @Test
    void rejectsEtfSettingsWithoutVanillaFallbackRegistry() {
        assertEquals(EndDensityBindingPolicy.Decision.REJECT_MISSING_VANILLA_FALLBACK,
                EndDensityBindingPolicy.decide(true, false));
    }
}
