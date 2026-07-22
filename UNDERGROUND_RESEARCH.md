# EndTerraForged 地下系统调研与路线

> 文档状态：研究参考，不是当前执行计划，也不代表所述功能均已实现。
> 更新日期：2026-07-09。当前阶段、优先级与完成定义以 [`GOAL.md`](GOAL.md) 和 [`PLAN.md`](PLAN.md) 为准。
> 执行边界：本文保留的是地下系统的设计依据；其中“下一步”“MVP”等措辞只描述调研当时的候选路线。当前不得据此跳过主岛外大陆的真实客户端验收、`FLOATING_SHELF` 性能基线或 Content Pack 工作，也不得把洞穴预览/候选 mask 当成正式方块生成。
> 本文件记录地下系统、洞穴系统与深渊深坑的调研结论和实现边界。实现路线保持 **NeoForge-first + LDLib2 主体验**，`common` 只承载 worldgen、preset、validator、builder 与 preview sampler，不直接依赖 LDLib2。

## 历史研究目标（非执行队列）

本节记录调研当时提出的地下系统起步方案，不定义当前开发顺序。它的核心判断是：地下系统不应一次性复制完整 RTF cave/noise-router，而应先形成可配置、可测试、可实时预览的 **Subsurface / Abyss Pits MVP**：

- 默认关闭，旧世界输出保持不变。
- 开启后在地形实体内产生垂直深坑/深渊切削。
- preset JSON 只保存可序列化 scalar 参数，不保存 biome holder、registry object 或平台 UI 对象。
- runtime 与 preview 使用同一套 mask math，避免滑块只改 JSON 而预览不可见。
- 完整洞穴系统后续分阶段扩展，避免 density、carver、biome、UI 同时大规模变更。

## 世界规格与性能预算

地下景观可以追求大尺度，但不应让每个末地默认承担史诗规格的成本。当前将世界包络与洞穴复杂度分开：洞穴算法保持可扩展，玩家默认进入轻量 world spec。

| 规格 | 包络 | 定位 | 状态 |
| --- | --- | --- | --- |
| Standard | `-256..255`，512 格 | 普通单人、整合包与服务器默认 | 已作为正式默认数据包 |
| Extended | 1024 格 | 更深地下层和较大洞厅 | 待创建世界规格接线与性能验收 |
| Grand | 2048 格 | 服务器/性能充足的整合包 | 待实现，UI 必须黄色警告 |
| Epic | 4064 格 | 实验性超大末地 | 待实现，不能默认启用 |

当前 `noise.size_vertical=1` 时，垂直 noise cell 数会近似随世界高度线性增长；因此 512 规格约为 4064 的八分之一。未来先以 Standard 收束洞厅、裂隙和深渊的空间语义，再让 Extended/Grand/Epic 作为创建世界时显式选择的包络，不允许通过已有世界的 preset 保存操作修改真实维度高度。

## 调研来源与许可边界

| 来源 | 协议 | 本项目使用方式 |
| --- | --- | --- |
| LDLib2 | LGPL-3.0 | NeoForge 侧 UI component bridge；`common` 不依赖。 |
| TerraForged | MIT | 参考地形生成与 preset 架构思想。 |
| ReTerraForged / 本地 RTF R9.3.6 | MIT | 参考详细 cave 参数规格与 preset 语义。 |
| ReTerraForged / 本地 RTF R9.6 | MIT | 参考核心生成链路思路，不引用 UI 设计、布局或视觉呈现。 |
| YUNG's Better Caves | LGPL-3.0 | 只做洞穴形态和分层路线调研，不复制代码。 |
| OpenTerrainGenerator | MIT | 参考配置驱动的 terrain/cave feature 管线与可移植边界。 |

本轮没有复制 RTF、YUNG's Better Caves 或 OpenTerrainGenerator 的洞穴源码。RTF 的 MIT 代码如后续直接改写到源码文件，必须保留对应版权头和来源说明。

