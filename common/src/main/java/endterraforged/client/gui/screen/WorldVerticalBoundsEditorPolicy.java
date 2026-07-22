package endterraforged.client.gui.screen;

import endterraforged.world.config.WorldVerticalBounds;

/**
 * Converts player-facing build limits into Minecraft's section-aligned world envelope.
 *
 * <p>The highest buildable Y is inclusive. Minecraft requires the exclusive top and
 * minimum Y to be multiples of 16, so valid maximum values end in {@code ...15}.
 * Keeping that constraint in the control scale prevents the editor from displaying a
 * value that the created dimension would later round or reject.</p>
 */
public final class WorldVerticalBoundsEditorPolicy {

    public static final int MIN_BUILD_Y = -2032;
    public static final int MAX_MIN_BUILD_Y = 0;
    public static final int MIN_MAX_BUILD_Y = 15;
    public static final int MAX_BUILD_Y = 2031;
    public static final int ALIGNMENT = 16;

    private WorldVerticalBoundsEditorPolicy() {
    }

    /** Returns a legal envelope whose top block is exactly {@code maxYInclusive}. */
    public static WorldVerticalBounds fromInclusiveLimits(int minY, int maxYInclusive) {
        requireAligned("minY", minY, 0);
        requireAligned("maxYInclusive", maxYInclusive, ALIGNMENT - 1);
        if (minY < MIN_BUILD_Y || minY > MAX_MIN_BUILD_Y) {
            throw new IllegalArgumentException("minY must be in [" + MIN_BUILD_Y + ", "
                    + MAX_MIN_BUILD_Y + "], got " + minY);
        }
        if (maxYInclusive < MIN_MAX_BUILD_Y || maxYInclusive > MAX_BUILD_Y) {
            throw new IllegalArgumentException("maxYInclusive must be in [" + MIN_MAX_BUILD_Y + ", "
                    + MAX_BUILD_Y + "], got " + maxYInclusive);
        }
        return new WorldVerticalBounds(minY, Math.addExact(Math.subtractExact(maxYInclusive, minY), 1));
    }

    /** Changes the bottom while preserving the exact player-facing maximum Y. */
    public static WorldVerticalBounds withMinimumY(WorldVerticalBounds current, int minY) {
        return fromInclusiveLimits(minY, current.maxYInclusive());
    }

    /** Changes the exact player-facing maximum Y while preserving the bottom. */
    public static WorldVerticalBounds withMaximumY(WorldVerticalBounds current, int maxYInclusive) {
        return fromInclusiveLimits(current.minY(), maxYInclusive);
    }

    private static void requireAligned(String name, int value, int remainder) {
        if (Math.floorMod(value, ALIGNMENT) != remainder) {
            throw new IllegalArgumentException(name + " must be congruent to " + remainder
                    + " modulo " + ALIGNMENT + ", got " + value);
        }
    }
}
