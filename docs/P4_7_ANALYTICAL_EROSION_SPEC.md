# P4.7 Analytical Erosion 技术规格

> 文档状态：当前有效；低成本 analytical baseline 的 test-only runtime 已实现，正式算法尚未选定，尚未接入 production density/preview。
> 最近更新：2026-07-26。
> 当前阶段：P4.6 客户端验收完成后，P4.7 候选基准中的低成本对照实现。
> 算法选型与性能架构见 [`P4_7_EROSION_ALGORITHM_RESEARCH.md`](P4_7_EROSION_ALGORITHM_RESEARCH.md)。

## 1. 目标

P4.7 保留一个低成本、逐列纯函数的 analytical erosion baseline。它只修改已经完成 AREA、
RIDGE 和 archipelago 合成后的 raw top，不重新选择大陆、terrain region、family 或 feature
ownership，也不尝试用后处理修复错误拓扑。

固定流水线为：

```text
raw top
-> analytical erosion
-> optional smoothing
-> final slope/curvature/void-edge metrics
-> continuity correction
-> EndLandmassVolume
```

该 pipeline 是低成本 baseline 的固定顺序，不预先宣称它是 P4.7 的最终正式算法。smoothing、最终
metrics、排水几何和 hydraulic tile 按后续候选切片评审；任何获选组合仍须保持
`raw top -> erosion/incision -> smoothing -> final metrics -> continuity correction -> volume` 的总体顺序。

P4.7 在接正式 density 前先比较本 baseline + bounded thermal、RTF-derived hydraulic primitive
tile，以及 bounded Priority-Flood + flow accumulation + stream-power。2024 analytical/multigrid
因缺少成熟、可验证的 Java 21/Minecraft 实现而降为研究储备，只在前三条均无法达标时恢复。
算法选择依据视觉、首块延迟、内存、分块连续性、访问顺序、C2ME 和 JFR，而不是来源偏好。

## 2. 项目与验证来源

- 项目：Architectury 多模块，Minecraft 1.21.1，Mojang mappings，Java 21。
- 平台：NeoForge-first；`common` 不依赖 NeoForge、Fabric 或 LDLib2。
- MCP：CodeGraph 与 minecraft-modding 可用。本规格的调用链、缓存和 blast radius 由 CodeGraph
  核对；Minecraft 版本、加载器和映射边界已由 workspace-aware minecraft-modding 检查确认。
- 源码：`EndHeightmap`、`EndTerrainProfileBuffer`、`EndTerrainSignalBuffer`、`EndDensity`、
  `TerrainPreviewSampler` 和旧 `ErosionFactory`。
- 只读参考：RTF `WorldFilters`、droplet `Erosion` 和相关 filter 顺序；不修改 RTF 仓库。
- 研究依据：`RTF_CORE_REUSE_RESEARCH.md` 4.14、4.17、4.20。

## 3. 当前实现事实

1. `EndHeightmap.getTerrainHeight(...)` 已在 raw top 中合成 AREA、RIDGE 和 archipelago；
   climate、river 与 lake 位于其后。
2. `EndTerrainProfileBuffer` 已提供 raw top、slope、curvature、roughness、erosion resistance 和
   terrain tags，且使用 caller-owned mutable buffer。
3. `EndDensity.ColumnCache` 以 X/Z/seed 和 runtime owner 为键，每个 worker 使用 256 项有界
   direct-mapped cache；top、underside 和 ocean floor 每列只刷新一次并复用于全部 Y。
4. 旧 `Erosion` / `ErosionFactory` 是可变 droplet tile 原型，目前只有 `TerrainPreviewSampler` 的
   `PreviewErosionGrid` 使用。其参数已进入 v3 Codec、Builder 和 UI，不能改义为新 analytical runtime。