## 洞穴系统重新定向：不采用 RTF cave 作为主方案

本轮重新调研后，RTF cave 只保留为“已有字段对照表”，不再作为 EndTerraForged 的主要洞穴设计来源。原因：

- RTF cave 更偏向 vanilla noise-router 参数补全，适合作为 overworld 兼容型洞穴配置，但不适合作为“真实、庞大、可探索”的末地地下系统主线。
- RTF 的 cheese / spaghetti / noodle 命名与 vanilla 1.18 噪声洞穴接近，但如果只搬参数，不会自然得到足够宏大、连通、可导航的洞穴网络。
- ETF 的末地地下系统需要更强的空间叙事：巨型空腔、垂直深坑、地下河/熔岩流、裂隙廊道、局部洞厅群，以及与大陆/地形层/深渊系统的耦合。

新的参考方向分三类：

1. **Vanilla 1.18 噪声洞穴**
   - 可借鉴三类形态分层：大开口 cheese cavern、宽隧道 spaghetti、细隧道 noodle，以及 aquifer/地下湖概念。
   - 只借鉴“多通道密度合成”的思想，不照搬 vanilla 参数命名作为主 UI。

2. **YUNG's Better Caves**
   - 可借鉴“表层入口低调、越深越夸张”的体验节奏，以及地下湖、地下河、熔岩海、维度级配置等产品思路。
   - 该项目为 LGPL-3.0，ETF 不复制源码；只参考功能层级和玩家体验目标。

3. **学术/通用体素洞穴算法**
   - 3D karst cave 研究强调“全局洞穴网络 + 局部通道形状 + 洞壁表面 + 钟乳石/石笋”等多尺度结构。
   - 游戏向 3D cave pipeline 可采用 L-system / graph 生成主通道骨架，再用 noise-perturbed metaball / SDF 进行体积雕刻。
   - Voxel/SDF 方案中，“噪声 worm + Y 轴曲线 + 垂直扰动 + 低频 dead-end mask”适合 chunk-local deterministic 实现。

## 开源社区调研结论

本轮额外调研了当前仍有参考价值的开源/社区洞穴方案。结论是：没有一个项目能直接满足 ETF “末地、庞大、壮观、可配置、实时预览”的目标，但可以拆解吸收它们的优点。

### YUNG's Better Caves

定位：大型 Minecraft cave overhaul，强调维度级配置、地下湖/河、熔岩海、紧凑浅层洞穴和深层巨型 cavern。

可借鉴：

- 体验节奏：浅层较压迫，深层逐渐打开到巨型洞厅。
- 功能分层：入口、通道、洞厅、地下河/湖、熔岩区分开控制。
- 配置理念：维度级配置很适合 ETF 的 NeoForge-first preset 编辑器。

不直接借鉴：

- LGPL-3.0 源码不能直接搬进当前主线。
- 它的目标仍是 overworld 地下体验，末地需要更强的虚空、裂隙、浮岛和深渊语言。

### Worley's Caves / Worlium

定位：基于 Worley noise 的大规模连续洞穴系统；Worlium 是较新的 Fabric/NeoForge 方向，核心思路是用 Worley-noise density function 替代 vanilla cave carver。

可借鉴：

- “连续、巨大、迷宫感”的洞穴网络目标非常接近我们想要的宏大感。
- Worley noise 适合生成自然连通的洞穴团和大范围隧道结构。
- 作为 density function 的形态很适合后续正式接入 `EndDensity` / noise bridge。

不直接借鉴：

- GPL-3.0-only 项目不能复制实现。
- 纯 Worley 容易变成均匀迷宫，缺少“巨型地标洞厅”和“玩家导航层级”，需要叠加图网络和节点类型。

### Alex's Caves

定位：以洞穴 biome / 地下主题体验为核心，提供隐藏在地下的特殊洞穴区域。

可借鉴：

- 洞穴应该有强主题区，不只是几何空腔。
- 地下生态、结构、视觉目标要和 cave mask 解耦，后续走资源 key / 数据包层。

