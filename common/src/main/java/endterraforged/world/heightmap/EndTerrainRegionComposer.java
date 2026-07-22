package endterraforged.world.heightmap;

import java.util.ArrayList;
import java.util.List;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.terrain.TerrainRegionBuffer;
import endterraforged.world.terrain.TerrainRegionEntry;
import endterraforged.world.terrain.TerrainRegionFamily;
import endterraforged.world.terrain.TerrainRegionLayout;
import endterraforged.world.terrain.TerrainRidgeBuffer;
import endterraforged.world.terrain.TerrainRidgeLayout;

/** Composes AREA ownership with independent bounded ridge anchors. */
final class EndTerrainRegionComposer {
    static final float FEATURE_TAG_THRESHOLD = 0.20F;

    private static final ThreadLocal<EndTerrainSignalBuffer> SIGNAL_SCRATCH =
            ThreadLocal.withInitial(EndTerrainSignalBuffer::new);
    private static final ThreadLocal<EndTerrainSignalBuffer> CANDIDATE_SIGNAL_SCRATCH =
            ThreadLocal.withInitial(EndTerrainSignalBuffer::new);
    private static final ThreadLocal<TerrainRidgeBuffer> RIDGE_SCRATCH =
            ThreadLocal.withInitial(TerrainRidgeBuffer::new);
    private static final float REGION_WARP_STRENGTH_FACTOR = 0.125F;
    private static final float TARGET_RIDGE_REACH_TO_SPACING = 1.25F;
    private static final int PLAINS_ENTRY_ID = 0;
    private static final int HILLS_ENTRY_ID = 1;
    private static final int PLATEAU_ENTRY_ID = 2;
    private static final int RIDGE_LAYOUT_SEED_OFFSET = 0x45D9F3B;
    private static final float RIDGE_ROUGHNESS = 0.82F;
    private static final float RIDGE_EROSION_RESISTANCE = 0.68F;
    private static final int MOUNTAIN_TAG = 1 << TerrainRegionFamily.MOUNTAINS.ordinal();

    private final TerrainRegionLayout areaLayout;
    private final TerrainFamilyRuntime families;
    private final EndTerrainRidgeRuntime ridges;
    private final TerrainRidgeLayout ridgeLayout;
    private final int terrainRegionSize;

    EndTerrainRegionComposer(TerrainConfig config, int seed) {
        if (enabled(config.volcano())) {
            throw new IllegalArgumentException(
                    "REGION_PLANNED does not support the frozen COMPACT volcano runtime");
        }
        int terrainSeed = seed + config.terrainSeedOffset();
        this.families = new TerrainFamilyRuntime(config, seed);
        this.terrainRegionSize = config.terrainRegionSize();

        List<TerrainRegionEntry> areaEntries = new ArrayList<>(3);
        addArea(areaEntries, TerrainRegionFamily.PLAINS, config.plains(), PLAINS_ENTRY_ID);
        addArea(areaEntries, TerrainRegionFamily.HILLS, config.hills(), HILLS_ENTRY_ID);
        addArea(areaEntries, TerrainRegionFamily.PLATEAU, config.plateau(), PLATEAU_ENTRY_ID);
        this.areaLayout = areaEntries.isEmpty()
                ? null
                : new TerrainRegionLayout(
                        terrainSeed + 240,
                        Math.max(128, config.terrainRegionSize() * 2),
                        config.terrainRegionSize() * REGION_WARP_STRENGTH_FACTOR,
                        areaEntries);

        if (this.areaLayout != null && enabled(config.mountains())) {
            this.ridges = new EndTerrainRidgeRuntime(config, seed);
            int configuredSpacing = Math.max(128, Math.round(configuredRegionSize(config.mountains())));
            int requiredSpacing = (int) Math.ceil(
                    this.ridges.maxReach() / TARGET_RIDGE_REACH_TO_SPACING);
            int spacing = Math.max(configuredSpacing, requiredSpacing);
            this.ridgeLayout = new TerrainRidgeLayout(
                    terrainSeed + RIDGE_LAYOUT_SEED_OFFSET,
                    spacing,
                    this.ridges.maxReach(),
                    this.ridges::influence);
        } else {
            this.ridges = null;
            this.ridgeLayout = null;
        }
    }