5. 旧 preview 会分配 `Cell[]` 并运行独立 droplet 数学，不能作为 P4.7 正式 runtime。
6. `EndAnalyticalErosionRuntime` 与 caller-owned `EndAnalyticalErosionBuffer` 已实现，并在 canonical
   fixture、确定性、零影响边界和性能观测中使用；当前没有 production `EndDensity` 或 preview caller。

## 4. 第一切片边界

### 4.1 启用范围

baseline 只接受受控 `REGION_PLANNED` 输入，并使用实现内部固定常量；候选比较完成前只在测试和
benchmark harness 中启用：

- 不新增 Codec、Validator、Builder、preset JSON 或 UI 字段。
- 不开放 `format_version=4`。
- 不在候选比较前接入正式 `EndDensity` top。
- 不修改 `LEGACY_SELECTOR`、旧 v3 preset、已保存世界或默认 preset 的输出。
- 不删除、不重命名、不重解释旧 droplet erosion 字段。

### 4.2 明确不做

- 不实现 hydraulic droplet、tile cache、sediment transport 或质量守恒模拟。
- 不输出伪造的 sediment 值；首批 sediment 语义保持未实现。
- 不接 RTF water table、`RiverCache`、biome、surface material 或 `GeneratorContext`。
- 不创建 ETF 私有 worldgen executor。
- 不新增洞穴、液体、Content Pack 或 UI 功能。

## 5. Runtime API

新增 immutable、线程安全的 `EndAnalyticalErosionRuntime`，以及 caller-owned、非线程安全的
`EndAnalyticalErosionBuffer`。建议的最小内部 API 为：

```java
void apply(
        EndTerrainProfileBuffer profile,
        float landness,
        float inlandness,
        float outerActivation,
        float availableThicknessBlocks,
        boolean archipelagoDominant,
        EndAnalyticalErosionBuffer output);
```

`EndAnalyticalErosionBuffer` 第一版只保留：

- `top`：侵蚀后的归一化 top。
- `erosionDelta`：`top - rawTop`，首批必须小于等于零。
- `erosionStrength`：`[0,1]` 诊断标量。
- `drainagePotential`：`[0,1]` 的局部 valley/drainage 候选，只用于诊断，不直接挖槽。
- `activation`：中央保护、landness、inlandness 和 thickness 门控后的最终强度。

runtime 不持有可变 scratch、tile、集合或 world 引用。调用方拥有输入和输出 buffer；任何缓存只是
可丢弃的性能层，不能改变结果。

## 6. 导数量纲

raw top 保持 `EndLevels` 的归一化高度，但坡度和曲率必须先换算为世界方块高度：

```text
heightBlocks = rawTop * worldHeight
dx = (eastBlocks - westBlocks) / (2 * sampleDistanceBlocks)
dz = (southBlocks - northBlocks) / (2 * sampleDistanceBlocks)
gradient = sqrt(dx * dx + dz * dz)
slope = gradient / (1 + gradient)

laplacian = (eastBlocks + westBlocks + southBlocks + northBlocks - 4 * centreBlocks)
            / (sampleDistanceBlocks * sampleDistanceBlocks)
curvature = laplacian / (1 + abs(laplacian))
```

当前 `sampleTerrainProfile()` 直接对归一化 top 求差，和其 Javadoc 声明的“方块高度/方块”不一致。
P4.7a 必须先修正量纲，并用 synthetic plane/paraboloid 测试证明 Standard 与高世界使用相同物理斜率。
该修正不得在 analytical runtime 启用前改变 legacy worldgen top。

raw profile 的 slope/curvature 是侵蚀输入。侵蚀和 smoothing 后对外公开的 final metrics 必须重新
采样，不能把 raw input metrics 冒充最终 surface metrics。

## 7. Analytical 语义

第一版使用固定半径五点 raw top stencil，不引入随机数：