不直接借鉴：

- GPL 系列许可不适合直接搬代码。
- 它更偏“洞穴内容/biome/玩法”，不是我们当前最需要的底层巨型洞穴几何系统。

### Vanilla 1.18+

定位：cheese / spaghetti / noodle 多通道噪声洞穴、aquifer 和深度分层。

可借鉴：

- 多通道合成：大洞厅、宽通道、细通道分别建模。
- 深度控制：越深越容易出现大体量空间。
- aquifer 思路：地下液体不应该只是随机水坑，而应由区域级水体系统控制。

不直接借鉴：

- Vanilla 的洞穴目标是 overworld 地层，不是末地“虚空巨穴”。
- 直接照 cheese/spaghetti/noodle 参数做，会变成另一套 vanilla cave，而不是 ETF 的标志性地下系统。

### 体素/SDF/图网络方案

定位：不依赖具体 Minecraft 实现，通过 graph skeleton、SDF/metaball、noise warp 生成可控体积空间。

可借鉴：

- 先生成“空间骨架”，再雕刻体积，能保证可导航、可控规模和可测试。
- SDF/Metaball 适合做巨大洞厅、裂隙、桥脊、拱顶和非球形空间。
- Graph 节点可用于 preview、调试、后续结构/群系挂点。

ETF 最终方案应以这一类为主，再吸收 YUNG 的体验节奏、Worley 的连续性、Vanilla 的多通道合成、Alex's Caves 的主题分区。

## ETF 推荐方案：End Abyssal Cave System

建议把 ETF 洞穴系统设计成 **End Abyssal Cave System**，不走 RTF cave 参数搬运路线。核心是“三层生成”：

### 1. Macro Network：宏观洞穴骨架

负责“有没有庞大、可探索、连通的地下结构”。

- 以 chunk-region 为单位确定 cave network seed，保证跨 chunk 连续。
- 用稀疏图/空间节点生成主洞厅、分支廊道和回环。
- 节点类型建议：
  - `CHAMBER`：巨型洞厅。
  - `RIFT`：垂直裂隙/深渊竖井。
  - `TUNNEL`：中型连接通道。
  - `SIPHON`：地下河/熔岩流通道。
  - `POCKET`：局部小洞穴群。
- 图约束：
  - 控制最大坡度，避免不可导航。
  - 控制节点间最短距离，避免洞厅互相糊成噪声团。
  - 少量回环，避免所有洞穴都是死胡同。
  - 可按 `landness`、地形高度、subsurface depth zone 限制生成区域。

### 2. Volume Carving：体积场雕刻

负责“洞穴的真实体量和边界形状”。

- 主洞厅使用 metaball / ellipsoid SDF，支持非球形、大跨度、压扁或拉长。
- 通道使用 capsule SDF 或 swept sphere，沿骨架边连接。
- 裂隙使用 vertical slab / cone SDF，和现有 Abyss Pit 视觉语言兼容。
- 每个 SDF 再叠加 3D noise warp / Worley detail，避免边界过于光滑。
- 体积合成不直接暴露给 preset；preset 只暴露 scalar，如密度、尺寸、深度、连通性、粗糙度。

### 3. Local Detail：局部地貌与生态

负责“探索时有层次，而不是空洞盒子”。

- 地下河/熔岩流：先作为 preview/runtime mask，不急于放液体方块。
- 洞厅台地/桥脊：在洞厅底部保留局部平台，避免巨洞全空不可走。
- 顶部垂坠物：末地风格可做 “endstone teeth / crystal roots”，暂不做 registry-bound 内容进 preset。
- 地下群系：不把 biome holder 放进 preset，只在后续数据包/资源 key 层处理。

## 新配置路线

`CaveTunnelConfig` 当前已经落地为第一批配置骨架，但它不再代表 RTF cave 主线，而是作为 ETF 洞穴系统的 “tunnel probability seed config” 保留。随后已经新增 spectacle-first 洞穴系统的三组主配置：

