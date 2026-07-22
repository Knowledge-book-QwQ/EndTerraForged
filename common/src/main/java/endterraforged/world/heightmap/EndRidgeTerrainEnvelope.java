/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Adapted from ReTerraForged's RidgeTerrainEnvelope (MIT) for
 * EndTerraForged (LGPL-3.0-or-later).
 *
 * EndTerraForged changes:
 * - keeps only allocation-free ridge geometry used by End worldgen
 * - accepts precomputed rotation vectors from the anchor layout
 * - removes placement profiles, registries and Cell integration
 */
package endterraforged.world.heightmap;

import endterraforged.world.noise.NoiseMath;

/** Allocation-free geometry for one finite, curved mountain chain. */
final class EndRidgeTerrainEnvelope {
    static final float MAX_ORGANIC_WIDTH_SCALE = 1.12F;

    private static final double GEOMETRY_EPSILON = 1.0E-6D;
    private static final double ROTATION_TOLERANCE = 1.0E-3D;
    private static final double MIN_ORGANIC_WIDTH_SCALE = 0.82D;
    private static final double PRIMARY_WIDTH_VARIATION = 0.20D;
    private static final double SECONDARY_WIDTH_VARIATION = 0.10D;
    private static final double MIN_TIP_START = 0.64D;
    private static final double TIP_START_RANGE = 0.12D;
    private static final double MIN_TIP_WIDTH_SCALE = 0.65D;

    private EndRidgeTerrainEnvelope() {
    }

    static float influence(float x, float z,
                           float centerX, float centerZ,
                           float coreHalfLength, float outerHalfLength,
                           float coreHalfWidth, float outerHalfWidth,
                           float rotationCos, float rotationSin,
                           float firstControlOffset, float secondControlOffset,
                           float edgeBlend) {
        if (!validInputs(x, z, centerX, centerZ,
                coreHalfLength, outerHalfLength, coreHalfWidth, outerHalfWidth,
                rotationCos, rotationSin, firstControlOffset, secondControlOffset, edgeBlend)) {
            return 0.0F;
        }

        double localX = localX(x, z, centerX, centerZ, rotationCos, rotationSin);
        double localZ = localZ(x, z, centerX, centerZ, rotationCos, rotationSin);
        double distanceSquared = nearestDistanceSquared(
                localX, localZ, coreHalfLength, coreHalfWidth,
                firstControlOffset, secondControlOffset);
        double longitudinal = longitudinalTaper(
                localX, coreHalfLength, outerHalfLength,
                firstControlOffset, secondControlOffset);
        if (!Double.isFinite(distanceSquared) || longitudinal <= 0.0D) {
            return 0.0F;
        }

        double normalizedPosition = Math.clamp(localX / coreHalfLength, -1.0D, 1.0D);
        double widthScale = organicWidthScale(
                normalizedPosition, firstControlOffset, secondControlOffset)
                * endpointWidthScale(longitudinal);
        double transverse = transverseInfluence(
                distanceSquared, coreHalfWidth * widthScale,
                outerHalfWidth * widthScale, edgeBlend);
        return unit(transverse * longitudinal);
    }

