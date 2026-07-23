# EndTerraForged 当前执行计划

> 文档状态：当前有效，只维护当前事实、执行队列、验收和阻塞。
> 最近更新：2026-07-22。
> 长期目标见 [`GOAL.md`](GOAL.md)，长期决策见 [`MEMORY.md`](MEMORY.md)。

## 1. 当前结论

- 当前版本：`0.1.7` 开发工作树，尚未达到公开稳定版标准。
- 平台：NeoForge-first + LDLib2；Fabric 只维持低维护编译边界。
- 默认世界规格：Standard，`min_y=-256`、`height=512`。
- RTF 核心复用策略：对于 MIT 许可且能独立验证的大陆、海岸、地貌、形状化放置和噪声 primitive，优先直接移植并保留原版权头、来源说明和 `NOTICE.md` 记录；不再仅以“参考思路”替代成熟实现。ETF 继续保留末地体积、中央保护、平台边界、并发缓存和原创 UI。
- 2026-07-18 已审查 RTF 最新 `codex/r10x-volcano-rt4-fluid-routing` 工作树：火山区域 artifact、provenance、bounded cache、single-flight、生命周期、RT4 流体路由和高世界优化研究可作为 ETF 后续契约参考；RTF 工作树保持只读，不能把其未提交代码或游戏证据缺口写成 ETF 已完成功能。ETF 当前 `EndTerrainVolcanoRuntime` 仍是封闭的 analytical COMPACT 草稿，P4 先完成地表、兼容性、C2ME parity 和 Standard JFR 门禁。
- RTF 地表复用的权威实现规格见 [`docs/RTF_CORE_REUSE_RESEARCH.md`](docs/RTF_CORE_REUSE_RESEARCH.md)。`R0/R1/R2` 已完成 golden parity、显式 `RTF_MULTI` runtime、末地分带与有限 shelf 的代码闭环；但当前 `EndTerrainComposer` 仍主要是“单一选择噪声 + 通用 Perlin 层”，没有 terrain region、成熟地貌族和有限 ridge/compact feature，因此当前视觉质量只能视为原型。
- 2026-07-18 路线校正：RTF 最新地形区架构已经用预览和实机证据否决“RIDGE 拥有完整宏观区域”的 S4.2 语义。P4 的首个生产契约由正权重 `AREA` 地貌组成统一、无空洞的 terrain ownership 分区；`RIDGE` 使用独立、有界 anchor overlay，不能夺取宏观 owner。RTF 最新火山线已有较成熟候选，但 ETF 当前仍因阶段范围和末地语义冻结 `COMPACT`，不将其纳入本轮 ownership。完整结论见 [`docs/reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md`](docs/reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md)。
- P4.6 精确 jar 的新世界和 ETF/RTF/C2ME 客户端验收已闭环。当前最高优先级是 P4.7-0：先为
  `REGION_PLANNED + archipelago` 建立可重复的缓存、allocation、cold/warm、chunk traversal 和 JFR
  基线，再比较 local analytical、RTF-derived hydraulic、2024 analytical/multigrid 与
  Priority-Flood/flow/stream-power 候选。当前 legacy terrain 只作为迁移与性能基线，不继续投入大段
  视觉修补。
- 原版主岛、黑曜石柱、龙战、返回门、网关及外围区域当前冻结，不属于近期修改范围。
- 内容扩展路线已从 biome-only 升级为 ETF Worldgen Content Pack API；当前仅有规格，没有 loader/runtime。
- 地下继续使用 ETF 自研路线，不采用 RTF cave；P4 高质量地表完成前不新增洞穴功能。

## 2. 当前源码事实

### 2.1 已有主干

- `EndPreset` 已聚合大陆、地形、气候、群系布局、侵蚀和地下配置。
- 主要配置已形成 immutable record、Codec、Validator、Builder 和测试模式。
- `EndPresetRuntimeResolver` 以 Minecraft 实际 world bounds 对齐 preset。
- `EndHeightmap`、`EndDensity`、climate、river、lake、subsurface 和 floating islands 已进入 runtime 链。
- runtime density 已有按线程隔离的有界原始类型列缓存。
- preview 已覆盖地表、气候、群系、深渊和多个洞穴诊断模式。
- common 已有分页编辑器与子编辑器；NeoForge 有 LDLib2 action bar bridge。
- 创建世界编辑器使用独立 ETF 入口，不再注册 `WorldPresets.NORMAL` 的原版 `Customize` 槽位，避免与 ReTerraForged 等主世界生成器互相覆盖；仍需在同载客户端确认最终位置与窄窗口行为。
- Preset Library 领域逻辑和基础 GUI 已存在，但真实客户端清单尚未完整验收。
- common/platform 边界、语言 key 和 release artifact 已有自动门禁；2026-07-13 当前源码快照已顺序通过 `:common:test`、NeoForge/Fabric 编译和完整 `:verifyReleaseArtifacts --no-daemon`。

### 2.2 不得误报为完成

