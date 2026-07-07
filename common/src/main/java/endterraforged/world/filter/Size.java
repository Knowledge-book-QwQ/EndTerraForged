/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Original MIT notice retained per license terms.
 */
package endterraforged.world.filter;

/**
 * Geometry of a generation tile: the interior block grid plus a symmetric
 * border used for sampling neighbours without wrap-around.
 *
 * <p>Verbatim from upstream so tile/block math stays compatible. A tile of
 * factor {@code f} covers {@code 2^f} chunks; {@code make(size, border)} pads
 * the interior with {@code border} blocks on every side to give {@code total}.
 * The backing {@code Cell[]} of a tile has length {@code arraySize = total*total}.</p>
 *
 * <p><b>Thread safety:</b> immutable record, safe to share across threads.</p>
 */
public record Size(int size, int mask, int border, int total, int lowerBorder, int upperBorder, int arraySize) {

    /** Bitwise mask of the interior size (assumes size is a power of two). */
    public int mask(int i) {
        return i & this.mask;
    }

    /** Row-major index of the cell at local {@code (x, z)} within the padded grid. */
    public int indexOf(int x, int z) {
        return z * this.total + x;
    }

    public static int chunkToBlock(int i) {
        return i << 4;
    }

    public static int blockToChunk(int i) {
        return i >> 4;
    }

    public static int count(int minX, int minZ, int maxX, int maxZ) {
        int dx = maxX - minX;
        int dz = maxZ - minZ;
        return dx * dz;
    }

    public static Size make(int size, int border) {
        int total = size + 2 * border;
        return new Size(size, size - 1, border, total, border, border + size, total * total);
    }

    public static Size chunks(int factor, int borderChunks) {
        int chunks = 1 << factor;
        return make(chunks, borderChunks);
    }

    public static Size blocks(int factor, int borderChunks) {
        int chunks = 1 << factor;
        int blocks = chunks << 4;
        int borderBlocks = borderChunks << 4;
        return make(blocks, borderBlocks);
    }
}
