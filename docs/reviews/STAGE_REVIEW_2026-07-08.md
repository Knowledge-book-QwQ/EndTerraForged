# EndTerraForged 阶段性代码与架构审查报告

> 文档状态：历史审查快照，仅反映 2026-07-08 当时的工作树，不代表当前路线或完成状态。
> 当前目标与计划见 [`../../GOAL.md`](../../GOAL.md) 和 [`../../PLAN.md`](../../PLAN.md)。
> 日期：2026-07-08
> 范围：当前工作树中的 preset、worldgen、preview、地下洞穴、GUI、平台边界与文档结构。
> 结论：当前架构可以继续承载下一阶段开发；最大风险不在功能方向，而在地下编辑器参数继续膨胀、NeoForge 依赖解析链路需要持续监控、以及后续 3D 洞穴 runtime 接入前需要更明确的图网络边界。

## 当前进度

### 已形成的主线

- 平台路线已稳定为 **NeoForge-first + LDLib2 主体验**。
- `common` 继续承担 worldgen、preset codec、validator、builder、preview sampler 和可测试 GUI 结构。
- Fabric 保留为低维护编译边界，不新增官方高级 UI 组件库。
- RTF cave 已明确不作为主方案；地下路线转为 ETF 自有 **End Abyssal Cave System**。

### 已完成的能力

- `EndPreset` 已聚合大陆、地形、气候、群系布局、地下、侵蚀等配置。
- `ContinentConfig`、`TerrainConfig`、`ClimateConfig`、`BiomeLayoutConfig`、`SubsurfaceConfig` 等已经形成 codec / validator / builder / test 的稳定模式。
- 实时预览已覆盖 `HEIGHT`、`LANDNESS`、`LAYERS`、`BIOMES`、`ABYSS`、`CAVES`、气候通道等模式。
- 地下系统已有：
  - `AbyssPitConfig` + `EndSubsurface`，可接入 `EndDensity` 做深渊 carve。
  - `CaveSystemConfig` / `CaveNetworkConfig` / `CaveChamberConfig`，作为旗舰洞穴系统的 scalar 配置骨架。
  - `EndCavePreviewMask`，提供 deterministic 二维洞穴足迹预览。
  - 地下编辑器 Caves 参数区，可实时调参并查看 CAVES 预览。
- UI 已抽出 `EditorScreenWidgets`、`EditorScrollLayout`、`EditorColumnLayout`、`TerrainPreviewWidget`、`TerrainPreviewControls` 等轻量 helper。

## 架构审查

### 配置层

评价：健康。

- `EndPreset` 作为单一 preset 聚合点是正确方向。
- 每个新增配置基本都遵循 record + codec + validator + builder + round-trip 测试。
- `SubsurfaceConfig` 作为地下聚合 record 合理，`abyss`、`caves`、`cave_system`、`cave_network`、`cave_chambers` 分层明确。

后续要求：

- 新增 `CaveRiftConfig`、`CaveRiverConfig` 前必须先明确 runtime 消费者，不只落 JSON。
- 不把 biome holder、registry object、平台 UI 状态塞进 preset JSON。

### Runtime / Worldgen

评价：方向正确，但下一阶段风险变高。

- `EndDensity` 仍保持离散 `0/1` solid/void 判定，利于测试和稳定接入。
- `EndSubsurface` 已接正式 carve；`EndCavePreviewMask` 只做二维预览，没有假装是完整 3D 洞穴，这是正确边界。
- `EndWorldgenBootstrap` 负责发布 runtime access，符合“preset -> runtime”的方向。

主要风险：

- 下一阶段如果直接把二维 CAVES mask 接进 `EndDensity`，会制造错误语义和世界生成回归。
- 3D 洞穴需要先有 graph/chamber 节点 runtime，再接 `strength(x, yNorm, z, landness, terrainTopNorm)`。

### Preview

评价：健康。

- `TerrainPreviewSampler` 按 mode 延迟构建对应 runtime，避免所有模式都承担洞穴/深渊开销。
- `BIOMES` 与 runtime biome layout 对齐，`ABYSS` 与 `EndSubsurface` 对齐，`CAVES` 与 `EndCavePreviewMask` 对齐。
- preview tests 已覆盖参数变化、模式隔离和 ARGB 输出。

后续要求：

- 当 CAVES 拆分洞厅/裂隙/通道/地下河子 mask 后，preview 应继续使用同一 runtime math，而不是 UI 私有算法。

### GUI

评价：可继续推进，Reset / Done / Cancel 基础流程已收束。

- 子编辑器 + shared helper 的路线是对的，避免主编辑器堆满参数。
- 地下编辑器目前已经同时承载 Abyss 与 Caves 参数，短期可用。
- `SubsurfaceConfigEditorScreen` 使用独立 cave builder 暂存 UI 状态，提交/预览时组合回 `SubsurfaceConfig`，避免把 `SubsurfaceConfigBuilder` 膨胀成过多 scalar setter。
- 主编辑器、大陆、气候、群系布局、侵蚀、地下与地形层编辑器的 Reset 已统一为原地重置配置并重建当前 screen，保留分页 / 分区、滚动位置、预览模式 / scale，以及洞穴剖面轴向 / 偏移视角状态；Done / Cancel 不改变原有语义。

主要风险：

- 地下编辑器继续增加 rift / river / graph 参数后，单页会变得拥挤。
- 下一步应拆分为 Abyss / Caves 子页或分段控件，不要继续无上限纵向堆 slider。

### 平台边界

评价：健康。

- `CommonSourceBoundaryTest` 已约束 common 禁止直接 import `com.lowdragmc.*`、`net.fabricmc.*`、`net.neoforged.*`。
- LDLib2 通过 common 侧反射 fallback 和 NeoForge 桥接保持可选，不污染 common。
- Fabric 编译边界近期通过，说明 NeoForge-first 策略未破坏低维护兼容。