- 当前 `CONTINENTAL` 仍由 `CompleteContinent(1.0F)` 产生连续全覆盖实体，不是产品要求的多块大陆。
- `EndCentralRegionPolicy` 现将中央原版区限制在半径 `1536`，并在 `1536..2048` 平滑交接到 ETF 外部大陆。成功 bootstrap 会保留 vanilla End `final_density`；`EndDensityFunction.Bound` 在中央区委托它，浮岛 overlay 使用其下界保持 `max(main, floating)` 数学中性。
- `OUTER_CONTINENTS` 已进入 `EndHeightmap` runtime，并只在中央启动带外生效。新世界 preset 使用 `RTF_MULTI` 的确定性 cell landmass、复合 warp 与 R2 分带；显式 `LEGACY_RADIAL` 继续使用 `IslandsContinent`、`outer_continent_scale` 和有机 coast 参数，供旧 preset 与兼容测试保留。旧 JSON 缺少 coast 字段时保持 `RADIAL_LEGACY`，不静默重塑存档。
- `EndPreset.defaults()` 现为 `format_version=3` 的 `OUTER_CONTINENTS` + `FLOATING_SHELF` + `RTF_MULTI`，并显式持久化 `continent.bands`。`ContinentConfig` 的空 JSON 与历史 preset 仍解为 `LEGACY_RADIAL`；v0-v2 JSON 缺少 `continent.bands` 时使用 `LEGACY_PASSTHROUGH`，无版本 JSON 保持 `format_version=0` 的历史 `ISLANDS`，无 `volume_mode` 的 continent 保持 `LEGACY_COLUMN`，未知版本由 validator 拒绝。
- `EndTerrainComposer` 当前仍使用一个低频 selector 按权重区间选择 plains/hills/plateau/volcano 等层，并在相邻区间做 cross-fade；该路径只保留为 legacy。受控 `REGION_PLANNED` 已完成 AREA-only terrain region、多候选 AREA morphology 与独立三段曲线 RIDGE overlay，但仍缺稳定 resource key、非径向 uplift、群岛海岸、侵蚀排水和真实客户端验收。`COMPACT` 火山草稿冻结，不属于当前生产路线，因此自动测试通过不代表地貌已经达到发布质量。
- 2026-07-17 固定 seed `123`、玩家报告坐标约 `(11069, -2573)` 的巨大连接墙已定位并修复：`RtfMultiContinent` 的 shaped landness 在 raw edge 约 `0.502` 处有海岸塑形切换，`BandedContinent` 曾错误地把它直接作为 inland relief 分带输入，使相邻一格高度最多跳变约 `153.48` 格。现在外部 shelf 保留 shaped coastline，进入 rim 前以 smoothstep 平滑回接连续 raw edge；RTF golden 输出不变。新增固定坐标连续性门禁，要求该区域相邻列高度差不超过 `16` 格。此修复解决的是大陆分带信号泄漏，不代表 `REGION_PLANNED` 的完整区域地貌过渡已经完成。
- 2026-07-18 `REGION_PLANNED` 已建立统一 `EndTerrainSignalBuffer`：AREA family runtime 与正式高度采样同源输出 auxiliary height、family roughness、erosion resistance 和 terrain tag bitset；独立 RIDGE overlay 使用同一高度/preview 路径覆盖 signal，COMPACT 在 validator、Codec 与 composer 三处明确拒绝。采样使用 caller-owned primitive buffer，保持无逐列分配；analytical erosion 和 UI 调试层仍未完成。
- 中央保护尚未完成真实客户端回归；`ChunkMap` 的 direct `RandomState.create(settings, noises, seed)` 现在在调用前一次性捕获 `ServerLevel.registryAccess().asGetterLookup()`，以构造精确 vanilla End fallback。其他没有可信动态注册表的 direct 路径、fallback 构造失败或 binding 失败仍会带诊断地拒绝，而不再继续生成可能损坏中央区的空气。剩余风险是实际 NeoForge/整合包调用链回归，不是静默降级。
- ReTerraForged R10 的重型 `GeneratorContext` 只在 router 含有 RTF `CellSampler.Marker` 时启用，ETF End router 不含该标记，因而 RTF 不应在末地接管其密度语义。但两者都会触及全局 `NoiseChunk` 构造链。2026-07-13 首次实际同载使用旧 ETF jar，日志确认 ETF `@Redirect` 抢占 RTF redirect，导致 RTF `0/1` 注入失败并中断主世界区块生成。ETF 已改为优先级 `1100` 的 `@ModifyArg` 组合 chunk visitor，使参数变换先于默认优先级 redirect 且不移除原始 `NoiseRouter.mapAll` 调用；源码、单元测试、双平台编译和发布包门禁已通过，重新打包后的真实 NeoForge 同载验证仍待执行。
- `LEGACY_COLUMN` 配合 `WITH_FLOOR` 仍会在有陆地的列中填充到世界底部，用于旧 preset 兼容；新默认 `FLOATING_SHELF` 在 terrain top 与 landness 加权 underside 之间生成有限体积，不再无条件填到底部。
- `有底海洋（下方实体）` 与 `无底海洋（下方虚空）` 已接入正式外海流体：`RandomState` 持有按 seed 与不可变 `EndDensity` 绑定的 `EndOceanFluidPicker`。2026-07-17 实机在约 `(10079,1,-648)` 发现首版只按 `landness == 0` 供水，导致进入大陆投影后整列断水、有限大陆架悬在干燥空腔上；现已改为三维外海连通判定：大陆架 underside 以下属于外部水体，shelf 体积内与有底海床以下的雕刻空间仍属于洞穴。`WITH_FLOOR` 的低频海床连续延伸到大陆投影下方，`NO_FLOOR` 保持无实体海床。自动门禁已通过，修复后真实客户端新区块仍待验收。
- 新默认 preset 使用 `format_version=3`、显式 `continent.volume_mode=FLOATING_SHELF` 和 `continent.bands`。无 `volume_mode` 的历史 continent JSON 保持 `LEGACY_COLUMN`；v0-v2 缺失 bands 时保持直通，防止保存后的旧世界被静默重塑。
- `EndBiomeSource#getNoiseBiome` 当前忽略 Y，只能做二维环带与 climate variant。
- surface rule 当前主要处理顶面 `floor`，没有 Content Profile、underside 或 cave surface palette。
- Content Pack loader、profile selector、palette 和 placement anchor 尚未实现。
- 创建世界的自定义垂直规格链已接通：世界页编辑合法的“最低可建造 Y / 最高可建造 Y”，Done 后只替换当前 `WorldDimensions` 的 End stem，并同步复制 `DimensionType` 与 `NoiseGeneratorSettings` 的边界；Overworld、Nether、其他模组维度、biome source 和全部非高度字段保持不变。创建前会再次应用所选规格，防止数据包重载覆盖。已有世界继续从实际 `ServerLevel` 只读显示，禁止伪扩容。
- Standard 仍为默认 `-256..255`。当前先提供 16 格对齐的精确自定义滑块；Extended、Grand、Epic 的命名快捷按钮与独立 world-spec 持久化模型仍属于 P6。最高 Y 只提供 Minecraft 真正支持的 `...15` 值，因此界面显示值与游戏内实际建筑上限完全一致；总高度达到 1024 时显示黄色性能警告。
- 正式地下河水体、桥梁、石柱、地下生态和结构挂点尚未实现。
- C2ME、BetterEnd、Nullscape、ReimagEND 类内容和用户超大型整合包尚无版本化兼容矩阵。
- 当前工作树包含大量未提交和未跟踪开发文件，尚未达到发布仓库卫生状态。

## 3. P1 真实基线结论

固定环境：NeoForge 开发客户端、Standard、seed `7286241398135878839`、视距 12、无其他末地内容模组。

已确认：

- 实际加载的 End noise settings 为 `minY=-256`、`height=512`、`seaLevel=0`。
- `aquifersEnabled=false`，`defaultBlock=end_stone`，`defaultFluid=water`。
- ETF density 成功绑定，没有 bootstrap fallback 或 sampling exception。
- 外部坐标 `4096,-223,4096` 的目标方块为 `minecraft:end_stone`，目标流体为 `minecraft:empty`。
- 2026-07-13 实机复现：ETF 有限大陆把下方正确置为空密度后，Minecraft 的 disabled aquifer 仍调用全局流体规则，后者硬编码 `Y < -54` 为岩浆。`MixinNoiseBasedChunkGenerator` 现只对带 ETF density placeholder 的 End generator 替换为空气 picker；正式河流和熔岩必须走独立放置链。
- 初次进入末地记录一次 `8548 ms / 170 ticks behind` 的服务端峰值。
- 传送到外部新区后出现约 10 FPS 的客户端渲染峰值，加载稳定后恢复约 95 FPS；当时服务端约 3 ms/tick。
- 日志记录 `gameRenderer.level`、`gui.debug` 和 `updateDisplay` 卡帧，说明客户端 mesh/render 与 F3 图表必须和服务端 worldgen 分开测量。
- 系统随后发生强制重启，但 Minecraft 在此前已经正常 `Stopping!` 并保存；没有 JVM crash、OOM 或 NVIDIA driver reset 证据，不能单独归因 ETF。
- 外部截图出现加载完成后仍存在的直壁/截断感。2026-07-13 审查确认横向 landness 已平滑，但旧 shelf 会在 landness 接近零时保留至少 48 格厚度，且可选辅助地形未同步衰减，二者会把平滑边缘放大为实体幕墙。现已改为同一 `edgeFade` 同时收敛 underside 厚度和辅助高度；仍需在新世界或未生成区块完成视觉回归。

P1 诊断基线已经足够进入根因修复；修复后的运行时回归仍未完成。

## 4. 当前执行队列

### P2：主岛外多大陆拓扑

状态：代码、定点测试和三模块编译已通过；当前阻塞是固定 seed 的真实 NeoForge 客户端验收。P2 未验收前，不得将中央原版保护或外部大陆宣称为发布级完成，也不得跳过 P3 去扩展洞穴、主岛或 Content Pack。

