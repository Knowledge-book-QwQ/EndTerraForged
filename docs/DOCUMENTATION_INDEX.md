# EndTerraForged 文档索引

> 文档状态：当前有效。
> 最近更新：2026-07-23。
> 用途：告诉开发者和后续模型每份文档的权威范围，避免把历史材料当成当前计划。

## 1. 固定阅读顺序

每次上下文恢复、目标模式启动或大段开发开始前按顺序阅读：

1. [`../AGENTS.md`](../AGENTS.md)：仓库硬规则、构建命令和平台约束。
2. [`../GOAL.md`](../GOAL.md)：长期产品目标、阶段目标和完成定义。
3. [`../MEMORY.md`](../MEMORY.md)：长期决策、踩坑和兼容知识。
4. [`../PLAN.md`](../PLAN.md)：当前阶段、下一批任务、阻塞和风险。
5. 本索引中的相关技术规格。
6. 当前代码、测试、资源、日志与 `git status`。

发生冲突时按以下优先级判断：

`AGENTS.md` > `GOAL.md` > `MEMORY.md` > `PLAN.md` > 当前技术规格 > 历史文档。

代码和资源决定“现在实际实现了什么”；目标文档决定“接下来应该实现什么”。文档不得用计划替代代码事实，也不得用历史实现限制已更新的产品决策。

## 2. 当前权威文档

| 文档 | 权威范围 | 维护规则 |
| --- | --- | --- |
| [`../AGENTS.md`](../AGENTS.md) | 工程规则、模块边界、构建与验证 | 规则改变时更新 |
| [`../GOAL.md`](../GOAL.md) | 长期目标、阶段、完成定义 | 产品决策改变时更新 |
| [`../PLAN.md`](../PLAN.md) | 当前执行队列、已完成事实、阻塞 | 每个大段开发后更新 |
| [`../MEMORY.md`](../MEMORY.md) | 长期决策、陷阱、兼容经验 | 只记录长期有效信息 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | common/platform、数据流、线程和扩展边界 | 架构契约改变时更新 |
| [`RTF_CORE_REUSE_RESEARCH.md`](RTF_CORE_REUSE_RESEARCH.md) | RTF MIT 大陆、地貌区域、地貌族、形状化特征、群岛与侵蚀复用，以及 ETF 高质量地表重构规格 | RTF 地表复用边界或阶段 B 实现决策改变时更新 |
| [`P4_7_EROSION_ALGORITHM_RESEARCH.md`](P4_7_EROSION_ALGORITHM_RESEARCH.md) | P4.7 论文、开源仓库、许可证、候选矩阵、统一 benchmark、缓存与 JFR 性能架构 | 算法候选、上游许可/成熟度或 P4.7 选型改变时更新；当前尚未选定正式算法 |
| [`P4_7_ANALYTICAL_EROSION_SPEC.md`](P4_7_ANALYTICAL_EROSION_SPEC.md) | P4.7 低成本 local analytical baseline 的 runtime API、量纲、零影响边界、缓存、preview 与验收门禁 | baseline 契约改变时更新；不代表正式算法已选定，runtime 待实现 |
| [`reviews/P4_7_BASELINE_2026-07-23.md`](reviews/P4_7_BASELINE_2026-07-23.md) | P4.7-0 smoke-profile traversal 的首个可重复观测与限制 | 每次基线观测或门禁范围改变时新增/更新 |
| [`ETF_TO_RTF_IMPROVEMENT_TRACKER.md`](ETF_TO_RTF_IMPROVEMENT_TRACKER.md) | ETF 中发现、验证并准备人工回送 RTF 的改进事项唯一状态台账 | 每次发现通用改进、RTF 状态变化或完成迁移时更新；不得从 ETF 任务直接修改 RTF |
| [`TALL_WORLD_OPTIMIZATION_REVIEW.md`](TALL_WORLD_OPTIMIZATION_REVIEW.md) | 高世界、热路径、C2ME 与精确空 cell 优化审查 | 性能架构或基准结论改变时更新 |
| [`CONTENT_PACK_SPEC.md`](CONTENT_PACK_SPEC.md) | ETF Worldgen Content Pack API v1 当前规格 | Content Pack 契约改变时更新；当前待实现 |
| [`TERRA_CONTENT_COMPATIBILITY_RESEARCH.md`](TERRA_CONTENT_COMPATIBILITY_RESEARCH.md) | Terra/ReimagEND 能力、许可和适配边界 | 上游或兼容决策改变时更新 |
| [`../UNDERGROUND_RESEARCH.md`](../UNDERGROUND_RESEARCH.md) | 地下算法调研和设计依据 | 只作研究参考；其中候选 MVP/下一步措辞不定义当前队列 |
| [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) | 目录和关键包导航 | 包结构明显变化时更新 |
| [`../NOTICE.md`](../NOTICE.md) | 许可、上游参考与代码来源 | 引入/改写第三方实现时更新 |
| [`../README.md`](../README.md) | 对外项目入口和最短构建说明 | 保持简洁，不承载完整路线 |
| [`../DEVELOPMENT.md`](../DEVELOPMENT.md) | 贡献流程与本地开发入口 | 不重复 AGENTS 的全部规则 |

