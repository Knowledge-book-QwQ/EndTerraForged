package endterraforged;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import endterraforged.world.level.levelgen.EndDensityFunction;

/**
 * Common bootstrap entry-point shared by all mod loaders (Fabric &amp; NeoForge).
 *
 * <p>EndTerraForged is an End-dimension-focused terrain mod inspired by
 * ReTerraForged. End-specific registries (density functions, biomes,
 * features, surface rules) are wired up here as development progresses.</p>
 */
public class EndTerraForged {
	public static final String MOD_ID = "endterraforged";
	public static final Logger LOGGER = LogManager.getLogger("EndTerraForged");

	public static void bootstrap() {
		LOGGER.info("EndTerraForged bootstrap: initialising End dimension worldgen.");
		registerDensityFunctions();
	}

	/**
	 * Registers the End's custom density-function codecs on the vanilla
	 * {@code minecraft:density_function} registry, so that
	 * {@code noise_settings} JSON can reference them by id (e.g.
	 * {@code {"type":"endterraforged:end_density"}}).
	 *
	 * <p>This only publishes the <em>placeholder</em> codec — the live
	 * {@link endterraforged.world.heightmap.EndDensity} is bound later by
	 * a stage-3.4 Mixin via {@code mapAll}. See
	 * {@link EndDensityFunction} for the two-state design.</p>
	 */
	private static void registerDensityFunctions() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
				location(EndDensityFunction.NAME), EndDensityFunction.CODEC);
		LOGGER.debug("EndTerraForged: registered density function {}",
				EndDensityFunction.NAME);
	}

	public static ResourceLocation location(String name) {
		if (name.contains(":")) return ResourceLocation.parse(name);
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
	}
}
