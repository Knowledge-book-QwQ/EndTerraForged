# P4.7 Analytical Erosion 技术规格

> 文档状态：当前有效；低成本 analytical baseline 契约已冻结，正式算法尚未选定，runtime 尚未实现。
> 最近更新：2026-07-22。
> 当前阶段：P4.6 客户端验收完成后，P4.7 候选基准中的低成本对照实现。
> 算法选型与性能架构见 [`P4_7_EROSION_ALGORITHM_RESEARCH.md`](P4_7_EROSION_ALGORITHM_RESEARCH.md)。

## 1. 目标

P4.7 保留一个低成本、逐列纯函数的 analytical erosion baseline。它只修改已经完成 AREA、
RIDGE、uplift 和 archipelago 合成后的 raw top，不重新选择大陆、terrain region、family 或 feature
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

P4.7 在接正式 density 前先比较本 baseline、RTF-derived hydraulic primitive tile、2024
stream-power analytical/multigrid tile，以及 Priority-Flood + flow accumulation + stream-power。
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

1. `EndHeightmap.getTerrainHeight(...)` 已在 raw top 中合成 AREA、RIDGE、uplift 和 archipelago；
   climate、river 与 lake 位于其后。
2. `EndTerrainProfileBuffer` 已提供 raw top、slope、curvature、roughness、erosion resistance 和
   terrain tags，且使用 caller-owned mutable buffer。
3. `EndDensity.ColumnCache` 以 X/Z/seed 和 runtime owner 为键，每个 worker 使用 256 项有界
   direct-mapped cache；top、underside 和 ocean floor 每列只刷新一次并复用于全部 Y。
4. 旧 `Erosion` / `ErosionFactory` 是可变 droplet tile 原型，目前只有 `TerrainPreviewSampler` 的
   `PreviewErosionGrid` 使用。其参数已进入 v3 Codec、Builder 和 UI，不能改义为新 analytical runtime。
5. 旧 preview 会分配 `Cell[]` 并运行独立 droplet 数学，不能作为 P4.7 正式 runtime。

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
11. 同一 input artifact 下的 local analytical、RTF-derived hydraulic、2024 analytical/multigrid、
    Priority-Flood/flow/stream-power 视觉与性能对照。

验证顺序：定点测试 -> `:common:test` -> `:neoforge:compileJava` -> `:fabric:compileJava` ->
`:verifyReleaseArtifacts --no-daemon` -> 新世界真实客户端 -> ETF/RTF/C2ME 矩阵 -> JFR。

## 12. 实现切片

1. **P4.7-0 performance baseline**：建立 P4.6 smoke profile 的 raw top、full column、chunk-like traversal、
   cache counters、allocation 和 JFR 基线，不改变正式地形。
2. **P4.7a local analytical baseline**：修正导数量纲，新增 immutable analytical runtime 与
   caller-owned output，只跑纯单元测试和统一 fixture，不接正式 top。
3. **P4.7b candidate bake-off**：以同一 primitive input artifact 比较 RTF-derived hydraulic SoA tile、
   2024 analytical/multigrid、Priority-Flood + D8/D-infinity + stream-power；bounded thermal 只作统一
   可选收尾。
4. **P4.7c selection/density integration**：选择满足视觉和性能门禁的最小组合，只对受控
   `REGION_PLANNED` 接入列缓存，完成 volume 与零影响门禁。
5. **P4.7d preview/parity**：REGION_PLANNED preview 改为同源 runtime，legacy droplet preview 保留。
6. **P4.7e final metrics**：在 erosion/smoothing 后计算 final slope、curvature 和 void-edge metrics。
7. **P4.7f drainage geometry**：只接干谷、裂谷或悬空排水槽几何，不接水体。

## 13. 完成定义

P4.7 只有在算法 bake-off、获选 runtime、preview、列/最终 tile 缓存、确定性、C2ME、volume、客户端
视觉和性能证据全部完成后才可标记完成。local analytical baseline 通过不等于正式算法已选定；技术规格、
编译或单元测试通过都不能替代真实客户端验收。
