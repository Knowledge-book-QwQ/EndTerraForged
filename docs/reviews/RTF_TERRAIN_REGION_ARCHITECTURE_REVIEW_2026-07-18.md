# RTF 最新地形区架构阶段审查

> 文档状态：当前决策记录，不替代 `PLAN.md` 的执行队列。
> 审查日期：2026-07-18。
> 审查对象：ReTerraForged `codex/r10x-volcano-rt4-fluid-routing` 工作树，提交 `407faec`。
> 使用边界：RTF 工作树只读；本轮没有修改或构建 RTF。

## 1. 结论

RTF 最新地形区架构最值得 ETF 吸收的不是完整类图，而是经过预览和实机反馈修正后的职责拆分：

```text
连续宏观 ownership
    -> AREA 地貌填满基础空间
    -> RIDGE 使用独立、有界 anchor 叠加
    -> 最多组合三个相交山链候选
    -> 高度取最大值，元数据由最强 physical influence 决定
```

RTF 当前实现允许 `AREA + COMPACT` 参与完整 ownership，`RIDGE` 只作为有限 overlay。ETF 当前
阶段仍冻结 `COMPACT`，因此 ETF 的下一版生产契约进一步收窄为：

```text
ETF AREA 多候选连续 ownership
    + RTF RIDGE bounded anchor overlay
    + RTF 三段曲线、宽度变化、core/apron 和端点收敛数学
    + ETF caller-owned primitive buffer、stable id 和 C2ME 边界
```

这意味着 ETF 当前“`AREA/RIDGE/COMPACT` 一起竞争 owner，再让 RIDGE 在 footprint 外回退到
underlay”的实现必须重构。它可以保留为受控迁移基线，但不能成为 `format_version=4` 的正式契约。

## 2. 审查证据

RTF 只读审查覆盖以下实现和设计材料：

- `TerrainCatalog`：在 runtime 构造期拆分 base、compact 与 ridge 角色。
- `UnifiedTerrainRegionLayout`：维护完整宏观 ownership。
- `AreaTerrainUnderlaySampler`：为结构化地貌提供真实宿主地貌。
- `TerrainAnchorLayout`：提供确定性 jittered anchor、权重选择和静态有界搜索。
- `ShapeAwareTerrainComposer`：组合 compact winner 与最多三个 ridge candidate。
- `RidgeTerrainEnvelope`：提供有限三段曲线、宽度变化、core/apron 与端点收敛。
- `TERRAIN_SHAPE_LAYOUT_PLAN.md`：记录 S4.2 被预览证据否决，以及 S4.3-S4.7 的修正过程。
- `TERRAIN_REGION_FLOW_GPU_RESEARCH.md`：明确 CPU 确定性 authority、GPU 延后和 bounded region 原则。

## 3. RTF 新架构解决了什么

### 3.1 RIDGE 不再拥有平坦宏观区域

RTF S4.2 曾让 `RIDGE` 拥有完整 terrain region，但实体山脉只覆盖其中一小块。结果是：

- 预览把可见平地标成 mountain owner；
- ridge `weight` 支付的是整块区域面积，而不是山带出现频率；
- footprint 外虽然高度回到 underlay，identity 和配置语义仍然错位；
- 后续 biome、侵蚀和 surface 消费者难以判断“这是山地区还是只是山脉附近的宿主平地”。

S4.3 将 `RIDGE` 从完整 ownership 中移除。footprint 外保留真实 AREA/COMPACT owner；ridge
只在物理影响大于零时改变高度和可见地貌。该修正与 ETF 当前玩家反馈中的几何边界、平地身份
错位和山体“被框在一块区域里”的问题直接相关。

### 3.2 山脉出现频率与面积权重分离

RTF 当前语义是：

- `AREA`/`COMPACT weight` 表示宏观面积占比；
- `RIDGE spacing` 控制山带出现密度；
- 同一 spacing band 内，`RIDGE weight` 只控制不同 ridge entry 的相对选择概率；
- 正权重 anchor 不再经过隐藏 activation probability 随机丢弃。

ETF 第一阶段只保留 AREA ownership，因此 `weight / regionScale^2` 只属于 AREA 的面积补偿。
RIDGE 不再复用这套面积语义，避免 UI 和 preset 参数名看似一致、实际含义却互相冲突。

### 3.3 相交山系有界组合

RTF 对每个采样点最多保留三个最强 ridge candidate：

- 候选搜索集合静态有界；
- caller 顺序、catalog 顺序和并发顺序不改变结果；
- 相交山脉的 relief 取最大值，不相加形成不受控尖峰；
- 所有候选都可扩大连续山地区域；
- 最强 physical influence 决定 terrain identity、erosion 和 weirdness 元数据；
- 较弱候选不能注入第二套方向性侵蚀元数据。

ETF 应保留这个组合契约，但用自己的 primitive scratch/buffer 表达，不移植 RTF 可变 `Cell`。

### 3.4 山脉几何不再是直线矩形 envelope

RTF S4.4-S4.6 的有效数学包括：

- 确定性三段曲线中心线；
- 物理宽度在 82%-112% 之间进行大尺度变化；
- 两端分别在最后 24%-36% 半长内提前收窄；
- core 保持完整山地，独立 outer apron 平滑回到宿主；
- 搜索 reach 包含曲率偏移和最大有机宽度，不能裁掉合法 footprint；
- footprint 外严格归零；
- 端点宽度使用有界线性收敛，拒绝 `sqrt(taper)` 在零点产生无界导数和垂直端墙。

