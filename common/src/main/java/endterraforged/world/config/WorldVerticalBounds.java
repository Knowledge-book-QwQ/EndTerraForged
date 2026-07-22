package endterraforged.world.config;

/**
 * Immutable vertical envelope supplied by the actual Minecraft dimension.
 *
 * <p>The envelope is dimension data, not an editor preference. Worldgen uses
 * this type to keep an editable preset's height math aligned with the loaded
 * {@code NoiseSettings}; changing a preset alone cannot resize a registered
 * Minecraft dimension.</p>
 *
 * <p><b>Thread safety:</b> immutable value record; safe to share.</p>
 */
public record WorldVerticalBounds(int minY, int height) {

    public WorldVerticalBounds {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0, got " + height);
        }
        Math.addExact(minY, height);
    }

    /** Returns the exclusive Y coordinate immediately above this world. */
    public int maxYExclusive() {
        return Math.addExact(minY, height);
    }

    /** Returns the highest block Y that can exist inside this envelope. */
    public int maxYInclusive() {
        return maxYExclusive() - 1;
    }

    /** Returns whether a block Y lies inside this world's vertical range. */
    public boolean contains(int y) {
        return y >= minY && y < maxYExclusive();
    }
}
