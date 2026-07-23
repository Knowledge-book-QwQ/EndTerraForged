# EndTerraForged 声明

EndTerraForged 以 LGPL-3.0-or-later 协议开源，详见 `LICENSE`。

## 项目来源

本项目参考了 TerraForged 与 ReTerraForged 的地形生成思想，并在 EndTerraForged 中实现了面向末地的拓扑、海平面、浮空岛、大陆、气候、河流、侵蚀与 GUI 集成。

当前工作区使用的参考资料：

- TerraForged / ReTerraForged 的地形与 preset 架构思想，以及其 MIT 许可下可借鉴的实现模式。
- 本地 RTF R9.3.6 代码：作为详细配置项、默认值、Codec 字段和参数语义基线。
- 本地 RTF R9.6 代码：作为稳定大陆、地貌和噪声核心实现基线。
- 当前 RTF 开发分支：只读用于审查 terrain region、shape-aware placement、volcano
  morphology 与性能改进；这些开发中能力必须在 ETF 独立验证后才能移植或默认启用。
- RTF 地表核心复用方案以标签 `R9.3.6` / `R9.6` 的 `ContinentGenerator`、
  `AdvancedContinentGenerator`、`ArchipelagoPopulator`
  和 terrain shape primitive 为主要来源。无平台、注册表、`GeneratorContext`、`Cell`、
  水位或 river cache 耦合的 MIT 纯数学可以直接移植；实际源码进入 ETF 时，必须在对应
  文件保留版权头并在本文件追加具体类与修改说明。
- 本地 RTF R9.3.6 的 `ArchipelagoPopulator` 大陆架、offshore depth 和海岸过渡只用于 ETF 浮空大陆架语义调研；ETF 不照搬其海洋地形实现。
- YUNG's Better Caves：用于地下洞穴形态与分阶段路线调研；不复制源码。
- Worley's Caves / Worlium、Alex's Caves、Vanilla 1.18+ caves 与通用 SDF/图网络体素方案：仅用于洞穴体验目标、分层思路和技术路线调研；不复制源码，也不把其 UI 或视觉表现迁入本项目。
- OpenTerrainGenerator：用于配置驱动地形/洞穴管线调研；不复制源码。
- Terra：用于 Content Pack、虚拟 biome、palette 和 feature 分层调研；协议为 MIT，本轮只参考公开架构与配置语义。
- ReimagEND / Aeropelago：用于主岛外 3D 内容、虚拟 biome 和兼容包需求调研；配置与资源协议为 GPL-3.0，不复制进 ETF 核心。

R9.6 的用户界面设计、布局结构和视觉呈现不会被复制。

## 第三方库

- LDLib2 由 Low-Drag-MC 提供，协议为 LGPL-3.0。本项目只在 NeoForge 侧将其作为正式高级 UI bridge 集成，`common` 不直接依赖。
- YUNG's Better Caves 协议为 LGPL-3.0，本项目仅做架构/路线调研，不复制其实现。
- OpenTerrainGenerator 协议为 MIT，本项目仅做配置管线调研，若未来直接改写 MIT 源码需保留来源说明。
- Terra 协议为 MIT；若未来直接改写其源码，必须保留上游版权与许可说明。
- ReimagEND 与 Aeropelago 的 GPL 配置、结构和资源不得直接进入 ETF LGPL 核心；派生兼容内容必须独立审查并履行 GPL 义务。
- Minecraft、Fabric、NeoForge、Architectury 及其工具链分别遵循各自协议。

当源码文件直接复制或改写 MIT 许可代码时，必须在对应文件中保留上游版权头，并在本声明中保留来源说明。

## 已直接移植的 RTF 大陆数学

- `common/src/main/java/endterraforged/world/noise/domain/CompoundWarp.java`
  改写自 ReTerraForged `R9.3.6` / `R9.6` 的
  `raccoonman.reterraforged.world.worldgen.noise.domain.CompoundWarp`。保留复合坐标变换数学，
  删除 Codec、注册表和平台耦合。
- `common/src/main/java/endterraforged/world/noise/domain/Domains.java` 中的
  `domainPerlin`、`domainSimplex` 与 `compound` 工厂改写自同版本 `Domains`。仅引入
  `RtfMultiContinent` 需要的纯构造方法。
- `common/src/main/java/endterraforged/world/continent/RtfMultiContinent.java`
  改写自 ReTerraForged `R9.3.6` / `R9.6` 的
  `raccoonman.reterraforged.world.worldgen.cell.continent.simple.ContinentGenerator`。保留
  seed 顺序、复合 domain warp、3x3 Voronoi、`DISTANCE_2_DIV` 与 shape field；删除
  `GeneratorContext`、`Cell`、资源池、river cache、主世界 biome、water table 与 terrain
  写入。ETF 在其外层继续负责中央末地保护、有限大陆体积、preview 和并发安全。
- `common/src/main/java/endterraforged/world/noise/Perlin2.java`
  改写自 ReTerraForged `R9.3.6` / `R9.6` 的
  `raccoonman.reterraforged.world.worldgen.noise.module.Perlin2`。保留 24 方向 gradient、
  octave/gain/lacunarity 累加、范围推导和 stored-seed 语义；删除 Codec 与 noise registry
  bootstrap。
