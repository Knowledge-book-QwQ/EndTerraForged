package endterraforged.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import endterraforged.EndTerraForged;

@Mod("endterraforged")
public class EndTerraForgedNeoForge {

	public EndTerraForgedNeoForge(IEventBus modEventBus, ModContainer container) {
		EndTerraForged.bootstrap();
	}
}