- `ridgeMask` 由负曲率和 mountain terrain tag 派生，用于保护 crest，避免把有限山脊削平。
- `valleyMask` 由正曲率派生，用于 `drainagePotential`，首批不直接降低谷底。
- flank erosion 由 slope、roughness、`1 - erosionResistance` 和 activation 共同决定。
- erosion 只允许降低 top；最大切削同时受固定 block 上限、当地高出 reference surface 的高度和
  `availableThicknessBlocks` 限制。
- 输出必须 finite，并 clamp 到合法世界高度；continuity correction 只处理 NaN/Infinity、越界和
  浮点边界，不创造新地貌。

内部常量在 P4.7a 由固定 seed fixture 锁定，不进入 preset。后续需要玩家参数时，必须完整补齐 Codec、
Validator、Builder、runtime、preview 和测试，不能直接复用旧 droplet 字段。

## 8. 零影响边界

以下条件必须输出 `top == rawTop`、`erosionDelta == 0`：

- analytical runtime disabled 或 terrain layout 不是 `REGION_PLANNED`。
- `outerActivation == 0` 的中央 vanilla 保护区。
- void 或不可见 landness。
- coast/void-edge 门控尚未达到安全下限。
- finite shelf 的可用厚度不足以保留安全体积。
- archipelago top 主导的列；第一切片不侵蚀附属群岛，避免误用 mainland family resistance。

中央启动带只能使用同一个 `outerActivation` 平滑门控，不能新增硬半径墙。海岸、薄 shelf 和群岛的
零影响门禁必须有边界两侧定点测试。

## 8.1 Bounded thermal 对照契约

P4.7b 的第一条候选只建立无缓存、固定成本的 thermal/talus 对照，不接 production density：

- 固定为 4 邻域、2 次同步 pass 和 2-cell halo；不得由 preset、机器负载或 worker 数改变。
- 输入为 canonical primitive grid：source top、landness、inlandness、outer activation、
  erosion resistance、available thickness、保护 mask 与 archipelago-dominant mask。
- 每个 pass 只从当前高度网格读取，把超过固定 talus 的材料移动量累加到独立 delta 网格，再一次性发布
  下一高度网格；禁止原地更新导致扫描方向改变结果。
- 每个源格的输出材料使用固定 block 上限和 thickness budget；向 4 个较低邻格按超出 talus 的比例分配，
  最后一个接收者吸收浮点余量，使源格扣减量与接收量一致。
- `outerActivation == 0`、void、coast/thin shelf、保护 mask、厚度不足和 archipelago-dominant 格不参与
  输出或接收；保护边界两侧都必须有定点测试。
- resistance 只衰减材料输出，不改变 ownership 或输入信号。canonical ridge crest 与 plateau edge 使用
  同一 resistance channel 证明它们不会被无条件抹平；isolated spike 必须出现有限、守恒的松弛。
- runtime immutable、stateless、thread-safe；全部高度、delta 和剩余输出预算由 caller-owned primitive
  buffer 持有。首切片不创建 tile、cache、executor、集合或每次调用对象。
- 成本测试记录同一 fixture 的 `ns/output cell`、primitive scratch bytes 和 checksum，不设置跨机器硬阈值，
  也不把 scratch bytes 误报为后续 tile candidate 的 peak artifact bytes。

只有该对照在 flat/plane、spike、ridge、plateau、coast/thin shelf、archipelago、重复顺序和多线程测试中
通过，才进入 canonical tile substrate；它本身不具备排水能力，也不预先成为正式获选算法。

2026-07-26 第一切片已按上述契约实现。一次完整 common 测试中的本机 JDK 21 观测为
`19.1-19.3 ns/output cell`、`17,424` primitive scratch bytes 和预热后当前线程 `0 bytes/apply`；这些数字只描述
无缓存 canonical fixture 调用，不代表 tile peak、C2ME worker、客户端、整机 allocation 或正式区块成本。

## 8.2 Canonical primitive tile substrate 契约

P4.7b 的第二切片只建立候选共用的 tile 输入、key、worker cache 和测量边界，不实现 hydraulic、
Priority-Flood、flow accumulation 或 stream-power：