- [x] 审查 `TopologyMode`、`CompleteContinent`、`IslandsContinent`、`ContinentalShatteredContinent`、`ContinentConfig` 和 preset 兼容策略。
- [x] 建立中央原版保护：常规 `RandomState.create(provider, key, seed)` 成功 bootstrap 时保留 vanilla End `final_density`，并在保护区内由 `EndDensityFunction.Bound` 委托它；浮岛 overlay 不改变 fallback 结果。
- [x] 建立纯、确定性的 `EndCentralRegionPolicy`：中央区半径 `1536`，外部大陆满强度半径 `2048`；定点测试覆盖半径边界、fallback 委托与 `max` 组合恒等性。该范围保留原版主岛和早期外岛缓冲，不再把四千格原版外岛区误判为受保护中心。
- [x] 修复 ETF 空密度被原版全局流体规则填为岩浆：仅 ETF End generator 使用空气 fluid picker，避免 `Y < -54` 的 vanilla lava fallback 填充大陆下方、虚空海峡和当前未正式实现液体的洞穴空腔。
- [x] 将海洋模式接入正式外海流体：在 `NoiseChunk.forChunk` 参数层使用可组合的 `@ModifyArgs` 读取当前 ETF `RandomState`，绑定 `EndOceanFluidPicker`；供水判定复用列缓存的 landness 与 underside，大陆外列和有限大陆架下方开放空间进水，shelf 内部洞穴不进水。`WITH_FLOOR` 使用跨大陆投影连续的低频海床，`NO_FLOOR` 不建海床。自动门禁已通过，修复后客户端验收未完成。
- [x] 为 direct `RandomState.create(settings, noises, seed)` 建立安全 provider 路径：`MixinChunkMap` 在其两个原版调用点捕获 `ServerLevel` 的动态注册表 getter-provider，`MixinRandomState` 一次性消费它以构造 vanilla fallback；没有可信 provider 时仍拒绝 ETF End 创建，binding 阶段缺 fallback/异常同样拒绝区块生成，避免 placeholder 空气。仍需完成单人世界中央流程真实回归，才可标记发布级完成。
- [x] 审查 ReTerraForged R10 主世界与 ETF 末地同载边界：RTF 的 `RandomState` 仅在其 marker router 中启用主世界上下文；首次实机加载确认旧 ETF `NoiseChunk.@Redirect` 与 RTF redirect 冲突。已将 ETF 绑定改为优先级 `1100` 的 `@ModifyArg` visitor 组合，保留 RTF 的主世界 wrapper 与 ETF 的末地 runtime binding，并新增结构回归测试防止重新引入 redirect 冲突；非 ETF End 的 `NoiseChunk` 不再把 `RandomState` 写入该绑定 ThreadLocal。
- [x] 将独立创建世界入口 `MixinCreateWorldScreen` 与布局策略 `CreateWorldPresetEditorButtonPlan` 加入 release artifact 关键类清单；2026-07-13 已完整运行 `:verifyReleaseArtifacts --no-daemon`，两套 common transform、Fabric/NeoForge remap jar、metadata 和资源清单全部通过。
- [ ] 用兼容修复后的 NeoForge jar 实测 RTF 主世界 + ETF 末地：确认没有 redirect 冲突/RTF 注入失败，主世界 RTF terrain 可生成，进入末地后 ETF `fallbackPresent=true` 且外部大陆可生成。
- [x] 保留 `CONTINENTAL` 为旧版全覆盖兼容路径，新增 `OUTER_CONTINENTS` 作为正式多大陆拓扑；P2 初版使用 `format_version=1`，P3 升级为显式 shelf，R2 已将当前新 preset 升级为 `format_version=3` + 显式 bands；旧无版本 JSON 仍保持历史 `ISLANDS`。
- [x] 实现主岛外多个宏观大陆：默认 `outer_continent_scale=4096`，利用确定性 cell centre、尺寸变化、跳过与 warp 生成约 4000-8000 格陆块和虚空海峡。
- [x] `outer_continent_scale` 已完成 Codec、Validator、Builder、编辑器滑块、runtime 与 preview 消费；旧 `continent_scale` 保持破碎大陆用途。
- [x] 完成有机海岸闭环：`coast_shape`、`coast_scale`、`coast_strength`、`coast_cell_blend` 已进入 Codec、Validator、Builder、runtime 和大陆子编辑器；它们服务于显式 `LEGACY_RADIAL` 路径，旧 JSON 缺字段保持 `RADIAL_LEGACY`。新世界默认 `RTF_MULTI` 使用其 RTF shape field 与 R2 分带；广域外大陆预览会按大陆尺度扩大取景并对代表中心做局部细化，避免将宏观大陆裁成几何椭圆；真实客户端外观回归仍待完成。
- [x] 建立 R9.3.6 `RTF_MULTI` 固定 seed/坐标 golden fixture：`RtfMultiContinentTest` 固化 root seed `123456789`、R9.3.6 参数和六个正负/远距离坐标的 `Float.floatToIntBits` 输出；测试不读取外部 RTF jar 或工作树。
- [x] 新增显式 `ContinentAlgorithm` 并移植纯 `RtfMultiContinent`：保留 R9.3.6/R9.6 的复合 warp、3x3 Voronoi、`DISTANCE_2_DIV`、shape field 和 seed 顺序；删除 `Cell`、`GeneratorContext`、river cache 与主世界 biome/terrain 写入。2026-07-15 起，新 `EndPreset.defaults()` 在 `OUTER_CONTINENTS` 下默认使用 `RTF_MULTI`，并用固定 seed 的四个主方向走廊测试保证中央保护带外一万格内可遇见陆地；旧 JSON 和显式旧 preset 仍走 `LEGACY_RADIAL`。未实现的 `RTF_ADVANCED` / `RTF_UPLIFT_EXPERIMENTAL` 会在验证阶段带明确错误拒绝。
- [x] R0/R1 自动门禁：相关定点测试、完整 `:common:test`、`:neoforge:compileJava`、`:fabric:compileJava` 与 `:verifyReleaseArtifacts --no-daemon` 均于 2026-07-14 顺序通过；真实客户端、RTF 同载、C2ME 与 JFR 验收仍未完成。
- [x] 完成 R2 末地分带与有限体积整合：`ContinentBandsConfig` 使用 `void_outer`、`shelf`、`rim`、`coast`、`inland` 五段阈值；`BandedContinent` 在不改动 `RtfMultiContinent` golden 输出的前提下输出 shelf landness 与 inland relief；`EndDensity` 的线程隔离列缓存复用同一 X/Z 的大陆信号、top 和 underside，避免每个 Y 重采样。大陆子编辑器只暴露已实现的 `LEGACY_RADIAL` / `RTF_MULTI`，并提供分带开关和五个阈值滑块，实时预览与 Done 使用同一 builder；旧 preset 只有在用户显式启用 bands 后才升级为 v3。v3 preset 必须显式提供 bands，v0-v2 缺失时使用 `LEGACY_PASSTHROUGH`。
- [>] `RTF_ADVANCED`、terrain region、地貌族、shape-aware 山脉/火山、附属群岛与 uplift 已统一移动到 P4，避免继续把“大陆算法”和“完整地表质量”拆成互不相干的补丁。
- [x] 预览在外部大陆模式下确定性选择保护带外代表性 landness 窗口，侵蚀采样同步使用该世界坐标；其他旧拓扑仍保持原点视图。
- [x] 覆盖旧 JSON 缺省迁移、custom round-trip、非法 scale、中心保护、外部大陆/虚空、参数差异和预览确定性。
- [x] 当前 P2 路径不修改、重塑或调参原版主岛及外围区域；保护区内 density 委托 vanilla fallback。
- [ ] 按 [`docs/reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](docs/reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md) 完成新世界固定 seed 的中央流程、外部大陆、直壁和加载峰值回归，并记录日志与截图证据。

验收：固定 seed 下中央保护区仍由原版 density 决定，保护区外生成多个独立宏观陆块和可见虚空海峡；不存在全图实体覆盖、固定半径墙或大面积一格平板；runtime 与 preview 的 ETF 外部 landness/inlandness 一致，预览明确不冒充渲染原版中央岛。R2 的定点测试、`:common:test`、`:neoforge:compileJava`、`:fabric:compileJava` 和 `:verifyReleaseArtifacts --no-daemon` 已顺序通过；仍需真实客户端、RTF 同载、C2ME 和 JFR 证明。

### P3：有限垂直体积与 Standard 性能

状态：配置、runtime、预览、编辑器和自动测试已完成；2026-07-13 直壁回归已通过定点、完整 common、双平台编译和发布包门禁。真实客户端视觉、JFR 性能基线和 C2ME 兼容回归尚未完成。

- [!] 2026-07-14 15:09 的历史整合包日志同时加载了 ETF、RTF 与 C2ME：ETF 记录
  `fallbackPresent=true`，日志未见 `InjectionError`、`Redirect conflict` 或 RTF `0/1` 注入失败；
  C2ME 将 ETF density 与 floating-island placeholder 记录为 `DelegateNode`。但该样本早于当前
  R2 测试 jar，且 `floatingIslandsPresent=true`、同时加载 Voxy 等渲染/预生成组件、没有 JFR，
  并出现 `2250 ms` 与 `2561 ms` 服务端停顿。因此它只能证明历史组合未立即注入崩溃，不能证明
  当前 jar 的性能或兼容性；必须按固定 seed 协议重新验收。
- [x] 定义不可变 `EndLandmassVolume` primitive：top surface、landness 加权 underside、主体/边缘厚度和 mode。
- [x] 实现 `FLOATING_SHELF`；新默认主体厚度 `160`、边缘厚度 `48`，可在 `16..2048` 格范围内调节。
- [x] 新默认不再从 top 无条件填充到实际世界底部；旧 `LEGACY_COLUMN` 仅用于旧 JSON/显式兼容配置。
- [x] 将 `EndDensity` 的 underside 放入线程隔离列缓存，避免同列每个 Y 重算平滑厚度；subsurface carve 只在有限实体体积内执行。
- [x] `TerrainPreviewMode.VOLUME` 和大陆子编辑器复用同一 runtime primitive，实时显示厚度并可编辑 mode、主体厚度和边缘厚度。2026-07-14 视觉审查先发现旧公式将 terrain top 的高频起伏平行复制到底面，剖面像噪声薄片；第一次修正又把内部底面过度简化成水平板。现已改为独立、种子确定的两层低频 underside：宏观中心仍由主体/边缘厚度控制，低频起伏幅度为当地厚度的 18%，边缘再平滑回接 top，并在 `EndDensity` 列缓存中每个 X/Z 只采样一次。`RTF_MULTI` 在编辑器中被显式选中或重置时会应用 `continent_scale=3000`、`jitter=0.7` 等 R9.3.6 推荐基线；真实客户端仍需确认大陆体量与过渡。
- [x] 增加独立 X/Z 垂直体积剖面：`LandmassSlicePreviewSampler` 直接消费无 subsurface carve 的 `EndDensity`，在 `OUTER_CONTINENTS` 中复用代表性外部观察点；大陆编辑器提供轴向和偏移控件。`RTF_MULTI + 广域` 的平面预览按 `continent_scale * 8` 取样至少两个宏观单元，不再用 `outer_continent_scale` 裁取单个大陆内部；栅格上限不变。
- [x] 修复外部大陆边缘实体直壁：有限 shelf 的厚度在 `landness -> 0` 时必须同步收敛到零，不能保留固定 `shelf_edge_thickness`；可选 plains/hills/plateau/volcano 的高度贡献使用同一 `EndLandmassVolume.edgeFade`。覆盖公式、真实 density 列与辅助地形高度回归；剖面预览因直接消费 `EndDensity` 自动保持一致。
- [x] 审查 density 热路径；continent、terrain、river、lake、cave graph 和 floating island 的整体 profiler 审查仍待真实基线。
- [x] 审查 climate、river、lake 后处理的最终高度下界：完整链始终不低于 `levels.surface`。`EndLandmassVolume` 在构造期缓存最大厚度导出的 `minimumUnderside`，`EndDensity` 仅在有限 shelf 且低于该界时于列缓存前返回；测试覆盖完整链与无缓存逐点等价，不截短 `NoiseChunk`。
- [x] 审查缓存容量、owner、seed/config key、ThreadLocal 生命周期和世界切换：`EndDensity` 与 cave graph 使用每 worker 单个弱 owner 的有界缓存，owner 切换会失效；Floating Islands 与 river 已有 owner 失效，lake 仅有可覆写 scratch；preset/climate/biome layout holders 在 server halt 清理。交错 runtime 回归测试覆盖 density 与 cave graph，后续 C2ME 实机仍需验证线程调度与长时间内存曲线。
- [ ] 按 [`docs/reviews/STANDARD_PERFORMANCE_PROTOCOL.md`](docs/reviews/STANDARD_PERFORMANCE_PROTOCOL.md) 用 JFR/Profiler 分开记录服务器 worldgen、客户端 mesh/render、GC 和资源重载，并以 shelf 模型重建 Standard 基线。
- [x] 完成 C2ME 源代码预审：density/continent/cave 只使用 seed 与坐标哈希，不消费顺序随机数；`RandomSource` 仅是 feature placement 的原版签名参数且 ETF 不读取。默认 shelf + 完整高度后处理 + cave graph 的四 worker 对照与单线程逐位一致。真实 C2ME DFC delegate、调度顺序和长时间 heap 曲线仍需在实际整合环境验证。
- [ ] 只有 profile 证明必要时才原型化最终 density 空 cell material 跳过。

性能测试固定使用游戏 6 GB、视距 12、模拟距离 8；关闭 F3 饼图和 FPS/TPS 图表。开发机结果只作基线，不替代普通玩家硬件验证。

验收：Standard 持续新区块生成不长期超过 50 ms/tick，无重复多秒停顿；相同 seed/preset 在不同区块访问顺序下结果一致。

### P4：高质量地表重建

权威规格：[`docs/RTF_CORE_REUSE_RESEARCH.md`](docs/RTF_CORE_REUSE_RESEARCH.md)。

状态：P4.0 自动门禁已于 2026-07-15 完成，真实 NeoForge、RTF 与 C2ME 短回归仍待执行。
P4.1 已完成纯数学、完整大陆信号以及受控 `EndHeightmap` / finite volume / preview
内部接线；`RTF_ADVANCED` 仍由 validator、Codec 和编辑器拒绝，尚未进入正式 preset
或默认算法。P4.4 的 RIDGE 自动门禁已完成；`EndTerrainVolcanoRuntime` 仅是尚未审查和
验证的 COMPACT 草稿。P4.0 真实回归和 P4.1 Standard/JFR 性能门禁通过前不得开放高质量模式。
P4 期间不新增洞穴、Content Pack loader、Fabric 高级 UI 或无消费者参数。

交付节奏：

- `0.1.8`：兼容与安全基线，只收束中央保护、有限 volume、空流体、RTF/C2ME 同载和
  可诊断失败；不把 legacy 地貌外观宣传为正式质量。
- `0.2.0-alpha.1`：`RTF_ADVANCED + TerrainRegionPlan`，形成自然大陆和稳定地貌区域，
  但尚不承诺完整地貌族。
- `0.2.0-alpha.2`：核心地貌族、稳定区域变体和 eligibility 完成，替换“连续抖动噪声”
  原型。
- `0.2.0-alpha.3`：有限山脉、非径向 uplift、附属群岛和海岸层级完成；火山仍为后续独立里程碑。
- `0.2.0-beta.1`：analytical erosion、排水、2D/剖面预览和编辑器闭环完成。
- `0.2.0`：Standard 性能、确定性、ETF + RTF、ETF + C2ME、ETF + RTF + C2ME 和真实
  整合包矩阵通过后发布。

P4 的实现必须先建立稳定 seed 命名空间和公共 primitive signal buffer。preview、
surface、structure 与后续 Content Pack 只能消费这些正式信号，禁止各自重算一套地貌。
`format_version=3` 永久保留 legacy；高质量 `REGION_PLANNED` 配置闭环使用
`format_version=4`。

#### P4.0：当前基线短回归

- [x] 补 `EndVoidFluidPicker` 直接行为测试：任意 X/Z/Y（含 `Y < -54`）始终返回空气，
  不返回 water/lava。
- [x] 补 `EndDensityVisitor` 双 placeholder 组合测试：同一 vanilla fallback 只经过
  downstream visitor 映射一次，terrain/floating bound 共享同一 mapped fallback 语义。
- [x] 顺序运行定点测试、`:common:test`、NeoForge/Fabric 编译和 release artifact gate，
  生成新的 NeoForge jar 与 SHA-256。
  - 2026-07-17 NeoForge jar：
    `neoforge/build/libs/endterraforged-0.1.7.jar`
    - SHA-256：
      `5438711B558C7173412DF2A54D2EC26C04EF9FCA4660C833741A0896B901FCE1`
  - 本轮生产改动包含正式外海流体选择器、有底海洋低频海床和按 `RandomState` 绑定的
    `NoiseChunk.forChunk` 流体参数组合；必须在新世界或未生成区块测试。
    - 2026-07-17 同包完成创建世界动态垂直规格：创建世界可精确调整最低/最高可建造 Y，实际 End `DimensionType` 与 noise bounds 同步更新；已有世界仍只读。
  - 2026-07-17 根据实机截图修复大陆投影整列断水：流体判定从二维外海 gate 升级为 landness + finite underside 三维连通判定；有底海床延伸到大陆下方，海床以下洞穴仍保持独立流体语义。
- [x] 对实际验证包与测试实例 RTF/C2ME 做静态兼容审查：ETF jar 使用
  `@ModifyArg(priority=1100)`，RTF R10 preview1 jar 仍使用同调用点 `@Redirect` 并继续
  调用原始 `mapAll(visitor)`；C2ME DFC 对未知 ETF density 预计使用 `DelegateNode`。
  该结论只证明组合结构合理，不替代真实区块生成，详见
  [`docs/reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](docs/reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md)。
