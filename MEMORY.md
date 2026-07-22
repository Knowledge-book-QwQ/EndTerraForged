# EndTerraForged 项目记忆

> 文档状态：当前有效。
> 最近整理：2026-07-22。
> 本文件只记录长期有效的架构决策、踩坑和兼容经验；当前任务见 [`PLAN.md`](PLAN.md)，完整产品路线见 [`GOAL.md`](GOAL.md)。

## 1. 产品与平台决策

- 项目采用 NeoForge-first + LDLib2 主体验。Fabric 保留低维护编译和社区移植边界，不建设第二套官方高级 UI。
- `common` 是 preset、Codec、Validator、Builder、worldgen、runtime、preview sampler 和纯策略的唯一跨平台核心，禁止直接依赖 LDLib2/NeoForge/Fabric API。
- RTF R9.3.6 是详细参数与配置语义基线，R9.6 是稳定核心实现基线；当前 RTF 开发分支只作为需要独立验证的候选来源。禁止复制 RTF UI、布局和视觉呈现。
- 2026-07-14 用户明确授权：RTF 为同一维护方的 MIT 项目，ETF 对其成熟核心实现采取“直接移植优先”而非只参考思路。每次直接移植必须保留 MIT 版权头、写明来源与 ETF 改动，并更新 `NOTICE.md`；仍不复制 RTF UI。移植边界应优先选择无 `GeneratorContext`、主世界 biome/river cache 或平台 API 依赖的纯数学 primitive，再由 ETF 的 End-specific volume、central protection、preview 和 C2ME-safe runtime 组合。
- 2026-07-18 RTF 最新只读审查对象是 `C:\Users\klbook\.codex\worktrees\e95f\ReTerraForged` 的 `codex/r10x-volcano-rt4-fluid-routing` 工作树。RT1-RT4 已形成区域火山 artifact、provenance、bounded cache、single-flight、生命周期和最终流体路由契约，但工作树仍有未提交修改，且固定种子新世界、双加载器、C2ME/Noisium、长时间缓存和发布门禁仍需独立证据。ETF 只能吸收这些契约，不能把 RTF regional runtime 或 RT4 流体方块链误记为 ETF 已完成。
- 2026-07-18 ETF 火山边界保持不变：`EndTerrainVolcanoRuntime` 只作为 `REGION_PLANNED` 下受控的有限 analytical COMPACT relief；它不包含 RTF `Cell`、`GeneratorContext`、artifact、流体、surface material、biome registry 或 river cache，也不开放到玩家 preset/UI。后续若有正式区域消费者，优先为 ETF 自己建立 caller-owned primitive artifact 契约，并保留 analytical fallback。
- 2026-07-18 外部性能资料审查结论：Bye-Pregen 只作为 1.21.1 post-processing profiling 参考；quick-noise 只作为未来纯 3D 洞穴/离线预览的 Java 21 CPU benchmark 候选；按 Y section 并行不能创建第二套固定线程池或嵌套 `parallel()`；QUICK_V2、native/GPU 和 C2ME compiler shield 均不得作为透明默认优化。ETF 仍以固定 seed、RTF/C2ME parity、Standard JFR、边界连续性和完整高度正确性为先。
- 2026-07-18 跨项目反哺规则：[`docs/ETF_TO_RTF_IMPROVEMENT_TRACKER.md`](docs/ETF_TO_RTF_IMPROVEMENT_TRACKER.md) 是 ETF 向 RTF 回送通用改进的唯一状态台账。ETF 任务只读 RTF；先记录 ETF 证据、RTF 当前事实、风险和 RTF 验收门禁，再由独立 RTF 分支人工实现。其他 review/调研只作为来源，不重复维护反哺状态。
- 2026-07-14 RTF 大陆复用边界：R9.3.6 与 R9.6 的 `ContinentGenerator` / `AdvancedContinentGenerator` 大陆核心没有版本差异，后续按 golden parity -> `RTF_MULTI` -> 末地 `landness/inlandness` 分带 -> `RTF_ADVANCED` 的顺序移植。`UpliftContinentGenerator` 只允许提取平滑 Voronoi gradient/centroid uplift 标量，不移植 water table、river、lake 或 terracing；`ArchipelagoPopulator` 只拆出附属群岛 mask、海岸分带和 relief，禁止整体搬入主世界海洋/`Cell` 语义，也不得复用高空 `FloatingIslandsField`。已有 `format_version=2` 缺少 `continent.algorithm` 时必须保持 legacy 大陆；R2 新增 bands 持久化字段后已升级为 `format_version=3`。
- 2026-07-15 `RTF_ADVANCED` 纯数学移植陷阱：高级大陆 warp 必须使用 RTF `Perlin2` 的
  24 方向 gradient，不能用普通 `Perlin` 代替；cell size variance 调用的是四参数
  `NoiseUtil.map(value, min, max, range)` 归一化重载，不是五参数线性映射。误用五参数
  重载会改变大陆尺寸分布，即使代码仍能编译。当前实现已用固定 seed 的 edge、
  continent id、distance、center 与 skip 逐位 fixture 锁定，并覆盖并发、重排、远坐标、
  buffer 清理和边界连续性。2026-07-15 已完成受控 `EndHeightmap`、大陆分带、有限体积和
  preview 内部接线，但 `isImplemented()`、Codec/validator 和编辑器仍拒绝该算法；
  它只供集成与性能测试使用，不代表正式 preset 或默认 worldgen 已启用。
- 2026-07-15 大陆信号身份契约：正式 `ContinentSignals` 将 edge、landness、inlandness
  等连续值与 identified、packed cell `long` id、world-space center 等离散 ownership
  分开。RTF 上游 float cell value 只保留在 parity 诊断中，不作为 ETF 持久或下游身份，
  避免超远坐标碰撞。`BandedContinent` 只能重塑连续值，非零 outer activation 只能缩放
  连续值，中央保护/legacy/skip 必须清空 identity。后续 TerrainRegionPlan、preview、
  SurfaceContext 和 Content Pack 复用该身份，不得根据混合后的高度或 edge 重新猜测大陆。
