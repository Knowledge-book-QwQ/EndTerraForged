/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Original MIT notice retained per license terms.
 */
package endterraforged.world.filter;

import endterraforged.world.cell.Cell;

/**
 * Read/write view over a tile's cell grid, passed to {@link Filter}s.
 *
 * <p>{@code getBlockX/Z} give the world-space origin of the tile (the
 * lower-border corner), {@code getBlockSize} describes the padded grid, and
 * {@code getBacking} exposes the raw {@code Cell[]} for direct indexed access
 * (the erosion brush uses this for performance — no per-cell method dispatch).</p>
 */
public interface Filterable {
    int getBlockX();

    int getBlockZ();

    Size getBlockSize();

    Cell[] getBacking();

    Cell getCellRaw(int x, int z);
}