- tile key 必须按值包含 algorithm id/version、world seed、runtime fingerprint、world bounds、terrain
  version、canonical tile X/Z、sample width/height、halo samples 和 sample distance。key 不使用对象 identity、
  worker id、请求序号或区块访问顺序。
- canonical tile origin 由 `floorDiv(block, coreSizeBlocks)` 计算，负坐标与正坐标使用同一数学分区；core
  block size 由 `(sampleSize - 2 * halo) * sampleDistance` 唯一决定。
- immutable input artifact 使用 primitive SoA，第一版固定包含 source top blocks、landness、inlandness、
  outer activation、roughness、erosion resistance、available thickness、ridge influence、AREA family、
  terrain tags 和 mask bits。mask bits 至少区分 erosion-protected 与 archipelago-dominant。
- artifact 构建器只能把全新数组的 ownership 转交给 artifact；发布后不得保留可写引用。artifact 只暴露
  按 index/XZ 的 primitive getter、稳定 checksum 和精确 primitive bytes，不暴露内部数组。
- harness cache 固定为 worker-owned、非线程安全、有界 fully-associative cache；owner 由稳定 runtime
  fingerprint 判断，owner 变化时整表清空并记录 owner swap。该布局只用于候选测量，不预选 production cache。
- cache 记录 request、hit、miss、successful build、eviction、owner swap、current/peak resident primitive
  bytes 和 builder-reported peak primitive bytes。build 失败不得发布半成品。
- 多 worker duplicate builds 由 test-only 外部 ledger 按稳定 key 汇总为 `successful builds - distinct keys`；
  首切片只测量重复，不引入 shared single-flight、executor、scheduled cleanup 或跨 worker mutable cache。
- cold build 与 warm hit 分开记录 p50/p95；allocation 测量必须排除 fixture iterator、输出格式化和 ledger
  自身分配。substrate 的输入 artifact bytes 不得冒充后续 hydraulic/flow 候选的 peak artifact bytes。
- 1/2/4/6 worker、正负 tile 坐标、不同请求顺序、eviction 和 owner swap 都必须保持每个 key 的 checksum
  逐位一致。相邻 tile border continuity 只有实际算法写入输出 channel 后才作为候选门禁，substrate 不伪造。

该 substrate 仍不得接入 `EndDensity`、preview、preset 或 UI。只有实际 RTF-derived hydraulic SoA tile 与
Priority-Flood/flow/stream-power tile 建立后，才分别记录它们的 scratch、peak artifact、duplicate build、
border bits 和 cold/warm 成本。

2026-07-26 substrate 已按上述契约实现。canonical 33 x 33 input tile 为 `44,649` primitive bytes，16-slot
harness cache peak resident 为 `714,384` bytes；本机两次 common 测试记录 cold build p50/p95
`0.032-0.050/0.217-0.314 ms`、warm hit p50/p95 `1.7/5.9-12.5 us`、cold `44,960 bytes/build` 和 warm
`0 bytes/hit`。4 个共享 key 的 1/2/4/6 worker duplicate builds 为 `0/4/12/20`，checksum 相同。以上仍是
substrate-only 证据，不包含任何 hydraulic、routing、incision、border output 或 production cache 成本。

## 8.3 RTF-derived hydraulic SoA tile 契约

2026-07-26 已完成第一种实际 tile 候选，仍只用于测试和 benchmark：

- runtime immutable、线程安全；builder 与四个 `float[]` scratch 由 worker 独占，输出 artifact 为五个
  `float[]`：final top、signed delta、erosion strength、drainage potential 与 activation。
- 固定 RTF R9.3.6/R9.6 的 135 droplets/source chunk、lifetime 12、water/speed `0.7`、erosion/deposition
  `0.5`、inertia `0.05`、gradient weight `0.95`、capacity `4.0/0.01`、gravity `3.0`、evaporation
  `0.01` 与 radius-4 brush；reference height 固定为 256 blocks。