- [ ] 使用新世界或未生成区块确认最新 underside 不再复制地表高频噪声，也不退化为水平截面。
- [>] 已修复 RTF shaped landness 切换泄漏到 inland relief 造成的一格宽巨墙，并通过固定 seed/坐标连续性测试、完整 common 测试、双平台编译和发布包门禁；仍需用新世界或未生成区块在 `(11069, -2573)` 附近完成视觉回归。
- [ ] 确认大陆边缘无固定直墙、无大面积一格平板、无填底和 `Y < -54` 整层岩浆。
- [ ] 分别以“有底海洋”和“无底海洋”在新世界/新区块确认：海水连续包围海岸并进入有限大陆架 underside 以下；有底模式大陆与连续海床连接，无底模式大陆架下方为水且没有实体海床；shelf 内部洞穴和有底海床以下洞穴未被全淹，中央保护区未被 ETF 海水改写。
- [ ] ETF + RTF 同载无 `Redirect conflict` / `InjectionError`，RTF 主世界和 ETF 末地均能生成。
- [ ] ETF + C2ME 固定坐标输出与无 C2ME 参考一致；记录 delegate 和明显停顿。
- [ ] 该回归只验证修复没有继续破坏世界，不把 legacy terrain 外观当作最终质量门禁。

#### P4.1：R3 `RTF_ADVANCED`

