# 高世界与大型地形生成优化审查

> 文档状态：当前有效的技术审查，不是功能完成声明。
> 最近更新：2026-07-12。
> 参考资料：只读审查 `ReTerraForged/docs/R10_TALL_WORLD_GENERATION_OPTIMIZATION_RESEARCH.md`、本地 Minecraft 1.21.1 映射字节码和 ETF 当前源码。
> RTF 仓库不得由 ETF 开发任务修改；本文件只记录可供人工反哺的结论。

## 1. 结论

ETF 不应通过预测地表最高点、固定安全余量或缩短 `NoiseChunk` 高度来优化大型世界。这些方案无法覆盖结构 beardifier、数据包替换、第三方 density 修改和未来实体体积扩展，低估一次就会生成平顶、截断洞穴或区块接缝。

ETF 的执行顺序应为：

1. 保留完整的实际 `NoiseSettings` 垂直范围和原版 `NoiseRouter.mapAll(visitor)` 包装链。
2. 先做数学等价、配置不变量和热路径零分配优化。
3. 用固定 seed 的 JFR/Spark 数据确认 density、material rule、浮岛、洞穴或方块写入中的真实瓶颈。
4. 只有 profile 证明空中 material loop 仍占主导时，才原型化“最终 density 已缓存后的精确空 cell 跳过”。
5. 先把 Noisium 当作伴生模组和对照组；没有 profile 证据时不自研 palette 直写路径。

正确生成是性能基线的一部分。少生成、截断生成或改变 seed 输出不能算优化。

## 2. ETF 当前状态

### 2.1 已具备

- `MixinNoiseChunk` 先用 `EndDensityVisitor` 绑定 ETF 叶节点，然后继续执行原版 `router.mapAll(originalVisitor)`。ETF 没有 RTF 历史上的 raw-router 绕过问题，原版 interpolation/cache marker 生命周期仍保留。
- runtime 始终使用 Minecraft 实际加载的 `minY/height`；Standard 默认为 `-256..255`，不再按旧 preset 请求扩张生成范围。
- `EndDensity`、浮岛、洞穴图和河流几何缓存均有界并按线程隔离；owner/seed/config 切换边界已经显式处理。
- `EndRiverMap`、`EndTerrainComposer` 和 density 后处理已移除已确认的临时 record 与重复 landness 采样。
- 当前 End noise settings 禁用 aquifer 和 ore vein，这使未来的精确空 cell 原型比 RTF 的通用 Overworld 路径更容易证明，但仍必须在最终 density/structure beardifier 之后判断。

### 2.2 本轮已实现

1. `final_density` 从 `clamp(add(end_density, floating_islands), 0, 1)` 改为 `max(end_density, floating_islands)`。
   - ETF 主 density 的契约是离散 `0/1`，浮岛场范围是 `0..1`，两种表达式逐点等价。
   - Minecraft 1.21.1 字节码显示 `ADD.fillArray` 会创建第二个 `double[]`；`MAX.fillArray` 在目标数组上逐项组合，不创建该数组。
   - 同时移除一层不再需要的 `clamp` 节点。
   - 真实 ETF 两个 Bound 函数的 3D 采样等价性与资源 JSON 结构均有测试。
   - 如果未来主 density 改成连续正值，必须先重新证明该等价关系，不能沿用当前结论。
2. `LEGACY_COLUMN` 的 `NONE/NO_FLOOR` 在参考 surface 以下会先返回虚空，再访问列缓存。
   - 这是旧列式大陆与 `SeaMode` 组合的精确不变量，不是预测地表上界。
   - 它仍服务于历史 preset 与显式兼容配置；当前 Standard 新默认是 `FLOATING_SHELF`，不得把这条旧路径的收益写成默认性能结论。
   - `FLOATING_SHELF` 的实体下界取决于包含 climate/river/lake 后处理的最终 terrain top 和 landness 加权厚度。原始 terrain、climate、river、lake 均已证明保持 `levels.surface` 下界，因此 `EndLandmassVolume` 可缓存最大厚度导出的 `minimumUnderside`；`EndDensity` 只在低于该精确全局界时于列缓存前返回。
   - `WITH_FLOOR` 同样不使用该快路径，因为底层是否实体仍取决于 landness 和地下 carve。