- halo 固定为 `lifetime + brushRadius = 16` samples。128/256 block core 分别形成 64 x 64 与 96 x 96
  sample tile；droplet source、插值位置和 `floorDiv` 分区使用全局 sample 坐标。
- droplet path 只读取 immutable source top；侵蚀、沉积和 flux 同步累计，单元格最终 cut/fill 在发布时按
  activation、`1 - erosionResistance`、12-block 上限与 thickness budget 统一限幅。该设计避免共享可变
  预算把 tile 外 droplet 顺序传播到重叠 core。
- 中央保护、void、无 AREA owner、低 landness、薄 shelf、保护 mask 与 archipelago-dominant 单元严格
  零影响；输出不伪造 sediment 或 water profile。

自动门禁已覆盖 frozen constants/RNG/golden、flat、ridge、plateau、basin、watershed、coast/thin shelf、
archipelago、finite/budget、sediment transport accounting、正负坐标、一个 256 core 与四个 128 core 的
五通道逐位一致，以及 generic cache 的 owner swap、eviction、failed build、request order、1/2/4/6 worker
checksum 和 duplicate builds。固定 watershed checksum 为 `4281940154564766763L`。

本轮多次 JDK 21 synthetic 测量：128 core cold p50 `4.404-5.626 ms`、p95 `5.375-22.903 ms`，warm
p50 `0.8-2.7 us`、p95 `3.3-19.2 us`；output `81,920` bytes、scratch `65,536` bytes、build peak
`315,392` bytes、16-slot resident `1,310,720` bytes、6-worker 估算 `8,260,776` bytes。256 core cold
p50 `5.210-8.654 ms`、p95 `6.739-10.428 ms`、output `184,320` bytes、scratch `147,456` bytes、build
peak `709,632` bytes。128 core cold allocation 为 `250,512 bytes/build`，warm hit 为 `0 bytes`；固定
workload 为 2,027/4,633 droplets、22,556/52,819 steps、977,936/2,323,187 brush writes，core/halo
steps 为 6,468/16,088 与 25,645/27,174，stationary/boundary/lifetime stops 为 `0/376/1651` 与
`0/539/4094`。该结果不设置跨硬件阈值，不替代客户端、C2ME 或 JFR。

## 9. 接入与缓存

最终获选算法的正式接入点是 `EndDensity.ColumnCache.refresh()` 所消费的 heightmap top 路径：

1. 复用当前列的 `EndLandmassSignalBuffer`。
2. 计算最终 raw top 和 raw profile。
3. 运行 analytical erosion，得到 final top。
4. 使用 final top 计算 `EndLandmassVolume.underside(...)`。
5. 把 final top、underside 和需要的诊断标量写入同一列缓存，全部 Y 复用。

禁止在 `EndDensity.density()` 的每个 Y 分支重新采样邻域。若五点 raw top 成本需要缓存，只能使用
per-worker、有界、owner-aware、精确 X/Z/seed key 的 primitive cache；缓存命中、淘汰、访问顺序和
worker 数不得影响位级结果。

在接入前必须记录当前 256 项 direct-mapped column cache 的 hit、miss、collision、owner swap 和
raw-top evaluation 数。缓存容量等于一个 16 x 16 chunk 的列数不代表无冲突；是否改成 chunk-local
tag、set-associative 或 final tile cache 由测量决定，不凭推测替换。

## 10. Preview 契约

- `REGION_PLANNED` preview 必须调用与正式 worldgen 相同的 `EndHeightmap` / analytical runtime。
- 旧 `PreviewErosionGrid` 只保留为 legacy v3 droplet 参数的兼容预览，不得进入正式 density。
- P4.7a 不新增编辑器控件；测试可直接采样 `erosionStrength` 与 `drainagePotential`。
- P4.8 再增加 erosion/drainage/final slope 调试层，并复用本规格的 primitive 输出。