- [x] 已新增 `RtfAdvancedContinent`、`AdvancedContinentSignalBuffer` 与测试，
  保留两阶段 Voronoi、中垂线距离、cell size variance、中心修正和 cliff/bay 海岸逻辑；
  `ContinentAlgorithm.isImplemented()` 仍拒绝该模式，因此当前不会被正式 preset 或 UI 启用。
- [x] 已直接移植 RTF MIT `Perlin2` 的 24 方向 gradient、octave loop、stored-seed 和
  range 语义，并补齐 `Noises.perlin2` 三个工厂重载；没有用普通 `Perlin` 代替。
- [x] 为 R9.3.6/R9.6 高级大陆建立固定 seed、cell id、center、edge、distance、skip 与
  landness golden fixture；临时输出必须转换为固定断言，不保留 `System.out`。
- [x] 已审查 `NoiseMath.map` 参数顺序、size variance、中心坐标离散化和 skip 后 id 清零，
  确保与上游 fixture 逐位一致。
- [x] 已扩展 caller-owned `ContinentSignalBuffer` 与不可变 `ContinentSignals`：连续
  edge/landness/inlandness 与离散 identified/packed continent id/center 分离。
  `RTF_MULTI` 与 `RTF_ADVANCED` 提供稳定 identity；bands 只重塑连续值，中央保护为零时
  明确清空 identity，避免 wrapper 丢失或混合 ownership。
- [x] 覆盖 mapAll、重复/重排/并发采样、负坐标、超远坐标、skip 不泄漏旧状态和 cell
  边界连续性。
- [>] 已与 `RTF_MULTI` 对比边界连续性、远坐标和并发确定性，并加入同 JVM 完整 signal
  采样微基准。2026-07-15 本机一次结果约为 `RTF_MULTI 1286 ns/sample`、
  `RTF_ADVANCED 1677 ns/sample`、`1.30x`；该数字只用于发现数量级回退，不替代 allocation
  证据、完整区块 Standard p95、JFR 或 C2ME 实机。
- [x] fixture、完整 `:common:test`、NeoForge/Fabric 编译与
  `:verifyReleaseArtifacts --no-daemon` 已于 2026-07-15 顺序通过；两端最终 jar 均包含
  `Perlin2`、`RtfAdvancedContinent` 和 `AdvancedContinentSignalBuffer`，`NOTICE.md`
  已记录实际移植类。
- [x] 已完成受控 runtime 集成：`OUTER_CONTINENTS + RTF_ADVANCED` 在直接构造的测试
  preset 中装配为 `OuterContinentsContinent -> BandedContinent -> RtfAdvancedContinent`；
  `EndLandmassVolume` 与宽域 preview 使用相同 RTF tectonic scale，中央保护、finite shelf、
  0/1 density 和 runtime/preview 同源均有集成测试。
- [x] 公开门禁保持关闭：`isImplemented()` 仍为 false，Codec/validator 仍拒绝，
  大陆编辑器候选仍只有 `LEGACY_RADIAL` / `RTF_MULTI`。
- [>] P4.0 真实回归和 P4.1 完整 Standard/JFR 性能门禁通过前，不在编辑器中暴露或写出
  `RTF_ADVANCED`。

#### P4.2：R4 统一 `TerrainRegionPlan`

- [x] 新增 `TerrainLayoutMode.LEGACY_SELECTOR` / `REGION_PLANNED` 的受控 runtime/Codec/Builder
  骨架；旧字段缺省仍解码为 legacy，v3 preset 明确拒绝写入 `REGION_PLANNED`，避免 jar 更新
  静默重塑已有世界。
- [ ] 高质量路线完成配置闭环后写出 `format_version=4`；v3 永久保留 legacy layout，
  不随 jar 更新静默重塑。
- [>] 已提取 warped Voronoi region、稳定 owner id/center/edge、ownership family、边界两侧
  family、underlay family、visible family、blend 与 orientation；边界候选现额外暴露 packed cell
  id，正式稳定变体以 `regionId + entryId` 作为完整身份。
- [x] 2026-07-17 完成多尺度 region 交汇混合：不再写死“owner + 一个邻居”或固定三个候选；布局在固定 `5 x 5`、最多 `16` 个条目的搜索预算内保存候选，以平滑紧支撑 score-ratio 核筛选实际过渡集合，再归一化为 partition-of-unity 权重。候选进入/离开过渡带时权重连续，正式高度与 LAYERS 诊断消费同一布局；首批三 AREA fixture 每列最多激活 `8` 个候选。
- [x] 2026-07-18 ownership catalog 已收窄为正权重 `AREA`；AREA 继续使用多尺度紧支撑 partition-of-unity、稳定 `regionId + entryId` 和 `weight / regionScale^2` 面积补偿。RIDGE 不再参与该 catalog。
- [x] RIDGE 已改用独立、确定性、有界 anchor sampler；footprint 外逐位回到真实 AREA 结果，不再构造随机 underlay。COMPACT 已从生产 catalog 排除，并由 validator、Codec 与 composer 明确拒绝。
- [>] AREA 搜索使用固定半径 `2`，条目上限 `16`，因此最坏候选检查数为 `400`；RIDGE 搜索半径由 `maxReach / spacing` 构造期推导并限制为 `6`。两者均使用 caller-owned 固定数组、无逐列对象分配。未来 variable-radius catalog 仍需独立的最大半径与早停证明，不能直接扩大当前窗口。
- [ ] 第一阶段继续消费现有固定 terrain 配置，不同时引入任意列表 schema。
- [x] `EndTerrainRegionComposer` 使用 immutable `TerrainRegionLayout` 与 caller-owned buffer
  组织 plains/hills/plateau AREA region，再通过独立 `TerrainRidgeLayout` 叠加有限山脉；
  `EndHeightmap` 高度、分类和 preview 诊断共同消费该路径。v4 preset 与编辑器仍需等待剩余
  地表链和真实客户端门禁，不能仅凭 ownership/overlay 拆分开放。

#### P4.3：R5 核心地貌族

- [>] 首批受控 runtime 已落地：`TerrainFamilyRuntime` 直接适配 RTF 的 plains/steppe、
  hills 两种变体与 plateau 形态；它只进入 `REGION_PLANNED`，v3 preset 和玩家 UI 仍关闭。
- [x] 同一 ownership region 按派生 seed、`regionId + entryId` 和 family 选择稳定 morphology
  variant，边界混合分别读取两侧身份，禁止逐点随机换形态。
- [>] 已移植并测试 RTF 的 `Billow`、`Cubic` 与 `Terrace` 纯数学 primitive；ridge、
  curve/spline、Worley edge 与 masked erosion 仍按后续实际消费者接入，不复制 registry/Codec 外壳。
- [x] 2026-07-18 每个首批 AREA family 已通过 `EndTerrainSignalBuffer` 输出 height、roughness、erosion resistance 和 terrain tag bitset；`EndHeightmap.sampleTerrainSignals` 与正式 auxiliary height 共享同一候选混合路径。RIDGE overlay 以最强 physical influence 插值 feature 通道，COMPACT 不进入该链。`EndErosionField` 仍属于 P4.7，不得把这些输入描述为完整侵蚀。
- [x] 2026-07-18 新增 `EndTerrainProfileBuffer` 与 `EndHeightmap.sampleTerrainProfile`：显式五点采样提供归一化 raw top、slope、curvature 及中心地貌信号。该 API 不进入默认 density 热路径，也不代表 analytical erosion 已完成。
- [x] 新路径已按 region plan 选择/混合首批 AREA family；现有 `EndTerrainComposer` 保持
  `LEGACY_SELECTOR` 兼容 adapter，不再接收视觉补丁。
