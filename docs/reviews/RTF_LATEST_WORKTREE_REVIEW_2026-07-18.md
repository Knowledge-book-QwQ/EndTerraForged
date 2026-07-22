# RTF 最新工作树审查与 ETF 吸收边界

> 文档状态：当前有效的 ETF 侧只读审查记录。
> 日期：2026-07-18。
> 重要边界：本记录只修改 EndTerraForged 文档，不修改 ReTerraForged 工作树。

## 1. 审查对象

本轮审查的 RTF 事实来源是：

- 工作树：`C:\Users\klbook\.codex\worktrees\e95f\ReTerraForged`
- 分支：`codex/r10x-volcano-rt4-fluid-routing`
- 代码基线：`fdb0f05`
- 工作树状态：RT4 相关 artifact、流体解析和测试仍有未提交修改，因此不能只按 HEAD 判断；当前工作树内容才是最新研究状态。

本轮重点阅读了 RTF 的高世界优化、地貌区域与 GPU、火山 R10/R10X、河流分阶段包和 Geology 集成研究文档，并与 ETF 当前源码和测试对照。

## 2. 结论先行

RTF 火山分支目前已经不是单一形态草稿，而是具备以下完整研究链：

```text
冻结 host DEM
  -> 解析式火山初始形态
  -> 区域地质历史模拟
  -> 不可变 packed artifact
  -> runtime 局部采样
  -> final fluid / DEM route
  -> analytical fallback
```

“基本成熟”的准确含义是：代码架构、数据契约、artifact、缓存生命周期和 RT4 流体路由已经形成可继续验收的系统；它仍不能自动等同于两个加载器、固定种子新世界、C2ME/Noisium 同载、长时间缓存曲线和正式发布门禁全部通过。

ETF 当前接入的是受控的 `EndTerrainVolcanoRuntime`：

- 只提供有限的 footprint、坡面、rim、crater 和 bounded relief；
- 通过 `EndTerrainRegionComposer` 的 `COMPACT` 路径消费；
- 默认配置关闭，并且当前高质量 `REGION_PLANNED` 路径仍未向玩家 preset/UI 开放；
- 不包含 RTF 的 `Cell`、`GeneratorContext`、区域 artifact、流体方块、surface material、biome registry 或 river cache。

因此，RTF 火山成熟不会改变 ETF 当前“先收束地表质量、兼容性和性能，再决定是否接入地下/火山 artifact”的顺序。

## 3. 值得吸收的研究成果

### 3.1 区域 artifact 与 provenance

RTF 的区域火山 runtime 值得 ETF 未来用于洞厅、裂隙、深渊和大型地下水文。可吸收的不是 RTF 类层次，而是契约：

- artifact 保存算法版本、输入 provenance、host DEM fingerprint 和 content hash；
- 采样通道使用固定长度 primitive 数据，不在密度热路径创建对象；
- `height delta`、沉积、材料、渗透率、历史年龄、hydrology、authority 和 fluid route 分开表达；
- 不支持的输入明确拒绝，只有白名单 deterministic rejection 才允许 analytical fallback；
- cache 有完成条目数和字节双上限，单 key single-flight，世界卸载时显式关闭；
- runtime 和 preview 使用同一 artifact 语义，不能各自重算一套候选结果。

ETF 暂不建立通用 artifact 框架。等第一个正式地下区域消费者确定后，再抽取 caller-owned primitive artifact 接口，避免为未来功能提前增加 schema、cache 和生命周期复杂度。

### 3.2 地貌 identity 与 physical influence 分离

RTF 的 `TerrainCatalog`、区域变体和 `MountainChainBlender` 共同说明：高质量地形不能只保存最终高度。ETF 已经吸收了其中的核心原则：

- ownership、visible family、underlay family、feature identity 和 physical influence 分开；
- AREA 负责连续底层，RIDGE/COMPACT 只在有界 footprint 内叠加；
- footprint 外 physical influence 必须严格归零并回到 underlay；
- 同一 region 使用稳定的 `regionId + entryId` 变体，边界只混合连续信号。

后续不得再用最终高度反推火山、山脉或洞穴身份，也不得让通用噪声层覆盖结构化地貌的权威信号。

### 3.3 高世界优化

RTF 的高世界研究与 ETF 现有审查结论一致：

