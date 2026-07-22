package endterraforged.world.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentBandsConfig;

class BandedContinentTest {

    @Test
    void interiorBandsUseContinuousEdgeAcrossRawShapeCutover() {
        BandedContinent continent = new BandedContinent(
                new ShapeCutoverContinent(), ContinentBandsConfig.DEFAULT);
        ContinentSignals below = continent.signalsAt(-1.0F, 0.0F, 0);
        ContinentSignals above = continent.signalsAt(1.0F, 0.0F, 0);

        assertTrue(Math.abs(below.landness() - above.landness()) < 0.01F);
        assertTrue(Math.abs(below.inlandness() - above.inlandness()) < 0.02F);
        assertEquals(below.landness(), continent.compute(-1.0F, 0.0F, 0), 0.0F);
        assertEquals(above.landness(), continent.compute(1.0F, 0.0F, 0), 0.0F);
    }

    @Test
    void outerShelfRetainsContinuousShapeModulation() {
        BandedContinent continent = new BandedContinent(
                new OuterShapeContinent(), ContinentBandsConfig.DEFAULT);

        ContinentSignals shaped = continent.signalsAt(-1.0F, 0.0F, 0);
        ContinentSignals full = continent.signalsAt(1.0F, 0.0F, 0);

        assertTrue(shaped.landness() < full.landness(),
                "outer shelf shape must still affect the coastline silhouette");
    }

    private static final class ShapeCutoverContinent implements Continent {

        @Override
        public float compute(float x, float z, int seed) {
            return x < 0.0F ? 0.4425F : 0.5021F;
        }

        @Override
        public void sampleSignals(float x, float z, int seed, ContinentSignalBuffer output) {
            if (x < 0.0F) {
                output.setIdentified(0.5017F, 0.4425F, 1.0F, 1L, 0, 0);
            } else {
                output.setIdentified(0.5021F, 0.5021F, 1.0F, 1L, 0, 0);
            }
        }

        @Override
        public float minValue() {
            return 0.0F;
        }

        @Override
        public float maxValue() {
            return 1.0F;
        }

        @Override
        public endterraforged.world.noise.Noise mapAll(Visitor visitor) {
            return visitor.apply(this);
        }
    }

    private static final class OuterShapeContinent implements Continent {

        @Override
        public float compute(float x, float z, int seed) {
            return x < 0.0F ? 0.14F : 0.20F;
        }

        @Override
        public void sampleSignals(float x, float z, int seed, ContinentSignalBuffer output) {
            output.setIdentified(0.22F, compute(x, z, seed), 1.0F, 2L, 0, 0);
        }

        @Override
        public float minValue() {
            return 0.0F;
        }

        @Override
        public float maxValue() {
            return 1.0F;
        }

        @Override
        public endterraforged.world.noise.Noise mapAll(Visitor visitor) {
            return visitor.apply(this);
        }
    }
}