已收束风险：

- NeoForge `:compileJava` 曾表现为 Loom/TinyRemapper remap 阶段不稳定；线程 dump 和 offline 失败日志已确认主要风险在依赖解析 / MCP 临时下载链路。仓库 content filter 修复后编译已通过，但后续仍需保留诊断流程。

## 代码审查发现

### P1：地下编辑器即将膨胀

影响：后续维护和用户体验。

当前 `SubsurfaceConfigEditorScreen` 已同时展示 Abyss 与 Caves 大量参数。继续追加 rift、river、graph 参数会让单页滚动变得难用，也会让 UI 逻辑继续变长。

建议：

- 下一阶段先做 UI 分段收束。
- 优先拆成 Abyss / Caves 两个内部页或同屏分组导航。
- 保留当前 shared helper，不急着引入重型继承层。

进展：

- Abyss / Caves 顶部分区已落地。
- Reset 流程已改为保留当前分区、预览与剖面视角状态，降低调参过程中的拖拽阻力。

### P1：3D 洞穴 runtime 尚未成型

影响：正式 worldgen 接入。

当前 CAVES 是二维顶视足迹，适合预览和调参，不适合直接 carve 世界。

建议：

- 先实现 graph/chamber 节点 runtime。
- 再扩展到 3D strength。
- 最后接 `EndDensity`，并加默认等价、开启差异、确定性、0/1 输出测试。

### P2：`world.config` 包已经很大

影响：可发现性和长期维护。

`world.config` 当前已有大量 record / builder / validator / storage / library。短期可接受，因为配置集中便于查找；长期会变重。

建议：

- 暂不急着迁移，避免大规模重排。
- 后续当洞穴配置继续增加时，可以考虑 `world.config.subsurface` 或 `world.config.cave` 子包，但要一次性迁移并更新测试。

### P2：NeoForge remap / 依赖解析链路不稳定

影响：NeoForge-first 验证闭环。

近期 `:common:test`、`:fabric:compileJava` 稳定通过。`NeoForge :compileJava` 曾多次表现为卡在 Loom/TinyRemapper remap，但线程 dump 显示 Gradle daemon 实际在 `SpecContextImpl.getDependentMods` 的远程 metadata 请求中等待；offline 日志进一步暴露 NeoForge MCP 会将 Mojang `client.txt` 下载到临时目录，完全离线时仍会失败。

已处理：

- 为 Gradle 仓库添加 content filter，避免 NeoForge 配置阶段在无关仓库上反复 HEAD 探测。
- 显式补齐 Fabric / Architectury 仓库，避免全局仓库过滤误伤 Fabric 低维护边界。
- 修复后 `:neoforge:compileJava --configure-on-demand --no-daemon --stacktrace` 已通过。

后续建议：

- 若 NeoForge 再次长时间等待，优先抓线程 dump 和 offline 失败点，而不是直接归因于 TinyRemapper 或项目源码。
- 保留 `W:` ASCII 路径 + JDK 21 + 仓库内 `TEMP/TMP` 的验证方式。

### P3：根目录文档较多

影响：信息发现。

根目录已有 `README.md`、`PLAN.md`、`ROADMAP.md`、`DEVELOPMENT.md`、`NOTICE.md`、`UNDERGROUND_RESEARCH.md`，再继续放阶段报告会变乱。

已处理：

- 新增 `docs/PROJECT_STRUCTURE.md`。
- 新增 `docs/reviews/STAGE_REVIEW_2026-07-08.md`。
- 后续阶段报告统一放入 `docs/reviews/`。

## 阶段性验证记录

近期已通过：

- `:common:test --configure-on-demand --no-daemon`
- `:fabric:compileJava --configure-on-demand --no-daemon`
- `DatapackResourceValidationTest`
- `git diff --check`，仅 CRLF 提示

本轮已收束：

- `:neoforge:compileJava --configure-on-demand --no-daemon --stacktrace`：通过，耗时约 2 分 31 秒。输出仍包含 Loom 的 `staticInstance` remap 警告与 NeoForge 入口 unchecked 注记，但没有 Java 编译错误。
- 编辑器 Reset 流程收束后，`:common:test --configure-on-demand --no-daemon`、`:fabric:compileJava --configure-on-demand --no-daemon` 与 `:neoforge:compileJava --configure-on-demand --no-daemon` 均通过；`git diff --check` 通过，仅 CRLF 提示。

## 后续计划

### 下一段优先级

1. 地下编辑器 UI 收束：拆 Abyss / Caves 分区或子页。
2. 设计并实现 cave graph/chamber 节点 runtime。
3. 将 CAVES 从二维足迹扩展到 3D strength。
4. 接入 `EndDensity` cave carve，并加行为测试。
5. 后续再补 rift、river、liquid、地下群系和结构挂点。

### 质量规则

- 每个新增配置必须有 codec、validator、builder、默认值、round-trip、非法范围、旧 JSON 缺省兼容测试。
- 每个 preview mode 必须有参数变化测试和既有模式隔离测试。
- 每次触碰 common/platform 边界后运行 `:common:test` 和 `:fabric:compileJava`。
- NeoForge 依赖解析 / remap 链路继续单独跟踪，不把工具链缓存问题当作业务功能失败。

## 总结

当前项目已经从“参数补齐”进入“系统成型”阶段。配置层、预览层和 UI 调参入口基本成型，下一阶段的关键不是继续堆参数，而是把洞穴系统从二维预览推进到可测试的 3D graph/runtime。只要继续守住 preset 边界、platform 边界和测试闭环，架构可以支撑后续的大型洞穴系统。
