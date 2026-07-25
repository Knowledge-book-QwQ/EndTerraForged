package endterraforged.world.erosion;

/** Stable value key for one canonical P4.7 candidate tile. */
record EndErosionTileKey(long algorithmId,
                         int algorithmVersion,
                         long worldSeed,
                         long runtimeFingerprint,
                         int minY,
                         int worldHeight,
                         int terrainVersion,
                         int tileX,
                         int tileZ,
                         int sampleWidth,
                         int sampleHeight,
                         int haloSamples,
                         int sampleDistanceBlocks) {

    EndErosionTileKey {
        if (algorithmId == 0L) {
            throw new IllegalArgumentException("algorithmId must be non-zero");
        }
        if (algorithmVersion <= 0) {
            throw new IllegalArgumentException("algorithmVersion must be > 0");
        }
        if (worldHeight <= 0) {
            throw new IllegalArgumentException("worldHeight must be > 0");
        }
        if (terrainVersion < 0) {
            throw new IllegalArgumentException("terrainVersion must be >= 0");
        }
        if (haloSamples < 0) {
            throw new IllegalArgumentException("haloSamples must be >= 0");
        }
        int haloSpan = Math.multiplyExact(haloSamples, 2);
        if (sampleWidth <= haloSpan || sampleHeight <= haloSpan) {
            throw new IllegalArgumentException("sample dimensions must contain a non-empty core");
        }
        if (sampleDistanceBlocks <= 0) {
            throw new IllegalArgumentException("sampleDistanceBlocks must be > 0");
        }
        Math.addExact(minY, worldHeight);
        Math.multiplyExact(sampleWidth, sampleHeight);
        Math.multiplyExact(sampleWidth - haloSpan, sampleDistanceBlocks);
        Math.multiplyExact(sampleHeight - haloSpan, sampleDistanceBlocks);
    }

    int cellCount() {
        return Math.multiplyExact(this.sampleWidth, this.sampleHeight);
    }

    int coreWidthBlocks() {
        return Math.multiplyExact(
                this.sampleWidth - this.haloSamples * 2, this.sampleDistanceBlocks);
    }

    int coreHeightBlocks() {
        return Math.multiplyExact(
                this.sampleHeight - this.haloSamples * 2, this.sampleDistanceBlocks);
    }

    long originBlockX() {
        return (long) this.tileX * coreWidthBlocks();
    }

    long originBlockZ() {
        return (long) this.tileZ * coreHeightBlocks();
    }

    long sampleOriginBlockX() {
        return originBlockX() - (long) this.haloSamples * this.sampleDistanceBlocks;
    }

    long sampleOriginBlockZ() {
        return originBlockZ() - (long) this.haloSamples * this.sampleDistanceBlocks;
    }

    EndErosionTileKey withTile(int tileX, int tileZ) {
        return new EndErosionTileKey(
                this.algorithmId, this.algorithmVersion, this.worldSeed,
                this.runtimeFingerprint, this.minY, this.worldHeight,
                this.terrainVersion, tileX, tileZ, this.sampleWidth,
                this.sampleHeight, this.haloSamples, this.sampleDistanceBlocks);
    }

    EndErosionTileKey forBlock(int blockX, int blockZ) {
        return withTile(
                Math.floorDiv(blockX, coreWidthBlocks()),
                Math.floorDiv(blockZ, coreHeightBlocks()));
    }
}
