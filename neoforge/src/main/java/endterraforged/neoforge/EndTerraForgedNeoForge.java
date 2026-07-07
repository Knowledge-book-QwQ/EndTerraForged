/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). NeoForge entry
 * point — wires the mod into NeoForge's mod-loading lifecycle.
 *
 * <p><b>Why DeferredRegister (not Registry.register).</b> NeoForge
 * freezes vanilla registries <em>before</em> mod constructors run.
 * Calling {@code Registry.register} from a mod constructor (as
 * v0.1.0-preview through v0.1.2-preview did) crashes with
 * {@code IllegalStateException: Registry is already frozen}.
 * {@code DeferredRegister} defers the actual registration to
 * NeoForge's {@code RegisterEvent}, which fires at the correct
 * lifecycle point (before freezing). Fabric doesn't have this
 * issue — its {@code onInitialize} runs before the freeze.</p>
 */
package endterraforged.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.registries.BuiltInRegistries;

import endterraforged.EndTerraForged;
import endterraforged.world.level.biome.EndBiomeSource;
import endterraforged.world.level.levelgen.ClimateMoistureCondition;
import endterraforged.world.level.levelgen.ClimatePlacementFilter;
import endterraforged.world.level.levelgen.ClimateTemperatureCondition;
import endterraforged.world.level.levelgen.EndDensityFunction;
import endterraforged.world.level.levelgen.FloatingIslandsFunction;

/**
 * NeoForge mod entry point.
 *
 * <p>Constructs {@code DeferredRegister} instances for each vanilla
 * registry the mod extends (density function types, biome sources,
 * material conditions, placement modifier types) and registers them
 * on the mod event bus. The actual registration happens during
 * NeoForge's {@code RegisterEvent} — the correct lifecycle point
 * for vanilla-registry writes.</p>
 *
 * <p><b>Why not call {@link EndTerraForged#registerAll()}.</b> That
 * method uses direct {@code Registry.register} calls, which work on
 * Fabric (where {@code onInitialize} runs before the freeze) but
 * crash on NeoForge (where mod constructors run after the freeze).
 * The CODEC/TYPE constants referenced below are defined in the common
 * module — only the registration <em>mechanism</em> is
 * loader-specific.</p>
 */
@Mod("endterraforged")
public class EndTerraForgedNeoForge {

	/**
	 * Deferred register for custom density function types
	 * ({@code endterraforged:end_density},
	 * {@code endterraforged:floating_islands}).
	 *
	 * <p>These are the two-state placeholder codecs — the live
	 * {@link endterraforged.world.heightmap.EndDensity} field is
	 * bound later by {@code MixinRandomState} via
	 * {@code EndDensityVisitor.mapAll}.</p>
	 *
	 * <p><b>Why raw {@code DeferredRegister}.</b> The vanilla registries
	 * use wildcard element types (e.g.
	 * {@code Registry<DensityFunctionType<?, ?>>}). Java generics
	 * can't express a {@code DeferredRegister<DensityFunctionType<?, ?>>}
	 * — the wildcard prevents the {@code .register(name, supplier)}
	 * call from type-checking. Using a raw {@code DeferredRegister}
	 * with {@code @SuppressWarnings} is the standard NeoForge pattern
	 * for vanilla wildcard registries.</p>
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final DeferredRegister DENSITY_FUNCTION_TYPES =
			DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
					EndTerraForged.MOD_ID);

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final DeferredRegister BIOME_SOURCES =
			DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE,
					EndTerraForged.MOD_ID);

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final DeferredRegister MATERIAL_CONDITIONS =
			DeferredRegister.create(BuiltInRegistries.MATERIAL_CONDITION,
					EndTerraForged.MOD_ID);

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final DeferredRegister PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
					EndTerraForged.MOD_ID);

	static {
		// Density function types — stateless placeholder codecs
		DENSITY_FUNCTION_TYPES.register(EndDensityFunction.NAME,
				() -> EndDensityFunction.CODEC);
		DENSITY_FUNCTION_TYPES.register(FloatingIslandsFunction.NAME,
				() -> FloatingIslandsFunction.CODEC);

		// Biome source — fully self-contained codec
		BIOME_SOURCES.register(EndBiomeSource.NAME,
				() -> EndBiomeSource.CODEC);

		// Surface-rule conditions — climate-gated
		MATERIAL_CONDITIONS.register(ClimateTemperatureCondition.NAME,
				() -> ClimateTemperatureCondition.CODEC.codec());
		MATERIAL_CONDITIONS.register(ClimateMoistureCondition.NAME,
				() -> ClimateMoistureCondition.CODEC.codec());

		// Placement modifier — climate-gated feature placement
		PLACEMENT_MODIFIER_TYPES.register(ClimatePlacementFilter.NAME,
				() -> ClimatePlacementFilter.TYPE);
	}

	public EndTerraForgedNeoForge(IEventBus modEventBus, ModContainer container) {
		EndTerraForged.bootstrap();
		DENSITY_FUNCTION_TYPES.register(modEventBus);
		BIOME_SOURCES.register(modEventBus);
		MATERIAL_CONDITIONS.register(modEventBus);
		PLACEMENT_MODIFIER_TYPES.register(modEventBus);
	}
}
