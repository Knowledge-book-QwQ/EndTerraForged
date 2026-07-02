package endterraforged;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import endterraforged.world.level.biome.EndBiomeSource;
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
		registerBiomeSources();
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

	/**
	 * Registers the End's custom {@link net.minecraft.world.level.biome.BiomeSource}
	 * codec on the vanilla {@code minecraft:biome_source} registry, so that
	 * {@code dimension} JSON can reference it via
	 * {@code "biome_source": {"type":"endterraforged:end_biome_source"}}.
	 *
	 * <p>Unlike the density-function placeholder, the biome source codec is
	 * fully self-contained — five explicit {@code Holder<Biome>} fields
	 * resolved at DFU time, no Mixin late-binding needed. See
	 * {@link EndBiomeSource} for the geometric ring-segmentation design.</p>
	 */
	private static void registerBiomeSources() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE,
				location(EndBiomeSource.NAME), EndBiomeSource.CODEC);
		LOGGER.debug("EndTerraForged: registered biome source {}",
				EndBiomeSource.NAME);
	}

	public static ResourceLocation location(String name) {
		if (name.contains(":")) return ResourceLocation.parse(name);
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
	}
}