- `CaveNetworkConfig`
  - `region_size`
  - `network_density`
  - `chamber_spacing`
  - `branching_factor`
  - `loop_chance`
  - `max_slope`
  - `min_landness`
- `CaveChamberConfig`
  - `chamber_probability`
  - `min_radius`
  - `max_radius`
  - `vertical_stretch`
  - `floor_bias`
  - `roughness`
- `CaveRiftConfig`
  - `rift_probability`
  - `rift_depth`
  - `rift_width`
  - `rift_taper`
  - `abyss_connection_chance`
- `CaveRiverConfig`
  - `enabled`
  - `river_probability`
  - `channel_width`
  - `lava_bias`
  - `water_bias`

总控 record：

- `CaveSystemConfig`
  - `enabled`
  - `seed_offset`
  - `depth_start`
  - `depth_end`
  - `spectacle_bias`
  - `connectivity`
  - `surface_opening_chance`

其中 `spectacle_bias` 用来控制“壮观程度”：值越高，越偏向巨型洞厅、长裂隙、桥脊、地下河；值越低，越偏向普通通道和局部 pocket。这样 UI 上可以提供一个玩家能理解的总旋钮，同时底层仍然保留细项参数。

当前实现状态：

- `CaveSystemConfig`、`CaveNetworkConfig`、`CaveChamberConfig` 已挂入 `SubsurfaceConfig`，字段分别为 `cave_system`、`cave_network`、`cave_chambers`。
- 三组配置均已补 codec、validator、builder、默认值、round-trip、非法范围、旧 JSON 缺省兼容与聚合校验测试。
- `EndCavePreviewMask` 已作为二维顶视足迹 runtime 落地，使用 cave system / network / chamber 三组配置生成 deterministic `strength(x, z, landness)`。
- `EndCaveField` 已作为第一版正式 3D 洞穴 runtime 落地：它复用二维足迹作为水平网络种子，再用 `depth_start`、`depth_end`、`vertical_stretch`、`floor_bias` 形成垂直体积，并通过 `EndSubsurface.carves(...)` 接入 `EndDensity`。
- `EndCaveGraph` 已作为 graph/chamber 节点结构 MVP 落地：它从现有 scalar 派生 deterministic 洞厅节点和粗通道连接，当前不新增 preset 字段，不把节点对象序列化进 JSON。
- `EndCaveGraph` 已继续拆出内部 `CHAMBER`、`RIFT`、`FLOW` 节点语义；`RIFT` 当前表现为窄而纵深的裂隙 carve，`FLOW` 当前表现为更平缓的地下流线通道 carve。它们仍是派生 runtime 语义，不是新的 preset JSON 字段。
- `TerrainPreviewMode.CAVES` 已接入 sampler 与独立调色板，用同一套 preview mask 显示洞穴总体足迹；`CAVE_CHAMBERS` 与 `CAVE_NETWORK` 已能分别查看洞厅和网络通道子 mask。
- `EndCaveGraphPreviewMask` 已接入 `CAVE_RIFTS` 与 `CAVE_FLOWS` 预览模式，可单独查看 graph 派生的裂隙和流线通道。它只从 runtime graph 派生，不新增 preset JSON 字段。预览当前仍是顶视图，后续需要补深度带表达，避免玩家误以为只在二维平面发生变化。
- `CAVE_WATER` 与 `CAVE_LAVA` 已作为地下液体候选预览落地：二者从现有 `FLOW` / `RIFT` graph runtime 派生，只用于调试和规划地下水/熔岩分布，暂不新增 preset 字段，也不放置液体方块。
- 本轮参数可用性审查后继续确认：`CAVE_WATER` / `CAVE_LAVA` 不是正式地下液体系统，英文/中文 UI 都必须保持 candidate 语义；`SubsurfaceConfig` 需要继续用负向测试防止 `cave_liquids`、`cave_water`、`cave_lava` 等预览层误进入 preset JSON。只有在 preview 与正式方块生成 / 填充路径同时存在后，才考虑新增 `CaveLiquidConfig`。
- `CAVE_DEPTH` 已作为第一版顶视深度带落地：sampler 沿 `EndCaveField` 正式 3D strength 路径扫描洞穴深度区间，用颜色表达最强洞穴出现在浅层 / 中层 / 深层。它仍不是真正的垂直剖面 UI，后续需要继续补深度切片或剖面视图。
- `CaveSlicePreview` / `CaveSlicePreviewSampler` / `CaveSlicePreviewWidget` 已作为真正垂直洞穴剖面的 common UI 基础落地：横轴为世界距离，纵轴为洞穴深度，颜色继续复用正式 3D cave strength 的深度调色。当前在地下编辑器 Caves 分区支持 X/Z 轴向切换和剖面偏移；这些是 UI 视角状态，不进入 preset JSON。
- 地下编辑器已将 Abyss / Caves 拆成顶部模式分区；Caves 区暴露 `cave_system`、`cave_network`、`cave_chambers` 的第一批低风险 scalar，UI 使用独立 builder 暂存编辑状态，预览/提交时再组合回 `SubsurfaceConfig`，避免继续膨胀聚合 builder。Caves 区的实时预览现在只在洞穴相关模式间循环，并记住上次选择的子预览；Abyss 区只暴露 `ABYSS` 预览，避免全局预览模式干扰地下调参。
- `CaveRiftConfig` 与 `CaveRiverConfig` 暂未落地，等待 graph/chamber 节点和 3D carve 稳定后再接。

