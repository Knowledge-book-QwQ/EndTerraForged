package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link SubsurfaceConfig}.
 */
public final class SubsurfaceConfigBuilder {

    private final AbyssPitConfigBuilder abyssBuilder = new AbyssPitConfigBuilder();
    private final CaveTunnelConfigBuilder caveBuilder = new CaveTunnelConfigBuilder();
    private final CaveSystemConfigBuilder caveSystemBuilder = new CaveSystemConfigBuilder();
    private final CaveNetworkConfigBuilder caveNetworkBuilder = new CaveNetworkConfigBuilder();
    private final CaveChamberConfigBuilder caveChamberBuilder = new CaveChamberConfigBuilder();

    public SubsurfaceConfigBuilder() {
        this(SubsurfaceConfig.DEFAULT);
    }

    public SubsurfaceConfigBuilder(SubsurfaceConfig source) {
        load(source);
    }

    public SubsurfaceConfig build() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                abyssBuilder.build(), caveBuilder.build(),
                caveSystemBuilder.build(), caveNetworkBuilder.build(),
                caveChamberBuilder.build());
        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public SubsurfaceConfigBuilder reset() {
        load(SubsurfaceConfig.DEFAULT);
        return this;
    }

    private void load(SubsurfaceConfig source) {
        Objects.requireNonNull(source, "source");
        AbyssPitConfig abyss = source.abyssPitConfig();
        CaveTunnelConfig caves = source.caveTunnelConfig();
        CaveSystemConfig caveSystem = source.caveSystemConfig();
        CaveNetworkConfig caveNetwork = source.caveNetworkConfig();
        CaveChamberConfig caveChambers = source.caveChamberConfig();
        abyssBuilder.reset()
                .enabled(abyss.enabled())
                .seedOffset(abyss.seedOffset())
                .pitScale(abyss.pitScale())
                .pitOctaves(abyss.pitOctaves())
                .pitLacunarity(abyss.pitLacunarity())
                .pitGain(abyss.pitGain())
                .threshold(abyss.threshold())
                .edgeFalloff(abyss.edgeFalloff())
                .depth(abyss.depth())
                .depthCurve(abyss.depthCurve())
                .minLandness(abyss.minLandness());
        caveBuilder.reset()
                .enabled(caves.enabled())
                .entranceProbability(caves.entranceProbability())
                .cheeseDepthOffset(caves.cheeseDepthOffset())
                .cheeseProbability(caves.cheeseProbability())
                .spaghettiProbability(caves.spaghettiProbability())
                .noodleProbability(caves.noodleProbability());
        caveSystemBuilder.reset()
                .enabled(caveSystem.enabled())
                .seedOffset(caveSystem.seedOffset())
                .depthStart(caveSystem.depthStart())
                .depthEnd(caveSystem.depthEnd())
                .spectacleBias(caveSystem.spectacleBias())
                .connectivity(caveSystem.connectivity())
                .surfaceOpeningChance(caveSystem.surfaceOpeningChance());
        caveNetworkBuilder.reset()
                .regionSize(caveNetwork.regionSize())
                .networkDensity(caveNetwork.networkDensity())
                .chamberSpacing(caveNetwork.chamberSpacing())
                .branchingFactor(caveNetwork.branchingFactor())
                .loopChance(caveNetwork.loopChance())
                .maxSlope(caveNetwork.maxSlope())
                .minLandness(caveNetwork.minLandness());
        caveChamberBuilder.reset()
                .chamberProbability(caveChambers.chamberProbability())
                .minRadius(caveChambers.minRadius())
                .maxRadius(caveChambers.maxRadius())
                .verticalStretch(caveChambers.verticalStretch())
                .floorBias(caveChambers.floorBias())
                .roughness(caveChambers.roughness());
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid subsurface config builder state: " + message);
    }

    public boolean abyssEnabled() {
        return abyssBuilder.enabled();
    }

    public int abyssSeedOffset() {
        return abyssBuilder.seedOffset();
    }

    public int abyssPitScale() {
        return abyssBuilder.pitScale();
    }

    public int abyssPitOctaves() {
        return abyssBuilder.pitOctaves();
    }

    public float abyssPitLacunarity() {
        return abyssBuilder.pitLacunarity();
    }

    public float abyssPitGain() {
        return abyssBuilder.pitGain();
    }

    public float abyssThreshold() {
        return abyssBuilder.threshold();
    }

    public float abyssEdgeFalloff() {
        return abyssBuilder.edgeFalloff();
    }

    public int abyssDepth() {
        return abyssBuilder.depth();
    }

    public float abyssDepthCurve() {
        return abyssBuilder.depthCurve();
    }

    public float abyssMinLandness() {
        return abyssBuilder.minLandness();
    }

    public boolean cavesEnabled() {
        return caveBuilder.enabled();
    }

    public float caveEntranceProbability() {
        return caveBuilder.entranceProbability();
    }

    public float caveCheeseDepthOffset() {
        return caveBuilder.cheeseDepthOffset();
    }

    public float caveCheeseProbability() {
        return caveBuilder.cheeseProbability();
    }

    public float caveSpaghettiProbability() {
        return caveBuilder.spaghettiProbability();
    }

    public float caveNoodleProbability() {
        return caveBuilder.noodleProbability();
    }

    public CaveSystemConfig caveSystemConfig() {
        return caveSystemBuilder.build();
    }

    public CaveNetworkConfig caveNetworkConfig() {
        return caveNetworkBuilder.build();
    }

    public CaveChamberConfig caveChamberConfig() {
        return caveChamberBuilder.build();
    }

    public SubsurfaceConfigBuilder abyssEnabled(boolean value) {
        abyssBuilder.enabled(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssSeedOffset(int value) {
        abyssBuilder.seedOffset(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssPitScale(int value) {
        abyssBuilder.pitScale(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssPitOctaves(int value) {
        abyssBuilder.pitOctaves(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssPitLacunarity(float value) {
        abyssBuilder.pitLacunarity(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssPitGain(float value) {
        abyssBuilder.pitGain(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssThreshold(float value) {
        abyssBuilder.threshold(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssEdgeFalloff(float value) {
        abyssBuilder.edgeFalloff(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssDepth(int value) {
        abyssBuilder.depth(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssDepthCurve(float value) {
        abyssBuilder.depthCurve(value);
        return this;
    }

    public SubsurfaceConfigBuilder abyssMinLandness(float value) {
        abyssBuilder.minLandness(value);
        return this;
    }

    public SubsurfaceConfigBuilder cavesEnabled(boolean value) {
        caveBuilder.enabled(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveEntranceProbability(float value) {
        caveBuilder.entranceProbability(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveCheeseDepthOffset(float value) {
        caveBuilder.cheeseDepthOffset(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveCheeseProbability(float value) {
        caveBuilder.cheeseProbability(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveSpaghettiProbability(float value) {
        caveBuilder.spaghettiProbability(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveNoodleProbability(float value) {
        caveBuilder.noodleProbability(value);
        return this;
    }

    public SubsurfaceConfigBuilder caveSystemConfig(CaveSystemConfig value) {
        Objects.requireNonNull(value, "value");
        caveSystemBuilder.reset()
                .enabled(value.enabled())
                .seedOffset(value.seedOffset())
                .depthStart(value.depthStart())
                .depthEnd(value.depthEnd())
                .spectacleBias(value.spectacleBias())
                .connectivity(value.connectivity())
                .surfaceOpeningChance(value.surfaceOpeningChance());
        return this;
    }

    public SubsurfaceConfigBuilder caveNetworkConfig(CaveNetworkConfig value) {
        Objects.requireNonNull(value, "value");
        caveNetworkBuilder.reset()
                .regionSize(value.regionSize())
                .networkDensity(value.networkDensity())
                .chamberSpacing(value.chamberSpacing())
                .branchingFactor(value.branchingFactor())
                .loopChance(value.loopChance())
                .maxSlope(value.maxSlope())
                .minLandness(value.minLandness());
        return this;
    }

    public SubsurfaceConfigBuilder caveChamberConfig(CaveChamberConfig value) {
        Objects.requireNonNull(value, "value");
        caveChamberBuilder.reset()
                .chamberProbability(value.chamberProbability())
                .minRadius(value.minRadius())
                .maxRadius(value.maxRadius())
                .verticalStretch(value.verticalStretch())
                .floorBias(value.floorBias())
                .roughness(value.roughness());
        return this;
    }
}
