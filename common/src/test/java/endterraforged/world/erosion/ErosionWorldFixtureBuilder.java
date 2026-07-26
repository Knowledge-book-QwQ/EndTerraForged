package endterraforged.world.erosion;

/** Builds world-space continuous inputs shared by P4.7 tile candidates. */
final class ErosionWorldFixtureBuilder
        implements EndErosionTileCache.Builder<EndErosionTile> {

    enum Kind {
        FLAT,
        PLANE,
        LONG_PLANE,
        ISOLATED_SPIKE,
        RIDGE,
        PLATEAU_EDGE,
        CLOSED_BASIN,
        WATERSHED,
        COAST_THIN_SHELF,
        ARCHIPELAGO,
        FLOW_FIELD
    }

    @FunctionalInterface
    interface BuildObserver {
        void built(EndErosionTileKey key);
    }

    private static final float FIELD_SCALE_BLOCKS = 256.0F;

    private final Kind kind;
    private final float centerBlockX;
    private final float centerBlockZ;
    private final boolean protectionEnabled;
    private final BuildObserver observer;

    ErosionWorldFixtureBuilder(Kind kind, float centerBlockX, float centerBlockZ) {
        this(kind, centerBlockX, centerBlockZ, true, null);
    }

    ErosionWorldFixtureBuilder(Kind kind,
                                 float centerBlockX,
                                 float centerBlockZ,
                                 boolean protectionEnabled,
                                 BuildObserver observer) {
        this.kind = kind;
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
        this.protectionEnabled = protectionEnabled;
        this.observer = observer;
    }

    @Override
    public EndErosionTileCache.BuildResult<EndErosionTile> build(EndErosionTileKey key) {
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

        for (int z = 0; z < key.sampleHeight(); z++) {
            float worldZ = key.sampleOriginBlockZ() + (long) z * key.sampleDistanceBlocks();
            for (int x = 0; x < key.sampleWidth(); x++) {
                float worldX = key.sampleOriginBlockX() + (long) x * key.sampleDistanceBlocks();
                float u = (worldX - this.centerBlockX) / FIELD_SCALE_BLOCKS;
                float v = (worldZ - this.centerBlockZ) / FIELD_SCALE_BLOCKS;
                float radius = (float) Math.sqrt(u * u + v * v);
                float top = height(this.kind, u, v, radius);
                float land = landness(this.kind, u, v, radius, top);
                int index = z * key.sampleWidth() + x;
                sourceTopBlocks[index] = Math.clamp(top, 0.05F, 0.95F) * key.worldHeight();
                landness[index] = Math.clamp(land, 0.0F, 1.0F);
                inlandness[index] = this.kind == Kind.LONG_PLANE
                        ? 0.80F : Math.clamp(1.0F - radius * 1.35F, 0.0F, 1.0F);
                outerActivation[index] = 1.0F;
                roughness[index] = roughness(this.kind);
                ridgeInfluence[index] = ridgeInfluence(this.kind, v);
                erosionResistance[index] = this.protectionEnabled
                        ? erosionResistance(this.kind, radius, ridgeInfluence[index]) : 0.0F;
                availableThickness[index] = 4.0F + 156.0F * landness[index];
                areaFamily[index] = this.kind.ordinal() + 1;
                terrainTags[index] = terrainTags(this.kind);
                if (this.protectionEnabled && this.kind == Kind.COAST_THIN_SHELF) {
                    masks[index] |= EndErosionTile.MASK_EROSION_PROTECTED;
                }
                if (this.kind == Kind.ARCHIPELAGO) {
                    masks[index] |= EndErosionTile.MASK_ARCHIPELAGO_DOMINANT;
                }
            }
        }

        EndErosionTile tile = EndErosionTile.fromOwnedArrays(
                key, sourceTopBlocks, landness, inlandness, outerActivation,
                roughness, erosionResistance, availableThickness, ridgeInfluence,
                areaFamily, terrainTags, masks);
        if (this.observer != null) {
            this.observer.built(key);
        }
        return new EndErosionTileCache.BuildResult<>(tile, tile.primitiveBytes());
    }

    private static float height(Kind kind, float u, float v, float radius) {
        return switch (kind) {
            case FLAT -> 0.50F;
            case PLANE -> 0.50F + 0.24F * u;
            case LONG_PLANE -> 0.50F + 0.01F * u;
            case ISOLATED_SPIKE -> 0.46F
                    + 0.28F * Math.max(0.0F, 1.0F - radius * 3.4F);
            case RIDGE -> 0.46F + 0.26F * (float) Math.exp(-v * v * 50.0F)
                    * (0.72F + 0.28F * (float) Math.cos(u * Math.PI));
            case PLATEAU_EDGE -> 0.46F
                    + 0.24F * (1.0F - smoothStep(0.24F, 0.36F, radius));
            case CLOSED_BASIN -> 0.66F
                    - 0.24F * (float) Math.exp(-radius * radius * 25.0F);
            case WATERSHED -> 0.50F + 0.14F * Math.abs(u) - 0.12F * Math.abs(v);
            case COAST_THIN_SHELF -> 0.44F + 0.18F * smoothStep(-0.28F, 0.10F, u);
            case ARCHIPELAGO -> 0.43F + 0.30F * islandSignal(u, v);
            case FLOW_FIELD -> 0.52F + 0.08F * u
                    + 0.035F * (float) Math.sin(u * Math.PI * 18.0F)
                    + 0.035F * (float) Math.cos(v * Math.PI * 16.0F);
        };
    }

    private static float landness(Kind kind, float u, float v, float radius, float top) {
        if (kind == Kind.LONG_PLANE) {
            return 0.80F;
        }
        if (kind == Kind.COAST_THIN_SHELF) {
            return smoothStep(-0.38F, 0.14F, u)
                    * (0.82F - 0.16F * Math.min(1.0F, radius));
        }
        if (kind == Kind.ARCHIPELAGO) {
            return Math.clamp((top - 0.43F) * 3.3F, 0.0F, 1.0F);
        }
        return Math.clamp(0.78F - radius * 0.34F, 0.0F, 1.0F);
    }

    private static float roughness(Kind kind) {
        return switch (kind) {
            case ISOLATED_SPIKE, RIDGE, WATERSHED -> 0.90F;
            case LONG_PLANE, PLATEAU_EDGE, CLOSED_BASIN -> 0.60F;
            case FLOW_FIELD -> 0.75F;
            default -> 0.35F;
        };
    }

    private static float ridgeInfluence(Kind kind, float v) {
        return kind == Kind.RIDGE
                ? Math.clamp((float) Math.exp(-v * v * 50.0F), 0.0F, 1.0F)
                : 0.0F;
    }

    private static float erosionResistance(Kind kind,
                                           float radius,
                                           float ridgeInfluence) {
        return switch (kind) {
            case RIDGE -> ridgeInfluence;
            case PLATEAU_EDGE -> 1.0F - smoothStep(0.34F, 0.44F, radius);
            default -> 0.0F;
        };
    }

    private static int terrainTags(Kind kind) {
        return switch (kind) {
            case RIDGE -> 1;
            case PLATEAU_EDGE -> 1 << 1;
            case COAST_THIN_SHELF -> 1 << 2;
            case ARCHIPELAGO -> 1 << 3;
            default -> 0;
        };
    }

    private static float islandSignal(float u, float v) {
        float first = gaussian(u + 0.28F, v + 0.10F, 0.055F);
        float second = gaussian(u - 0.22F, v - 0.18F, 0.045F);
        float third = gaussian(u + 0.04F, v - 0.30F, 0.035F);
        return Math.max(first, Math.max(second, third));
    }

    private static float gaussian(float u, float v, float scale) {
        return (float) Math.exp(-(u * u + v * v) / scale);
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float alpha = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }
}