## 推荐算法：Spectacle-First Cave Pipeline

为满足“庞大壮观”的目标，建议不要从小通道开始扩展，而是反过来：

1. **先定地标洞厅**
   - 每个大 region 先决定是否生成 1-3 个主洞厅。
   - 主洞厅使用 ellipsoid/metaball SDF，半径可达到多个 chunk。
   - 洞厅底部保留台地或桥脊，保证可探索，不做纯空球。

2. **再连通洞厅**
   - 用低成本图网络连接洞厅和裂隙节点。
   - 通道不是简单直线，使用 cubic spline / noise-warped polyline。
   - 至少保留少量回环，避免玩家走到死胡同。

3. **再加裂隙与深坑**
   - `RIFT` 节点连接现有 `AbyssPitConfig` 的视觉语言。
   - 垂直裂隙可以贯穿多层，但要限制 surface opening，避免地表到处破洞。

4. **最后加局部细节**
   - Worley / 3D noise 只用于洞壁细节和局部 pocket，不主导整体结构。
   - 地下河/熔岩流先只生成 mask，后续再落 block/liquid。

这个 pipeline 的关键优势是：先保证“看起来壮观、能探索”，再用噪声增加自然感；不会变成随机噪声洞，也不会变成满世界均匀迷宫。

落地顺序建议：

1. 已实现 `EndCavePreviewMask` 的 deterministic 2D preview mask；它不直接执行正式 carve，而是作为 `EndCaveField` 的水平网络种子。
2. 已新增 `TerrainPreviewMode.CAVES`，当前显示洞穴总体足迹；洞厅、网络、裂隙、流线、地下水候选、熔岩候选和顶视深度带已经拆成独立预览模式，并已补地下编辑器垂直剖面 UI 基础、轴向切换和偏移控制，后续再补正式液体生成路径。
3. 已在地下编辑器新增 Abyss / Caves 分区切换；深渊区同步 `ABYSS` 预览，洞穴区同步 `CAVES` 预览，并先暴露 `cave_system`、`cave_network`、`cave_chambers` 的低风险 scalar。
4. 已实现 `EndCaveField` 作为第一版 3D runtime，并扩展出 `strength(x, z, landness, yNorm, terrainTopNorm, worldHeight)`；`EndDensity` 已能在已判定 solid 后调用 cave runtime 执行 carve。
5. 下一步做参数可用性审查，确认哪些 slider 应继续保留为玩家可见项，哪些应下沉为高级/内部派生参数。
6. 已实现第一版 graph/chamber 节点 runtime，让洞厅和粗通道从确定性节点网络中出现，而不是只依赖连续噪声。
7. 已把节点类型拆出 `CHAMBER`、`RIFT`、`FLOW` 三类内部语义，并补了可单独查看的洞厅、网络、裂隙、流线、深度带和 common 垂直剖面采样；下一步继续做剖面 UI、地下液体 mask、地下群系、特征装饰和结构交互。

