# 2026-07-13 工作树集成与仓库卫生审查

> 文档状态：阶段性审查快照，不定义当前产品路线。
> 当前任务顺序以 [`../../PLAN.md`](../../PLAN.md) 为准。
> 本审查只读取 Git 与当前文件，不执行 stage、commit、reset、clean、恢复或删除。

## 1. 结论

当前工作树能够提供开发构建，但还不是可复现的 Git 快照。大量正式源码、测试、平台适配和中文文档只存在于未跟踪文件中；如果只克隆当前仓库版本，无法重建目前的 P2/P3、编辑器、Preset Library、地下骨架和兼容修复。

这不是要求立即把所有文件一次性提交。正确做法是先完成当前 ETF+RTF 实机回归和发布产物门禁，再按职责审查每个依赖集合，确保每个集成批次包含实现、测试、资源和文档，且不会把实验性完成度写成正式功能。

## 2. 2026-07-13 统计

| 状态 | 数量 |
| --- | ---: |
| 已跟踪且修改 | 71 |
| 已跟踪且删除 | 2 |
| 未跟踪 | 221 |

未跟踪文件按发布职责分类：

| 类别 | 数量 |
| --- | ---: |
| common 生产 Java | 103 |
| common 测试 Java | 75 |
| 中文文档与审查资料 | 40 |
| NeoForge 客户端适配 | 3 |

没有超过 1 MiB 的未跟踪文件。`.gradle-home/`、`.gradle-tmp/`、`.codegraph/`、各模块 `build/` 和 `neoforge/run/` 均被正确忽略，没有进入待提交范围。

## 3. 已确认风险

### 3.1 发布关键源码未跟踪

`verifyReleaseArtifacts` 当前列出 44 个 common 关键类：

- 8 个对应源码已跟踪。
- 36 个对应源码仍未跟踪。
- 没有关键 common 源码在磁盘上缺失。

平台关键入口中，Fabric 与 NeoForge 主入口已跟踪；以下三个 NeoForge 客户端关键类仍未跟踪：

- `EndTerraForgedClientScreens`
- `ExistingWorldPresetEditorLauncher`
- `LdLib2ActionBar`

现有 build 目录或本地 jar 含有这些类，只能证明本机曾构建成功，不能证明当前 Git 快照可复现。

### 3.2 严格卫生门禁当前必然失败

`crash-2026-07-04_23.29.44-server.txt` 已在工作树中删除，同时被当前 `.gitignore` 命中，但仍属于 Git 索引中的历史跟踪文件。`verifyRepositoryHygiene` 会把它报告为 tracked-but-ignored，直到未来明确的仓库集成操作把该删除状态纳入版本历史。

不要恢复崩溃文件来“让状态变绿”，也不要在当前审查中自动修改索引。

### 3.3 测试引用启发式

103 个未跟踪生产类中，86 个类名可在测试源码中找到直接引用；17 个没有直接类名引用：

- 11 个是 Minecraft screen/widget 渲染适配，主要由纯布局策略测试和真实客户端验收覆盖。
- 2 个是 `MixinChunkMap` / `NoiseRouterDataAccessor` 平台接线，需要结构测试与真实运行链路覆盖。
- 4 个纯逻辑或配置实现没有直接类名引用：`ClimateFalloff`、`AbyssPitConfigValidator`、`ContinentConfigValidator`、`OuterContinentsContinent`。

“没有直接类名引用”不等于“没有行为覆盖”：validator 可由上层 validator 测试间接消费，continent 可由 factory/heightmap 行为测试间接消费。集成前仍应逐个确认对应测试断言确实到达这些分支，不能只以类名搜索作为完成证据。

## 4. 集成依赖集合

以下是审查集合，不是自动 stage 或固定 commit 命令。每个集合在实际集成前都要重新查看 `git diff` 并运行相应门禁。

1. **构建与平台边界**：Gradle、metadata、access widener、common/platform mixin、release artifact gate。
2. **Preset 与配置格式**：`EndPreset`、Codec、Validator、Builder、旧 JSON 迁移、存储、配置测试。
3. **P2/P3 正式 worldgen**：中央保护、外部大陆、有限大陆体积、density/bootstrap、资源 JSON、确定性与边界测试。
4. **Preview 与编辑体验**：sampler、剖面、布局策略、编辑器、Preset Library、NeoForge LDLib2 bridge、语言资源和 UI 策略测试。
5. **地下骨架**：subsurface、abyss、cave graph、候选 mask、对应配置和测试。必须继续标明正式生成与 preview-only 能力，不能因源码进入版本历史就宣称地下系统完成。
6. **文档与许可**：目标、计划、架构、研究、手工验收清单、NOTICE 和历史状态标记。

这些集合存在编译依赖，不能机械地按目录拆分。例如 `EndPreset` 已引用地下配置，主编辑器已引用多个子编辑器；若要求每个中间提交都可编译，需要先建立真实依赖顺序，不能简单把所有 config 或所有 UI 单独拿走。

## 5. 收束门禁

在仓库进入可发布状态前必须同时满足：

1. ETF+RTF 和固定 seed 客户端验收完成，当前测试 jar 的行为有记录。
2. `:common:test`、`:neoforge:compileJava`、`:fabric:compileJava` 顺序通过。
3. `:verifyReleaseArtifacts --no-daemon` 重建双平台产物并通过；旧 Fabric jar 不算当前证据。
4. 每个预期发布的未跟踪源码、测试、资源和文档都经过归属审查。
5. `git diff --check` 无实际错误。
6. `:reportRepositoryHygiene` 不再出现无法解释的类别。
7. 准备发布提交时，`:verifyRepositoryHygiene` 不再报告 tracked-but-ignored 或未跟踪发布文件。

在这些证据齐全前，只能称为长期开发工作树，不能称为可发布源码快照。
