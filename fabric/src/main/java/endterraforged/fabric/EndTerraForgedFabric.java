package endterraforged.fabric;

import net.fabricmc.api.ModInitializer;
import endterraforged.EndTerraForged;

/**
 * Fabric entry point.
 *
 * <p>Fabric's {@code onInitialize} runs during the mod-loading phase,
 * <em>before</em> vanilla registries freeze. This means direct
 * {@code Registry.register} calls work here — unlike NeoForge, which
 * freezes registries before mod constructors run and requires
 * {@code DeferredRegister}.</p>
 */
public class EndTerraForgedFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EndTerraForged.bootstrap();
		EndTerraForged.registerAll();
	}
}
