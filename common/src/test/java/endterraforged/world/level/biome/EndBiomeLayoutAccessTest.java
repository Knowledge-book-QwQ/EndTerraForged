package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import endterraforged.world.config.BiomeLayoutConfig;

class EndBiomeLayoutAccessTest {

    @BeforeEach
    void clearHolder() {
        EndBiomeLayoutAccess.clear();
    }

    @Test
    void getReturnsNullBeforeAnySet() {
        assertNull(EndBiomeLayoutAccess.get(),
                "holder must be null before any layout is published");
    }

    @Test
    void getOrDefaultReturnsDefaultBeforeAnySet() {
        assertSame(EndBiomeLayout.DEFAULT, EndBiomeLayoutAccess.getOrDefault(),
                "getOrDefault() must return the default layout before any set()");
    }

    @Test
    void setThenGetReturnsSameInstance() {
        EndBiomeLayout layout = BiomeLayoutConfig.DEFAULT.buildRuntime();

        EndBiomeLayoutAccess.set(layout);

        assertSame(layout, EndBiomeLayoutAccess.get(),
                "get() must return the same layout reference passed to set()");
        assertSame(layout, EndBiomeLayoutAccess.getOrDefault(),
                "getOrDefault() must return the published layout after set()");
    }

    @Test
    void clearDropsPublishedValue() {
        EndBiomeLayoutAccess.set(BiomeLayoutConfig.DEFAULT.buildRuntime());

        EndBiomeLayoutAccess.clear();

        assertNull(EndBiomeLayoutAccess.get(),
                "clear() must drop the published layout");
        assertSame(EndBiomeLayout.DEFAULT, EndBiomeLayoutAccess.getOrDefault(),
                "clear() must restore the default fallback in getOrDefault()");
    }

    @Test
    void setNullIsEquivalentToClear() {
        EndBiomeLayoutAccess.set(BiomeLayoutConfig.DEFAULT.buildRuntime());

        EndBiomeLayoutAccess.set(null);

        assertNull(EndBiomeLayoutAccess.get(),
                "set(null) must be equivalent to clear()");
        assertSame(EndBiomeLayout.DEFAULT, EndBiomeLayoutAccess.getOrDefault(),
                "set(null) must restore the default fallback in getOrDefault()");
    }
}