- `common/src/main/java/endterraforged/world/continent/RtfAdvancedContinent.java`
  改写自 ReTerraForged `R9.3.6` / `R9.6` 的
  `raccoonman.reterraforged.world.worldgen.cell.continent.advanced.AdvancedContinentGenerator`
  与 `AbstractContinent` 的纯大陆部分。保留 seed 顺序、两阶段 Voronoi、中垂线距离、
  cell size variance、中心修正、skip 和 cliff/bay 海岸数学；删除 `GeneratorContext`、
  `Cell`、`Resource<Cell>`、river cache 与主世界控制点外壳。当前已受控接入
   `EndHeightmap`、有限大陆体积和 preview 的集成测试路径，但 validator、Codec 与编辑器
   仍拒绝该算法，因此尚未成为正式 preset 或玩家可选 runtime。
- `common/src/main/java/endterraforged/world/terrain/TerrainRegionLayout.java`、
  `TerrainRegionBuffer.java` 与 `TerrainRegionPlan.java` 改写自当前 ReTerraForged 开发线的
  `UnifiedTerrainRegionLayout`、`RegionEdgeValue` 与相关候选选择数学。保留 warped Voronoi、
  有界候选搜索、稳定区域身份、中心、边缘和旋转语义；删除 `Cell`、`Resource<Cell>`、
  populator、registry、对象池和 RTF worldgen/UI 耦合。ETF 使用 immutable runtime 与
  caller-owned primitive buffer；宏观 ownership 只接受 AREA，region size 通过候选密度补偿
  而非改变目标面积占比。RIDGE 和 COMPACT 不参与该 ownership。
- `common/src/main/java/endterraforged/world/terrain/TerrainRidgeLayout.java` 与
  `TerrainRidgeBuffer.java` 改写自当前 ReTerraForged 开发线的 `TerrainAnchorLayout`。
  保留确定性 jittered anchor、有界搜索、Top-3 influence 排序和稳定 tie-break；删除 entry
  registry、`Cell`、对象池和 executor，并改为单一内部 RIDGE spacing band 与 caller-owned
  primitive buffer。
- `common/src/main/java/endterraforged/world/noise/Billow.java`、`Cubic.java` 与 `Terrace.java`
  改写自 ReTerraForged `R9.3.6` / `R9.6` 对应 noise module。保留多 octave billow、
  cubic value interpolation 与 terrace step 数学；删除 Codec、registry 和平台外壳。
- `common/src/main/java/endterraforged/world/heightmap/TerrainFamilyRuntime.java` 适配自
  ReTerraForged `Populators` 与 `RegionVariantPopulator` 的纯地貌函数。ETF 保留
  plains/steppe、hills 两种变体和 plateau 的数学形态，以及按 `regionId + entryId`
  固定变体的语义；删除 `Cell` 写入、terrain registry、overworld ground/water 与 biome
  耦合，并将输出限制为末地有限大陆体积使用的 scalar relief。
- `common/src/main/java/endterraforged/world/heightmap/EndRidgeTerrainEnvelope.java` 与
  `EndTerrainRidgeRuntime.java` 适配自 ReTerraForged 当前开发线 `RidgeTerrainEnvelope`、
  `ShapeAwareTerrainComposer` 与 `MountainChainBlender`。保留三段曲线中心线、82%-112%
  有机宽度变化、独立 core/apron、提前端点收窄、有限 shaped influence 和 host 保留原则。
  ETF 改为 immutable scalar relief、独立 anchor identity 和 AREA owner 上的最大值组合；删除
  `Cell`、对象池、河流、水位、biome、registry、executor 与主世界地形耦合。RIDGE footprint
  外严格输出零，不会退化成整图山脉噪声。
- `common/src/main/java/endterraforged/world/heightmap/EndTerrainVolcanoRuntime.java`
  改写自 ReTerraForged 当前开发线 `CompactTerrainEnvelope`、`VolcanoFootprint` 与
  `VolcanoProfile` 的有限火山几何。ETF 保留旋转椭圆 footprint、flank、rim 与 crater
  的 scalar relief，删除 `Cell`、lava block、surface material、biome、river deflection、
  registry、对象池和 executor 耦合。该文件当前仍处于未验证开发状态，尚未表示正式
  火山功能可用；`REGION_PLANNED` 的 validator、Codec 和 composer 当前都明确拒绝启用它。
- `common/src/main/java/endterraforged/world/heightmap/EndArchipelagoMask.java`
  改写自 ReTerraForged R9.3.6/R9.6 的 `ArchipelagoPopulator` 纯 mask 部分。保留
  `sizeNoise`、`densityNoise`、smoothstep 阈值和离岸衰减的数学语义；删除 `Cell`、
  `GeneratorContext`、water/biome/terrain 写入、river cache 与客户端依赖。ETF 新增
  中央末地保护、有限 landmass volume、caller-owned signal buffer 和末地化 coast gate。
