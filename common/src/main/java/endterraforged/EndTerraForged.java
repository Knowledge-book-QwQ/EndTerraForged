package endterraforged;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resources.ResourceLocation;

/**
 * Common bootstrap entry-point shared by all mod loaders (Fabric &amp; NeoForge).
 *
 * <p>EndTerraForged is an End-dimension-focused terrain mod inspired by
 * ReTerraForged. End-specific registries (noise, biomes, features, surface
 * rules, chunk generation) will be wired up here as development progresses.</p>
 */
public class EndTerraForged {
	public static final String MOD_ID = "endterraforged";
	public static final Logger LOGGER = LogManager.getLogger("EndTerraForged");

	public static void bootstrap() {
		LOGGER.info("EndTerraForged bootstrap: initialising End dimension worldgen.");
		// TODO: register End-specific noise modules, biomes, features,
		//  surface rules, density functions and chunk generator hooks here.
	}

	public static ResourceLocation location(String name) {
		if (name.contains(":")) return ResourceLocation.parse(name);
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
	}
}