- [x] `EndTerrainEligibilityPolicy` 已按 landness/inlandness、有限 shelf 支撑和中央保护
  限制 REGION_PLANNED 的 RIDGE relief。拆分后该策略只缩放 overlay physical influence，
  必须始终保留真实 AREA ownership。
- [ ] 对外 family/entry 使用稳定 resource key，运行时仅编译为数组索引，禁止把列表位置
  写入存档或 Content Pack 契约。
- [ ] 固定 seed 预览和实机截图必须能明确辨认平原、高原、丘陵与山地区域，不能仍像连续抖动噪声。

#### P4.4：R6 有界山脉，火山路线冻结

- [!] 2026-07-15 的首个受控 RIDGE runtime 证明了有限、确定性、footprint 外零输出和
  runtime/preview 同源，但它依赖 RIDGE ownership region，且几何仍接近直线有限 envelope。
  该实现保留为迁移基线，不再作为 P4.4 的最终架构。
- [x] `REGION_PLANNED` 不再把 legacy 全局 mountain noise 当作基础地形，AREA underlay 与有限
  RIDGE 是唯一地貌来源，避免两套山地数学叠成连续抖动噪声。
- [x] 2026-07-15 当前工作树已顺序通过 `:common:test --rerun-tasks`、
  `:neoforge:compileJava`、`:fabric:compileJava` 与
  `:verifyReleaseArtifacts --no-daemon`。自动门禁不替代新世界、ETF+RTF、ETF+C2ME 或
  Standard JFR 的真实客户端验收。
- [>] 工作树存在 `EndTerrainVolcanoRuntime` 内部草稿。RTF 最新火山实验线已有较成熟的区域
  artifact、provenance、缓存生命周期和流体路由候选，但 ETF 仍因当前阶段范围、末地体积语义
  和独立性能门禁冻结该草稿：不开放 preset/UI，不作为本轮 `0.2.0` 地表验收项。
- [x] P4.4a 已完成 `EndTerrainEligibilityPolicy`：只按 landness、inlandness、当地 shelf
  厚度和中央保护限制 RIDGE 的 physical influence；`EndLandmassVolume` 的支撑厚度以参考
  surface 计算，不能让 ridge 用自身抬高后的 top 证明支撑。height、LAYERS preview 与诊断
  共同消费同一策略；拆分后 AREA 不支付 ridge anchor 之外的额外形态成本，continent id 与
  AREA terrain region identity 保持不变。
- [x] 已完成 AREA signal 与独立 RIDGE overlay 的局部通道混合和 profile 输入契约；COMPACT 草稿仅保留封闭纯数学测试，不进入正式配置。
- [x] P4.4b：新增独立 RIDGE anchor sampler，按 spacing band 编译 immutable catalog，使用
  caller-owned primitive scratch 和稳定 tie-break；bounded search 必须与 exhaustive oracle 一致。
- [x] P4.4c：每点最多保留三个 ridge candidate；重叠 relief 取最大值而非求和，最强 physical
  influence 决定 feature identity、roughness、erosion resistance 和 tags，catalog 顺序不得改变结果。
- [x] P4.4d：移植并末地化 RTF 三段曲线、82%-112% 宽度变化、独立 core/apron、提前端点收窄
  和有界线性端点导数。保持 footprint 外零尾部，不移植 `Cell`、对象池或 registry 外壳。
- [x] P4.4e：修正现有“RIDGE ownership outside footprint”测试，补 AREA owner 保持、Top-3、
  最大值组合、端点/边界连续性、重排/并发确定性、allocation 与 runtime/preview 同源门禁。
- [ ] 火山单列为 P4.10 之后的独立评审项：先冻结末地地质形态、有限
  footprint/underlay/volume、fixed-seed fixture 和 Standard 性能预算，再按模块评审 RTF 已成熟的
  纯数学、artifact/provenance 与流体路由契约；不得整体搬入 `Cell`、主世界 surface/river/biome
  耦合，也不得因上游已有实现跳过 ETF 真实客户端和兼容门禁。
- [x] RIDGE 的 physical influence 在 footprint 外严格为零并回到 AREA 结果，AREA ownership
  identity 保持稳定；不得产生无限尾部、天空柱或整图叠加。
- [ ] 不移植 RTF lava block、surface material、biome usage、river deflection 或 `CellPopulator` 架构。
- [ ] 完成新世界真实客户端、ETF+RTF、ETF+C2ME 与 JFR 验收后，才能将该阶段的代码闭环视为发布候选；
  当前 `format_version=4`、玩家 preset 和 UI 仍保持关闭。
- [x] 2026-07-18 自动门禁：40 项定点测试、完整 `:common:test`（144 个测试类、1104 项）、
  NeoForge/Fabric 编译和 `:verifyReleaseArtifacts --no-daemon` 顺序通过；这不替代上述真实运行门禁。

#### P4.5：R7 uplift

- [x] 新增 immutable `EndTerrainUpliftRuntime`：复用现有 caller-owned
  `ContinentSignalBuffer` 的 edge、landness、inlandness 和 corrected centre，输出独立 `[0,1]`
  scalar；不重新采样第二套 Voronoi ownership。
- [x] uplift 已作为 `REGION_PLANNED + RTF` 的宏观 relief envelope 接入
  `EndHeightmap`，并通过 `EndDensity` 列缓存复用完整大陆信号；靠近海岸由 edge、landness 和
  inlandness 三重门控回落。legacy terrain、旧 preset 和非 RTF 算法不改变原高度公式。
- [x] `TerrainPreviewMode.UPLIFT`、调色板和中英文 key 已接入，preview 与 runtime 使用同一
  uplift scalar；没有接入 water table、river cache、lake、terracing 或正式玩家配置字段。
- [x] 已补 centroid 平移不变性、中心峰值、海岸归零、范围、legacy 禁用、信号闭环和预览确定性
  测试；2026-07-19 `:common:test` 共 1110 项通过，双平台编译和 release artifact 门禁通过。
- [ ] 真实客户端视觉、RTF 同载、C2ME 和 JFR 仍未验收；在这些证据完成前，uplift 继续只属于
  高质量实验路径，不开放为新 preset 的独立可调字段。

#### P4.6：R8 附属群岛与海岸

- [x] 已将 RTF archipelago 的纯噪声语义拆成 `EndArchipelagoMask`、`EndCoastBands` 和
  `EndArchipelagoRelief`；没有迁移 `Cell`、水位、biome、terrain type、river cache 或客户端职责。
- [x] 已建立 `EndLandmassSignalBuffer`，在同一 X/Z 采样中保存主大陆 identity、群岛诊断 chain id、
  群岛 mask 和合成 landness；群岛不覆盖大陆 ownership、AREA 或 RIDGE identity。
- [x] 已生成受控的附属群岛、岛链和陆桥候选；最终 top 使用 `max(mainlandTop, archipelagoTop)`，
  群岛只在 `OUTER_CONTINENTS + RTF_MULTI + REGION_PLANNED` 实验 runtime 启用。
- [x] 群岛复用 `EndLandmassVolume`，不生成独立海床，不调用高空 `FloatingIslandsField`；`EndDensity`
  列缓存复用完整 landmass 信号和 underside，不在每个 Y 重复采样群岛噪声。
- [x] 已增加 `TerrainPreviewMode.ARCHIPELAGO`，preview 直接消费 runtime landmass buffer，
  并补齐中英文翻译和 mask/分带/relief/并发定点测试。
- [x] 中央保护区、主岛和外围冻结区在纯 runtime 门禁中保持不变；默认 preset 与 legacy 路径不启用群岛。
- [x] 增加非持久化 P4.6 NeoForge 实机验收入口：仅在 JVM 参数
  `-Dendterraforged.p46_archipelago_smoke_test=true` 下，将未配置的 fallback 替换为固定
  `RTF_MULTI + REGION_PLANNED` profile；不写 preset JSON、不放宽 v3 validator，关闭参数后普通
  默认行为完全不变。2026-07-20 首次整合包实测发现创建世界编辑器误读 runtime fallback 并触发
  v3 builder 拒绝；现已拆分 runtime 与 editable fallback。创建世界和已有世界的编辑入口保持
  显示，但编辑器只读取可持久化的安全 fallback；smoke profile 不会进入 editor builder。用户点击
  Done 后，以界面明确提交的普通 preset 为准，Cancel 则保留未配置时的 smoke runtime。