## 3. 历史文档

历史文档用于追溯当时结论，不参与当前路线决策。

| 文档 | 状态 |
| --- | --- |
| [`../ROADMAP.md`](../ROADMAP.md) | 旧路线入口，已停止维护并指向 `GOAL.md` |
| [`BIOME_PACK_SPEC.md`](BIOME_PACK_SPEC.md) | 已退役的 biome-only 草案，指向 Content Pack 新规格 |
| [`reviews/STAGE_REVIEW_2026-07-08.md`](reviews/STAGE_REVIEW_2026-07-08.md) | 2026-07-08 阶段审查快照 |
| [`reviews/WORKTREE_INTEGRATION_REVIEW_2026-07-13.md`](reviews/WORKTREE_INTEGRATION_REVIEW_2026-07-13.md) | 2026-07-13 工作树集成与仓库卫生快照 |
| [`../.trae/specs/add-biome-climate-layering/spec.md`](../.trae/specs/add-biome-climate-layering/spec.md) | 已完成的气候群系分层历史规格 |
| [`../.trae/specs/add-biome-climate-layering/tasks.md`](../.trae/specs/add-biome-climate-layering/tasks.md) | 对应历史任务列表 |
| [`../.trae/specs/add-biome-climate-layering/checklist.md`](../.trae/specs/add-biome-climate-layering/checklist.md) | 对应历史验收清单 |

历史文档顶部必须写明“历史快照，不代表当前计划”。不要为了符合新路线而改写历史结论主体；新结论写入当前权威文档。

## 4. 验收清单

本轮 RTF 只读审查记录包括：

- [`reviews/RTF_LATEST_WORKTREE_REVIEW_2026-07-18.md`](reviews/RTF_LATEST_WORKTREE_REVIEW_2026-07-18.md)：火山 artifact、生命周期、RT4 流体路由与性能研究吸收边界。
- [`reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md`](reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md)：AREA ownership、RIDGE bounded overlay、Top-3 山系组合和端点数学的阶段决策。

两份 review 都是决策证据，不改变 `GOAL.md` 的长期目标，也不替代 `PLAN.md` 的当前执行队列。

[`reviews/PRESET_LIBRARY_SMOKE_TEST.md`](reviews/PRESET_LIBRARY_SMOKE_TEST.md)、
[`reviews/WORLDGEN_BASELINE_PROTOCOL.md`](reviews/WORLDGEN_BASELINE_PROTOCOL.md) 与
[`reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md)、
[`reviews/STANDARD_PERFORMANCE_PROTOCOL.md`](reviews/STANDARD_PERFORMANCE_PROTOCOL.md) 是仍有效的手工测试清单，
不是路线文档。完成一次真实客户端验收后应记录：

- 测试日期、模组版本和加载器版本。
- 单人/多人上下文。
- worldDir 与 active preset 结果。
- `latest.log` 关键状态。
- 失败项与对应 issue/计划项。

## 5. 文档职责规则

- `GOAL.md` 不记录每轮测试日志。
- `PLAN.md` 不保存完整项目历史。
- `MEMORY.md` 不复制代码中显而易见的类列表。
- 技术规格必须标注“当前有效”“待实现”或“历史”。
- 调研文档说明为什么选择某条技术路线，不宣称功能已落地。
- ETF 向 RTF 的反哺状态只在 `ETF_TO_RTF_IMPROVEMENT_TRACKER.md` 维护；其他调研只提供证据并链接该台账。
- README 只提供定位、状态、文档入口、构建和许可。
- 任何“已完成”必须能在当前代码、测试、资源或手工验收记录中定位。
- 中文路线、计划、调研和架构文档保持中文；Java 标识符、命令和资源键保持原文。

## 6. 更新检查

文档大改后至少检查：

1. 相对 Markdown 链接可解析。
2. 不再把 `min_y=-2032,height=4064` 描述为当前默认。
3. 不再承诺 Fabric 官方完整高级 UI。
4. 不再把 RTF cave 作为 ETF 地下主线。
5. 不把 preview-only 液体 mask 描述为正式方块生成。
6. 不把尚未实现的 Content Pack、动态 world spec 或 3D 预览写成已完成。
7. `GOAL.md` 与 `PLAN.md` 的当前阶段一致。
8. `MEMORY.md` 只保留长期有效信息。
