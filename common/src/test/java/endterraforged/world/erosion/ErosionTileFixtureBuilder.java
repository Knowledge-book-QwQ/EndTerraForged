package endterraforged.world.erosion;

/** Builds canonical tile artifacts from the shared P4.7 synthetic fixture. */
final class ErosionTileFixtureBuilder implements EndErosionTileCache.Builder {

    @FunctionalInterface
    interface BuildObserver {
        void built(EndErosionTileKey key);
    }

    private final ErosionFixture fixture;
    private final BuildObserver observer;

    ErosionTileFixtureBuilder(ErosionFixture fixture) {
        this(fixture, null);
    }

    ErosionTileFixtureBuilder(ErosionFixture fixture, BuildObserver observer) {
        this.fixture = fixture;
        this.observer = observer;
    }

    @Override
    public EndErosionTileCache.BuildResult build(EndErosionTileKey key) {
        validateGeometry(key);
        int cells = key.cellCount();
        float[] sourceTopBlocks = new float[cells];
        float[] landness = new float[cells];
        float[] inlandness = new float[cells];
        float[] outerActivation = new float[cells];
        float[] roughness = new float[cells];
        float[] erosionResistance = new float[cells];
        float[] availableThickness = new float[cells];
        float[] ridgeInfluence = new float[cells];
        int[] areaFamily = new int[cells];
        int[] terrainTags = new int[cells];
        byte[] masks = new byte[cells];

        for (int index = 0; index < cells; index++) {
            sourceTopBlocks[index] = this.fixture.rawTopValues()[index] * key.worldHeight();
            landness[index] = this.fixture.landnessValues()[index];
            inlandness[index] = this.fixture.inlandnessValues()[index];
            outerActivation[index] = this.fixture.outerActivationValues()[index];
            roughness[index] = this.fixture.roughnessValues()[index];
            erosionResistance[index] = this.fixture.erosionResistanceValues()[index];
            availableThickness[index] = this.fixture.availableThicknessValues()[index];
            ridgeInfluence[index] = this.fixture.ridgeInfluenceValues()[index];
            areaFamily[index] = this.fixture.areaFamilyValues()[index];
            terrainTags[index] = this.fixture.terrainTagsValues()[index];
            byte mask = 0;
            if (this.fixture.erosionMaskedValues()[index]) {
                mask |= EndErosionTile.MASK_EROSION_PROTECTED;
            }
            if (this.fixture.archipelagoDominantValues()[index]) {
                mask |= EndErosionTile.MASK_ARCHIPELAGO_DOMINANT;
            }
            masks[index] = mask;
        }

        EndErosionTile tile = EndErosionTile.fromOwnedArrays(
                key, sourceTopBlocks, landness, inlandness, outerActivation,
                roughness, erosionResistance, availableThickness, ridgeInfluence,
                areaFamily, terrainTags, masks);
        if (this.observer != null) {
            this.observer.built(key);
        }
        return new EndErosionTileCache.BuildResult(tile, tile.primitiveBytes());
    }

    private void validateGeometry(EndErosionTileKey key) {
        if (key.sampleWidth() != this.fixture.size()
                || key.sampleHeight() != this.fixture.size()
                || key.haloSamples() != ErosionFixture.HALO
                || key.sampleDistanceBlocks() != (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS) {
            throw new IllegalArgumentException("tile key does not match the canonical fixture");
        }
    }
}
