# EndTerraForged 项目规则

## 项目概况

- 项目名：EndTerraForged
- 当前版本：0.1.7
- Minecraft：1.21.1
- Java：21
- 架构：Architectury 多模块
- 模块：
  - `common`：跨加载器共用代码，包含配置、Codec、Validator、Builder、预览采样、地形生成核心逻辑。
  - `neoforge`：NeoForge 平台入口与 LDLib2 高级 UI 桥接，是官方主体验。
  - `fabric`：Fabric 平台入口与低维护兼容边界，不承诺官方完整高级 UI。
- 映射：Official Mojang mappings，通过 Architectury Loom 配置。
- 关键依赖：
  - Architectury Plugin 3.4.164
  - Architectury Loom 1.11.445
  - NeoForge 21.1.219
  - Fabric Loader 0.16.3
  - Fabric API 0.102.0+1.21.1
  - LDLib2 2.2.6

## 产品路线

- 路线采用 NeoForge-first + LDLib2 主体验。
- `common` 禁止直接依赖 LDLib2、NeoForge 或 Fabric 专用 API。
- Fabric 保留编译和基础兼容边界，复杂 UI 可留给社区移植。
- RTF R9.3.6 作为参数、默认值和配置语义基线；RTF R9.6 作为稳定核心实现基线。RTF 是同一维护方的 MIT 项目，无平台/注册表/`GeneratorContext`/`Cell`/水位/河流缓存耦合的纯数学可直接移植，但必须保留版权头、记录来源与 ETF 改动并更新 `NOTICE.md`。
- 不复制 RTF UI 设计、布局结构或视觉呈现。
- 地下系统不走 RTF 洞穴方案，改用 EndTerraForged 自研的庞大洞穴、裂隙、深渊与垂直剖面路线。
- 当前最高优先级是先完成最新 jar 的中央保护、底面、直壁、岩浆、RTF/C2ME 短回归，再按 `RTF_ADVANCED -> TerrainRegionPlan -> 地貌族 -> ridge/compact 山脉火山 -> uplift -> 群岛海岸 -> 侵蚀排水 -> preview/UI -> 性能兼容` 重建阶段 B 高质量地表。当前 `EndTerrainComposer` 的单一 selector + 通用噪声层只是 legacy 原型，不再继续大段修补；原版主岛和外围区域仍冻结到最后阶段。
- ETF Worldgen Content Pack API 负责 biome、Content Profile、surface palette 和 feature 扩展；外部 generator 不得与 ETF 同时接管 End density。
- Terra/ReimagEND 只通过独立内容适配包兼容，不在 core 中实现通用 Terra 解释器或复制 GPL 内容。

## 文档事实源

- 开始大段开发或上下文恢复时，按 `AGENTS.md` -> `GOAL.md` -> `MEMORY.md` -> `PLAN.md` -> 相关技术文档 -> 当前代码与 `git status` 的顺序读取。
- `GOAL.md` 是目标模式唯一入口；`PLAN.md` 只记录当前执行队列；`MEMORY.md` 只记录长期决策与踩坑。
- `ROADMAP.md`、历史 review 和 `.trae/specs` 不再作为当前路线来源。
- 完整文档权威关系见 `docs/DOCUMENTATION_INDEX.md`。

## 编码规范

- Java 代码遵循 Google Java Style 的主要约定：4 空格缩进、UTF-8、`--release 21`。
- 公共配置、运行时 worldgen、预览采样、UI 层保持清晰边界。
- 新增可序列化配置必须配套：
  - Codec
  - Validator
  - Builder
  - 默认值
  - round-trip 测试
  - invalid validation 测试
  - worldgen 或 preview 行为测试
- 只把可序列化 scalar、threshold、range 放进 preset JSON；registry-bound holder 不写入 preset JSON。
- 热路径避免不必要对象创建和重复计算。
- 注释解释“为什么”，不解释代码表面行为。

## UI 约定

- NeoForge 使用 LDLib2 作为高级 UI 主线。
- `common` 可包含跨平台 Minecraft client GUI 基础组件，但不得直接引用 LDLib2。
- 高级编辑器保持分页 + 子编辑器结构，避免把所有参数塞进一个页面。
- 实时预览必须使用与 worldgen/runtime 尽量一致的数学路径，避免 slider 只改 JSON 但预览不可见。
- 错误状态必须可见，不允许静默失败。
- 新增 GUI 文案、错误状态或预览模式时，必须同步更新 `en_us.json` 与 `zh_cn.json`；`DatapackResourceValidationTest` 会校验两个语言文件 key 集合完全一致。