- 2026-07-14/15 R0/R1 大陆移植完成：`ContinentAlgorithm` 已定义 `LEGACY_RADIAL`、`RTF_MULTI`、`RTF_ADVANCED`、`RTF_UPLIFT_EXPERIMENTAL`；当前只有 legacy 与 `RTF_MULTI` 可通过 validator，未实现选项明确拒绝。`RtfMultiContinent` 保留 RTF `ContinentGenerator` 的 root seed 顺序、复合 warp、3x3 最近/次近扫描、`DISTANCE_2_DIV`、`clampMin=0.2` 和 `inland=0.502` shape 语义，输出为不可变 `[0,1]` landness。`RtfMultiContinentTest` 固化 R9.3.6 参数、root seed `123456789` 和六个坐标的逐位 fixture，并覆盖重排、多线程、远坐标、连续性和 `mapAll`。`OUTER_CONTINENTS + RTF_MULTI` 由 `OuterContinentsContinent` 包裹，中央保护、有限体积、EndDensity 与 preview 继续使用既有 ETF 边界。2026-07-15 起，只有新 `EndPreset.defaults()` 使用 `ContinentConfig.rtfMultiDefaults()`；`ContinentConfig.defaults()`、空 JSON 和显式旧 preset 继续保持 `LEGACY_RADIAL`，避免静默重塑存档。固定 seed 的四方向一万格走廊测试防止新默认再次产生“万格无大陆”。
- 2026-07-14 R2 分带与有限体积整合完成代码闭环：`ContinentBandsConfig` 默认阈值为 `void_outer=0.10`、`shelf=0.25`、`rim=0.327`、`coast=0.448`、`inland=0.502`，Validator 强制 `[0,1]` 与严格递增。`BandedContinent` 只包裹 `OUTER_CONTINENTS + RTF_MULTI`，不回写 `RtfMultiContinent` 的 golden 输出；它把原始信号转换为有限 shelf 的 landness 与山地、高原、火山 relief 的 inlandness。密度热路径使用 caller-owned `ContinentSignalBuffer` 和线程隔离原始类型列缓存，不在每个 Y 创建 record、数组或集合；preview 通过同一 `EndHeightmap` / `EndDensity` 路径观察差异。大陆子编辑器只显示已实现算法，并将 `RTF_MULTI`、大陆分带开关与五个阈值直接写入同一 preview/Done builder，避免实机验收误测到默认 legacy 路径。旧 preset 仅在用户显式启用 bands 后升级为 v3；未启用时保留原格式和兼容语义。新 v3 preset 必须显式写出 bands；v0-v2 缺失 bands 时解码为 `LEGACY_PASSTHROUGH`，保持旧世界输出。自动门禁已通过，真实客户端、RTF 同载、C2ME 与 JFR 验收仍是发布前置条件。
- 2026-07-15/17 海洋正式生成边界：禁止把 ETF 全局流体规则简单改成“sea level 以下全水”，因为 disabled aquifer 会把大陆洞穴和 shelf 下方负 density 一并交给 picker。`EndOceanFluidPicker` 在 `NoiseChunk.forChunk` 参数层按当前不可变 `EndDensity` 与 seed 绑定。首版只用二维 `landness == 0` 判断外海，2026-07-17 实机截图在约 `(10079,1,-648)` 与 `(10825,-12,-659)` 证明大陆投影内整列断水，使有限 shelf 悬在干燥空腔上。当前契约改为三维连通：零 landness 外海始终供水；正 landness 的有限大陆列只在 cached underside 以下供水；shelf 体积内的 carve 不供水。`WITH_FLOOR` 的三 octave 低频海床跨外海和大陆投影连续，海床以下即使被 subsurface carve 也不由外海 picker 灌水；`NO_FLOOR` 不建海床并让 underside 以下保持水体。中央保护、无海模式和 bootstrap 降级仍返回 air，vanilla `Y < -54` lava fallback 不会回来。热路径只复用线程隔离列缓存 primitive，不新增对象或共享可变状态。
- 2026-07-14 地表质量根因结论：当前 ETF 的视觉问题不是“参数还不够多”，而是生成层级缺失。`EndTerrainComposer` 主要使用一个低频 selector，把权重区间映射到 plains/hills/plateau/volcano 等通用噪声层；它没有稳定的 terrain region id/center/edge，没有 area/ridge/compact 三类放置语义，也没有 RTF 成熟地貌族的独立 morphology。结果是地貌在剖面上更像连续抖动噪声，而不是由区域组织的平原、高原、山系和火山。后续禁止继续靠增加 slider、叠加全局噪声或修饰现有 selector 来冒充高质量地表。
- 2026-07-14 高质量地表架构锁定为：`ContinentSignals -> TerrainRegionPlan -> TerrainFamilyRuntime -> StructuredFeatureOverlay -> Uplift/Archipelago -> Erosion/Drainage -> EndLandmassVolume -> EndDensity`。`TerrainRegionPlan` 必须是不可变、零热路径分配的区域结果，至少提供 primary/secondary family、blend、region id、center、edge 与稳定方向；正式 density 仍按 X/Z 列缓存最终横向信号。
- 2026-07-14 RTF 地表复用分三级。可直接移植：高级大陆的纯 Voronoi/中垂线数学、稳定噪声 primitive、成熟山地/高原等 shape 与有限 ridge envelope。必须末地化改造：uplift、archipelago、river geometry、hydraulic erosion、terrain region/placement，需要去除海洋、水位、`Cell`、registry、river cache 与主世界 terrain type 写入。明确拒绝：RTF UI、RTF cave、`GeneratorContext`、池化/可变 `Cell`、`Resource<Cell>`、全局 water table、主世界 biome 耦合和独占 `NoiseRouter.mapAll` 注入。
- 2026-07-18 火山来源状态修正：RTF 最新 `codex/r10x-volcano-rt4-fluid-routing` 工作树已经形成较成熟的区域 artifact、provenance、bounded cache、single-flight、生命周期和 RT4 流体路由候选。ETF 的 `EndTerrainVolcanoRuntime` 仍冻结，但原因是当前阶段范围、末地体积语义和独立性能/兼容门禁，而不是继续断言 RTF 火山没有实现。ETF 首个高质量地表垂直切片仍以 AREA 地貌、有限 RIDGE、非径向 uplift、群岛海岸和 analytical erosion 为准；火山后续单独评审。
- 2026-07-14 当前 RTF 开发分支中的 `VariableTerrainRegionLayout`、`TerrainCatalog`、`ShapeAwareTerrainComposer` 与有限 ridge placement 是有价值的候选，但它们属于比 R9.6 更新的开发线。ETF 不得把“当前 RTF 有代码”自动等同于“稳定可直接默认启用”；必须先做来源版本记录、独立 fixture、性能预算和 ETF 末地语义审查。RTF volcano 的来源成熟度由后续 2026-07-15 决策单独否决。
- 2026-07-15 shape-aware ownership 修正：RTF S3/S4.1 的“AREA host + 稀疏 shaped anchor overlay”只保留为几何原型，不作为 ETF 最终生产架构。ETF 首个生产版 `TerrainRegionPlan` 由全部正权重 `AREA` family 建立统一无空洞 ownership；`weight` 表示 AREA 的近似面积占比，region size 使用 `weight / scale^2` 补偿并只改变重复区域尺寸。RIDGE 是有界物理形态叠加，不得夺取宏观 ownership；COMPACT/火山在来源与自研规格成熟前冻结。ownership family、underlay family、visible family 与 physical influence 分开；ridge 在 footprint 外物理影响归零并回到 underlay，ownership 不得随机退回另一个 host。
- 2026-07-18 RTF S4.3-S4.7 为上述决策提供了新的实证：让 RIDGE 拥有完整宏观区域会产生“平地却属于山地 owner”的身份和权重错位。ETF 已完成 AREA-only ownership 与独立 bounded ridge anchors：每点最多保留三个 ridge candidate，重叠 relief 取最大值，最强 physical influence 决定 feature anchor identity、roughness、erosion resistance、tags 与 visible family；AREA `regionId + entryId` 在 eligibility 前后保持不变。ETF 保留多候选 partition-of-unity、caller-owned primitive buffer、packed stable id 和 `400` 次 AREA 最坏搜索预算。
- 2026-07-18 RTF ridge 纯数学已移植为 `EndRidgeTerrainEnvelope`：确定性三段曲线中心线、82%-112% 大尺度宽度变化、独立 core/apron、最后 24%-36% 半长内提前收窄，以及拒绝 `sqrt(taper)` 奇异导数的有界线性端点收敛。`TerrainRidgeLayout` 使用 caller-owned Top-3 primitive buffer 和稳定 tie-break；exhaustive oracle、最大值组合、端点、跨区块与并发测试已通过。实际新世界、C2ME/RTF 同载和 JFR 仍未完成。
- 2026-07-15 额外 RTF 复用范围：`TerrainAnchorLayout` 的有界搜索证明、per-region 稳定 variant、`RegionLerper`/`MountainChainBlender` 的连续通道与身份分离、`PerlinRidge`/`SimplexRidge`、terrace、curve/spline、Worley edge 与 masked erosion primitive 均可作为候选。只移植纯数学和测试契约，不复制 `Cell`、对象池、registry/Codec 外壳或共享 mutable state。高世界继续禁止猜测 surface 上限；精确空 cell 优化只能在最终 density 与流体安全都可证明后研究。
- 2026-07-15 RTF 扩展能力审查：`TerrainLowRedirect` 只复用为不改变 ownership 的
  `EndTerrainEligibilityPolicy`；`WorldFilters` 只复用后处理阶段顺序，droplet erosion
  必须改造成 primitive、region-aligned、有界 tile；`ClimateModule` 只提取稳定 climate
  region/id/center/edge 与温湿度场；`TerrainBiomeFilter` 改成稳定 resource key、加载期
  编译和有界 climate fallback；surface/structure 下游统一消费只读 `SurfaceContext`
  与 placement anchors。RTF preview 仅参考 generation id、旧帧保留、取消和渲染线程
  upload，不复制 UI。正式 worldgen 不创建 ETF 私有 executor，辅助 preview/tile worker
  必须同时受 CPU、heap 与有界队列约束。