ETF 当前 `EndTerrainRidgeRuntime` 仍主要是基于 owner 中心和方向的直线有限 envelope。它能证明
“有限”和“确定性”，但不能提供自然弯曲山系、独立坡脚和可靠端点剖面。

## 4. ETF 应保留的现有优势

这次修正不意味着用 RTF 类整体覆盖 ETF：

- ETF 的 caller-owned primitive buffer 比 RTF pooled mutable `Cell` 更适合 C2ME 并发边界。
- ETF 的紧支撑多候选 partition-of-unity 比只保留 best/second edge 更适合 AREA 多尺度交汇。
- ETF 当前 AREA 搜索最坏预算为 `16 entries * 5 * 5 = 400`，不应为了复用 ridge 任意扩大。
- ETF 已覆盖候选进入/退出、三岔口、多尺度换位和最多约八个激活 AREA 候选的连续混合。
- ETF 使用 packed `long regionId + entryId` 作为稳定身份；不得复制列表下标或
  `name.hashCode()` 作为持久身份。
- ETF 的中央保护、有限浮空大陆体积、外海流体和平台兼容边界继续位于 RTF 数学之外。

## 5. 下一代码块

实现必须按以下顺序进行，不把 ownership 拆分、山脉几何和 UI 混成一次大改：

1. 将 `TerrainRegionLayout` 收窄为正权重 AREA 的无空洞 ownership；RIDGE/COMPACT 不再进入
   owner candidate catalog。COMPACT 继续由 validator 和正式 runtime 拒绝。
2. 新增独立的 RIDGE anchor sampler。构造期按 spacing band 编译 immutable catalog，采样使用
   caller-owned primitive scratch，禁止逐点集合和对象创建。
3. 为每点最多保留三个 ridge candidate，定义稳定 tie-break；relief 取最大值，最强 influence
   决定 feature identity 和信号通道。
4. 移植并末地化三段曲线、宽度变化、core/apron、提前收窄和有界端点导数纯数学，保留 MIT
   来源头和 ETF 改动说明。
5. 让 `EndTerrainRegionComposer` 先采样 AREA ownership 和 family signals，再叠加 ridge；
   eligibility 只缩放 physical influence，不修改 AREA owner。
6. runtime 稳定后更新 LAYERS/剖面预览；最后才设计 v4 preset 与编辑器参数。

## 6. 自动门禁

下一代码块至少需要以下测试：

- 任意坐标的 ownership 都是 AREA，RIDGE 永远不能成为 ownership entry。
- footprint 外 owner、height、roughness、resistance、tags 与纯 AREA 结果完全一致。
- bounded anchor search 与扩大窗口 exhaustive oracle 一致。
- 最多三个 ridge candidate；第四个及以后不能改变结果。
- 重叠 relief 使用最大值而非求和，不产生交叉尖峰。
- 最强 influence 决定 identity/erosion metadata，catalog 重排不改变结果。
- 三段曲线连续，core/apron 独立，宽度变化和端点收敛有固定 seed fixture。
- footprint 外严格零，跨区块边界连续，负坐标和远坐标结果有限。
- 单线程、重排和多线程逐位一致；热路径无共享可变状态。
- allocation 审查覆盖 anchor、envelope 和 composition 稳态路径。
- runtime height、terrain signals 与 preview 使用同一候选和组合数学。

完成定点测试后，严格顺序运行 `:common:test`、NeoForge 编译、Fabric 编译和 release artifact
门禁。固定 seed 客户端必须检查山脉侧面、端点、交叉、宿主过渡和新区块生成成本；自动测试不能
替代视觉证据。

## 7. 性能与兼容边界

- 不在正式 worldgen 中创建 ETF 私有 executor，线程调度继续交给 Minecraft/C2ME。
- AREA 与 RIDGE 使用独立预算；不能把两个 catalog 做笛卡尔积。
- spacing band 数量、每 band entry 数、搜索半径和 Top-K 必须在构造期验证并有硬上限。
- 高频路径只访问 immutable runtime、primitive array 和 caller/thread-owned scratch。
- 不使用无界 cache，不跨 chunk pathfind，不在采样时解析 preset/registry。
- 保留 ETF 当前组合式 `NoiseRouter.mapAll` 兼容策略，不引入 RTF 平台注入或 executor。
- ETF+RTF、ETF+C2ME、ETF+RTF+C2ME 必须分别做固定 seed parity 和 JFR 回归。

## 8. 许可与明确不移植内容

RTF 为 MIT，同维护方允许复用成熟纯数学。实际移植时仍必须保留版权头、记录具体来源类与 ETF
改动，并同步更新 `NOTICE.md`。本轮只有审查文档，没有产生需要写入 `NOTICE.md` 的新移植代码。

以下内容不进入 ETF：

- RTF UI、布局和视觉呈现；
- 可变/池化 `Cell` 与 `GeneratorContext`；
- 主世界 water table、river cache、biome registry 和 surface material 耦合；
- RTF 私有 executor、预览静态帧缓存和平台注入；
- RTF cave；
- 未经 ETF fixed-seed、C2ME 和 Standard 性能门禁验证的整套类复制。

RTF 最新火山实验线已经形成较成熟的区域 artifact、provenance、缓存生命周期和流体路由候选，
但 ETF 当前冻结 COMPACT 是阶段范围和末地语义决策，不再归因于“RTF 火山没有实现”。火山应在
AREA ownership、RIDGE overlay、uplift、群岛海岸和侵蚀链稳定后单独评审。