## RTF R9.3.6 Cave 参数规格（仅作对照，不作为主方案）

本地 RTF R9.3.6 的 `CaveSettings` 暴露了以下配置项，可作为 EndTerraForged 后续完整洞穴阶段的规格参考：

- `entranceCaveProbability`
- `cheeseCaveDepthOffset`
- `cheeseCaveProbability`
- `spaghettiCaveProbability`
- `noodleCaveProbability`
- `caveCarverProbability`
- `deepCaveCarverProbability`
- `ravineCarverProbability`
- `largeOreVeins`
- `legacyCarverDistribution`

RTF 默认值为：

```text
entranceCaveProbability = 0.0
cheeseCaveDepthOffset = 1.5625
cheeseCaveProbability = 1.0
spaghettiCaveProbability = 1.0
noodleCaveProbability = 1.0
caveCarverProbability = 0.14285715
deepCaveCarverProbability = 0.07
ravineCarverProbability = 0.02
largeOreVeins = true
legacyCarverDistribution = false
```

需要注意：RTF 的完整 cave 逻辑主要接入 vanilla `NoiseRouter` 和 density functions，例如 cheese / spaghetti / noodle / entrances / underground 等通道。EndTerraForged 当前的 `EndDensity` 是离散 `0/1` solid/void 决策层，直接迁移完整 RTF cave 逻辑会同时牵动 noise-router、carver、chunk-gen bridge 和 GUI，因此不适合在 MVP 阶段一次性落地。

## ETF 当前接入点

当前地下系统的低风险接入点是 `EndDensity`：

- `EndHeightmap` 负责 2D 地形高度、陆地度和地形层。
- `EndDensity` 将 2D 地形转换为 3D solid/void。
- `SeaMode` 已决定是否向世界底部填充实体。
- 新增地下 runtime 可以在“已判定为 solid”之后执行 carve，把部分实体转为空。

这个位置的优点：

- 默认关闭时完全不改变旧世界。
- 输出仍保持 `0.0` 或 `1.0`，不会提前引入复杂 density smoothing。
- 可以用 deterministic 单元测试验证 carve 范围。
- preview sampler 可以复用同一个 runtime mask，保证所见即所得。

限制：

- 第一版 3D cave MVP 现在能表达“二维足迹 + 垂直体积包络”的洞穴 carve，但还不是完整 graph/chamber 洞穴网络。
- 后续若要做真正的洞厅节点、裂隙、通道回环、地下河或 cheese/spaghetti/noodle 细节，需要引入更丰富的 3D noise、SDF/graph runtime 与可能的 vanilla density bridge。

## Subsurface / Abyss Pits MVP

本轮实现的配置主干：

- `SubsurfaceConfig`
  - `abyss`
- `AbyssPitConfig`
  - `enabled`
  - `seed_offset`
  - `pit_scale`
  - `threshold`
  - `edge_falloff`
  - `depth`
  - `min_landness`

默认值：

```text
enabled = false
seed_offset = 1600
pit_scale = 900
threshold = 0.82
edge_falloff = 0.12
depth = 384
min_landness = 0.25
```

默认关闭是硬边界：旧 preset 缺省 `subsurface` 字段会解码为关闭状态，`EndDensity(EndHeightmap)` 旧构造仍等价于禁用地下层。

runtime 设计：