- 2026-07-15 高质量地表持久化决策：现有 `format_version=3` 永久保持当前大陆算法、
  volume 与 `LEGACY_SELECTOR`。`REGION_PLANNED`、稳定 resource key 和完整配置闭环完成后
  使用新的 `format_version=4`；不得用同名字段换公式或随 jar 更新重塑旧世界。
- 2026-07-15 RTF 复用总原则进一步收束：ETF 不只复用大陆轮廓，还复用经逐位 fixture
  验证的 terrain ownership、区域稳定变体、地貌形态 primitive、有限 ridge/compact
  envelope 和连续信号混合规则。气候、surface、structure、preview 与侵蚀只复用设计
  契约并改造成 ETF 的 immutable、caller-owned primitive signal 管线。正式 runtime
  必须使用固定 seed 命名空间，新增模块不得因构造顺序变化让旧模块 seed 漂移。
- 2026-07-15 版本交付决策：`0.1.8` 只承担兼容与安全基线，不代表地表质量完成；
  `format_version=4` 的 `0.2.0` 才承载 `RTF_ADVANCED + REGION_PLANNED + 核心地貌族`
  的高质量地表。现有 v3 preset 永久保持 legacy 语义。
- 2026-07-15 P4.2 首次受控 runtime 接线：`TerrainLayoutMode.REGION_PLANNED` 已进入
  `TerrainConfig` 的 Codec/Builder/Validator，并由 `EndPresetValidator` 拒绝写入 v3；
  因此它目前只能通过受控 runtime/profile 测试进入 `EndHeightmap`，不能被玩家 preset 或 UI
  启用。`EndTerrainRegionComposer` 使用 immutable `TerrainRegionLayout` 与每线程 scratch
  buffer，将现有 plains/hills/plateau 辅助层组织为 AREA region，并使高度、分类与 preview
  诊断共用该路径；没有 AREA 层或启用尚未实现的 COMPACT volcano 会明确校验失败。
- 2026-07-15 P4.3 首批核心地貌族：`TerrainFamilyRuntime` 以 immutable noise tree 适配
  RTF 的 plains/steppe、hills 两种形态和 plateau，并以派生 seed、packed `regionId` 与
  `entryId` 固定变体。`TerrainRegionBuffer` / `TerrainRegionPlan` 现在暴露边界候选的
  packed cell id；边界高度混合分别采样两侧完整身份，避免同 family 相邻区域错误复用主侧
  变体。`Billow`、`Cubic`、`Terrace` 已作为保留 MIT 版权头的纯数学 primitive 进入 common。
  输出仍是 bounded scalar relief；2026-07-18 起 `EndTerrainSignalBuffer` 同时提供
  family-level roughness、erosion resistance 和 terrain tags。独立 RIDGE overlay 现在以
  有界 physical influence 对 AREA 通道做插值，feature tag 采用固定阈值防止边缘污染；COMPACT
  在 `REGION_PLANNED` 的 validator、Codec 和 composer 中明确拒绝，不能静默忽略；
  这只是后续 erosion/content 的输入契约，不是完整侵蚀。v4 preset 和玩家 UI 仍未实现。
- 2026-07-15 P4.4 首个有限 RIDGE：`EndTerrainRidgeRuntime` 将 RTF
  `ShapeAwareTerrainComposer` / `MountainChainBlender` 的有限 structured-feature 原则改造成
  immutable scalar relief。它使用遗留 RIDGE owner 的 center/orientation，在有限 length/width
  envelope 内生成山脉，端点与横向边缘平滑归零；`REGION_PLANNED` 同时停止消费 legacy 全局
  mountain base。该版本通过了当时的自动门禁，但 2026-07-18 已确认其 ownership 前提错误，
  只能作为迁移和性能基线；正式实现必须改用真实 AREA owner 上的独立 bounded ridge anchors。
- 2026-07-18 P4.4 正式替代链：`TerrainRegionLayout` 只接受 AREA，`TerrainRidgeLayout` 独立
  搜索有限 anchors，`EndTerrainRegionComposer` 先完成 AREA signals，再组合 Top-3 ridge。
  eligibility 只在确有可达 ridge candidate 后计算 shelf 支撑；无候选和零 multiplier 均逐位回到
  AREA 信号。共享 scratch 为每线程固定 primitive buffer，不持有 world/runtime 引用，适用于 C2ME worker。
- 2026-07-15 结构化地貌交付门禁：`RIDGE`、`COMPACT` 等新 feature 只有在来源边界、
  footprint/连续性/确定性定点测试、正式 height 与 preview 同源、完整构建和真实客户端
  回归均通过后，才能进入 `format_version=4`、玩家 preset 或 UI。工作树中未验证的
  `EndTerrainVolcanoRuntime` 只能视为草稿。`EndTerrainEligibilityPolicy` 已落地：
  它只限制 RIDGE physical influence，使用 landness、inlandness、当地 shelf 参考支撑和
  OUTER_CONTINENTS 中央保护；continent/region/ownership/underlay identity 不变。
- 2026-07-18 profile 输入契约：`EndTerrainProfileBuffer` 的 `rawTop` 沿用
  `EndHeightmap#getTerrainHeight` 的归一化高度坐标，不能误称为世界 Y；通过
  `EndLevels#scale(float)` 转换为实际 Y。`slope` 使用五点中心差分的
  `gradient / (1 + gradient)`，`curvature` 使用带符号五点 Laplacian 的
  `k / (1 + abs(k))`。profile 只在显式 API 调用时增加四个邻域高度采样，不进入 density 默认路径。