## 构建与验证

优先使用 Gradle Wrapper。

```powershell
.\gradlew.bat :common:test --configure-on-demand --no-daemon
.\gradlew.bat :neoforge:compileJava --configure-on-demand --no-daemon
.\gradlew.bat :fabric:compileJava --configure-on-demand --no-daemon
```

发布包 / transform 验证不要使用 `--configure-on-demand`，否则 Architectury transform 任务可能拿不到 mappings provider。推荐使用根任务：

```powershell
.\gradlew.bat :verifyReleaseArtifacts --no-daemon
```

该任务会依赖 Fabric / NeoForge 的 `remapJar`，并检查 `common` 的 `transformProductionFabric` / `transformProductionNeoForge` jar 以及最终 Fabric / NeoForge remap jar，确认平台 metadata、平台入口类和关键 common 类存在。根任务带 `--configure-on-demand` 会快速失败，避免进入 Architectury transform 的 mappings provider 异常。
发布包门禁还会检查关键资源：common mixin、`pack.mcmeta`、中英文语言文件、End 核心 datapack JSON 与样例 biome climate preset JSON。Fabric 最终 jar 额外检查 `endterraforged.accesswidener`；NeoForge 最终 jar 不强制包含 access widener。新增必须进入最终 jar 的关键资源时，同步更新 `verifyReleaseArtifacts` 的资源清单。
`verifyReleaseArtifacts` 还会枚举 `common/src/main/resources` 并要求每个资源都被分类为最终 jar 必带资源或 common transform-only 资源；新增 common resource 时必须同步更新资源分类清单，避免静默漏包。
发布包门禁会读取最终 Fabric / NeoForge jar 内的 metadata 内容，校验 mod id、mod version、license、入口类、mixin 列表、项目 URL、Minecraft 版本范围以及 Fabric Loader / Fabric API / NeoForge 依赖范围是否与 `gradle.properties` 和项目约定一致；修改版本或依赖属性时必须让 metadata 模板继续使用 Gradle 占位符展开，且最终 jar 内不得残留 `${...}` 占位符。

`CommonSourceBoundaryTest` 会扫描 common main Java 与 common resources，禁止直接引用 LDLib2、Fabric 或 NeoForge 包名。`LdLib2ActionBars` 中用于反射探测 NeoForge LDLib2 bridge 的字符串是显式 allowlist 例外；新增例外必须能说明为什么 common 不会获得平台编译 / 运行时依赖。

不要在同一个工作树上并行运行任何 Gradle 构建。开发编译、测试、remap 和发布验证都会读写 Gradle cache 或 `common` 的 jar / transform 产物，并行运行可能生成空 jar 或 stale jar，导致平台模块找不到 common 类。

发布前执行仓库卫生检查：

```powershell
git status --short --ignored
.\gradlew.bat :reportRepositoryHygiene --no-daemon
.\gradlew.bat :verifyRepositoryHygiene --no-daemon
```

确认 `.gradle-home/`、`.gradle-tmp/`、`.codegraph/`、`build/`、`run/`、logs、crash reports 和 heap dumps 都没有进入待提交范围。
`reportRepositoryHygiene` 只做分组报告，不会失败，适合开发中随时查看；`verifyRepositoryHygiene` 是发布前严格门禁，会在存在已跟踪但被 `.gitignore` 命中的文件或仍有未跟踪发布相关文件时失败。当前长期开发工作树很脏时不要把严格任务当作日常编译门禁，等准备 stage / release commit 前再运行。

在 Windows 中文路径下，Gradle/Architectury 测试 worker 可能异常退出。当前推荐通过 ASCII 盘符映射运行：

```powershell
$repo = (Get-Location).ProviderPath
& subst.exe W: /D 2>$null
& subst.exe W: "$repo"
$env:JAVA_HOME='F:\zulu21.30.15-ca-jdk21.0.1-win_x64'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:TEMP='W:\.gradle-tmp'
$env:TMP='W:\.gradle-tmp'
Set-Location 'W:\'
.\gradlew.bat :common:test --configure-on-demand --no-daemon
```

## 工作流

- 修改代码前先阅读相关文档和当前代码，不依赖压缩前记忆直接动手。
- 每轮保持一个可独立验证的功能块。
- 每轮结束前尽量运行 `:common:test` 和平台编译检查。
- 修改平台边界时必须额外运行 `:fabric:compileJava`。
- 文档更新优先保持中文，尤其是路线、调研、进度和架构说明。
- 不回滚用户或其他轮次留下的未提交改动，除非用户明确要求。