- [x] 2026-07-20 首次 smoke worldgen 截图确认群岛有限体积已出现，但主大陆退化为 `Y=0`
  平面。根因是测试 profile 直接继承 `TerrainConfig.DEFAULT` 中 disabled 的 plains/hills，使
  `REGION_PLANNED` 没有 AREA ownership。测试 profile 现显式启用 plains/hills，并增加固定 seed
  外部大陆高度差门禁；runtime 启动日志同时输出 `terrainLayout` 与 `archipelago` 激活状态。
- [x] 2026-07-20 第二次 smoke 审查确认日志中的 `seaMode=NONE` 是测试 profile 继承默认值造成的，
  不是海洋 runtime 失效；同时原 profile 没有保证固定观察范围出现足够明显的山脊。smoke profile
  现明确使用 `WITH_FLOOR`、启用 plains/hills/plateau AREA，并使用仅限 smoke 的山脊强度与尺度，
  新增固定 seed 的山地可见性和外海判定测试。`EndPreset.defaults()`、已保存 preset 和旧世界行为
  不变。
- [x] 2026-07-22 P4.6 客户端验收闭环：构建产物与测试实例 jar 均为 SHA-256
  `9BF60C8E673E1D9DDD3F101E51C4CDC55BDAE212FB4AF238AAB63FB1D2EC47D3`，用户确认该精确产物已完成
  新世界和 ETF/RTF/C2ME 组合实机测试，本轮不重复启动同一客户端。额外确认创建世界入口、已有单人世界
  入口和 editor builder 隔离正常；冗余客户端已关闭，实例恢复为 C2ME 启用、RTF disabled 的原状态。
- [ ] 当前 `format_version=3` 仍拒绝持久化 `REGION_PLANNED`，群岛尚未开放为玩家 preset 或编辑器选项；
  `format_version=4`、玩家配置开放和 P4.9 的完整 JFR/发布矩阵仍待后续切片。

#### P4.7：R9 侵蚀与排水

- [x] 已完成 [`docs/P4_7_EROSION_ALGORITHM_RESEARCH.md`](docs/P4_7_EROSION_ALGORITHM_RESEARCH.md)：
  不把 local analytical 与 RTF droplet 当作二选一；统一比较 local analytical baseline、RTF-derived
  hydraulic primitive tile、2024 analytical/multigrid、Priority-Flood + D8/D-infinity + stream-power
  和 bounded thermal 收尾。论文、仓库、许可证、C2ME 边界和性能架构已记录。
- [x] [`docs/P4_7_ANALYTICAL_EROSION_SPEC.md`](docs/P4_7_ANALYTICAL_EROSION_SPEC.md) 现在冻结的是低成本
  baseline 契约，不代表正式算法已选定；不新增 preset/UI 字段，不改义旧 droplet `ErosionConfig`，
  不建立私有 worldgen executor。
- [x] 2026-07-23 完成 P4.7a 的导数量纲前置修正：`EndHeightmap.sampleTerrainProfile` 在求 slope/curvature
  前将归一化 raw top 乘回 `EndLevels.worldHeight`，并用 Standard/1024 高世界 synthetic plane/paraboloid
  尺度不变量测试锁定契约；local analytical runtime、正式 top 接线和 benchmark 仍未完成。
- [x] 2026-07-23 建立第一块 P4.7-0 smoke-profile 观测：固定 seed `123456789`、`(8192,8192)` 的 16 x 16
  profile traversal，ordered/shuffled checksum 相同；单次本机观察为 60,385.9 与 32,033.3 ns/profile。
  结果已记录在 [`docs/reviews/P4_7_BASELINE_2026-07-23.md`](docs/reviews/P4_7_BASELINE_2026-07-23.md)，不作为性能门禁。
- [ ] P4.7-0：在改变正式 top 前建立 P4.6 smoke profile 性能基线：raw top、full column、16 x 16
  chunk-like traversal、cold/warm p50/p95、allocated bytes、raw-top evaluation、cache hit/miss/collision/
  eviction、tile peak bytes，以及 ETF/RTF/C2ME 四组合 JFR。当前 5k/50k JUnit 平均 `ns/op` 只作观测，
  不能继续充当性能门禁。
- [ ] P4.7a：先修正 `EndTerrainProfileBuffer` 导数量纲，再实现 local analytical baseline：坡度、曲率、
  ridge/valley mask、family resistance、landness/inlandness 和 thickness 门控；只进入统一 fixture 与
  benchmark，不接正式 `EndDensity`，首批不伪造 sediment。
- [ ] P4.7b：使用同一 caller-owned primitive input artifact 进行候选 bake-off：RTF droplet 改写为
  primitive SoA/canonical tile；2024 stream-power analytical 使用 multigrid tile 原型；Priority-Flood +
  D8/D-infinity + stream-power 验证 drainage/incision；bounded thermal 只作统一可选收尾。
- [ ] P4.7c：选择同时满足视觉、volume、首块延迟、内存、边界、访问顺序、C2ME 和 JFR 门禁的最小
  组合后，才对受控 `REGION_PLANNED` 接入 `EndDensity` 列缓存或 final immutable tile cache。legacy、
  中央保护、void、海岸、薄 shelf 和 archipelago-dominant 列保持零影响。
- [ ] P4.7d：REGION_PLANNED preview 消费获选的同一 runtime；旧 `PreviewErosionGrid` 只保留为
  v3 droplet 参数兼容预览，不进入正式 density。
- [ ] 固定后处理顺序：raw top -> erosion -> smoothing -> slope/curvature/void-edge metrics
  -> continuity correction -> volume；各阶段使用 primitive buffer 并可独立关闭验证。
- [ ] `raw top` 必须先汇总 AREA、RIDGE、COMPACT、uplift 和 archipelago 的最终 relief；
  侵蚀不重新选择地貌、不改变 ownership，也不得用后处理补救错误的大陆拓扑。
- [ ] smoothing 只修正局部尖峰，不得抹平 ridge、crater、plateau edge 或海岸层级。
- [ ] RTF river geometry 只改造成干谷、裂谷或悬空排水槽候选，不搬主世界 water table/`RiverCache`。
- [ ] 所有 tile 候选使用 canonical world-space key、由 stencil/lifetime/drainage context 推导的 fixed halo、
  worker-owned primitive buffer 和有界 owner-aware cache；禁止用访问顺序产生 source 或创建私有 executor。
- [ ] 固定 seed、缓存碰撞/淘汰、不同区块访问顺序和 C2ME 多 worker 结果逐位一致；任一 tile 候选都要
  额外证明相邻 tile border 逐位一致，不能只要求 hydraulic 通过。

#### P4.8：R10 preview 与编辑器

- [ ] 新增 continent edge/landness/inlandness、terrain region/family、eligibility、
  ridge/compact feature、uplift、archipelago、slope、sediment 和 erosion 调试层。
- [ ] 2D、高度着色和 X/Z 剖面消费同一 runtime primitive。
- [ ] 实现共享 `PreviewGenerationScheduler`：immutable snapshot、generation id、低清/高清
  重采样、最后成功帧、有界队列、关闭取消和渲染线程 texture upload。
- [ ] preview worker 与正式 worldgen worker 隔离；默认只使用 1-2 个低优先级线程，
  不与 Minecraft/C2ME 争用整机逻辑处理器。
- [ ] 参数按大陆、区域规划、地貌族和结构化特征拆分子编辑器；不复制 RTF UI。
- [ ] 拖动低清、松手高清、过期任务取消和低配关闭高成本预览继续有效。

#### P4.9：R11 性能、兼容与默认候选

- [ ] 分别记录 legacy、`RTF_MULTI`、`RTF_ADVANCED` 和完整 region-planned 组合的 JFR。
- [ ] 高世界不使用预测 surface 或固定 margin 截短 `NoiseChunk`；只有最终 density cell 能严格证明空且流体安全时才评估 material fast path。
- [ ] 记录 noise generation p50/p95、allocation/chunk、MSPT、GC、客户端 mesh/render 与首次资源加载。
- [ ] ETF + RTF、ETF + C2ME、ETF + RTF + C2ME 固定版本矩阵通过。
- [ ] 只有视觉、性能、确定性和兼容门禁同时通过，才讨论新世界默认切换。
- [ ] 旧 v3 preset 始终保持原大陆算法、volume 和 `LEGACY_SELECTOR`，不随 jar 更新静默重塑。
- [ ] 正式 worldgen 不创建 ETF 私有 executor；辅助 preview/tile worker 同时受 CPU、heap
  与有界队列限制。