- `SubsurfaceConfig.buildRuntime(seed)` 生成不可变 `EndSubsurface`。
- `EndSubsurface.abyssStrength(x, z, landness)` 计算深渊 mask 强度。
- `EndDensity` 在常规 solid 判断后调用 `EndSubsurface.carves(...)`。
- ABYSS 预览模式调用同一套 `abyssStrength(...)`，只改变颜色映射，不改变其他 preview mode。

## UI 路线

NeoForge 主体验继续使用 LDLib2 bridge；`common` 屏幕通过 `LdLib2ActionBars` 反射桥接并保留 vanilla fallback。

地下编辑器采用既有子编辑器结构：

- 入口位于主编辑器 Advanced 页。
- 子编辑器使用滚动布局，避免高参数量时挤压小窗口。
- 默认预览模式为 `ABYSS`。
- `Done` 将 `SubsurfaceConfig` 回写主 `EndPresetBuilder`。
- `Reset` 恢复默认关闭状态。
- 错误通过屏幕底部状态文本显示，不静默失败。

Fabric 仍只作为低维护兼容边界，不引入第二套高级 UI 组件库。

## 分阶段路线

1. **MVP：深渊深坑**
   - 已接入 scalar config、codec、validator、builder、runtime、preview、UI 入口和测试。
   - 默认关闭，开启后产生可观测 carve。

2. **Cave scalar 扩展**
   - 已把 RTF R9.3.6 的首批 cave probability / depth scalar 放入 `SubsurfaceConfig` 下的独立 `CaveTunnelConfig`。
   - 当前阶段只接 codec、validator、builder 与测试；preview mask 和正式 chunk-gen 仍分阶段推进。

### `CaveTunnelConfig` 已落地规格

当前已新增 `CaveTunnelConfig`，挂在 `SubsurfaceConfig` 的可缺省字段 `caves` 下。它只承载可序列化 scalar，不保存 registry holder、density function 或 UI 状态。默认值策略采用“ETF 默认不改旧世界，RTF 数值作为启用后的语义参考”：`enabled=false` 是硬边界，旧 preset 缺省 `subsurface.caves` 时仍不会改变正式 worldgen 输出。

| 字段 | 建议默认 | validator 范围 | 接入说明 |
| --- | --- | --- | --- |
| `enabled` | `false` | boolean | 关闭时 runtime 与 preview mask 均返回 0，保持旧世界输出。 |
| `entrance_probability` | `0.0` | `[0.0, 1.0]` | 对应 RTF `entranceCaveProbability`；先只影响 `CAVES` 预览 mask。 |
| `cheese_depth_offset` | `1.5625` | `[0.0, 8.0]` | 对应 RTF `cheeseCaveDepthOffset`；后续 3D runtime 用作 cheese 深度阈值。 |
| `cheese_probability` | `1.0` | `[0.0, 1.0]` | 对应 RTF `cheeseCaveProbability`；先作为 cheese mask 权重。 |
| `spaghetti_probability` | `1.0` | `[0.0, 1.0]` | 对应 RTF `spaghettiCaveProbability`；先作为细长洞穴 mask 权重。 |
| `noodle_probability` | `1.0` | `[0.0, 1.0]` | 对应 RTF `noodleCaveProbability`；先作为细洞 mask 权重。 |

实现状态与后续边界：

