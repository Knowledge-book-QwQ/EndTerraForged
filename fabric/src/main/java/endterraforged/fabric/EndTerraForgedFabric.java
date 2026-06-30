package endterraforged.fabric;

import net.fabricmc.api.ModInitializer;
import endterraforged.EndTerraForged;

public class EndTerraForgedFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EndTerraForged.bootstrap();
	}
}