## 3. 候选优化分级

### P0：真实基准与可观测性

在修改高风险 Mixin 前，固定以下矩阵：

- Standard 512：新默认 `OUTER_CONTINENTS + FLOATING_SHELF`，分别测试地下关闭、只开深渊和洞穴开启；历史 `LEGACY_COLUMN` 只作为兼容对照。
- 浮岛关闭/开启。
- ETF 单模组、Noisium 对照、C2ME 对照；NeoForge 为主体验，Fabric/C2ME 保持兼容验证。
- 未来 world spec 完成后再加入 1024、2048、4064，不能用只改 preset 的伪高世界测试。

每组使用新世界、相同 seed/preset/坐标，JVM 预热后记录：

- 每区块 noise generation 中位数和 p95。
- chunks/s、MSPT、CPU 栈、allocation/chunk、GC 和峰值 live heap。
- density 调用、列缓存命中/未命中、浮岛横向扫描、洞穴图查询和垂直 cell 数。
- 固定坐标方块输出、结构、洞穴、地表与区块边界一致性。

计数器只能使用 primitive/LongAdder 类结构，默认关闭；不能在每次 density sample 记录日志或写集合。

### P1：低风险热路径

- 根据 JFR 决定是否把 `EndDensity`、浮岛和洞穴的 `ThreadLocal.get()` 下沉为 `NoiseChunk`/Bound 持有的 sampler。只有证明 Bound 不会被跨 worker 并发调用后才能移除 ThreadLocal。
- `EndDensity` 与 cave graph 已收束为每 worker 单个有界缓存，以弱 owner 识别 runtime；owner 变化会失效，避免 C2ME 长生命周期 worker 在世界切换后积累 per-runtime `ThreadLocal` 值。此模式不替代 C2ME 实机的线程调度和 heap 曲线验证。
- 缓存只由 immutable config 决定的倒数、平方半径、垂直支撑范围和 enabled 标志。
- 审查 `fillArray` 是否可批量消费 ETF 的 2D 列数据；自定义叶节点无法被 C2ME DFC 编译时，应提供稳定 delegate 路径，不伪造 density bounds。
- 继续用字节码/JFR 检查正式 `compute/apply` 路径的 record、数组、装箱和异常对象分配。
- `FLOATING_SHELF` 的低 Y 早退已完成：climate、river、lake 均保持 `levels.surface` 下界，构造期缓存的 `minimumUnderside` 只用于 shelf 自定义叶节点，不缩短 `NoiseChunk`。未来新增 height post-processor 必须保留该下界；否则删除该早退并重新建立逐点等价基线。

### P2：精确空 cell material 快路径

只在原版 `CacheAllInCell` 已填充完整 4x8x4 最终 density 后判断：

- 所有值必须非正。
- structure beardifier、blending 和第三方 density 结果必须已经进入数组。
- 当前 ETF aquifer 关闭时，可以原型化跳过 128 次 material/aquifer/block-state 检查。
- aquifer 开启或无法证明无流体时必须走原版循环。
- 实现必须可关闭，并在未知 router、Mixin 冲突或数据包替换时自动回退完整路径。

这是侵入性 Mixin，只有相同输出且高世界至少获得可重复的显著收益才接受。Standard 不得出现超过 5% 的稳定回退。

### P3：方块写入与 palette

先测试 Noisium 是否兼容并测量收益。只有 profile 证明方块写入仍是主要瓶颈，且 Noisium 无法覆盖 NeoForge 主体验时，才考虑 ETF 独立实现。

自研路径必须同时维护 non-empty、fluid、random tick、heightmap、lighting 和 post-processing 状态，并主动检测 Noisium/Lithium/其他同点 Mixin。不得复制 Noisium 源文件。

### P4：认证垂直计划

