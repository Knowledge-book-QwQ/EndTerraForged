# P4.7 侵蚀、排水与性能方案调研

> 文档状态：当前有效，算法选型调研；不代表 runtime 已实现。
> 最近更新：2026-07-22。
> 调研时间点：仓库、许可证和维护状态均以 2026-07-22 的上游事实为准。
> 实现契约见 [`P4_7_ANALYTICAL_EROSION_SPEC.md`](P4_7_ANALYTICAL_EROSION_SPEC.md)。

## 1. 结论先行

ETF 不应在“完整照搬 RTF droplet”与“只做五点局部 analytical”之间二选一。两者解决的
问题不同：RTF hydraulic erosion 能产生明显的冲刷与沉积外观，但 tile、对象布局、线程池和缓存
不适合直接进入 ETF；当前五点 analytical 方案成本低、确定性强，却无法构造大陆尺度的汇流树和
有方向的河谷。

当前推荐路线是先建立同一输入、同一视觉 fixture、同一性能预算的候选基准，再采用分层组合：

```text
raw top + ownership/thickness/protection masks
-> macro drainage candidate (Priority-Flood + D-infinity / stable graph)
-> bounded incision candidate (stream-power analytical or RTF-derived hydraulic tile)
-> bounded thermal/talus relaxation
-> final metrics and continuity correction
-> EndLandmassVolume
```

短期实现顺序调整为：

1. 先修正 profile 导数量纲并建立 P4.6 性能基线、缓存指标和统一 tile benchmark。
2. 保留现有五点 analytical 作为低成本 baseline 和明确 fallback，不预先宣布它是最终算法。
3. 对 RTF-derived hydraulic、2024 analytical/multigrid、Priority-Flood + flow accumulation +
   stream-power 三条候选做同图对比。
4. 只有候选同时通过视觉、边界、访问顺序、C2ME、首块延迟、内存和 JFR 门禁，才接入
   `EndDensity` 的正式列刷新。

因此，P4.7 下一步确实是侵蚀，但第一道工程任务不是立刻移植某套侵蚀，而是建立能淘汰错误方案的
基准与性能证据。

## 2. 项目事实与验证来源

### 2.1 项目边界

- Architectury 多模块，Minecraft 1.21.1，Mojang mappings，Java 21。
- NeoForge-first；`common` 不依赖 NeoForge、Fabric 或 LDLib2。
- 正式 worldgen 不创建 ETF 私有 executor，不依赖 GPU、native runtime 或在线模型。
- 同一 seed、preset 和 world spec 的输出必须与区块访问顺序和 worker 数无关。
- 原版中央区、主岛、龙战、黑曜石柱、返回门和网关继续冻结。
- RTF 仓库只读；MIT 纯数学可以按项目规则移植，保留版权和来源。

### 2.2 本轮工具与事实来源

- CodeGraph：核对 `EndHeightmap.sampleTerrainProfile`、`EndDensity.ColumnCache`、
  `PerformanceBenchmarkTest` 和本地 RTF `Erosion` 的调用链与实现。
- minecraft-modding MCP：确认 workspace 解析为 Minecraft 1.21.1、NeoForge 21.1.219、
  Mojang mappings；本轮不需要新的 Minecraft API 或 Mixin 结论。
- OpenAlex、Crossref 与 HAL API：核对论文标题、DOI、摘要和开放版本。
- GitHub MCP/API：核对仓库存在性、默认分支、许可证、近期 push 和代码入口。
- 用户实测：P4.6 精确 jar 已完成客户端与 ETF/RTF/C2ME 组合验收；本轮不重复客户端测试。

网页更新时间或 GitHub 仓库的 `updated_at` 不能代表代码最近提交时间；维护状态判断优先使用
`pushed_at`、默认分支内容和许可证文件。

## 3. ETF 当前性能问题

### 3.1 profile 会放大 raw top 成本

`EndHeightmap.sampleTerrainProfile()` 当前先采样一次 terrain signals，再分别采样 centre、east、west、
south 和 north 五个 raw top。进入 `REGION_PLANNED` 后，每个 raw top 都可能包含大陆、AREA region、
Top-3 ridge、uplift 和 archipelago 数学。直接在每个 density 列调用该 API，等价于把高质量地貌链
至少重复五次。

