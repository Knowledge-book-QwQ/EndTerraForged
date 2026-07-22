package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for underground terrain modifier configuration.
 */
public final class SubsurfaceConfigValidator {

    private SubsurfaceConfigValidator() {
    }

    public static DataResult<SubsurfaceConfig> validate(SubsurfaceConfig config) {
        if (config == null) {
            return DataResult.error(() -> "subsurface config must not be null");
        }
        DataResult<AbyssPitConfig> abyss = AbyssPitConfigValidator.validate(config.abyssPitConfig());
        if (abyss.error().isPresent()) {
            return DataResult.error(() -> "subsurface.abyss: "
                    + abyss.error().orElseThrow().message());
        }
        DataResult<CaveTunnelConfig> caves =
                CaveTunnelConfigValidator.validate(config.caveTunnelConfig());
        if (caves.error().isPresent()) {
            return DataResult.error(() -> "subsurface.caves: "
                    + caves.error().orElseThrow().message());
        }
        DataResult<CaveSystemConfig> caveSystem =
                CaveSystemConfigValidator.validate(config.caveSystemConfig());
        if (caveSystem.error().isPresent()) {
            return DataResult.error(() -> "subsurface.cave_system: "
                    + caveSystem.error().orElseThrow().message());
        }
        DataResult<CaveNetworkConfig> caveNetwork =
                CaveNetworkConfigValidator.validate(config.caveNetworkConfig());
        if (caveNetwork.error().isPresent()) {
            return DataResult.error(() -> "subsurface.cave_network: "
                    + caveNetwork.error().orElseThrow().message());
        }
        DataResult<CaveChamberConfig> caveChambers =
                CaveChamberConfigValidator.validate(config.caveChamberConfig());
        if (caveChambers.error().isPresent()) {
            return DataResult.error(() -> "subsurface.cave_chambers: "
                    + caveChambers.error().orElseThrow().message());
        }
        return DataResult.success(config);
    }
}