暂缓。ETF 已知 top surface、浮岛有限垂直支撑和“洞穴只减实体”等信息，但结构 beardifier、blending、数据包和第三方修改仍会使生成级垂直截断不安全。

这些边界只能用于 ETF 自定义叶节点内部的精确早退，不能直接缩短整个 `NoiseChunk`。如果未来实现，必须识别完整 router 签名、覆盖所有正 density 来源，并永久保留 full-height fallback。

## 4. C2ME 与大型整合包

- ETF 自定义 `EndDensityFunction.Bound` 与 `FloatingIslandsFunction.Bound` 对 C2ME DFC 属于未知叶节点，预期走 delegate；不能假设 C2ME 会自动编译 ETF 内部噪声。
- 对当前测试环境的 C2ME `0.4.0-alpha.0.113` 反编译确认：`McToAst` 只硬编码识别一组原版 density 类型及少数内置 Tectonic 绑定，未知类型统一创建 `DelegateNode`，没有可供外部模组稳定调用的通用扩展 SPI。
- `DelegateNode` 的单点路径会经 `InvocationShim` 调用 `DensityFunction.compute`；批量路径会调用 `DensityFunction.fillArray`。ETF Bound 继续实现 `SimpleFunction`，因此批量路径保留原版 `ContextProvider.fillAllDirectly` 与 ETF 的线程隔离列缓存，不需要为消除 warning 重写生成数学。
- 未经 JFR 证明不得为 C2ME 内部 AST 类新增依赖、反射或 Mixin。若 delegate 确认是 Standard 的主要热点，候选方案只能是 NeoForge optional bridge，且必须保持 common 无 C2ME 依赖、C2ME 缺失时零行为差异，并先完成 fixed-coordinate parity。
- 每个 Bound/runtime 必须 immutable；scratch 只能 worker-owned 或 ThreadLocal，不共享可变坐标缓存。
- 生成结果不能依赖区块调度顺序、线程 id 或连续随机数流。
- 兼容验证必须比较单线程参考与 C2ME 并行结果的固定坐标方块/heightmap，而不只是检查“不崩溃”。
- 当前 density/continent/cave 仅使用 seed 与坐标哈希；四 worker 的默认 shelf、完整高度后处理和 cave graph 采样已与单线程逐位一致。`RandomSource` 只作为 feature placement 的原版方法参数存在，ETF climate filter 不读取它。该预审不替代实际 C2ME DFC delegate、长时间生成和 heap 曲线验证。
- 优化 Mixin 与 C2ME、Noisium、ModernFix 的注入点冲突必须有启动日志和保守回退。

## 5. 禁止方案

- 2D 预扫高度加固定 `128/256/512` margin。
- 用 `DensityFunction.minValue/maxValue` 作为局部 section 空间证明。
- 只采样 cell 角点后推断整个非线性 density cell。
- 为性能静默关闭洞穴、结构、流体、浮岛或第三方 density。
- 以旧世界已生成区块、预览图或墙钟飞行观感代替新世界 JFR/Spark 基准。
- 默认启用 2048/4064 高世界，再要求普通玩家承担线性垂直成本。

## 6. 可人工反哺 RTF 的结论

RTF 仓库保持只读；反哺事项的状态、优先级和 RTF 验收门禁统一维护在
[`ETF_TO_RTF_IMPROVEMENT_TRACKER.md`](ETF_TO_RTF_IMPROVEMENT_TRACKER.md)。本节只保留本次性能
审查的技术证据，主要对应 `FB-001`、`FB-005`、`FB-006` 和 `FB-007`，不再单独表示迁移状态：