## 11. 测试与门禁

baseline 与候选台至少覆盖：

1. synthetic flat、plane、ridge、valley、plateau edge 和 isolated spike。
2. Standard 与高世界的物理量纲一致性。
3. disabled、legacy、中央保护、void、coast、薄 shelf 和 archipelago dominant 零影响。
4. erosion delta 有界、finite、只减不增；crest 与 plateau edge 不被抹平。
5. 固定 seed golden fixture、重复采样、坐标重排和多线程逐位一致。
6. cache collision、owner swap、world reload 和不同访问顺序一致。
7. runtime/preview 同源；REGION_PLANNED 不经过旧 droplet builder。
8. `EndDensity` final top 与 underside 使用同一侵蚀结果，volume 不出现直壁、负厚度或无限尾部。
9. 热路径在 ThreadLocal/cache 初始化后零对象分配；记录相对 P4.6 baseline 的列刷新和区块生成开销。
10. cold/warm p50/p95、allocated bytes、raw-top evaluation、cache collision/eviction 和 tile peak bytes。
11. 同一 input artifact 下的 local analytical + thermal、RTF-derived hydraulic 和
    Priority-Flood/flow/stream-power 视觉与性能对照；2024 multigrid 只保留研究记录。

验证顺序：定点测试 -> `:common:test` -> `:neoforge:compileJava` -> `:fabric:compileJava` ->
`:verifyReleaseArtifacts --no-daemon` -> 新世界真实客户端 -> ETF/RTF/C2ME 矩阵 -> JFR。

## 12. 实现切片

1. **P4.7-0A automated baseline**：建立 P4.6 smoke profile 的 raw top、full column、chunk-like traversal、
   cache counters 和 current-thread allocation 基线，不改变正式地形；当前已完成。
2. **P4.7-0B client/JFR baseline**：按 ETF、ETF+C2ME、ETF+RTF、ETF+RTF+C2ME 采集服务器/客户端
   JFR。它可与 test-only candidate 编码并行，但 selection 和 production integration 必须等待其闭环。
3. **P4.7a local analytical baseline**：修正导数量纲，新增 immutable analytical runtime 与
   caller-owned output，只跑纯单元测试和统一 fixture，不接正式 top。
4. **P4.7b candidate bake-off**：bounded thermal、canonical tile substrate、RTF-derived hydraulic SoA
   tile 与 bounded Priority-Flood + adaptive flow + stream-power 已完成 test-only 自动门禁。bounded flow
   契约见 [`P4_7_BOUNDED_FLOW_EROSION_SPEC.md`](P4_7_BOUNDED_FLOW_EROSION_SPEC.md)。下一步进行同图视觉
   审查与 P4.7-0B JFR；2024 analytical/multigrid 只在现有候选均失败时恢复。
5. **P4.7c selection/density integration**：选择满足视觉和性能门禁的最小组合，只对受控
   `REGION_PLANNED` 接入列缓存，完成 volume 与零影响门禁。
6. **P4.7d preview/parity**：REGION_PLANNED preview 改为同源 runtime，legacy droplet preview 保留。
7. **P4.7e final metrics**：在 erosion/smoothing 后计算 final slope、curvature 和 void-edge metrics。
8. **P4.7f dry drainage geometry**：只接有界干谷、裂谷或悬空排水槽几何，不接水体，不发布
   receiver/reach/bed/water authority；完整水文由
   [`P5_3D_HYDROLOGY_ARCHITECTURE_PLAN.md`](P5_3D_HYDROLOGY_ARCHITECTURE_PLAN.md) 管理。

## 13. 完成定义

P4.7 只有在算法 bake-off、获选 runtime、preview、列/最终 tile 缓存、确定性、C2ME、volume、客户端
视觉和性能证据全部完成后才可标记完成。local analytical baseline 通过不等于正式算法已选定；技术规格、
编译或单元测试通过都不能替代真实客户端验收。