- ETF 地下系统不采用 RTF cave。主线是自研 region graph + SDF/metaball/ellipsoid + spline/capsule + CSG。
- 默认世界规格为 Standard：`min_y=-256`、`height=512`。Extended 1024、Grand 2048、Epic 4064 都是未来创建世界时显式选择的规格，不是默认值。
- 2026-07-17 垂直范围契约：Minecraft 的 `height` 是从 `min_y` 开始计算的总垂直跨度，不是最高可建造 Y；例如 `min_y=-256,height=512` 的最高方块是 `Y=255`。创建世界编辑器只暴露 16 格分区合法的最低 Y 与 inclusive 最高可建造 Y（最高值恒为 `...15`），内部再派生总高度，禁止显示一个随后会被取整的假上限。Done 通过 `WorldCreationUiState.updateDimensions` 只复制并替换 End stem：`DimensionType` 与 `NoiseGeneratorSettings` 必须使用同一 bounds，其他维度、biome source 与全部非高度字段保持不变；`onCreate` 前再次应用，防止数据包重载覆盖。direct holder 必须通过原版 RegistryFileCodec round-trip 测试。已有世界只读实际 `ServerLevel` bounds，不能通过 preset 伪扩容。
- 1024 及以上必须给出黄色性能/新世界警告。已有世界不能通过保存 preset 改变已注册 dimension/noise 包络。
- 默认目标地表是多个直径约 4000-8000 格的大型大陆。原版主岛、黑曜石柱、龙战、返回门、网关及其外围区域当前冻结为原版行为；它们不属于当前大陆或性能修复范围，必须在外部大陆、性能与兼容性稳定后作为最后一个独立阶段处理。
- 大陆形态规划为 `FLOATING_SHELF`、`DEEP_MASS`、`LAYERED`、`FLOATING_SHELL`。本地 RTF `ArchipelagoPopulator` 的 `shelfEnd`、`offshoreDepth` 和海岸过渡可参考语义，但不能把海洋大陆架原样搬到末地。
- 2026-07-12/13 P1/P2 基线审查：已有世界的旧 preset 即使请求旧高度，runtime 只替换实际 world bounds，不会改写显式的 `SeaMode` 或 `TopologyMode`。当前 `WITH_FLOOR` 在有陆地的位置保持实体直到世界底部，当前 `CONTINENTAL` 实现则错误地把“多块大陆”解释为连续全覆盖实体；后续只在主岛外将其改为由虚空海峡分隔的宏观陆块。`EndCentralRegionPolicy` 把中央原版区固定为半径 `1536`，并定义 `1536..2048` 的外部启动带，保留主岛与早期原版外岛缓冲而不把四千格外岛区冻结。常规 provider 路径与 `ChunkMap` direct 路径现在都能保存 vanilla End `final_density`：后者仅在两个原版 direct 调用点把 `ServerLevel.registryAccess().asGetterLookup()` 传入一次性 thread-local，再由 `MixinRandomState` 同线程消费。`EndDensityFunction.Bound` 在中央区委托 fallback；`FloatingIslandsFunction.Bound` 返回 fallback 下界，确保 `max(main, floating)` 不会抬高 vanilla 负 density。该策略纯、无共享可变状态，适用于 C2ME 并行采样。没有可信 provider、vanilla fallback 构造失败或每区块 binding 失败时，必须带诊断地拒绝创建/生成；不得继续写 ETF placeholder 的空气区块。这个 fail-fast 契约不替代真实客户端回归，不能把主岛冻结误报为发布级完成。编辑器必须显示翻译后的语义名称，bootstrap 成功日志必须记录请求/实际包络、拓扑、海洋模式、是否保留底层和浮岛状态，避免把合法配置误报为 ETF worldgen 失效。
- 2026-07-12 P2 多大陆实现：`OUTER_CONTINENTS` 使用 `OuterContinentsContinent` 包裹现有无状态 cell landmass，在中央启动带内早退、保护带外才采样，所以不会在中央区域浪费 3x3 cell 扫描。`outer_continent_scale` 是独立字段，默认 `4096`，范围 `[1024,16384]`；不得复用 `continent_scale`，后者仍是 `CONTINENTAL_SHATTERED` 的既有语义。DataFixer `RecordCodecBuilder` 单组最多 16 个字段，新增第 17 个字段时必须用扁平内部 `MapCodec` 组合，不能改成嵌套 JSON。P2 初版以 `format_version=1` 标记 `OUTER_CONTINENTS`；P3 已将当前新默认推进至 version 2。缺失版本的旧 compact JSON 始终解为 `format_version=0` / `ISLANDS`；未知版本拒绝。外部大陆预览会确定性搜索代表性外部 landness 窗口，侵蚀网格必须使用同一世界坐标，不能只平移颜色。
- 2026-07-14 有机海岸契约：原 `IslandsContinent` 只按最近 feature point 做径向衰减，宏观大陆会呈现圆形或椭圆形边缘。新 preset 默认 `ContinentCoastShape.ORGANIC`，在同一次 3x3 cell 扫描中复用最近/次近距离形成 cell-boundary 轮廓，再叠加独立多尺度 coast noise；这样不会新增第二次 Worley 扫描，也不会引入共享状态。`coast_scale`、`coast_strength`、`coast_cell_blend` 只在 runtime 构造期建树并在采样期读取。旧 continent JSON 缺少 `coast_shape` 时必须解为 `RADIAL_LEGACY`，旧构造器也必须保持径向输出，禁止为视觉效果静默改变已有存档。`TerrainPreviewSampler` 必须通过同一 `EndHeightmap`/`IslandsContinent` 取样；`OUTER_CONTINENTS + WIDE` 的观察范围至少覆盖 1.5 倍 `outer_continent_scale`，并对粗搜索到的代表中心做确定性局部细化，防止预览把真实大陆裁成小椭圆。
- 2026-07-13/15 流体回归：Minecraft 1.21.1 的 `NoiseBasedChunkGenerator` 全局 fluid picker 固定在 `Y < -54` 返回 lava；即使 `aquifers_enabled=false`，disabled aquifer 仍会把所有非实体密度单元交给它。最初的构造器级全空气替换解决了岩浆，却也让海洋模式无法产水。当前改为每个 ETF `RandomState` 绑定外海 picker，并只在创建 `NoiseChunk` 时替换第 5 个 `forChunk` 参数；非 ETF RandomState 保留原 picker，因此不影响 RTF 主世界、下界或其他维度。正式地下河/熔岩仍必须使用独立 runtime 方块放置链，不能复用外海兜底。
- 2026-07-12/14 P3 有限大陆体积：`ContinentConfig` 的新默认是 `FLOATING_SHELF`，主体厚度 `160`、边缘厚度 `48`。`EndLandmassVolume` 的大陆内部底面以 `levels.surface - thickness` 为宏观中心，并叠加独立、世界种子驱动的两层低频 Simplex 起伏；起伏幅度为当地厚度的 18%，尺度跟随当前大陆算法的宏观尺度。它只在 `landness -> 0` 的边缘用 `edgeFade` 平滑回接 terrain top。禁止把高频地表噪声平行复制到底面，也禁止把内部底面退化成固定水平板。R2 后默认 preset 为 `format_version=3`，显式写出 `volume_mode` 与 bands。旧 continent JSON 缺少 `volume_mode` 时必须解为 `LEGACY_COLUMN`，即继续遵循旧 `SeaMode` 的 column/floor 语义；v0-v2 缺失 bands 时保持分带直通，不能把旧世界静默改成 shelf 或新大陆形态。此处底面公式处于预发布校准期，旧 `FLOATING_SHELF` 存档的未生成区块会使用新 jar 的公式，真实视觉回归必须使用新世界或明确标记的新区块。底面噪声只允许在 X/Z 列缓存刷新时采样一次；`EndDensity` 必须把 underside 与 landness/top 一起缓存在线程隔离原始类型列缓存中，不能在每个 Y 重复算厚度。中央区域仍由 vanilla fallback density 接管，shelf 仅影响主岛外 ETF density。`TerrainPreviewMode.VOLUME`、`LandmassSlicePreviewSampler` 和大陆编辑器必须读取同一 `EndLandmassVolume` / 无 carve `EndDensity` 路径，不能维护第二套预览公式。外部大陆剖面必须复用代表性外部观察点，不能默认落在受保护中央区而显示纯虚空。
- 2026-07-14 RTF 多大陆预览尺度：`RTF_MULTI` 的一个宏观 Voronoi 单元约为 `continent_scale * 4`。广域预览必须以至少两个单元（`continent_scale * 8`）为观察跨度；不得误用 `outer_continent_scale` 作为其取景尺度，否则 3000 级大陆会在“广域”窗口内看成一整片陆地。该规则只影响 GUI preview 的采样坐标跨度，采样栅格上限不变，不影响 runtime worldgen 或预览重绘成本。
- 2026-07-13/14 外部大陆边缘回归：平滑 `landness` 不能只用于横向 gate。旧 `FLOATING_SHELF` 在 landness 接近零时仍保留 `shelfEdgeThickness`，会在归零点形成高实体幕墙；可选辅助地形层若不衰减也会额外抬高墙顶。`EndLandmassVolume.edgeFade(landness)` 是有限 shelf 的唯一边缘收敛因子：它把边缘处的 terrain top 平滑过渡到内部独立低频 underside，辅助地形贡献也乘同一 fade；`LEGACY_COLUMN` 的 fade 固定为 `1` 以保持旧世界语义。最外缘可能因厚度小于一个方块而没有完整实体方块，这是消除多格直壁后的正常离散结果。客户端视觉回归必须在新世界或未生成区块进行，旧区块不会被新 jar 重塑。
- ETF Worldgen Content Pack API 将地形几何与 biome、Content Profile、surface palette 和 feature 解耦。Content Profile 独立于 biome holder，允许多个主题复用同一 registered biome；核心内置原版 fallback。Biome/registry holder 不进入 `EndPreset`。
- ETF 强制接管 End dimension/noise/density；其他模组通过 biome、feature、structure、tag、surface rule 和适配器混合。无法安全混合时必须明确提示。与 ReTerraForged R10 同载时，RTF 的主世界 `GeneratorContext` 仅在 router 含 RTF `CellSampler.Marker` 时启用，ETF End router 不会触发它；但两者都作用于 `NoiseChunk`。2026-07-13 首次实机同载证实旧 ETF `@Redirect` 会抢占 RTF redirect，并以 RTF `0/1` 注入失败中断主世界区块生成。因此 ETF 必须使用优先级 `1100` 的 `@ModifyArg` 包装原版 chunk visitor：高于默认优先级 redirect，先变换 visitor 参数但保留原始 `NoiseRouter.mapAll` 调用，禁止重新使用独占 `@Redirect`。该 wrapper 会先绑定 ETF placeholder，再将 bound leaf 和 vanilla fallback 交回原 visitor；fallback 必须只映射一次，保留 per-chunk interpolation/cache 语义。捕获 ThreadLocal 只保存 ETF End 的 `RandomState`，主世界及其他维度必须立即清除，避免给 RTF 主世界增加无意义的跨调用状态。修复 jar 需要在 RTF 主世界 + ETF 末地实机重测后才能宣称兼容。
- Terra 官方平台为 Fabric/Bukkit，ETF 为 NeoForge-first；两个 generator 不能同时接管同一 End dimension。ETF 不实现通用 Terra YAML/noise/TerraScript 解释器，只通过独立 Content Pack/adapter 兼容主岛外内容语义。
- Terra 使用 MIT；ReimagEND 与其引用的 Aeropelago 配置资源使用 GPL-3.0。不得把 ReimagEND GPL 配置、结构或资源复制进 ETF LGPL 核心；需要派生内容时使用独立兼容包并履行许可义务。