    static float relief(float x, float z,
                        float centerX, float centerZ,
                        float coreHalfLength, float outerHalfLength,
                        float coreHalfWidth, float outerHalfWidth,
                        float rotationCos, float rotationSin,
                        float firstControlOffset, float secondControlOffset,
                        float edgeBlend) {
        if (!validInputs(x, z, centerX, centerZ,
                coreHalfLength, outerHalfLength, coreHalfWidth, outerHalfWidth,
                rotationCos, rotationSin, firstControlOffset, secondControlOffset, edgeBlend)) {
            return 0.0F;
        }

        double localX = localX(x, z, centerX, centerZ, rotationCos, rotationSin);
        double localZ = localZ(x, z, centerX, centerZ, rotationCos, rotationSin);
        double distanceSquared = nearestDistanceSquared(
                localX, localZ, coreHalfLength, coreHalfWidth,
                firstControlOffset, secondControlOffset);
        double longitudinal = longitudinalTaper(
                localX, coreHalfLength, outerHalfLength,
                firstControlOffset, secondControlOffset);
        if (!Double.isFinite(distanceSquared) || longitudinal <= 0.0D) {
            return 0.0F;
        }

        double widthScale = organicWidthScale(
                localX / coreHalfLength, firstControlOffset, secondControlOffset)
                * endpointWidthScale(longitudinal);
        double effectiveHalfWidth = outerHalfWidth * widthScale;
        double transverse = 1.0D - smooth(
                Math.sqrt(Math.max(0.0D, distanceSquared)) / effectiveHalfWidth);
        return unit(transverse * longitudinal);
    }

    static float controlOffset(int anchorSeed, int salt, float curvature) {
        if (!Float.isFinite(curvature) || curvature <= 0.0F) {
            return 0.0F;
        }
        return NoiseMath.valCoord2D(anchorSeed + salt, anchorSeed, salt)
                * Math.min(1.0F, curvature);
    }

    private static double localX(float x, float z, float centerX, float centerZ,
                                 float rotationCos, float rotationSin) {
        double deltaX = x - (double) centerX;
        double deltaZ = z - (double) centerZ;
        return deltaX * rotationCos + deltaZ * rotationSin;
    }

    private static double localZ(float x, float z, float centerX, float centerZ,
                                 float rotationCos, float rotationSin) {
        double deltaX = x - (double) centerX;
        double deltaZ = z - (double) centerZ;
        return -deltaX * rotationSin + deltaZ * rotationCos;
    }

    private static double nearestDistanceSquared(double localX, double localZ,
                                                 double halfLength, double halfWidth,
                                                 double firstControlOffset,
                                                 double secondControlOffset) {
        double p0x = -halfLength;
        double p0z = 0.0D;
        double p1x = -halfLength / 3.0D;
        double p1z = firstControlOffset * halfWidth;
        double p2x = halfLength / 3.0D;
        double p2z = secondControlOffset * halfWidth;
        double p3x = halfLength;
        double p3z = 0.0D;

        double best = segmentDistanceSquared(localX, localZ, p0x, p0z, p1x, p1z);
        best = Math.min(best, segmentDistanceSquared(localX, localZ, p1x, p1z, p2x, p2z));
        return Math.min(best, segmentDistanceSquared(localX, localZ, p2x, p2z, p3x, p3z));
    }

    private static double segmentDistanceSquared(double x, double z,
                                                 double startX, double startZ,
                                                 double endX, double endZ) {
        double deltaX = endX - startX;
        double deltaZ = endZ - startZ;
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        double alpha = lengthSquared <= GEOMETRY_EPSILON * GEOMETRY_EPSILON
                ? 0.0D
                : ((x - startX) * deltaX + (z - startZ) * deltaZ) / lengthSquared;
        alpha = Math.clamp(alpha, 0.0D, 1.0D);
        double nearestX = startX + alpha * deltaX;
        double nearestZ = startZ + alpha * deltaZ;
        double distanceX = x - nearestX;
        double distanceZ = z - nearestZ;
        return distanceX * distanceX + distanceZ * distanceZ;
    }

    private static double transverseInfluence(double distanceSquared,
                                              double coreHalfWidth,
                                              double outerHalfWidth,
                                              double edgeBlend) {
        if (distanceSquared >= outerHalfWidth * outerHalfWidth) {
            return 0.0D;
        }
        if (outerHalfWidth <= coreHalfWidth + GEOMETRY_EPSILON) {
            double innerHalfWidth = coreHalfWidth * (1.0D - edgeBlend);
            if (distanceSquared <= innerHalfWidth * innerHalfWidth) {
                return 1.0D;
            }
            double alpha = (Math.sqrt(distanceSquared) - innerHalfWidth)
                    / (coreHalfWidth - innerHalfWidth);
            return 1.0D - smooth(alpha);
        }
        if (distanceSquared <= coreHalfWidth * coreHalfWidth) {
            return 1.0D;
        }
        double alpha = (Math.sqrt(distanceSquared) - coreHalfWidth)
                / (outerHalfWidth - coreHalfWidth);
        return 1.0D - smooth(alpha);
    }

