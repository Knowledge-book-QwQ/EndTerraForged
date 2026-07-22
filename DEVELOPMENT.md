# EndTerraForged 开发与贡献

> 工程硬规则以 [`AGENTS.md`](AGENTS.md) 为准，长期产品方向以 [`GOAL.md`](GOAL.md) 为准。本文只提供贡献流程和常用入口，不复制完整规则。

## 1. 环境

- Minecraft 1.21.1
- Java 21
- Architectury 多模块
- Official Mojang mappings
- NeoForge 21.1.219
- Fabric Loader 0.16.3
- Fabric API 0.102.0+1.21.1
- LDLib2 2.2.6，仅 NeoForge 高级 UI 使用

使用 `gradlew.bat`，不要依赖全局 Gradle。

## 2. 开始工作

1. 阅读 [`docs/DOCUMENTATION_INDEX.md`](docs/DOCUMENTATION_INDEX.md) 指定的权威文档。
2. 检查 `git status --short`；不要覆盖来源不明的现有修改。
3. 阅读相关实现、测试和资源，确认需求没有与现有功能重复。
4. 复杂 worldgen、跨平台或 UI 改动先更新对应技术规格和验收标准。
5. 将工作拆成可独立编译、测试和回滚的功能块。

## 3. 实现顺序

新增配置或生成能力按以下顺序落地：

1. runtime/worldgen 数学与消费路径。
2. Codec、Validator、Builder 和兼容默认值。
3. 定点测试与可观察生成差异。
4. preview 复用或严格对齐 runtime 数学。
5. common 编辑器状态与布局策略。
6. NeoForge LDLib2 bridge 和真实客户端验证。
7. Fabric 编译边界验证。

不要先做只有滑块和 JSON 的空参数，也不要用 preview-only mask 代替正式生成。

## 4. 模块职责

- `common`：preset、配置、worldgen、runtime、preview、通用 GUI 和纯策略。
- `neoforge`：平台事件、worldDir、toast、screen adapter、LDLib2 和官方客户端体验。
- `fabric`：基础平台接线、编译兼容和社区移植边界。

详细边界见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 5. 验证

定点测试通过后，按顺序运行：

```powershell
.\gradlew.bat :common:test --configure-on-demand --no-daemon
.\gradlew.bat :neoforge:compileJava --configure-on-demand --no-daemon
.\gradlew.bat :fabric:compileJava --configure-on-demand --no-daemon
```

触及发布包、资源、metadata 或平台入口时运行：

```powershell
.\gradlew.bat :verifyReleaseArtifacts --no-daemon
```

该任务不得带 `--configure-on-demand`。同一工作树禁止并行 Gradle，避免 common transform/remap jar 变成 stale 或空产物。

发布前运行：

```powershell
git status --short --ignored
.\gradlew.bat :reportRepositoryHygiene --no-daemon
.\gradlew.bat :verifyRepositoryHygiene --no-daemon
```

当前长期开发工作树未收束时，`verifyRepositoryHygiene` 可能按设计失败；它是发布门禁，不是日常编译任务。

## 6. Windows 中文路径

Gradle/Architectury worker 在中文路径下异常时，使用 [`AGENTS.md`](AGENTS.md) 中的 ASCII `subst` 流程。映射与构建必须在同一 PowerShell 会话内执行，并设置 JDK 21、`TEMP` 和 `TMP`。

## 7. 客户端与兼容验证

自动测试不能替代以下验证：

- 新世界正式 worldgen 与 preview 对比。
- 原版主岛、末影龙、返回门和折跃门流程。
- 已有世界暂停菜单、Preset Library、损坏文件和重进世界生效。
- LDLib2 组件、缩放、滚动、错误状态和低分辨率屏幕。
- C2ME 并行生成、主流末地模组和用户超大型整合包。

Preset Library 手工清单见 [`docs/reviews/PRESET_LIBRARY_SMOKE_TEST.md`](docs/reviews/PRESET_LIBRARY_SMOKE_TEST.md)。

## 8. 提交

提交格式：`<type>: <subject>`，type 使用 `feat|fix|refactor|chore|docs|test|perf`。subject 使用英文祈使句、小写开头、不加句号；正文说明改动原因和影响。

提交前确认：

- 功能块可独立编译和回滚。
- 相关测试与模块门禁通过。
- 中英文语言 key 同步。
- 新资源进入 release artifact 分类。
- 文档中的“已完成”与当前代码事实一致。
- 新增长期决策或踩坑已更新 [`MEMORY.md`](MEMORY.md)。
