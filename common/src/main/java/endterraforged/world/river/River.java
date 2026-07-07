/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged (LGPL-3.0-or-later). Geometry adapted from RTF's River (MIT)
 * — lineage TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Pure 2D segment math: no MC dependency, no heightmap coupling. The End
 * river network builds these segments per worley cell; EndRiverMap queries
 * distance / projection to carve valleys.
 */
package endterraforged.world.river;

/**
 * An immutable 2D line segment representing one river reach.
 *
 * <p>Borrowed from RTF's {@code River}: stores the endpoints plus pre-computed
 * direction vector, length, and squared length so per-sample distance queries
 * avoid redundant sqrt / division. The segment is the atomic unit of the river
 * network — a river with forks is a tree of {@code River} segments.</p>
 *
 * <p><b>Thread safety.</b> Immutable record; safe to share across chunk-gen
 * threads.</p>
 *
 * @param x1       start X
 * @param z1       start Z
 * @param x2       end X
 * @param z2       end Z
 * @param dx       unit direction X ({@code (x2-x1)/length})
 * @param dz       unit direction Z ({@code (z2-z1)/length})
 * @param length   segment length
 * @param lengthSq squared segment length (cached for projection)
 */
public record River(float x1, float z1, float x2, float z2,
                    float dx, float dz, float length, float lengthSq) {

    /** Builds a segment from two endpoints, pre-computing direction and length. */
    public static River of(float x1, float z1, float x2, float z2) {
        float ddx = x2 - x1;
        float ddz = z2 - z1;
        float lenSq = ddx * ddx + ddz * ddz;
        float len = (float) Math.sqrt(lenSq);
        float invLen = len > 0.0F ? 1.0F / len : 0.0F;
        return new River(x1, z1, x2, z2, ddx * invLen, ddz * invLen, len, lenSq);
    }

    /**
     * Perpendicular distance from {@code (px, pz)} to this segment's finite
     * line. Clamps the foot of the perpendicular to the segment endpoints so
     * the distance grows past the start / end rather than wrapping around.
     *
     * @return the shortest distance from the point to any point on the segment
     */
    public float distanceTo(float px, float pz) {
        float t = projection(px, pz);
        // Foot of perpendicular, clamped to [0,1] along the segment.
        float footX = x1 + (x2 - x1) * t;
        float footZ = z1 + (z2 - z1) * t;
        float ddx = px - footX;
        float ddz = pz - footZ;
        return (float) Math.sqrt(ddx * ddx + ddz * ddz);
    }

    /**
     * Projection parameter of {@code (px, pz)} onto the segment, clamped to
     * {@code [0,1]}. {@code 0} = at the start, {@code 1} = at the end. Used by
     * the carver to interpolate the riverbed level along the reach (source at
     * {@code t=0}, outlet at {@code t=1}).
     *
     * <p>Returns {@code 0} for a zero-length segment to avoid divide-by-zero.</p>
     */
    public float projection(float px, float pz) {
        if (lengthSq <= 0.0F) {
            return 0.0F;
        }
        float t = ((px - x1) * (x2 - x1) + (pz - z1) * (z2 - z1)) / lengthSq;
        if (t < 0.0F) return 0.0F;
        if (t > 1.0F) return 1.0F;
        return t;
    }
}
