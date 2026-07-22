# EndTerraForged 项目结构索引

> 文档状态：当前有效。
> 最近更新：2026-07-13。
> 本文只帮助定位代码和文档，不定义产品路线或完成状态。

## 根目录

| 路径 | 用途 |
| --- | --- |
| `AGENTS.md` | 工程规则、构建命令和架构硬约束 |
| `GOAL.md` | 长期产品目标、阶段目标和目标模式入口 |
| `PLAN.md` | 当前执行队列、已完成事实和阻塞 |
| `MEMORY.md` | 长期决策、陷阱和兼容经验 |
| `README.md` | 对外项目入口 |
| `DEVELOPMENT.md` | 贡献流程与常用验证入口 |
| `NOTICE.md` | 许可和上游参考说明 |
| `UNDERGROUND_RESEARCH.md` | 地下技术调研资料 |
| `ROADMAP.md` | 已退役旧路线说明 |
| `docs/` | 架构、规格、索引和审查资料 |
| `RTF参考/` | 本地只读 RTF 参考代码，不属于 ETF 源码 |

完整文档权威关系见 [`DOCUMENTATION_INDEX.md`](DOCUMENTATION_INDEX.md)。

当前关键技术文档：

- [`ARCHITECTURE.md`](ARCHITECTURE.md)：common/platform、worldgen、preview 和扩展边界。
- [`CONTENT_PACK_SPEC.md`](CONTENT_PACK_SPEC.md)：当前 Content Pack API v1 规格。
- [`TERRA_CONTENT_COMPATIBILITY_RESEARCH.md`](TERRA_CONTENT_COMPATIBILITY_RESEARCH.md)：Terra/ReimagEND 兼容与许可依据。
- [`TALL_WORLD_OPTIMIZATION_REVIEW.md`](TALL_WORLD_OPTIMIZATION_REVIEW.md)：高世界、热路径与 C2ME 优化边界。
- [`reviews/STANDARD_PERFORMANCE_PROTOCOL.md`](reviews/STANDARD_PERFORMANCE_PROTOCOL.md)：Standard 固定矩阵与 JFR 性能采集清单。
- [`BIOME_PACK_SPEC.md`](BIOME_PACK_SPEC.md)：已退役的 biome-only 草案入口。

## Gradle 模块

### `common/`

跨平台业务核心：

- preset/config/Codec/Validator/Builder。
- 大陆、地形、气候、侵蚀、群系布局、地下和 density runtime。
- preview sampler、palette、2D/剖面数据。
- common GUI screens/widgets 与纯 UI policy。
- 资源、mixin 和 JUnit 测试。

### `neoforge/`

官方 NeoForge 体验：

- 平台初始化和 mixin。
- 暂停菜单与已有世界编辑入口。
- worldDir、toast 和 screen adapter。
- LDLib2 action bar bridge 与开发 runtime 依赖。

### `fabric/`

低维护兼容边界：

- Fabric 初始化、mixin 和 access widener。
- common 跨平台编译验证。
- 社区高级 UI 移植基础。

## common 主要包

根包：`common/src/main/java/endterraforged/`

| 包 | 职责 |
| --- | --- |
| `client.gui.screen` | 主编辑器、子编辑器、Preset Library 和 common UI policy |
| `client.gui.widget` | 预览、布局、滑块、滚动和可复用 widget helper |
| `mixin` | Minecraft worldgen/client 接入点 |
| `util` | 通用工具与随机/哈希辅助 |
| `world.config` | `EndPreset`、配置 record、Codec、Validator、Builder 和存储 |
| `world.continent` | 宏观大陆与 landness |
| `world.heightmap` | surface、terrain composition、density、subsurface 和 bootstrap |
| `world.climate` | 温度、湿度、风与 runtime access |
| `world.filter` | 侵蚀与地形 filter |
| `world.cave` | 自研 3D cave field、region graph 和 preview mask |
| `world.preview` | 2D/剖面 preview sampler、mode、palette 和 settings |
| `world.level.biome` | biome source、layout、selector 和 runtime access |
| `world.noise` | 可组合噪声 primitive |
| `world.floatingislands` | 额外浮岛 overlay |
| `world.river` / `world.lake` | 地表河流与湖泊逻辑 |

具体职责边界见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

## 测试目录

根目录：`common/src/test/java/endterraforged/`

| 目录 | 重点 |
| --- | --- |
| `architecture` | common/platform 依赖、split package 和源码边界 |
| `world.config` | Codec、Validator、Builder、preset 与 storage |
| `world.preview` | preview 模式、采样差异与隔离 |
| `world.cave` | graph/field/mask、确定性与边界 |
| `world.heightmap` | terrain、density、subsurface 与缓存语义 |
| `client.gui.screen` | 编辑器纯策略和完成/启动行为 |
| `client.gui.widget` | 布局、滚动和组件边界 |
| `mixin` | 注入签名、client mixin 注册与共享 worldgen 调用点组合规则 |
| `resources` | JSON、语言 key、datapack 与资源引用 |

## 资源目录

`common/src/main/resources/` 包含：

- `data/minecraft/dimension_type/the_end.json`：当前 Standard 512 dimension type。
- `data/minecraft/worldgen/noise_settings/the_end.json`：当前 Standard 512 noise settings 与 ETF density 接入。
- `data/minecraft/dimension/the_end.json`：End generator 与 ETF biome source。
- `assets/endterraforged/lang/en_us.json` 和 `zh_cn.json`：中英文界面文案。
- common mixin、`pack.mcmeta` 和样例资源。

新增 common 资源时必须同步更新 release artifact 资源分类，否则 `verifyReleaseArtifacts` 会失败。

## 历史与参考资料

- `docs/reviews/`：阶段审查和手工验收清单。
- `.trae/specs/`：历史功能规格，不能当作当前路线。
- `RTF参考/ReTerraForged-R9.3.6/`：详细参数和旧实现语义参考。
- `RTF参考/ReTerraForged-R9.6/`：核心逻辑思路参考。

参考目录保持只读语义。直接改写 MIT 代码时按 [`../NOTICE.md`](../NOTICE.md) 记录来源；不得复制 RTF UI。