    float auxiliaryContribution(float x, float z, int seed, TerrainRegionBuffer output) {
        return auxiliaryContribution(x, z, seed, output, 1.0F);
    }

    float auxiliaryContribution(float x, float z, int seed, TerrainRegionBuffer output,
                                float ridgeMultiplier) {
        EndTerrainSignalBuffer signals = SIGNAL_SCRATCH.get();
        sampleSignals(x, z, seed, output, signals, ridgeMultiplier);
        return signals.height();
    }

    float auxiliaryContribution(float x, float z, int seed, TerrainRegionBuffer output,
                                float landness, float inlandness,
                                EndTerrainEligibilityPolicy eligibilityPolicy) {
        EndTerrainSignalBuffer signals = SIGNAL_SCRATCH.get();
        sampleSignals(x, z, seed, output, signals, landness, inlandness, eligibilityPolicy);
        return signals.height();
    }

    void sampleSignals(float x, float z, int seed,
                       TerrainRegionBuffer output, EndTerrainSignalBuffer signals,
                       float ridgeMultiplier) {
        if (!sampleArea(x, z, output, signals)) {
            return;
        }
        applyRidges(x, z, output, signals, ridgeMultiplier);
    }

    void sampleSignals(float x, float z, int seed,
                       TerrainRegionBuffer output, EndTerrainSignalBuffer signals,
                       float landness, float inlandness,
                       EndTerrainEligibilityPolicy eligibilityPolicy) {
        if (!sampleArea(x, z, output, signals)) {
            return;
        }
        TerrainRidgeBuffer candidates = sampleRidges(x, z);
        if (candidates == null || candidates.candidateCount() == 0) {
            output.setFeatureInfluence(0.0F, TerrainRegionFamily.MOUNTAINS);
            return;
        }
        applyRidgeCandidates(x, z, output, signals, candidates,
                ridgeMultiplier(x, z, landness, inlandness, eligibilityPolicy));
    }

    EndTerrainLayer selectedLayer(float x, float z, TerrainRegionBuffer output) {
        return selectedLayer(x, z, output, 1.0F);
    }

    EndTerrainLayer selectedLayer(float x, float z, TerrainRegionBuffer output,
                                  float ridgeMultiplier) {
        if (!sampleAreaOwnership(x, z, output)) {
            return EndTerrainLayer.NONE;
        }
        applyRidgeIdentity(x, z, output, ridgeMultiplier);
        return toLayer(output.visibleFamily());
    }

    EndTerrainLayer selectedLayer(float x, float z, TerrainRegionBuffer output,
                                  float landness, float inlandness,
                                  EndTerrainEligibilityPolicy eligibilityPolicy) {
        if (!sampleAreaOwnership(x, z, output)) {
            return EndTerrainLayer.NONE;
        }
        TerrainRidgeBuffer candidates = sampleRidges(x, z);
        if (candidates == null || candidates.candidateCount() == 0) {
            output.setFeatureInfluence(0.0F, TerrainRegionFamily.MOUNTAINS);
            return toLayer(output.visibleFamily());
        }
        applyRidgeIdentity(candidates, output,
                ridgeMultiplier(x, z, landness, inlandness, eligibilityPolicy));
        return toLayer(output.visibleFamily());
    }

    EndTerrainBlend selectedBlend(float x, float z, TerrainRegionBuffer output) {
        return selectedBlend(x, z, output, 1.0F);
    }

    EndTerrainBlend selectedBlend(float x, float z, TerrainRegionBuffer output,
                                  float ridgeMultiplier) {
        if (!sampleAreaOwnership(x, z, output)) {
            return EndTerrainBlend.NONE;
        }
        float ridgeInfluence = applyRidgeIdentity(x, z, output, ridgeMultiplier);
        return blendFromSample(output, ridgeInfluence);
    }

