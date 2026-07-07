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
 * A tile-level height-field transform run after the heightmap populates cells.
 *
 * <p>{@code apply} is called once per chunk-batch with deterministic
 * {@code (regionX, regionZ)} so that seeded filters (e.g. droplet erosion)
 * produce stable results across reloads.</p>
 */
public interface Filter {
    void apply(Filterable map, int regionX, int regionZ, int iterationsPerChunk);

    /**
     * Visits every cell in the tile's padded grid in row-major order. Default
     * helper for filters that need a full sweep.
     */
    default void iterate(Filterable map, Visitor visitor) {
        int total = map.getBlockSize().total();
        for (int dz = 0; dz < total; dz++) {
            for (int dx = 0; dx < total; dx++) {
                Cell cell = map.getCellRaw(dx, dz);
                visitor.visit(map, cell, dx, dz);
            }
        }
    }

    interface Visitor {
        void visit(Filterable map, Cell cell, int dx, int dz);
    }
}