- `Codec`：已新增 `CaveTunnelConfig.CODEC`，并在 `SubsurfaceConfig.CODEC` 中使用 `optionalFieldOf("caves", CaveTunnelConfig.DISABLED)`。
- `Validator`：已新增 `CaveTunnelConfigValidator`，`SubsurfaceConfigValidator` 已组合校验 `abyss` 与 `caves`；错误路径使用 `subsurface.caves.<field>`。
- `Builder`：已新增独立 `CaveTunnelConfigBuilder`；`SubsurfaceConfigBuilder` 已能组合构建 `abyss` 与 `caves`，后续 UI 子区应优先复用独立 builder，避免继续把 root builder 膨胀为大参数仓库。
- `Runtime`：当前已新增轻量 `EndCavePreviewMask`，提供 deterministic `strength(x, z, landness)`；同时新增 `EndCaveField` 作为第一版 3D runtime，通过 `EndSubsurface` 接入正式 `EndDensity.carves(...)`；新增 `EndCaveGraph` 作为派生节点图，负责洞厅节点、裂隙节点、流线节点和粗通道连接。后续地下河/熔岩流应增强 `EndCaveGraph`，而不是绕过 `EndSubsurface`。
- `Preview`：`TerrainPreviewMode.CAVES` 已落地，使用 `EndCavePreviewMask` 渲染洞穴总体足迹；已验证洞穴参数会改变预览签名，且不影响 `HEIGHT` / `COMBINED` / `BIOMES` / `ABYSS` 等既有模式。
- `UI`：地下编辑器已新增 Abyss / Caves 分区切换，复用 `EditorScrollLayout`、实时预览和 `LdLib2ActionBars` fallback；后续若参数继续增加，应继续拆成洞厅、裂隙、通道、地下河等 Caves 子区，不引入 Fabric UI 依赖。
- `测试`：已覆盖默认 decode/encode、custom round-trip、非法范围、builder reset/setter/invalid state、旧 JSON 缺省字段兼容；`CAVES` 预览差异、参数调节与既有模式不变测试已随 preview runtime 补齐；3D runtime 已覆盖禁用不 carve、开启后 carve、landness gate、deterministic 和 `EndDensity` 端到端接入；graph/chamber MVP 已覆盖禁用、节点强度、确定性、seed offset 差异，以及 spectacle bias / loop-connectivity 对节点语义签名的影响。

暂缓项：

- `cave_carver_probability`、`deep_cave_carver_probability`、`ravine_carver_probability`：这些参数需要明确的 vanilla carver / noise-router bridge，不能只落 JSON。
- `large_ore_veins`：涉及 ore vein feature 与 registry/datapack 资源，不进入当前 preset JSON。
- `legacy_carver_distribution`：会影响 carver 高度分布策略，等 carver bridge 路径确定后再处理。
- RTF `minCaveBiomeDepth` TODO：上游本身未完成，暂不作为规格来源。

3. **Graph / Chamber 3D Runtime**
   - 在 `EndCaveField` 后续版本中引入洞厅节点、通道图、裂隙节点和局部 3D noise 细节。
   - 保持 `EndDensity` 的稳定 API，必要时增加内部 composer，而不是让 UI 或 preset 直接知道 density function 细节。

4. **Vanilla Bridge / Carver 集成**
   - 评估是否通过 vanilla density/noise-router bridge 承载更自然的洞穴。
   - ravine/carver 相关参数只在有明确生成路径后启用。

5. **群系与地下特征**
   - 地下群系和矿脉等 registry-bound 内容不进入 preset JSON。
   - 如需要配置，采用资源 key 或数据包层，避免把 holder 直接塞进 preset。

## 验证要求

每个新增地下配置都必须覆盖：

- 默认 decode / encode。
- custom round-trip。
- 非法范围 validation。
- builder reset / setter / invalid state。
- 旧 JSON 缺省字段兼容。
- `CaveTunnelConfig` 当前只要求配置层默认 worldgen 行为等价；`CAVES` 顶视预览已由新的 cave system/network/chamber runtime 覆盖，正式 3D cave carve MVP 已由 `EndCaveField` 补齐行为测试。
- 默认 worldgen 行为等价。
- 开启后 `EndDensity` 可观测 carve、deterministic、输出仍为 `0/1`。
- `ABYSS` / `CAVES` preview 随参数变化，且不影响 height / climate / biomes 等既有模式。

固定验证命令：

```text
:common:test
:neoforge:compileJava
:fabric:compileJava
```

Windows 当前仍建议通过 ASCII junction 运行 Gradle，规避中文路径导致的测试 worker 或 mappings 解析异常。