    private static double longitudinalTaper(double localX,
                                            double coreHalfLength,
                                            double outerHalfLength,
                                            double firstControlOffset,
                                            double secondControlOffset) {
        double position = Math.clamp(localX / coreHalfLength, -1.0D, 1.0D);
        double endSelector = position < 0.0D ? firstControlOffset : secondControlOffset;
        double tipStart = MIN_TIP_START
                + TIP_START_RANGE * Math.clamp(endSelector * 0.5D + 0.5D, 0.0D, 1.0D);
        double tipStartDistance = tipStart * coreHalfLength;
        double distance = Math.abs(localX);
        if (distance <= tipStartDistance) {
            return 1.0D;
        }
        return 1.0D - smooth(
                (distance - tipStartDistance) / (outerHalfLength - tipStartDistance));
    }

    private static double organicWidthScale(double normalizedPosition,
                                            double firstControlOffset,
                                            double secondControlOffset) {
        double primary = 0.5D + 0.5D * Math.cos(
                (normalizedPosition * 1.25D + firstControlOffset * 0.35D) * NoiseMath.PI2);
        double secondary = 0.5D + 0.5D * Math.cos(
                (normalizedPosition * 2.5D - secondControlOffset * 0.25D) * NoiseMath.PI2);
        return MIN_ORGANIC_WIDTH_SCALE
                + PRIMARY_WIDTH_VARIATION * primary
                + SECONDARY_WIDTH_VARIATION * secondary;
    }

    private static double endpointWidthScale(double longitudinalTaper) {
        return MIN_TIP_WIDTH_SCALE
                + (1.0D - MIN_TIP_WIDTH_SCALE) * longitudinalTaper;
    }

    private static boolean validInputs(float x, float z,
                                       float centerX, float centerZ,
                                       float coreHalfLength, float outerHalfLength,
                                       float coreHalfWidth, float outerHalfWidth,
                                       float rotationCos, float rotationSin,
                                       float firstControlOffset, float secondControlOffset,
                                       float edgeBlend) {
        if (!Float.isFinite(x) || !Float.isFinite(z)
                || !Float.isFinite(centerX) || !Float.isFinite(centerZ)
                || !Float.isFinite(coreHalfLength) || !Float.isFinite(outerHalfLength)
                || !Float.isFinite(coreHalfWidth) || !Float.isFinite(outerHalfWidth)
                || !Float.isFinite(rotationCos) || !Float.isFinite(rotationSin)
                || !Float.isFinite(firstControlOffset) || !Float.isFinite(secondControlOffset)
                || !Float.isFinite(edgeBlend)
                || coreHalfLength <= GEOMETRY_EPSILON || outerHalfLength < coreHalfLength
                || coreHalfWidth <= GEOMETRY_EPSILON || outerHalfWidth < coreHalfWidth
                || Math.abs(firstControlOffset) > 1.0F || Math.abs(secondControlOffset) > 1.0F
                || edgeBlend <= 0.0F || edgeBlend >= 1.0F) {
            return false;
        }
        double rotationLengthSquared = rotationCos * (double) rotationCos
                + rotationSin * (double) rotationSin;
        return Math.abs(rotationLengthSquared - 1.0D) <= ROTATION_TOLERANCE;
    }

    private static float unit(double value) {
        return (float) Math.clamp(value, 0.0D, 1.0D);
    }

    private static double smooth(double value) {
        double clamped = Math.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