1. Minecraft 1.21.1 的 `DensityFunctions.Ap2.fillArray` 中，`ADD` 会分配与目标等长的第二数组，而 `MIN/MAX/MUL` 使用目标数组逐项计算。可以建立 density graph 代数审查，但每次替换都必须先证明值域与逐点等价，RTF 当前复杂洞穴/矿脉组合不能直接套用 ETF 的 `add -> max`。
2. 配置不变量驱动的叶节点内部早退，比预测整个 `NoiseChunk` 的最高 Y 更容易证明。例如某一功能在已知 Y 带必为常量时，应在进入昂贵 sampler 前返回，但不能忽略结构、aquifer 或其他正 density 来源。
3. Candidate A 的方向与 ETF 已有做法一致：自定义绑定应发生在原版 per-chunk visitor 之前，随后必须继续原版 `mapAll` 包装链。
4. 精确空 cell 跳过应基于最终缓存数组，并将 aquifer/未知 router 作为强制 fallback 条件。

## 6.1 外部性能研究补充（2026-07-18）

RTF 最新外部资料审查记录见其工作树中的 `docs/references/EXTERNAL_WORLDGEN_PERFORMANCE_RESEARCH_2026-07-18.md`。ETF 侧吸收以下结论：

1. 性能基准必须拆分 `fillFromNoise`、surface、features、structure、biome、ETF tile、C2ME/非 C2ME，并同时记录 wall-clock、线程时间、peak heap、allocation/chunk、固定 seed 方块 hash 和并发效率。外部项目公开的 `chunks/s` 只能作为方向性参考。
2. `quick-noise` 的规则 3D grid、批量接口和 tile ABI 可作为未来纯 3D 洞穴或离线预览候选；不能替换 ETF 的大陆、河流、气候、火山宿主 DEM 或主 `NoiseRouter`。第一步只能做 Java 21 CPU 参考 benchmark，包含 JNI/拷贝、cache hit/miss 和 raw-bit parity。
3. 按 Y section 并行只允许作为独立实验。不能创建第二套固定线程池、嵌套 `parallel()`、每个 section 重建完整 `NoiseChunk`，也不能与 C2ME 的 chunk 调度重复竞争。正式 worldgen 仍由 Minecraft/C2ME 所有线程调度。
4. Bye-Pregen 的 post-processing 排序、去重和边界流体保护值得在 Minecraft 1.21.1 重新审计后做 profiling；不得直接移植 1.20.1 的 `PalettedContainer` 解锁或 heightmap 快路径。
5. C2ME compiler shield 可以借鉴“错误必须可见”的诊断方式，但不能直接复制为遇到 ETF 就抛异常的 Mixin。ETF 应先建立 C2ME DFC 版本矩阵，再决定诊断、配置引导或保守回退。
6. QUICK_V2 的版本化 program、固定 tile、caller-owned buffer、unsupported-root fallback 和关闭协议值得研究；它改变宿主 2D 高程、跨 SIMD 可能产生不同输出，并且缺少完整 Minecraft chunk/C2ME/跨加载器证据，因此不能作为 ETF 透明性能后端或默认值。

许可边界：MT Chunk Generation 的源码为 GPL-3.0，不能复制到 ETF；Bye-Pregen 为 MIT，quick-noise 为 MIT/Apache-2.0，但许可兼容不等于 API、版本和行为兼容。所有外部实现仍需独立审计、保留来源并验证 ETF 的确定性和平台边界。

这批研究不会改变 ETF 当前优先级：先完成固定 seed、RTF/C2ME 同载、Standard JFR 和边界连续性基线；之后才允许洞穴 tile、后处理或 section ownership 进入独立 opt-in 实验。GPU、native、Java 25、火山地质模拟和正式地下水文不得与同一轮性能改动混合。

## 7. 验收门禁

- 数学优化：逐点等价测试、资源结构测试、完整 common 与双平台编译。
- sampler/cache 优化：确定性、跨实例 owner、跨 seed、并发顺序和无界增长测试。
- Mixin 优化：固定 seed 方块 parity、结构/洞穴/流体验收、NeoForge/Fabric/C2ME/Noisium 日志与 JFR。
- 高世界优化：无平顶、无截断、无区块缝；只比较生成结果正确的构建。

没有真实客户端与 profiler 证据前，只能称为“候选优化”或“自动测试通过”，不能宣称 Standard 卡顿已解决。