此外，当前 slope/curvature 直接对归一化 top 求差，没有先乘 `worldHeight`；这会使 Standard 与高世界
的相同物理坡度产生不同侵蚀强度。该问题必须先修正，不能通过调侵蚀常量掩盖。

### 3.2 256 项 direct-mapped 列缓存并非无冲突

`EndDensity` 当前每线程维护 256 项 direct-mapped cache，并用 X/Z float bits 与 seed 的 hash 直接
选择一个槽。它能很好地复用同一列的多个 Y，但对一个完整 16 x 16 chunk 并非一列一槽。

按当前 hash 对连续 16 x 16 整数列做静态检查，seed `42` 时典型结果只有约 151-166 个唯一槽，
即单 chunk 已发生约 90-105 次映射冲突，最拥挤槽包含 5-6 列。这不是实测 hit rate，但足以证明
“容量刚好等于 256”不等于“一个 chunk 无冲突”。侵蚀再引入四邻域查询后，冲突和重复计算风险会继续
增加。

候选优化必须先测再改：

- 记录 hit、miss、collision、owner swap 和每列 raw-top evaluation 次数。
- 比较当前 hash、按 `(blockX & 15, blockZ & 15)` 的 chunk-local tagging、两 bank 和小型
  set-associative 方案。
- 若最终采用 tile，缓存 final immutable tile，而不是只缓存单列并重复生成邻域。
- 缓存只是性能层；任何 eviction 或访问顺序都不得改变输出 bits。

### 3.3 当前 benchmark 是观测，不是门禁

`PerformanceBenchmarkTest` 使用 5,000 次预热和 50,000 次测量，只打印平均 `ns/op`：

- 没有失败阈值或同进程相对基线。
- 没有多 fork、p50/p95、首块 cold latency 或 allocation 结果。
- 没有 cache hit/miss/collision、tile build 次数和 owner 生命周期指标。
- 默认 preset benchmark 不是 P4.6 smoke 的完整 `REGION_PLANNED + archipelago` 链。
- JUnit 微基准不能替代实际 `NoiseChunk`、C2ME 调度、GC、客户端 mesh/render 或 JFR。

结论是用户对“当前优化还不尽人意”的判断成立。现有测试适合发现数量级灾难，不足以阻止 15%-50%
的持续 worldgen 回归。

## 4. 论文候选

### 4.1 2024 physically-based analytical erosion