## 2. 世界边界与坐标陷阱

- `EndLevels` 的 Y 归一化必须使用 `(worldY - minY) / worldHeight`，不能使用 `worldY / worldHeight`。
- surface fill、ground、elevation 与 `scale(float)` 必须以 dimension 的 `minY` 为原点；否则负 Y 世界会把地表压到错误高度并与 density 分叉。
- `EndPresetRuntimeResolver` 是 preset 与实际 world bounds 的唯一对齐点。合法地形参数可以保留，但 runtime envelope 必须替换为实际 bounds。
  - preset 中 sea/baseline Y 超出实际世界时应明确拒绝并触发可诊断降级，禁止静默 clamp。
  - P1 的正式世界采样坐标必须先落在实际 `WorldVerticalBounds` 内；旧高度请求不会扩大已加载的 Standard 包络。越界观察只能说明玩家位置在世界外，不能作为 density、流体或大陆形态的证据。
  - `SeaMode.NONE` 下 `Y < surface` 不自动意味着实体地下。Standard 512 只定义世界包络；地下实体厚度来自大陆 volume 和洞穴基础层设计。
- `DimensionProfile#minY` 的示例应使用 Standard `-256`。旧 `-2032..2031` 只属于历史 Epic 原型，不得再作为当前默认说明。

## 3. Preset 与编辑器生命周期

- 创建世界阶段没有可靠 `worldDir`，编辑器只能发布内存 preset；不要在该阶段硬接 named Preset Library 磁盘操作。
- 创建世界编辑器不得注册到 vanilla `PresetEditor.EDITORS` 的 `WorldPresets.NORMAL` 槽位。该表是全局一对一注册表，会与 ReTerraForged 等主世界生成模组争用原版“自定义”按钮。ETF 使用独立的创建世界入口；入口应避开 footer 和现有控件，空间不足时隐藏，不能重新占用 `Customize`。
- 真实 worldDir 在服务器创建/已有世界上下文获取。NeoForge 暂停菜单入口只在本地集成服务器存在时显示；多人客户端隐藏。
- 已有世界保存后的 worldgen runtime 通常要重进世界或重启本地服务器才完整生效，不承诺即时重建当前 `RandomState`。
- 损坏 active preset 必须拒绝打开，不能用默认值静默覆盖坏文件。
- Done 流程必须先 build/validate，再保存；只有保存成功或确实没有 worldDir 时才发布到 `EndPresetAccess` 并关闭界面。
- Preset Library 导入/导出使用世界目录下受控交换目录，不依赖平台文件选择器；导入只更新编辑状态，仍由 Done 统一持久化。
- 暂停菜单按钮应基于现有 widget bounds 选择安全位置；空间不足时不显示，避免覆盖其他模组按钮。
- editor camera、preview mode、slice axis、slice offset、scale 和调试 mask 都是 UI 状态，不进入 preset JSON。
- 2026-07-14 LDLib2 UI 崩溃回归：`ModularUI.ModularUIWidget` 不能作为普通 `Renderable` 直接添加到 vanilla `Screen`。在 LDLib2 2.2.27、Connector 和 ModernUI 同载环境中，打开 `ContinentConfigEditorScreen` 会使 Taffy 在 render 阶段访问失效 node 并崩溃。所有当前 ETF 子编辑器的 Done/Cancel action bar 必须使用 vanilla `Button` fallback；不要根据“检测到 LDLib2”自动嵌入 ModularUI。后续 NeoForge 高级 UI 只有在采用完整的原生 LDLib2 screen/host 生命周期并完成实机兼容验证后才能重新启用。

## 4. 配置实现约定

- 每个可序列化配置使用 immutable record + Codec + Validator + Builder + 测试的固定模式。
- Builder 是 UI 构造配置的唯一入口；不要在多个 screen setter 中手工复制 record 全字段，否则新增参数时容易漏传。
- 只添加存在 runtime 消费者和 preview 可观察差异的参数。没有消费者的字段不是进度，只是格式债务。
- 未支持的 JSON 字段应明确拒绝。`cave_liquids`、`cave_water`、`cave_lava`、`preview_mode`、`slice_axis`、`slice_offset`、`blocks_per_pixel` 等 preview/UI 字段不得进入 `SubsurfaceConfig`。
- 默认配置尽量保持旧世界行为；地下和高成本效果默认关闭或使用兼容默认。
- 新增动态翻译 key 时同步更新 `en_us.json` 与 `zh_cn.json`；资源测试要求 key 集合一致，并枚举 preview mode 等动态 key。

## 5. Worldgen 热路径经验