    private static EndTerrainBlend blendFromSample(TerrainRegionBuffer output,
                                                   float ridgeInfluence) {
        EndTerrainLayer area = toLayer(output.ownershipFamily());
        if (ridgeInfluence > 0.0F) {
            return new EndTerrainBlend(area, EndTerrainLayer.MOUNTAINS, ridgeInfluence);
        }
        for (int candidate = 1; candidate < output.candidateCount(); candidate++) {
            EndTerrainLayer alternative = toLayer(output.candidateFamily(candidate));
            if (alternative != area) {
                return new EndTerrainBlend(area, alternative, output.candidateWeight(candidate));
            }
        }
        return EndTerrainBlend.single(area);
    }

    EndTerrainBlend selectedBlend(float x, float z, TerrainRegionBuffer output,
                                  float landness, float inlandness,
                                  EndTerrainEligibilityPolicy eligibilityPolicy) {
        if (!sampleAreaOwnership(x, z, output)) {
            return EndTerrainBlend.NONE;
        }
        TerrainRidgeBuffer candidates = sampleRidges(x, z);
        float influence = candidates == null || candidates.candidateCount() == 0
                ? 0.0F
                : applyRidgeIdentity(candidates, output,
                        ridgeMultiplier(x, z, landness, inlandness, eligibilityPolicy));
        if (influence == 0.0F) {
            output.setFeatureInfluence(0.0F, TerrainRegionFamily.MOUNTAINS);
        }
        return blendFromSample(output, influence);
    }

    private boolean sampleArea(float x, float z,
                               TerrainRegionBuffer output,
                               EndTerrainSignalBuffer signals) {
        if (!sampleAreaOwnership(x, z, output)) {
            signals.clear();
            return false;
        }
        signals.clear();
        EndTerrainSignalBuffer candidateSignals = CANDIDATE_SIGNAL_SCRATCH.get();
        for (int candidate = 0; candidate < output.candidateCount(); candidate++) {
            this.families.sample(
                    output.candidateFamily(candidate),
                    output.candidateRegionId(candidate),
                    output.candidateEntryId(candidate),
                    output.sampleX(), output.sampleZ(), candidateSignals);
            signals.addWeighted(candidateSignals, output.candidateWeight(candidate));
        }
        return true;
    }

    private boolean sampleAreaOwnership(float x, float z, TerrainRegionBuffer output) {
        if (this.areaLayout == null) {
            return false;
        }
        this.areaLayout.sample(x, z, output);
        return true;
    }

    private void applyRidges(float x, float z,
                             TerrainRegionBuffer output,
                             EndTerrainSignalBuffer signals,
                             float ridgeMultiplier) {
        TerrainRidgeBuffer candidates = sampleRidges(x, z);
        if (candidates == null || candidates.candidateCount() == 0) {
            output.setFeatureInfluence(0.0F, TerrainRegionFamily.MOUNTAINS);
            return;
        }

        applyRidgeCandidates(x, z, output, signals, candidates, ridgeMultiplier);
    }