- 保留完整实际 `NoiseSettings` 垂直范围；
- 保留原版 `NoiseRouter.mapAll(visitor)` 包装和 interpolation/cache 生命周期；
- 不使用预测 surface、固定安全余量或 `DensityFunction.minValue/maxValue` 截断 `NoiseChunk`；
- 只有最终 density cell 已完成、且能证明流体安全时，才研究精确空 cell material 快路径；
- C2ME、Noisium 和 ModernFix 的外部吞吐数字只能作为对照，不能当作 ETF 结论。

ETF 的后续优化优先级仍是固定 seed/JFR 基线、缓存/分配审查和 C2ME 逐位 parity，不是继续扩大默认世界高度。

### 3.4 Preview 生命周期

RTF 的 `PreviewRefreshState` 提供可直接借鉴的状态契约：revision、single-flight、取消 pending future、旧帧可显示但不可交互、过期结果不得发布、界面关闭后不得上传纹理。ETF 后续实时预览应把这些状态放到每个 widget/screen 实例中，禁止全局静态 tile 或像素缓存。

### 3.5 河流和 Geology 研究

RTF 的河流研究明确把“区域图编译、河段/汇流、纵向 profile、corridor sampling、表面放置”分开，且不让局部采样点重新发明水面高度。ETF 未来应只吸收这一职责拆分，继续保持末地水体语义独立，不直接移植主世界 water table。

Geology 研究则验证了一个可反哺 ETF Content Pack API 的边界：跨模组只公开版本化、只读、caller-owned 的 terrain sample；不暴露 `Cell`、`GeneratorContext`、tile cache 或内部 registry。外部模组可以消费 terrain、slope、erosion、volcano influence 和 surface kind，但不能同时接管 ETF density。

GPU 研究的结论也保持有效：Java 21 CPU backend 是正式 authority，GPU 先用于 preview/offline prototype；只有 CPU/GPU parity、fallback、生命周期和兼容性均有证据后，才考虑进入正式 worldgen。

## 4. 明确不移植的内容

- RTF UI、布局结构和视觉呈现；
- RTF cave 方案；
- 可变 pooled `Cell`、`GeneratorContext`、`RiverCache` 和 RTF 私有 executor；
- 直接复制 RTF 的 `NoiseRouter` 独占 redirect 或绕过原版 visitor 包装；
- RT4 的 lava/water 方块写入和主世界水文语义；
- GPU/native backend 作为 ETF 默认运行时；
- 未经 ETF 固定 seed、性能和客户端门禁证明的 regional artifact 默认启用。

## 5. ETF 当前执行决策

1. RTF 最新分支保持只读；通用优化、契约和反哺状态统一记录在
   [`../ETF_TO_RTF_IMPROVEMENT_TRACKER.md`](../ETF_TO_RTF_IMPROVEMENT_TRACKER.md)，再由独立 RTF
   issue、分支和发布流程人工接入。
2. ETF 当前 `EndTerrainVolcanoRuntime` 继续作为封闭的 analytical COMPACT 草稿，不开放到玩家 preset、`format_version=4` 或正式 UI。
3. P4 先完成外部大陆、有限体积、海水/虚空边界、区域地貌过渡、RTF 同载、C2ME parity 和 Standard JFR 基线。
4. 后续第一个需要区域预计算的正式消费者确定后，才设计 ETF 自己的 artifact 接口；优先考虑巨型洞厅或洞穴网络 tile，而不是复制 RTF 火山包结构。
5. 火山若重新进入 ETF 路线，顺序必须是：纯形态 contract -> runtime/preview parity -> provenance/artifact -> 可选 surface/content adapter -> 客户端与性能门禁；正式流体和方块写入最后处理。

## 6. 当前风险

- RTF 最新工作树仍有未提交 RT4 修改，必须以分支和工作树快照记录为准；不可把 `fdb0f05` 当成全部最终内容。
- ETF 当前源码工作树同样包含大量未提交文件，源码、测试和真实客户端证据之间仍存在阶段差异。
- ETF 火山测试证明的是有限 relief 的确定性和边界，不证明 RTF regional artifact 或正式火山流体已进入 ETF。
- RTF/ETF/C2ME 的静态注入结构已经有设计依据，但仍需要当前 jar、固定种子新世界和长时间运行证据。

## 7. 本轮验证范围

- 已完成：ETF 与 RTF 最新工作树的静态源码、提交、未提交文件、研究文档和调用链对照。
- 未运行：Gradle、Minecraft、真实客户端、C2ME 长时间生成和 JFR。
- RTF 工作树：未修改。
