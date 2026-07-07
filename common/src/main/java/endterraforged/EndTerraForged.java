package endterraforged;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import endterraforged.world.level.biome.EndBiomeSource;
import endterraforged.world.level.levelgen.ClimateMoistureCondition;
import endterraforged.world.level.levelgen.ClimatePlacementFilter;
import endterraforged.world.level.levelgen.ClimateTemperatureCondition;
import endterraforged.world.level.levelgen.EndDensityFunction;
import endterraforged.world.level.levelgen.FloatingIslandsFunction;

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

	/**
	 * Common bootstrap — logs that the mod is loading.
	 *
	 * <p><b>Does NOT register anything.</b> Registration timing is
	 * loader-specific:</p>
	 * <ul>
	 *   <li>Fabric: calls {@link #registerAll()} from
	 *       {@code onInitialize()} — runs before vanilla registries
	 *       freeze, so direct {@code Registry.register} works.</li>
	 *   <li>NeoForge: uses {@code DeferredRegister} registered on the
	 *       mod event bus from {@code EndTerraForgedNeoForge}'s
	 *       constructor. NeoForge freezes vanilla registries
	 *       <em>before</em> mod constructors run, so calling
	 *       {@code Registry.register} directly (as v0.1.0-preview
	 *       through v0.1.2-preview did) crashes with
	 *       {@code IllegalStateException: Registry is already frozen}.</li>
	 * </ul>
	 */
	public static void bootstrap() {
		LOGGER.info("EndTerraForged bootstrap: initialising End dimension worldgen.");
	}

	/**
	 * Registers all custom vanilla-registry entries via direct
	 * {@code Registry.register} calls.
	 *
	 * <p><b>Fabric-only.</b> This method works on Fabric because
	 * {@code onInitialize} runs before vanilla registries freeze.
	 * NeoForge must NOT call this — it uses {@code DeferredRegister}
	 * instead (see {@code EndTerraForgedNeoForge}).</p>
	 */
	public static void registerAll() {
		registerDensityFunctions();
		registerBiomeSources();
		registerSurfaceConditions();
		registerPlacementModifiers();
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
		// Stage 3.6: floating-island overlay layer. Stateless unit codec —
		// the live FloatingIslandsField is bound at NoiseChunk construction
		// by EndDensityVisitor when the profile opts in.
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
				location(FloatingIslandsFunction.NAME), FloatingIslandsFunction.CODEC);
		LOGGER.debug("EndTerraForged: registered density function {}",
				FloatingIslandsFunction.NAME);
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

	/**
	 * Registers custom {@code SurfaceRules.ConditionSource} codecs on the
	 * vanilla {@code minecraft:material_condition} registry, so that
	 * {@code surface_rule} JSON in {@code noise_settings} can gate rules on
	 * the End's custom {@link endterraforged.world.climate.EndClimate} fields.
	 *
	 * <p>Vanilla's {@code SurfaceRules.Temperature} reads the
	 * {@code Climate.Sampler}, which is zeroed in the End's
	 * {@code noise_settings}. These conditions read
	 * {@link endterraforged.world.climate.EndClimateAccess} instead — the
	 * volatile-published End climate field built by {@code MixinRandomState}.</p>
	 */
	private static void registerSurfaceConditions() {
		Registry.register(BuiltInRegistries.MATERIAL_CONDITION,
				location(ClimateTemperatureCondition.NAME),
				ClimateTemperatureCondition.CODEC.codec());
		LOGGER.debug("EndTerraForged: registered surface condition {}",
				ClimateTemperatureCondition.NAME);
		Registry.register(BuiltInRegistries.MATERIAL_CONDITION,
				location(ClimateMoistureCondition.NAME),
				ClimateMoistureCondition.CODEC.codec());
		LOGGER.debug("EndTerraForged: registered surface condition {}",
				ClimateMoistureCondition.NAME);
	}

	/**
	 * Registers the custom {@code PlacementModifierType} on the vanilla
	 * {@code minecraft:placement_modifier_type} registry, so that
	 * {@code placed_feature} JSON can gate feature placement on the End's
	 * custom climate fields.
	 */
	private static void registerPlacementModifiers() {
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
				location(ClimatePlacementFilter.NAME),
				ClimatePlacementFilter.TYPE);
		LOGGER.debug("EndTerraForged: registered placement modifier {}",
				ClimatePlacementFilter.NAME);
	}

	public static ResourceLocation location(String name) {
		if (name.contains(":")) return ResourceLocation.parse(name);
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
	}
}