    private void applyRidgeCandidates(float x, float z,
                                      TerrainRegionBuffer output,
                                      EndTerrainSignalBuffer signals,
                                      TerrainRidgeBuffer candidates,
                                      float ridgeMultiplier) {

        float multiplier = Math.clamp(ridgeMultiplier, 0.0F, 1.0F);
        float strongestInfluence = candidates.influence(0) * multiplier;
        float strongestRelief = 0.0F;
        for (int candidate = 0; candidate < candidates.candidateCount(); candidate++) {
            float relief = this.ridges.contribution(
                    candidates.anchorSeed(candidate),
                    candidates.centerX(candidate), candidates.centerZ(candidate),
                    candidates.rotationCos(candidate), candidates.rotationSin(candidate),
                    x, z) * multiplier;
            strongestRelief = Math.max(strongestRelief, relief);
        }

        int terrainTags = signals.terrainTags();
        if (strongestInfluence >= FEATURE_TAG_THRESHOLD) {
            terrainTags |= MOUNTAIN_TAG;
        }
        signals.set(
                signals.height() + strongestRelief,
                blend(signals.roughness(), RIDGE_ROUGHNESS, strongestInfluence),
                blend(signals.erosionResistance(), RIDGE_EROSION_RESISTANCE, strongestInfluence),
                terrainTags);
        output.setFeatureInfluence(
                strongestInfluence, TerrainRegionFamily.MOUNTAINS, candidates.anchorKey(0));
    }

    private float applyRidgeIdentity(float x, float z,
                                     TerrainRegionBuffer output,
                                     float ridgeMultiplier) {
        TerrainRidgeBuffer candidates = sampleRidges(x, z);
        if (candidates == null || candidates.candidateCount() == 0) {
            output.setFeatureInfluence(0.0F, TerrainRegionFamily.MOUNTAINS);
            return 0.0F;
        }
        return applyRidgeIdentity(candidates, output, ridgeMultiplier);
    }

    private static float applyRidgeIdentity(TerrainRidgeBuffer candidates,
                                            TerrainRegionBuffer output,
                                            float ridgeMultiplier) {
        float influence = candidates.influence(0) * Math.clamp(ridgeMultiplier, 0.0F, 1.0F);
        long anchorKey = influence > 0.0F ? candidates.anchorKey(0) : 0L;
        output.setFeatureInfluence(influence, TerrainRegionFamily.MOUNTAINS, anchorKey);
        return influence;
    }

    private TerrainRidgeBuffer sampleRidges(float x, float z) {
        if (this.ridgeLayout == null) {
            return null;
        }
        TerrainRidgeBuffer output = RIDGE_SCRATCH.get();
        this.ridgeLayout.sample(x, z, output);
        return output;
    }

    void sampleRidgeCandidates(float x, float z, TerrainRidgeBuffer output) {
        if (this.ridgeLayout == null) {
            throw new IllegalStateException("ridge layout is disabled");
        }
        this.ridgeLayout.sample(x, z, output);
    }

    private void addArea(List<TerrainRegionEntry> entries,
                         TerrainRegionFamily family,
                         TerrainLayerConfig config,
                         int entryId) {
        if (!enabled(config)) {
            return;
        }
        entries.add(TerrainRegionEntry.area(
                entryId, family, config.weight(),
                Math.max(128, Math.round(configuredRegionSize(config)))));
    }

    private float configuredRegionSize(TerrainLayerConfig config) {
        return this.terrainRegionSize * config.horizontalScale();
    }

    private static float ridgeMultiplier(float x, float z,
                                         float landness, float inlandness,
                                         EndTerrainEligibilityPolicy eligibilityPolicy) {
        return eligibilityPolicy == null
                ? 1.0F
                : eligibilityPolicy.ridgeMultiplier(x, z, landness, inlandness);
    }

    private static float blend(float underlay, float feature, float influence) {
        float weight = Math.clamp(influence, 0.0F, 1.0F);
        return underlay + (feature - underlay) * weight;
    }

    private static boolean enabled(TerrainLayerConfig config) {
        return config.weight() > 0.0F
                && config.baseScale() > 0.0F
                && config.verticalScale() > 0.0F;
    }

    private static EndTerrainLayer toLayer(TerrainRegionFamily family) {
        return switch (family) {
            case PLAINS -> EndTerrainLayer.PLAINS;
            case HILLS -> EndTerrainLayer.HILLS;
            case PLATEAU -> EndTerrainLayer.PLATEAU;
            case MOUNTAINS -> EndTerrainLayer.MOUNTAINS;
            case VOLCANO -> EndTerrainLayer.VOLCANO;
        };
    }
}