Tzathas、Gailleton、Steer 与 Cordonnier，
[Physically-based Analytical Erosion for Fast Terrain Generation](https://doi.org/10.1111/cgf.15033)，
Computer Graphics Forum 2024；[HAL 开放记录](https://hal.science/hal-04525371)。

论文从 stream-power law 的解析解出发，把时间变成控制地貌年龄的函数参数，避免传统物理模拟的
数千步迭代；同时纳入 landslide 和 hillslope process。重要限制是：从 1D 扩展到 2D 仍使用
multigrid accelerated iterative process。它不是可在任意 X/Z 独立求值的五点公式。

对 ETF 的意义：

- 视觉和地质一致性潜力最高，适合高质量 CPU tile 候选。
- 可把 uplift、年龄、侵蚀抗性组织成清晰信号，比无方向局部削坡更符合 ETF 地貌架构。
- 必须先验证 canonical tile、halo、multigrid 边界条件、首块延迟和 worker 重复计算。
- 本轮没有找到作者公开的对应实现仓库，当前只能依据论文独立实现，不可假设已有可复制代码。

### 4.2 hydrology-first procedural terrain

Génevaux 等，
[Terrain Generation Using Procedural Models Based on Hydrology](https://doi.org/10.1145/2461912.2461996)，
TOG 2013；[HAL 记录](https://hal.science/hal-01339224)。

它先构造分层 drainage network 几何图，再求 watershed、河流类型和轨迹，最后用 terrain/river
patch 与 carving/blending 合成连续地形。它提示 ETF 不必让“河流”来自局部曲率猜测；排水图可以是
地貌生成的上游结构。

对 ETF 的意义：

- 与现有 `TerrainRegionPlan`、有限 ridge anchor 和未来 placement anchors 很契合。
- 可把 stable continent/region id 用作排水 graph 命名空间，构建跨 chunk 的确定性河谷。
- 论文面向输入 domain 和 construction tree，仍需重新设计为任意区块访问与有界局部搜索。
- GitHub 上的少量第三方 Python 复现成熟度低，不作为实现来源。

### 4.3 uplift + stream-power graph

Cordonnier 等，
[Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion](https://doi.org/10.1111/cgf.12820)，
Computer Graphics Forum 2016；[HAL 记录](https://inria.hal.science/hal-01262376)。

该方案从 uplift map 构建包含 elevation 与 flow 的全域 stream graph，使用 stream-power equation，
再以 landform kernels 合成 DEM。它能直接组织 dendritic river、watershed 和 mountain ridge。

对 ETF 的意义：

- ETF 已有独立 uplift 信号，这是比纯 droplet 更自然的长期方向。
- 最有价值的是“宏观 graph 决定排水，局部 kernel 决定剖面”的分层，而非复制全域数据结构。
- 全域 graph 不能直接用于无限 Minecraft 世界；需要按 continent/canonical region 生成稳定子图，
  并证明相邻 region 的 outlet 协议一致。

### 4.4 Priority-Flood

Barnes、Lehman 与 Mulla，
[Priority-Flood: An Optimal Depression-Filling and Watershed-Labeling Algorithm](https://doi.org/10.1016/j.cageo.2013.04.024)，
Computers & Geosciences 2014；[开放预印本](https://arxiv.org/abs/1511.04463)。并行扩展见
[Parallel Priority-Flood](https://doi.org/10.1016/j.cageo.2016.07.001) 与
[arXiv 1606.06204](https://arxiv.org/abs/1606.06204)。

Priority-Flood 的强项是 depression filling、watershed 和稳定排水预处理。它本身不会生成 RTF 式
冲刷和沉积外观，也不能凭一个有限 tile 知道无限上游 catchment。ETF 应把它看作 drainage foundation，
不是完整侵蚀器。

### 4.5 D-infinity flow direction

Tarboton，
[A New Method for the Determination of Flow Directions and Upslope Areas](https://doi.org/10.1029/96WR03137)，
Water Resources Research 1997。

D8 便宜、离散和易于固定 tie-break，但会产生明显八方向偏置；D-infinity 通过连续坡向把流量分配给
相邻接收者，排水图更自然。ETF 候选 benchmark 应同时保留 D8 作为低成本 oracle/fallback，并比较
D-infinity 的视觉收益是否值得额外分支和浮点成本。

### 4.6 O(n) implicit stream-power solver

Braun 与 Willett，
[A Very Efficient O(n), Implicit and Parallel Method to Solve the Stream Power Equation](https://doi.org/10.1016/j.geomorph.2012.10.008)，
Geomorphology 2013。

该工作证明在已知 drainage ordering 上可以高效求解 fluvial incision。它降低的是 stream-power
求解成本，不负责自动解决 ETF 的无限世界分块、sink/outlet 和 tile halo。适合作为 Priority-Flood +
flow graph 之后的 incision 核心候选。

### 4.7 hydraulic 与 thermal 参考

- Musgrave、Kolb 与 Mace，
  [The Synthesis and Rendering of Eroded Fractal Terrains](https://doi.org/10.1145/74334.74337)，
  SIGGRAPH 1989：经典 erosion/deposition 基础，但不是现代性能或分块答案。
- Mei、Decaudin 与 Hu，
  [Fast Hydraulic Erosion Simulation and Visualization on GPU](https://doi.org/10.1109/PG.2007.15)，
  2007；[HAL 记录](https://inria.hal.science/inria-00402079)：shallow-water、velocity、sediment
  transport 很有参考价值，但正式服务器 runtime 不能要求 GPU。
- Krištof 等，
  [Hydraulic Erosion Using Smoothed Particle Hydrodynamics](https://doi.org/10.1111/j.1467-8659.2009.01361.x)，
  2009：视觉强但粒子和邻域成本过高，不进入 P4.7 runtime shortlist。
- thermal/talus relaxation：只让超过休止角的材料局部下移，适合作为 1-3 次、固定半径、有界的
  收尾 pass；它不能替代排水或 hydraulic incision。

## 5. 开源仓库审查

| 仓库 | 许可证/状态 | 有价值部分 | ETF 边界 |
| --- | --- | --- | --- |
| 本地 ReTerraForged | MIT，同维护方 | droplet 视觉、参数语义、filter 顺序 | 可移植纯数学；必须重写对象布局、tile、缓存和线程模型 |
| [WhiteboxTools](https://github.com/jblindsay/whitebox-tools) | MIT，Rust，活跃 | depression、flow、watershed 的成熟 GIS 算法与测试思路 | 可做 source-level 参考；不引入 native/Rust runtime，移植前逐文件审查 |
| [Landlab](https://github.com/landlab/landlab) | MIT，Python/Cython，活跃 | flow routing、stream power、diffusion、landscape model fixture | 研究和离线 oracle；不作为 Java runtime 依赖 |
| [fastscape](https://github.com/fastscape-lem/fastscape) | BSD-3-Clause，Python，活跃 | 高层 landscape evolution 组合与科学验证 | 可参考模型/fixture；其核心 fastscapelib 是 GPL-3.0，不能复制 |
| [fastscapelib](https://github.com/fastscape-lem/fastscapelib) | GPL-3.0，C++/Python，活跃 | 高性能 flow routing、incision、diffusion | 只能参考论文与公开算法，不复制代码 |
| [RichDEM](https://github.com/r-barnes/richdem) | GPL-3.0，C++ | Priority-Flood、flow accumulation 实现 | 不复制；使用 Barnes 论文独立实现 |
| [TauDEM](https://github.com/dtarb/TauDEM) | 仓库含 GPLv3，C++/MPI，活跃 | D8/D-infinity、watershed、并行 DEM 工具 | 不复制；使用 Tarboton 论文独立实现 |
| [pysheds](https://github.com/pysheds/pysheds) | GPL-3.0，Python，活跃 | watershed 与 flow accumulation 验证 | 不复制；只作外部结果 oracle |
| [UnityTerrainErosionGPU](https://github.com/bshishov/UnityTerrainErosionGPU) | MIT，最后代码 push 2020 | GPU shallow-water + hydraulic + thermal 数据流 | 不用于正式 CPU/server runtime；可参考 SoA/pass 拆分 |
| [SimpleHydrology](https://github.com/weigert/SimpleHydrology) | 未发现许可证，最后代码 push 2023 | 水文网络、湖泊和视觉结果 | 无许可证视为不可复制，只参考公开效果 |
| [SoilMachine](https://github.com/weigert/SoilMachine) | 未发现许可证，最后代码 push 2023 | 耦合 geomorphology 的模块拆分 | 不复制 |
| [SimpleErosion](https://github.com/weigert/SimpleErosion) | 未发现许可证，最后代码 push 2020 | 简洁 particle erosion demo | 不复制，且成熟度不足 |
| [Immensa](https://github.com/MidnightTale/immensa) | MIT；2026-07-18 创建，0 star | Java Priority-Flood、flow accumulation、tile/halo、single-flight 思路 | 极新；目标 MC 1.21.11，依赖 ONNX/GPU/约 2.5 GB 模型；只作架构审查，不作为 ETF 依赖或成熟实现 |

Immensa 的 MIT 文件允许依法复用，但它本身派生自 terrain-diffusion-mc，并包含多个第三方来源；任何
未来代码级借鉴都必须先做逐文件 lineage 审查。当前没有必要承担这个风险，因为 ETF 需要的是少量
排水数学和缓存契约，不是 diffusion 模型生成器。

## 6. 候选矩阵

评分为当前架构预判，必须由统一 benchmark 证实。`高` 表示更有利，成本列的 `高` 表示更昂贵。

| 候选 | 视觉潜力 | CPU/首块成本 | 内存 | 分块连续性 | 排水能力 | 当前定位 |
| --- | --- | --- | --- | --- | --- | --- |
| 五点局部 analytical | 中低 | 低，但现实现会重复 5 次 raw top | 极低 | 天然连续 | 低 | baseline/fallback，不预定为最终效果 |
| RTF-derived droplet SoA tile | 高 | 中高，受 droplet x lifetime x brush 支配 | 中 | 需要 canonical tile + halo | 中 | 最强直接视觉候选 |
| 2024 analytical + multigrid tile | 高 | 未知，预期中 | 中高 | 需要严格边界条件 | 高 | 高质量研究首选，先做原型 |
| Priority-Flood + D8/D-infinity + stream power | 高，偏宏观河谷 | 中；routing 后可接 O(n) solver | 中 | outlet/catchment 最难 | 很高 | 最有希望的长期 drainage 主线 |
| bounded thermal/talus | 中 | 低至中 | 低 | 固定 halo 可证明 | 无 | 组合收尾，不单独交付 |
| GPU shallow-water / SPH | 高 | GPU 快、CPU/兼容成本高 | 高 | 难 | 高 | 排除正式 runtime，只作研究参考 |

没有候选同时在所有维度占优。正式方案很可能是“稳定宏观 drainage + 低次数局部形态 pass”，而不是
对每个 chunk 独立投放更多 droplets。

## 7. RTF 可以复刻到什么程度

可以复刻并验证：

- droplet 的 gradient、惯性、capacity、erosion/deposition 和 evaporation 数学。
- 世界坐标确定性的 droplet source 命名与固定 tie-break。
- smoothing 位于 erosion 之后的 filter 顺序。
- `erosionResistance`/mask 对侵蚀量的限制语义。

不能原样搬入：

- `Cell[]` 和每格 `int[][]` / `float[][]` brush。
- `WorldErosion` 只缓存一个当前尺寸实例的 `StampedLock` 生命周期。
- `TileGenerator` 的私有 `ThreadPools.WORLD_GEN`、`CompletableFuture[]` 和对象数组池。
- 带 scheduled executor 和过期扫描的全局 cache。
- `GeneratorContext`、水位、biome、river cache 和主世界 surface 语义。

ETF 原型应使用 primitive structure-of-arrays：`height[]`、`delta[]`、`water[]`、`sediment[]`、
`resistance[]`、`mask[]`。半径固定时，interior brush offset/weight 只存一份；边缘通过 halo 保证，
不为每个格子分配一套数组。droplet source 由 canonical tile 世界坐标产生，不能由“哪个 chunk 先请求”
决定。

## 8. 统一 benchmark 设计

### 8.1 输入与 fixture

所有候选消费同一个 caller-owned primitive input artifact：

- raw top blocks。
- landness、inlandness、outer activation、available thickness。
- AREA family、roughness、erosion resistance 和 terrain tags。
- RIDGE influence/crest mask、uplift、archipelago dominant mask。

至少固定以下图形和真实 seed fixture：flat、plane、paraboloid、isolated spike、finite ridge、plateau
edge、closed basin、two-outlet watershed、coast、thin shelf、archipelago，以及 seed `123456789` 的
P4.6 外部大陆窗口。候选不得用不同输入或为自己单独修图。

### 8.2 tile 形状

第一轮同时测试 128 与 256 block core；halo 不先拍脑袋固定，而由算法的最大 stencil、droplet lifetime、
thermal pass 和 drainage context 推导并记录。任何算法如果需要无限或无法界定的上游上下文，应转为
continent/region graph 或离线候选，不能用“加大 halo 大概够了”进入正式 worldgen。

tile key 至少包含：algorithm version、world seed、preset/runtime fingerprint、world bounds、canonical
tile X/Z 和必要的 Content-independent terrain version。不能使用对象 identity 或区块访问序号作为
持久身份。

### 8.3 指标

每个候选必须记录：

- cold tile build、warm hit、每列平均，以及 p50/p95/p99。
- raw-top evaluation count、cache hit/miss/collision/eviction、重复 tile build。
- allocated bytes/op、峰值 primitive artifact bytes、6 worker 估算总预算。
- core/halo 时间占比、算法迭代数、droplet steps 或 routing nodes。
- adjacent tile border bits、不同请求顺序、1/2/4/6 worker checksum。
- 视觉 fixture：侵蚀 delta、flow accumulation、ridge preservation、coast/volume safety。

### 8.4 工具

- 单元测试只负责数学、边界和 bit determinism。
- 建立独立可选 JMH source set，至少多 fork、median/p95 和 GC/allocation profiler；不要让 JMH 成为
  普通 `:common:test` 的启动负担。
- 用 JFR 记录真实 Standard 新区块生成，区分 server noise generation、GC、C2ME delegate 和客户端
  mesh/render。
- cache counters 只在测试或显式 dev flag 下启用；正式热路径不能持续支付 atomic counter 成本。

## 9. 性能架构建议

### 9.1 P4.7 接线前必须完成

1. 修正 profile 的 world-block 导数量纲。
2. 增加 P4.6 smoke profile 的 raw top、full column 和 16 x 16 chunk-like traversal benchmark。
3. 让基准分别测 cold、warm、ordered columns、shuffled columns 和 cache-collision coordinates。
4. 用 JFR 建立 ETF、ETF+C2ME、ETF+RTF、ETF+RTF+C2ME 的 P4.6 baseline。
5. 记录实际 cache 和 raw-top 重算次数，再决定 cache layout。

### 9.2 runtime 数据布局

- 算法 runtime immutable；scratch 与 SoA buffer 由 worker 持有。
- raw top 网格一次生成，多 pass 复用；不得让 slope、curvature、drainage 和 erosion 各自重新跑完整
  terrain chain。
- final top、underside、erosion、drainage 和 final metrics 同列/同 tile 一次发布。
- 首版缓存继续 per-worker、owner-aware、有界，避免跨世界强引用和未审查的共享 mutable state。
- 若 heavy tile 因多 worker 重复构建而不达标，再单独评审只发布 immutable result 的 bounded
  single-flight；不能直接复制 RTF 或 Immensa 的 executor/cache。
- 正式 worldgen 不做 native/GPU、自适应降质或依赖机器负载的算法切换。

### 9.3 暂不采用的“优化”

- 不用近似 `sqrt`、fast-math 或改变浮点顺序换速度，除非 fixture 和跨平台结果已重新冻结。
- 不在 density 的每个 Y 内运行侵蚀或邻域采样。
- 不在 Minecraft/C2ME worker 内再嵌套 `parallel()`，不创建第二个固定线程池。
- 不用无界 `ConcurrentHashMap` 保存 tile，也不让 scheduled cleanup 线程承担生命周期正确性。
- 不用预测 surface 或固定 margin 截短高世界 `NoiseChunk`。

## 10. 分阶段决策

### P4.7-0：测量与候选台

- 建立 P4.6 region-planned 性能基线、cache instrumentation 和统一 tile fixture。
- 将现有 JUnit `ns/op` 从人工输出补成可重复的相对比较；产品门禁仍以 JFR/MSPT 为准。
- 此阶段不改变正式地形。

### P4.7a：低成本 analytical baseline

- 修正导数量纲，完成五点 baseline 和零影响边界。
- 只作为 fallback、oracle 和候选对照；在算法 bake-off 前不进入玩家 preset/UI。

### P4.7b：三候选原型

- RTF droplet 改写为 primitive SoA tile。
- 2024 analytical/multigrid 做最小 CPU tile 原型。
- Priority-Flood + D8/D-infinity + stream-power 做 drainage/incision 原型。
- bounded thermal 作为三者都可选的统一收尾 pass。

### P4.7c：选择与正式接入

- 优先选择能在 Standard 下稳定满足 MSPT < 40、无持续 > 50、C2ME bit parity 和 volume safety 的
  最小组合。
- 视觉差异不明显时选择更简单、更快、内存更低的方案。
- 只有正式 runtime 选定后，preview 才接同一输出；UI 和 format v4 继续后置。

## 11. 剩余风险

- 2024 analytical 方法缺少已确认的官方开源实现，独立复现和参数标定成本未知。
- 任意有限 tile 都无法天然知道无限上游 catchment；排水需要稳定 outlet 协议或更高层 region graph。
- RTF droplet 的视觉优势可能不足以抵消 CPU、halo 与多 worker 重复构建成本。
- `EndRiverMap` 当前已在 raw terrain 后雕刻自己的程序化河谷；未来 drainage 主线必须合并职责，不能
  让旧 river 与新 erosion 各自重复挖槽。
- 当前工作树包含大量来源不同的未提交修改；本轮只更新文档，不整理、stage 或提交其他文件。

## 12. 当前选择

当前不冻结某一个正式侵蚀算法。冻结的是评选规则和实现边界：

- local analytical 是 baseline，不是默认冠军。
- RTF hydraulic 是必须参与比较的高质量候选，但只移植数学，不移植架构。
- Priority-Flood/D-infinity/stream-power 是长期排水主线候选。
- 2024 analytical/multigrid 是高质量 CPU 候选。
- thermal/talus 只做有界收尾。
- GPU、SPH、无界全局缓存和私有 executor 不进入正式 runtime。

完成 P4.7-0 的统一 benchmark 后，再用数据决定 P4.7b 哪一条进入正式 `EndDensity`。