验收：主岛外能看到多个自然大陆与虚空海峡；大陆内部由可辨识的 AREA 地貌区域和有限山系组织，群岛与海岸有层级，侵蚀不会破坏 volume；Standard、RTF、C2ME 和客户端门禁全部通过。火山不属于本阶段验收。

### P5：ETF Worldgen Content Pack API v1

权威规格：[`docs/CONTENT_PACK_SPEC.md`](docs/CONTENT_PACK_SPEC.md)。

- [ ] 冻结 Content Pack、Content Profile、fallback 和 dependency schema。
- [ ] 实现资源扫描、schema 校验、registry key/tag 解析和诊断汇总。
- [ ] 建立 immutable 3D selector，消费 x/y/z、climate、surface depth、terrain tags 和 surface kind。
- [ ] 新增稳定 `ClimateRegionPlan`，输出 region id/center/edge、temperature、moisture 和
  macro variant；高地/火山可使用 terrain anchor 采样同一气候场。
- [ ] profile/terrain/palette 使用稳定 resource key；加载期编译为数组索引和 bitset，
  不把列表下标当作持久化身份。
- [ ] biome/profile 不兼容时使用固定数量、固定顺序的有界 climate nudge；禁止无界候选缓存。
- [ ] 内置 `endterraforged:vanilla` fallback。
- [ ] 实现 profile 到 registered biome 的映射；多个 profile 可复用同一 biome holder。
- [ ] 实现 top、underside、cave floor、cave ceiling 和 void edge palette 条件。
- [ ] 冻结 `SurfaceContext`：ownership/visible family、terrain tags、feature influence、
  slope、curvature、erosion、sediment、drainage、landness/inlandness 和 climate。
- [ ] 实现 placed feature 引用与 profile placement filter。
- [ ] 提供 terrain region center、ridge crest/endpoint、volcano crater/flank、
  plateau interior/edge、coast/void edge 的版本化 placement anchors。
- [ ] runtime 完成后接 2D/剖面调试叠加，再接编辑器 pack 摘要。
- [ ] 验证资源重载、缺失依赖、fallback 环、确定性和 C2ME 并发读取。

验收：纯数据包可以定义基础 Content Profile；缺失第三方资源时世界仍加载且回退明确；热路径不解析 JSON/YAML 或查询磁盘。

### P6：兼容原型与世界规格

- [ ] 先完成一个只引用已注册 biome/feature 的主流末地模组兼容包原型。
- [ ] 按 [`docs/TERRA_CONTENT_COMPATIBILITY_RESEARCH.md`](docs/TERRA_CONTENT_COMPATIBILITY_RESEARCH.md) 制作 ReimagEND 类主岛外内容适配原型。
- [ ] ReimagEND 原型不接 Terra density、dragon island、dragon pit 或中央 buffer。
- [ ] 不在 ETF core 中复制 GPL 配置、结构或资源。
- [>] 创建世界已能把自定义 bounds 同步应用到 End `dimension_type + noise_settings`；后续仍需把 world spec 从普通 terrain preset 字段中独立出来，并增加命名规格快捷选择。
- [>] Standard 保持默认；合法自定义范围已经可用，Extended/Grand/Epic 的命名按钮、性能预算和产品化验收仍未完成。
- [x] 总高度 1024 及以上显示黄色性能警告；已有世界从实际 `ServerLevel` 只读显示并禁止伪扩容。

### P7：自研宏大地下系统

- [ ] 按巨型洞厅、深渊洞口、地下河、长距离网络、多层洞穴、桥梁/石柱的顺序收束。
- [ ] 每一层严格按 runtime -> preview -> UI 接入。
- [ ] 正式液体和结构挂点必须有独立 runtime，不得复用 preview-only mask 充数。
- [ ] 对 Standard/Extended 做性能、边界、种子稳定性和视觉测试。
- [ ] 与 C2ME 并行生成做专门验证。

### P8：发布、整合包与兼容矩阵

- [ ] 完成 Preset Library 真实客户端清单。
- [ ] 建立 C2ME、BetterEnd、Nullscape、结构模组、资源包/光影和用户整合包版本矩阵。
- [ ] 完成长时间新区块生成、重载、存档回归和缺失兼容包测试。
- [ ] 按 [`docs/reviews/WORKTREE_INTEGRATION_REVIEW_2026-07-13.md`](docs/reviews/WORKTREE_INTEGRATION_REVIEW_2026-07-13.md) 收束当前工作树：审查 221 个未跟踪文件的归属，确保 36 个未跟踪 common 发布关键源码和 3 个 NeoForge 客户端关键源码进入可复现的版本历史，并处理 tracked-but-ignored 的旧崩溃文件状态。
- [x] 当前源码快照已顺序通过 common、NeoForge、Fabric 和 release artifact 门禁。
- [ ] 收束未跟踪/已跟踪忽略文件后通过严格仓库卫生门禁；当前报告仍有 221 个未跟踪发布相关文件和 1 个 tracked-but-ignored 旧崩溃文件。
- [ ] GitHub 预发布后进入用户整合包抢先测试，再发布 Modrinth/CurseForge。

### P9：原版主岛与外围区域（最后阶段）

- [ ] 只有 P2-P8 稳定后才能启动。
- [ ] 默认目标是维持原版主岛、黑曜石柱、龙战、返回门和网关行为。
- [ ] 先建立独立 seed 回归、龙战流程和结构兼容测试，再评审最小整合方案。
- [ ] 可选 ETF 主岛重做不得阻塞首个稳定版本。

## 5. 当前禁止事项

- 不修改原版主岛或外围区域。
- 不新增洞穴功能或继续堆无消费者参数。
- 不继续用更多通用噪声层、slider 或全局 overlay 修补 `LEGACY_SELECTOR` 的视觉质量。
- 不把尚未完成定点测试、preview 语义、完整构建和客户端回归的 COMPACT/RIDGE 草稿写成
  完成功能或暴露给玩家 preset。
- 不先做 UI 再补 runtime。
- 不继续深度优化错误的全图实体拓扑。
- 不实现完整 Terra generator、YAML、表达式或 TerraScript 解释器。
- 不复制 ReimagEND GPL 内容到 ETF core。
- 不进行没有 profiler 证据的高风险 worldgen Mixin。
- 不新增 Fabric 官方高级 UI。
- 不清理、重置、覆盖、stage 或提交来源不明的工作树修改。

## 6. 固定验证顺序

1. 相关定点测试。
2. `:common:test`。
3. `:neoforge:compileJava`。
4. 触及 common 或平台边界时执行 `:fabric:compileJava`。
5. 发布阶段执行 `:verifyReleaseArtifacts --no-daemon`，禁止 `--configure-on-demand`。
6. `git diff --check`。
7. 仓库卫生报告和发布前严格门禁。
8. 重要 worldgen、UI 和兼容改动进行真实客户端验证。

Windows 中文路径异常时按 [`AGENTS.md`](AGENTS.md) 使用 ASCII `subst`/junction 与 JDK 21。同一工作树禁止并行运行 Gradle。

## 7. 当前风险

- 多大陆和 vertical volume 是结构性改动，必须先定义旧 preset/worldgen 迁移策略。
- P4 会替换当前地貌编排架构；必须保留 `LEGACY_SELECTOR` 迁移路径，不能让已有 v3 世界随 jar 更新改变未生成区块的地貌区域。
- 当前 RTF 开发分支的 variable region、shape-aware placement 与火山 artifact 已形成有价值候选，
  但 ETF 只能按具体来源版本、fixture、末地语义和性能结果分模块接入，不能整套默认启用。
- 当前工作树很脏，修改前必须区分已有用户/历史改动。
- 客户端卡顿包含 render/debug overlay 成分，不能只看总 CPU 或单一 MSPT。
- Content Pack 的 surface depth、palette 执行方式和 pack 持久化尚未冻结。
- Terra 官方不支持 NeoForge，ReimagEND 为 GPL-3.0 且 WIP；兼容必须通过 ETF 内容适配，不得承诺原包直接运行。
