package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;

class EndDensityVisitorTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void degradedVisitorReplacesTerrainPlaceholderWithFallbackDensity() {
        DensityFunction fallback = new TestFallback();
        EndDensityVisitor visitor = new EndDensityVisitor(null, null, 17, fallback);

        assertSame(fallback, visitor.apply(EndDensityFunction.INSTANCE));
    }

    @Test
    void missingFallbackRejectsUnsafeTerrainBinding() {
        EndDensityVisitor visitor = new EndDensityVisitor(null, null, 17);

        assertThrows(IllegalStateException.class,
                () -> visitor.apply(EndDensityFunction.INSTANCE));
    }

    @Test
    void boundDensityAlsoRejectsMissingCentralFallback() {
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.NONE, TopologyMode.ISLANDS, false),
                17);
        EndDensityVisitor visitor = new EndDensityVisitor(new EndDensity(heightmap), null, 17);

        assertThrows(IllegalStateException.class,
                () -> visitor.apply(EndDensityFunction.INSTANCE));
    }

    @Test
    void disabledFloatingIslandsLeaveOverlayPlaceholderNeutral() {
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.NONE, TopologyMode.OUTER_CONTINENTS, false),
                17);
        EndDensityVisitor visitor = new EndDensityVisitor(
                new EndDensity(heightmap), null, 17, new TestFallback());

        DensityFunction result = visitor.apply(FloatingIslandsFunction.INSTANCE);

        assertSame(FloatingIslandsFunction.INSTANCE, result);
        assertEquals(0.0D, result.compute(new TestContext()), 0.0D);
    }

    @Test
    void chunkVisitorMapsFallbackBeforeWrappingBoundDensity() {
        DensityFunction fallback = new TestFallback();
        List<DensityFunction> visited = new ArrayList<>();
        DensityFunction.Visitor chunkVisitor = new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                visited.add(function);
                return function;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
                return noise;
            }
        };
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.NONE, TopologyMode.ISLANDS, false),
                17);
        EndDensityVisitor visitor = EndDensityVisitor.withChunkVisitor(
                chunkVisitor, new EndDensity(heightmap), null, 17, fallback);

        DensityFunction result = visitor.apply(EndDensityFunction.INSTANCE);

        assertSame(fallback, visited.get(0));
        assertSame(result, visited.get(1));
        assertTrue(result instanceof EndDensityFunction.Bound);
    }

    @Test
    void terrainAndFloatingBoundsShareOneDownstreamMappedFallback() {
        DensityFunction fallback = new ConstantDensity(-0.5D);
        DensityFunction mappedFallback = new ConstantDensity(-0.75D);
        int[] fallbackMappings = {0};
        DensityFunction.Visitor chunkVisitor = new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                if (function == fallback) {
                    fallbackMappings[0]++;
                    return mappedFallback;
                }
                return function;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
                return noise;
            }
        };
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.NONE, TopologyMode.OUTER_CONTINENTS, false),
                17);
        EndDensityVisitor visitor = EndDensityVisitor.withChunkVisitor(
                chunkVisitor, new EndDensity(heightmap), FloatingIslandsField.defaults(),
                17, fallback);

        DensityFunction terrain = visitor.apply(EndDensityFunction.INSTANCE);
        DensityFunction floating = visitor.apply(FloatingIslandsFunction.INSTANCE);
        DensityFunction.FunctionContext central = new CentralContext();

        assertEquals(1, fallbackMappings[0],
                "terrain and floating placeholders must map the fallback exactly once");
        assertTrue(terrain instanceof EndDensityFunction.Bound);
        assertTrue(floating instanceof FloatingIslandsFunction.Bound);
        assertEquals(-0.75D, terrain.compute(central), 0.0D);
        assertEquals(-0.75D, floating.compute(central), 0.0D);
    }

    private static final class TestContext implements DensityFunction.FunctionContext {

        @Override
        public int blockX() {
            return 4096;
        }

        @Override
        public int blockY() {
            return 192;
        }

        @Override
        public int blockZ() {
            return 4096;
        }
    }

    private static final class CentralContext implements DensityFunction.FunctionContext {

        @Override
        public int blockX() {
            return 0;
        }

        @Override
        public int blockY() {
            return 64;
        }

        @Override
        public int blockZ() {
            return 0;
        }
    }

    private static final class TestFallback implements DensityFunction.SimpleFunction {

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return -0.5D;
        }

        @Override
        public double minValue() {
            return -0.5D;
        }

        @Override
        public double maxValue() {
            return -0.5D;
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Test density functions are never serialized");
        }
    }

    private record ConstantDensity(double value) implements DensityFunction.SimpleFunction {

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return value;
        }

        @Override
        public double minValue() {
            return value;
        }

        @Override
        public double maxValue() {
            return value;
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Test density functions are never serialized");
        }
    }
}