- `EndDensity` 是正式区块生成热点。当前列缓存采用每线程、有界、原始类型 direct-mapped 结构，并以 X/Z/seed 精确键复用 landness 与 terrain top。
- vanilla `NoiseChunk.fillAllDirectly` 以 Y 为外层、X/Z 为内层；相邻 Y 会重访同一组列，因此列缓存有效。
- `EndSubsurface.carves(...)` 依赖 Y，不能错误地塞入只按 X/Z 缓存的列结果。
- `EndHeightmap` 只有在横向采样比例为默认 1.0 时才能复用已知 landness；坐标缩放后必须按变换坐标重新走 terrain 路径。
- RTF `RtfMultiContinent` 的 raw `landness` 是经过海岸塑形的形状信号，在 raw edge 约 `0.502` 附近包含有意的 coast-shape cutover；它不保证适合作为 ETF inland relief 的连续输入。ETF 必须区分用于外部 shelf 轮廓的 shaped landness 与用于 rim/inland 起伏的 continuous edge，并在进入 rim 前平滑回接。只验证输出范围、golden parity 和确定性不足以发现这类错误；大陆与地貌改动必须增加固定 seed、preset、坐标的相邻列高度连续性门禁，并在失败信息中同时输出 raw edge、raw landness、banded inlandness、terrain 与最终 height。
- `FloatingIslandsField` 的 Gaussian 必须有有限垂直支撑。任意微小正 density 都会被视为固体，无限尾部会产生超薄长柱并浪费 3x3 Worley 扫描。
- 当前浮岛/湖泊/河流等缓存必须有界、按线程隔离，并在 owner/seed/config 切换时清理。
- 洞穴 runtime 不应为每个 density sample 创建 record。返回 primitive strength，节点/scratch 使用每线程复用。
- 只由 immutable config 决定的阈值、概率、归一化系数和最大深度应在 runtime 构造期缓存。
- preview 剖面中同一列的 world X/Z、landness 和 terrain top 不依赖 Y，应每列采样一次；3D cave strength 继续按 Y 采样。
  - `EndRiverMap` 的 3x3 扫描已缓存不可变河道几何，并使用每线程可复用 `RiverSample` scratch 返回最近结果；该 scratch 不能逃逸或在 raw terrain 采样期间重入。`getTerrainHeight` 不进入 river modifier，因此当前调用链满足这个前提，避免每个有效河流高度查询分配临时 record。
  - `EndTerrainComposer#auxiliaryContribution` 位于正式 height/density 热路径，只能返回标量，不得构造 `LayerBlend`、`EndTerrainBlend` 等预览诊断对象；对象化的 layer/alpha 描述只允许在 `selectedBlend` 这类非正式采样 API 使用。
  - 多尺度 terrain region 不能假设只有两个或三个 Voronoi 候选参与交汇。不同 family 拥有独立网格尺度时，固定 top-N 会在第 N/N+1 候选换位处产生细窄接缝。`TerrainRegionLayout` 必须在固定 `16 * 5 * 5 = 400` 最坏搜索预算内保留候选，用导数连续的紧支撑 score-ratio 核筛选过渡集合并归一化为 partition-of-unity 权重；`TerrainRegionBuffer` 由调用线程持有固定数组，不在热路径分配。紧支撑宽度必须同时通过候选切换连续性和激活候选数量门禁，不能只追求宽过渡而让每列重复采样十几个昂贵地貌。
  - `EndTerrainSignalBuffer` 是 family-level 的 caller-owned 通道契约：`height` 必须与正式 auxiliary height 逐位一致，`roughness`、`erosionResistance` 和 `terrainTags` 由区域 family 与有限 RIDGE/COMPACT influence 共同表达。坡度/曲率仍由独立 `EndTerrainProfileBuffer` 提供；接入 `EndErosionField` 前必须增加结构化 feature 通道和顺序无关测试。
  - `EndTerrainEligibilityPolicy` 不能在每个 REGION_PLANNED 列上无条件预先采样 shelf noise。
    ownership/overlay 拆分后，`EndTerrainRegionComposer` 先完成 AREA ownership，再由独立 bounded
    ridge anchor sampler 判断是否存在可达候选；只有存在候选时才计算 shelf 支撑和 feature 数学。
    COMPACT 本轮冻结，不得保留“RIDGE owner 才支付成本”的旧优化前提。
  - `EndDensity` 已拥有每列的 `landness` 缓存。经 `EndHeightmap` 调用河流和湖泊后处理时，应传递该精确 world-coordinate landness，避免再次采样 continent；公共 carver API 仍保留自行查询的调用形式，供没有列缓存的调用方使用。
  - ETF 的 `MixinNoiseChunk` 必须通过组合后的 visitor 先绑定自定义 density 叶节点，再交回原版 per-chunk visitor；不得以 `@Redirect` 独占共享 `router.mapAll` 调用、不得返回 raw router 或绕过 vanilla interpolation/cache wrapper。这个约束是 ReTerraForged 主世界与 ETF 末地同载的前提。
  - 当前 `EndDensity` 输出严格为 `0/1`，`FloatingIslandsField` 输出为 `0..1`，因此 `max(main, floating)` 与旧 `clamp(add(main, floating), 0, 1)` 等价，并避免原版 `ADD.fillArray` 的第二数组。若任一值域或连续 density 契约改变，必须恢复或重新证明组合语义。
  - 仅 `LEGACY_COLUMN` 的 `NONE/NO_FLOOR` 在 reference surface 以下是精确常量虚空，必须在访问列缓存和 terrain/subsurface sampler 前早退。`FLOATING_SHELF` 不套用该旧路径：原始 terrain 从 `levels.surface` 起算，climate、river、lake 都保留该下界，因此 `EndLandmassVolume` 可在构造期缓存最大厚度导出的 `minimumUnderside`；`EndDensity` 只在有限 shelf 且低于该界时于列缓存前返回。后续新增 height post-processor 必须保持此下界或删除该 shelf 早退，禁止按预估 terrain、固定 margin 或全局 density 值域做剪枝。
  - 缓存生命周期：`EndDensity` 与 `EndCaveGraph` 使用静态 `ThreadLocal` 的单个有界缓存，以弱 owner 引用识别 runtime；世界或 preset runtime 切换时先失效再采样，旧 world 不被缓存强引用。Floating Islands 与 river 也使用静态 owner-aware 缓存；lake 只复用可覆写 scratch。不要改回每 runtime 一个 `ThreadLocal`，否则 C2ME 长生命周期 worker 会累积世界切换留下的缓存值。进程级 preset/climate/biome layout holder 仍由 server halt 清理。
  - 禁止用预测 surface、固定 margin 或全局 `minValue/maxValue` 缩短 `NoiseChunk`。高风险下一候选只能是在最终 `CacheAllInCell` 值已包含结构/第三方修改后，以 aquifer-disabled 为前提的精确空 cell material 跳过。
  - 用户要求 ETF 任务只读参考本地 RTF 仓库，不得修改 RTF 文件；可反哺内容写入 ETF 文档，由用户或 RTF 维护流程人工迁移。
  - 2026-07-11 的本机 JUnit 微基准中，Standard 512 的 `WITH_FLOOR + CONTINENTAL`、气候/河流/湖泊与浮岛 overlay 组合约为 `1050 ns/op`。该值仅用于检测热路径回归，不可替代固定坐标的客户端 MSPT、GC 或 profiler 证据。

## 6. 洞穴实现状态语义

- `CaveTunnelConfig` 是旧兼容/规划骨架，单独启用它不应启用正式自研洞穴 carve。
- `CaveSystemConfig`、`CaveNetworkConfig`、`CaveChamberConfig` 与 `EndCaveField`/`EndCaveGraph` 已形成 3D carve 骨架，但仍需要正式产品化、性能压测和视觉验收。
- `CAVE_WATER` / `CAVE_LAVA` 是 graph flow/rift 派生的候选预览层，不生成正式水或熔岩方块。
- 地下功能按 runtime -> preview -> UI 顺序扩展。正式河流必须有方块/流体消费者，不能由颜色图冒充。
- 洞穴优先级：巨型洞厅、深渊洞口、地下河、长距离网络、多层洞穴、天然桥梁与石柱。

## 7. 并发与 C2ME

- worldgen 必须与区块访问顺序无关；相同 seed/preset/世界规格的固定坐标结果一致。
- 不在采样热路径写 static mutable collection，不跨 worker 共享可变节点或 scratch。
- `ThreadLocal` 数据必须有界并包含 owner 生命周期；多世界或配置切换不能读到旧缓存。
- runtime access holder 只发布完整 immutable 对象；bootstrap 失败和服务器停止时清理，避免跨世界 stale state。
- C2ME 验证要比较未缓存/单线程参考与并行固定坐标输出，不能只以“不崩溃”为标准。
- C2ME 源代码预审已确认 density/continent/cave 只由 seed 与坐标哈希决定，未消费顺序随机数；`ClimatePlacementFilter` 的 `RandomSource` 仅来自原版 placement 方法签名，ETF 不读取它。四 worker 的默认 shelf、完整高度后处理和 cave graph 采样已与单线程逐位一致，但不替代实际 C2ME DFC delegate、调度顺序和长时间 heap 验证。
- 2026-07-13 的 RTF+C2ME 对照实例（未加载 ETF）显示，C2ME DFC 会为 RTF `CellSampler` 输出 `Generating DelegateNode for type`，并继续完成 density compilation。对当前 C2ME `0.4.0-alpha.0.113` 的反编译确认：`McToAst` 仅硬编码原版 density 类型和少数内置 Tectonic 绑定，未知类型创建 `DelegateNode`，没有稳定的通用外部扩展 SPI；其单点路径调用 `compute`，批量路径调用 `fillArray`。该信息可作为 ETF 后续基线：未知自定义 density 的 delegate 通常保留语义，但可能损失 AST 优化；ETF 加载后必须记录 `EndDensityFunction.Bound`/相关 wrapper 是否进入 delegate，并用 JFR 判断成本，不能把警告本身当成崩溃或兼容通过。未经 JFR 证明不得为 C2ME 内部 AST 引入依赖、反射或 Mixin；若确有必要，只能实现不污染 common 的 optional NeoForge bridge。`HookCompatibility` 因通用 chunk-save 事件订阅禁用优化的警告也必须与 ETF density 成本分开归因。
- 自适应降级只允许影响 preview 或非核心客户端细节，不能让正式 worldgen 因机器负载产生不同地形。

