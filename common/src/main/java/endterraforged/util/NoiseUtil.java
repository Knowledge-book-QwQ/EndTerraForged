/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Original MIT notice retained per license terms.
 */
package endterraforged.util;

/**
 * Small, dependency-free math helpers shared by the worldgen pipeline.
 *
 * <p>Only the subset required by the current stage is ported; richer noise
 * helpers (gradient tables, sin lookup, vec records) will join this class (or
 * a dedicated {@code Noise} module) once the noise module is introduced, so
 * that nothing is imported ahead of its first consumer.</p>
 *
 * <p><b>Thread safety:</b> stateless utility class, all methods are safe to
 * call concurrently.</p>
 */
public final class NoiseUtil {

    private NoiseUtil() {
    }

    /**
     * Packs a pair of 32-bit coordinates into a single 64-bit seed, preserving
     * sign bits so that negative coordinates do not collide with positive ones.
     * The encoding mirrors upstream exactly so worldgen stays stable.
     */
    public static long seed(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | ((long) z & 0xFFFFFFFFL) << 32;
    }

    /** Clamps {@code value} to the closed range {@code [min, max]}. */
    public static float clamp(float value, float min, float max) {
        return (value < min) ? min : ((value > max) ? max : value);
    }
}