## 8. Bootstrap 与故障降级

- worldgen bootstrap 构造 climate、heightmap、density、subsurface 等 runtime 时应形成原子发布语义：完整成功才发布，失败回滚 access holder。
- 只 catch 可恢复的 `Exception`；`OutOfMemoryError`、`StackOverflowError` 等 JVM Error 应 fail fast。
  - density 绑定或单点采样失败需要定义安全 fallback，并对热路径日志限流，避免每方块刷屏。
  - fallback 是故障安全，不是正常兼容路径。真实测试必须检查日志，不能因为世界能打开就认为 ETF 正常接管。
  - 诊断底部流体时，应记录 `EndTerraForged captured loaded End noise settings` 的实际 `defaultBlock/defaultFluid/aquifersEnabled`；仓库数据包只说明预期资源，不能证明整合包运行时最终加载的设置。

## 9. Architectury、LDLib2 与构建陷阱

- 不要在同一工作树并行运行 Gradle。并行 common test、平台编译或 remap 曾产生 stale/空 common jar，导致平台找不到主类。
- transform/remap/release 验证不能带 `--configure-on-demand`，否则 Architectury 可能在 mappings provider 初始化前访问它。
- 根任务 `:verifyReleaseArtifacts --no-daemon` 负责检查 common transform jar、Fabric/NeoForge 最终 jar、metadata、关键类和关键资源。
- 新增必须进入最终 jar 的 common 资源或关键类时，同步更新 release artifact 分类/白名单。
- NeoForge 不能在与 common 相同的 Java package 声明平台类，否则开发运行时可能触发 JPMS split-package `ResolutionException`。
- LDLib2 2.2.6 生产 jar 通过 jarjar 携带 Taffy、Yoga、Kotlin runtime，但 Loom 开发启动不一定自动加入 legacy classpath；NeoForge 开发 runtime 需要显式依赖，ETF 发布 jar 不重复 shadow。
- `CommonSourceBoundaryTest` 会扫描 common Java 与 resources。LDLib2 bridge 反射字符串是显式 allowlist，不应扩张为普通平台依赖。

## 10. Windows 与仓库卫生

- Windows 中文路径可能让 Gradle/Architectury worker 类加载失败或静默退出。稳定方式是在同一 PowerShell 会话中建立 ASCII `subst`/junction，并设置 JDK 21、`TEMP`、`TMP` 后运行。
- 提权会话创建的盘符映射不一定对普通会话可见；映射和 Gradle 应在同一会话执行。
- `.gradle-home/`、`.gradle-tmp/`、`.codegraph/`、`build/`、`run/`、logs、crash reports 和 heap dumps 不提交。
- `reportRepositoryHygiene` 用于开发中分类；`verifyRepositoryHygiene` 是发布前严格门禁。
- 当前工作树包含大量未跟踪源码、测试和文档。发布前必须分类、审查并形成明确提交，不能把严格卫生失败误判为编译失败。

## 11. 发布与验证经验

- 常规顺序：定点测试 -> `:common:test` -> `:neoforge:compileJava` -> 边界变化时 `:fabric:compileJava`。
- NeoForge 可能在 TinyRemapper、远程 metadata 或 Mojang mappings 下载阶段失败；要区分工具链/网络失败与 Java 编译错误。
- 发布验证使用 `:verifyReleaseArtifacts --no-daemon`，不带按需配置。
- 自动测试不能替代真实客户端：新世界 worldgen、中央原版流程、已有世界编辑、LDLib2 布局、保存和重进都需要手工冒烟。
- 发布顺序：GitHub 预发布 -> 用户整合包抢先测试 -> 修复反馈 -> Modrinth/CurseForge。

## 12. 文档决策

- 2026-07-11 起，`GOAL.md` 是目标模式唯一入口，`PLAN.md` 只保存当前执行队列。
- `ROADMAP.md` 已退役，旧 4064 默认路线不得再作为当前事实。
- `UNDERGROUND_RESEARCH.md` 是研究资料，不负责当前进度。
- 历史 review 和 `.trae` spec 保留原结论，但必须标记为历史快照。
- 后续模型固定阅读顺序见 [`docs/DOCUMENTATION_INDEX.md`](docs/DOCUMENTATION_INDEX.md)。

## 13. 2026-07-19 P4.5 uplift

- 新增 `EndTerrainUpliftRuntime`，只消费已有 `ContinentSignalBuffer` 的 edge、landness、
  inlandness、owner identity 和 corrected centre；它是 immutable、线程安全、无采样期对象分配的
  `[0,1]` scalar。中心包络采用保守的大陆 cell 半径，edge 与 coast envelope 负责把宏观抬升在
  大陆边缘明确收回。
- uplift 不是第二套大陆 ownership，也没有复制 RTF 的 `Cell`、`GeneratorContext`、water table、
  river cache 或 terracing。RTF `UpliftContinentGenerator` 只作为 MIT 纯数学语义参考；ETF 的
  runtime 通过已发布中心和 caller-owned buffer 解耦。
- `EndHeightmap` 只在 `REGION_PLANNED` 且 RTF 算法启用时把 uplift 作为 relief envelope 使用；
  legacy terrain、旧 preset、非 RTF 算法和默认世界的高度结果保持兼容。`EndDensity` 复用列缓存中
  已采样的完整大陆信号，不能为了 uplift 在每个 Y 再采样大陆。
- `EndTerrainSignalBuffer.uplift()` 是独立通道，不与 auxiliary `height` 混淆；preview 的
  `UPLIFT` 模式调用同一 heightmap runtime。它只反映宏观抬升候选，不代表地貌、侵蚀或正式方块已完成。
- 本轮自动门禁：定点测试、完整 `:common:test`（1110 项）、`:neoforge:compileJava`、
  `:fabric:compileJava` 和 `:verifyReleaseArtifacts --no-daemon` 顺序通过。真实客户端、RTF+C2ME
  同载和 JFR 仍是后续证据，不能由自动门禁代替。

## 14. 2026-07-19 P4.6 archipelago runtime

- `EndArchipelagoMask` 只移植 RTF `ArchipelagoPopulator` 的纯 `sizeNoise`、`densityNoise`、
  smoothstep 和离岸门控语义；不移植 `Cell`、海洋水位、biome、terrain type、river cache、
  客户端 import 或 RTF UI。RTF 仓库保持只读。
- `EndCoastBands` 和 `EndArchipelagoRelief` 是 ETF 末地化的 runtime 常量与低 relief，暂不进入
  preset JSON。它们通过 `EndLandmassSignalBuffer` 与 `EndHeightmap`、`EndDensity` 和 preview
  共用同一次 X/Z 信号采样。
- 群岛不创建第二套大陆 ownership。`ContinentSignalBuffer` 的大陆 identity、AREA ownership、
  RIDGE identity 和主大陆 center 保持不变；群岛 chain id 只用于诊断，不写入 preset。
- 群岛最终 landness 使用 `max(mainlandLandness, archipelagoLandness)`，最终 top 使用
  `max(mainlandTop, archipelagoTop)`，垂直体积继续由 `EndLandmassVolume` 负责。群岛不生成海床，
  不调用 `FloatingIslandsField`，不改变 legacy/default preset。
- 群岛 runtime 仅在 `OUTER_CONTINENTS + RTF_MULTI + REGION_PLANNED` 的受控实验路径启用。
  当前 `format_version=3` validator 仍拒绝持久化 `REGION_PLANNED`，因此不能把自动测试通过写成
  玩家可用功能；format v4 和真实客户端视觉/JFR 仍是后续门禁。
- 本轮定点群岛/分带/relief/preview/并发测试、完整 `:common:test`、NeoForge/Fabric 编译和
  `verifyReleaseArtifacts --no-daemon` 均通过。真实客户端、RTF+C2ME 同载和 JFR 仍待后续
  收尾验证。
- 为避免在 format v4 尚未设计完成前放宽 v3 validator，P4.6 实机验收使用非持久化 JVM
  开关 `-Dendterraforged.p46_archipelago_smoke_test=true`。它只替换未配置时的 fallback，
  不写 preset、不改变默认值；已有 GUI 发布的 preset 优先级更高。runtime 使用
  `EndPresetAccess.getOrDefault()`，玩家编辑器必须使用 `getEditableOrDefault()`，防止不受 v3
  validator 支持的开发 profile 进入 editor builder 并在鼠标事件中崩溃。创建世界和暂停菜单入口
  不因 smoke 开关隐藏；入口显示但编辑器读取安全的可持久化 fallback，只有用户点击 Done 明确提交
  普通 preset 时才覆盖 smoke runtime，Cancel 不产生覆盖。
- 2026-07-20 首次 P4.6 实机截图中的大片 `Y=0` 平面不是大陆算法或 C2ME 失效，而是 smoke
  profile 错误继承了 disabled plains/hills，导致 region planner 没有 AREA entry。任何
  `REGION_PLANNED` 开发 profile 必须至少启用一个 AREA family，并用固定 seed 高度方差测试防止
  再次退化为 reference surface。
- 2026-07-20 第二次 smoke 审查：客户端日志确认该次实际加载的是最新 jar、seed `123456789`、
  `REGION_PLANNED` 和 `archipelago=true`，但 `seaMode=NONE` 也确认了海洋未启用的直接原因。原
  smoke profile 只为避免平面而启用 plains/hills，没有把山地可见性和海洋作为验收条件；这会让
  实机观察偏向低起伏的 RTF 风格区域地貌。现将非持久化 smoke profile 明确设为 `WITH_FLOOR`，
  启用 plains/hills/plateau AREA，并给山脊使用独立的测试强度与尺度。该调整不改变
  `EndPreset.defaults()`、持久化 preset、旧世界迁移或玩家 UI；新增固定 seed 测试同时验证可见山脊
  与外海判定。后续客户端必须换新 jar 和新世界复测，旧区块不会重生成。

## 15. 2026-07-22 P4.7 侵蚀、排水与性能决策

- P4.7 固定总体顺序为 `raw top -> erosion/incision -> smoothing -> final metrics -> continuity correction -> volume`。
  raw top 必须先包含 AREA、RIDGE、uplift 和 archipelago；侵蚀不改变 continent、terrain region、AREA
  ownership 或 RIDGE identity，也不修补错误拓扑。
- local analytical runtime 是 immutable、逐列纯函数，只使用 caller-owned primitive buffer 和实现内部
  固定常量。它作为低成本 baseline/fallback，不预先视为正式算法。P4.7 统一比较 local analytical、
  RTF-derived hydraulic primitive tile、2024 stream-power analytical/multigrid、Priority-Flood +
  D8/D-infinity + stream-power，以及 bounded thermal 收尾；选择依据视觉、首块延迟、内存、分块连续性、
  访问顺序、C2ME 和 JFR。详细调研见
  [`docs/P4_7_EROSION_ALGORITHM_RESEARCH.md`](docs/P4_7_EROSION_ALGORITHM_RESEARCH.md)。
- local baseline 与所有候选都不新增 Codec/Validator/Builder/UI，不改义 v3 droplet `ErosionConfig`，不创建
  私有 worldgen executor。只有获选的正式 runtime 完成 preview、性能和客户端门禁后才能开放 v4 参数。
- `EndTerrainProfileBuffer` 的 raw top 使用归一化高度，但 slope/curvature 的导数必须先按
  `worldHeight` 换算为世界方块量纲。现有实现直接对归一化 top 求差，与 Javadoc 不一致；P4.7a 必须用
  synthetic plane/paraboloid 覆盖 Standard 与高世界，避免侵蚀强度随世界高度错误缩放。
- 首批输出只保留 final top、signed erosion delta、erosion strength、drainage potential 和 activation；
  不伪造 sediment。raw profile metrics 只作为侵蚀输入，erosion/smoothing 后的公开 surface metrics 必须
  重新计算。
- 正式接入点是 `EndDensity.ColumnCache` 的 X/Z 列刷新。final top、underside 和诊断值每列只算一次并
  复用于全部 Y；任何邻域缓存都必须 per-worker、有界、owner-aware，且缓存命中、淘汰、访问顺序和
  C2ME worker 数不能改变位级结果。
- 当前 256 项 direct-mapped column cache 对连续 16 x 16 整数列存在显著 hash collision；容量等于
  chunk 列数不等于无冲突。P4.7-0 必须先记录 hit/miss/collision/owner swap/raw-top evaluation，再比较
  chunk-local tag、set-associative 或 final immutable tile cache，禁止无测量重写缓存。
- 当前 `PerformanceBenchmarkTest` 的 5,000 次预热、50,000 次测量和平均 `ns/op` 只有 DCE guard，没有
  threshold、multi-fork、p50/p95、allocation、cache 或 JFR 门禁。它只能作为观测工具；P4.7 正式接线前
  必须建立 P4.6 smoke profile 的 cold/warm、chunk-like traversal、allocation 与四组合 JFR 基线。
- RTF droplet 可移植 gradient、inertia、capacity、erosion/deposition、evaporation 与 filter 顺序，但不能
  搬入 `Cell[]`、per-cell `int[][]/float[][]` brush、单尺寸 `WorldErosion`、私有 worldgen executor、对象池
  或 scheduled cache。ETF 候选使用 primitive SoA、canonical world-space tile/source、fixed halo 和有界
  owner-aware cache。
- Priority-Flood 负责 depression/watershed 基础，不能单独产生高质量侵蚀外观；D-infinity/flow accumulation
  和 stream-power 才提供方向与切削。2024 analytical 方法的 2D 扩展仍使用 multigrid iterative process，
  不是逐点闭式函数，必须作为 tile 候选验证。thermal/talus 只做有界收尾。
- WhiteboxTools、Landlab 和 fastscape 可用于算法/fixture 研究；fastscapelib、RichDEM、TauDEM 和 pysheds
  的 GPL 代码不能复制。SimpleHydrology/SoilMachine/SimpleErosion 未发现许可证，不复制。Immensa 虽为
  MIT 且含 Java Priority-Flood/tile hydrology，但仓库极新、目标 MC 1.21.11 且依赖 ONNX/GPU/大模型，只作
  架构参考，不作为 ETF 1.21.1 runtime 依赖或成熟度证据。
- 第一切片仅启用受控 `REGION_PLANNED`。legacy、中央保护、void、海岸、薄 shelf 和
  archipelago-dominant 列保持零影响；附属群岛在拥有独立 resistance/身份语义前不参与侵蚀。
- REGION_PLANNED preview 必须消费同一 analytical runtime。旧 `PreviewErosionGrid` 只保留为 v3 droplet
  参数的兼容预览，不得进入正式 density。详细契约见
  [`docs/P4_7_ANALYTICAL_EROSION_SPEC.md`](docs/P4_7_ANALYTICAL_EROSION_SPEC.md)。
