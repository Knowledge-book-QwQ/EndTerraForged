# ETF 向 RTF 反哺改进台账

> 文档状态：当前有效，ETF 向 RTF 反哺事项的唯一状态台账。
> 最近更新：2026-07-19。
> 适用仓库：EndTerraForged 负责发现、记录和验证；ReTerraForged 由独立开发流程人工接入。
> 安全边界：ETF 开发任务只读 RTF 工作树，不直接修改、提交或构建 RTF。

本轮只读查重基线：

- RTF 工作树：`C:\Users\klbook\.codex\worktrees\e95f\ReTerraForged`
- 分支：`codex/r10x-volcano-rt4-fluid-routing`
- HEAD：`407faec`
- 检查时间：2026-07-18；当次 `git status --short` 无输出

RTF 工作树、分支或 HEAD 变化后，`OBSERVED` 和 `READY_FOR_RTF_REVIEW` 条目必须重新查重，不能
依据本次快照直接修改未来版本。

## 1. 用途

EndTerraForged 与 ReTerraForged 由同一维护方开发，两边应共享可复用的正确性、性能和兼容经验。
本文件专门记录“ETF 中发现或验证、值得带回 RTF”的改进，解决以下问题：

- 反哺结论散落在 review、性能调研和 `MEMORY.md` 中，后续容易遗漏。
- RTF 已经实现的能力可能被重复列为待办。
- 尚未验证的 ETF 想法可能被误写成 RTF 应立即采用的方案。
- 直接在 ETF 任务中修改 RTF 容易污染两个脏工作树和各自的测试证据。

本文件不替代 ETF 的 [`../PLAN.md`](../PLAN.md)，也不替代 RTF 自己的路线、测试和发布门禁。
ETF 当前任务优先级仍以 `PLAN.md` 为准；反哺项只有进入独立 RTF 开发轮次后才占用 RTF 排期。

## 2. 状态与优先级

每条事项必须使用以下状态之一：

| 状态 | 含义 |
| --- | --- |
| `OBSERVED` | 已发现问题或机会，但证据还不足以形成方案 |
| `ETF_PROVEN` | ETF 已有自动测试、运行证据或 profiler 证据支持 |
| `READY_FOR_RTF_REVIEW` | 方案、兼容边界和 RTF 验收门禁已明确，可进入独立 RTF 评审 |
| `RTF_EXPERIMENT` | 已在独立 RTF 分支实验，尚未成为正式实现 |
| `RTF_ACCEPTED` | RTF 已合入并完成自身门禁，必须记录 commit/版本 |
| `REJECTED` | 经证据否决；保留原因，防止后续重复尝试 |
| `SUPERSEDED` | 已被更新方案覆盖，必须指向替代条目 |

优先级只表示回送 RTF 的处理顺序，不改变 ETF 当前开发顺序：

- `P0`：已发生崩溃、存档风险或跨模组硬冲突。
- `P1`：确定性、长期兼容、明显接缝或可证明的热路径问题。
- `P2`：架构收束、工具和开发体验改进。
- `P3`：只有研究价值，暂不应进入生产代码。

## 3. 反哺流程

1. **发现**：在 ETF 中定位具体问题，记录固定 seed、坐标、日志、测试或 profiler 证据。
2. **查重**：只读检查 RTF 最新工作树，确认问题没有在 RTF 当前代码中解决。
3. **ETF 证明**：优先在 ETF 建立最小实现、逐位 fixture、并发 parity 或真实同载证据。
4. **登记**：在本文件新增/更新条目，写清收益、风险、非目标和 RTF 验收门禁。
5. **独立迁移**：只在单独 RTF 任务/分支中实现；不得从 ETF 任务直接修改 RTF 工作树。
6. **RTF 验证**：按 RTF 自己的 common、双加载器、固定 seed、性能和发布流程验收。
7. **关闭**：记录 RTF commit/版本并标为 `RTF_ACCEPTED`，或写明否决证据。

没有第 3 步证据的内容最多标为 `OBSERVED`。ETF 测试通过也不等于 RTF 可直接采用；两项目的
维度语义、`Cell`/tile 生命周期、加载器注入和 preset 兼容策略不同。

## 4. 当前总表

| ID | 优先级 | 状态 | 主题 | RTF 当前缺口 | 下一动作 |
| --- | --- | --- | --- | --- | --- |
| `FB-001` | P0 | `READY_FOR_RTF_REVIEW` | 共享 `NoiseChunk.mapAll` 注入可组合性 | RTF 当前仍独占同一调用点的 `@Redirect` | 在 RTF 独立分支拆分 setup 与 invocation ownership |
| `FB-002` | P1 | `OBSERVED` | 可持久化稳定 terrain entry identity | 仍使用列表 entry id 与 `name.hashCode()` 派生 seed | 先冻结兼容 schema 和旧 preset fallback |
| `FB-003` | P1 | `ETF_PROVEN` | 多尺度 AREA 多候选连续混合 | RTF AREA 边界主要依赖 winner/second | 建立 RTF 三岔口 fixture 与成本基线，再决定是否实验 |
| `FB-004` | P2 | `OBSERVED` | caller-owned primitive 采样契约 | 部分 anchor 已做到，通用 `Cell`/外部 sample 仍较重 | 只在新 API/artifact 采用，不全局重写 `Cell` |
| `FB-005` | P1 | `OBSERVED` | worldgen 调度权与线程预算 | RTF 有共享私有 executor，可能与 C2ME 争用 | 先做 C2ME/非 C2ME JFR，不凭理论重构 |
| `FB-006` | P0 | `READY_FOR_RTF_REVIEW` | ETF+RTF+C2ME 交叉兼容门禁 | 单仓测试无法复现两个模组争用同一 Mixin 点 | 建立固定版本、seed、坐标和方块 hash 协议 |
| `FB-007` | P2 | `OBSERVED` | density graph 代数与精确空 cell 证明 | 存在高世界性能压力，但错误剪枝会损坏地形 | 只做离线等价审查和最终 cell 实验 |
| `FB-008` | P2 | `ETF_PROVEN` | uplift 纯 scalar 与 Cell 解耦 | RTF uplift 仍同时承担 Cell 写入、water table 和上下文职责 | 在 RTF 独立轮次评估可选的纯 uplift helper |

## 5. 详细条目

### FB-001：共享 `NoiseChunk.mapAll` 注入可组合性

- **优先级/状态**：P0，`READY_FOR_RTF_REVIEW`。
- **ETF 证据**：旧 ETF 与 RTF 同载时，双方以相同优先级 `@Redirect` 拦截
  `NoiseChunk` 构造器中的同一个 `NoiseRouter.mapAll` 调用；ETF 先占目标后，RTF 强制注入得到
  `0/1` 并在区块线程触发 `InjectionError`。
- **ETF 修正**：ETF 改为优先级 `1100` 的 `@ModifyArg`，只组合 visitor 参数，不移除外层
  `mapAll` 调用；`MixinCompositionCompatibilityTest` 阻止重新引入 redirect。自动门禁已通过，
  最新打包 jar 的完整同载客户端回归仍是 ETF 侧待办。
- **RTF 当前事实**：最新只读工作树的
  `common/src/main/java/raccoonman/reterraforged/mixin/MixinNoiseChunk.java` 仍在该调用点使用
  `@Redirect`。redirect 同时负责 tile/cache 上下文、动态高度和继续执行 `noiseRouter.mapAll(visitor)`；
  因此不能简单删除。
- **建议方向**：将“调用前建立 RTF chunk/tile 上下文”和“拥有外层调用”拆开。优先评审在
  原始 INVOKE 前注入 setup，并保留原版 `mapAll` 调用；若使用链式 operation wrapper，必须证明
  与无该库的加载环境及其他 wrapper 可组合。`CellSampler.CacheChunk`、全部 15 个 router 字段、
  vanilla interpolation/cache wrapper 与动态高度语义必须保持。
- **RTF 验收**：RTF 主世界独立、非 RTF 维度、ETF 主岛外末地、ETF+RTF、RTF+C2ME、
  ETF+RTF+C2ME 均不出现注入失败；固定 seed 方块 hash 不变；全部 router 字段仍被包装；JFR
  不出现重复 mapAll 或 tile 重算回退。
- **非目标**：不要求 RTF 采用 ETF 的 End density；不允许通过降低 Mixin `require`、静默跳过
  RTF wrapper 或硬编码检测 ETF mod id 来掩盖冲突。

### FB-002：可持久化稳定 terrain entry identity

- **优先级/状态**：P1，`OBSERVED`。
- **RTF 当前事实**：`TerrainEntry` 以可编辑 `name` 作为数据字段，`TerrainProvider` 多处使用
  `entry.name.hashCode()` 派生地貌 seed/variant；runtime ownership 还使用列表 entry id。重命名会
  改变地形分布，Java 32 位字符串哈希也存在碰撞，列表重排则可能改变下游身份。
- **ETF 经验**：ETF 将 packed `long regionId` 与显式 `entryId` 分开，候选重排和并发采样有
  parity 测试；长期 Content Pack 路线进一步要求稳定 resource key，禁止列表下标成为持久身份。
- **建议方向**：在 RTF 新 preset 格式中引入不可见或专家可见的稳定 `entry_id`/`seed_salt`，
  display name 只负责 UI。旧 preset 缺字段时必须继续使用旧 `name.hashCode()`，只有显式升级才写入
  新身份；复制 entry 时生成新身份，重命名不改变身份。
- **RTF 验收**：旧 JSON 逐位保持；重命名不改 fixed-seed terrain；复制得到不同稳定布局；重排
  不改 ownership/variant；重复 id、缺失 id 和碰撞有明确错误；导入导出不丢身份。
- **风险**：这是存档/worldgen 兼容字段，不能用一次自动迁移静默重塑已有世界。ETF 自己的公开
  resource-key schema 尚未冻结，因此本条在形成 RTF Tech-Spec 前不得进入实现。

### FB-003：多尺度 AREA 多候选连续混合

- **优先级/状态**：P1，`ETF_PROVEN`。
- **RTF 当前事实**：AREA 区域最终边缘值主要由 winner/second distance 生成。该模型适合单尺度
  二元边界，但多个独立 region scale 在三岔口或第二/第三候选换位时可能形成细接缝。
- **ETF 证据**：ETF 在固定 `16 entries * 5 * 5 = 400` 最坏候选检查预算中，以导数连续的紧
  支撑 score-ratio 核筛选过渡集合，再归一化为 partition-of-unity；已有候选进入/离开、跨
  chunk、多尺度换位、三岔口、重排和并发测试，首批 fixture 每列最多约八个激活 AREA 候选。
- **建议方向**：先在 RTF 增加只读诊断和固定 seed 三岔口 fixture，比较 winner/second 与
  多候选 oracle。只有确认真实接缝后，才实验紧支撑多候选 AREA blend；RIDGE Top-3 anchor 是另一
  套有限 feature 语义，不能复用为 AREA blending。
- **RTF 验收**：候选换位一阶连续；面积占比不因 region scale 漂移；catalog 重排、负/远坐标、
  并发顺序一致；搜索与扩大窗口 oracle 一致；限制实际昂贵 terrain populator 采样数，并记录
  Standard/tall-world JFR。若视觉收益不足以覆盖成本，应标为 `REJECTED`，保留现模型。

### FB-004：caller-owned primitive 采样契约

- **优先级/状态**：P2，`OBSERVED`。
- **RTF 当前事实**：`TerrainAnchorLayout` 等新热路径已经使用固定 workspace 并做 allocation
  门禁，这是正确方向；但大量内部和未来外部采样仍围绕可变 `Cell`、tile reader 和生命周期对象。
- **ETF 经验**：大陆、terrain region、family signals、profile 与 ridge Top-3 使用 caller-owned
  primitive buffer；连续 influence/relief 与 AREA/feature identity 分开，scratch 不逃逸，
  正式热路径不创建 record/集合。`TerrainRidgeLayoutTest` 已覆盖 bounded search 与 exhaustive
  oracle、负/远坐标及四线程逐位一致。
- **建议方向**：不全局重写 RTF `Cell`。只对新建的跨模组 terrain sample、区域 artifact 查询、
  bounded geometry sampler 和 profiler API 使用版本化只读接口或 caller-owned primitive result。
  现有稳定 `Cell` 管线保持兼容 adapter。
- **RTF 验收**：JFR/bytecode 证明目标路径分配下降；调用者所有权、线程安全和生命周期写入
  Javadoc；旧 `Cell` 输出逐位一致；不得让外部 API 暴露 `GeneratorContext`、tile cache 或池对象。

### FB-005：worldgen 调度权与线程预算

- **优先级/状态**：P1，`OBSERVED`。
- **问题**：大型整合包中 Minecraft、C2ME、RTF tile、预览、地图渲染和 GC 会竞争处理器。
  即使总 CPU 只有 40%，错误的任务粒度、依赖等待或嵌套 executor 也可能让客户端长时间无响应。
- **ETF 原则**：正式 worldgen 不创建第二套固定线程池，Minecraft/C2ME 拥有 chunk 调度；preview
  或离线 tile worker 必须低优先级、有界队列、可取消，并同时受 CPU 与 heap 预算约束。
- **建议方向**：先对 RTF 当前共享 executor 做 C2ME/非 C2ME JFR，分离 tile 生成、等待、主线程
  stall、allocation 和 cache miss。只有 profiler 证明过度订阅或依赖阻塞后，才评审 caller-owned
  executor、同步编译或任务预算接口。
- **RTF 验收**：固定 seed 输出逐位一致；无嵌套 `parallel()`；逻辑处理器 4/8/16/32 档位均无
  饥饿；关闭世界后没有遗留任务/强引用；15 分钟新区块生成的 p95、GC 和 peak heap 不回退。

### FB-006：ETF+RTF+C2ME 交叉兼容门禁

- **优先级/状态**：P0，`READY_FOR_RTF_REVIEW`。
- **问题**：单仓源码测试无法发现多个模组争用同一 Mixin 调用点，也不能证明一个维度的 wrapper
  没有泄漏到另一个维度。FB-001 的真实崩溃就是现有单仓门禁漏掉的情况。
- **建议方向**：建立独立、版本锁定的兼容协议，而不是让 ETF 或 RTF 测试依赖对方源码。测试包
  固定 Minecraft、NeoForge、ETF、RTF、C2ME 版本、JVM 参数、seed 和坐标，并保存两维度的方块
  hash、关键日志断言与 JFR 基线。
- **最小矩阵**：RTF；ETF；RTF+ETF；RTF+C2ME；ETF+C2ME；RTF+ETF+C2ME。主世界验证 RTF
  marker/tile/cache，末地验证 ETF density/中央保护/外部大陆；两边都验证结构、流体和新区块重载。
- **RTF 验收**：无 `InjectionError`、Mixin 0/1、unresolved placeholder、重复 router wrapper 或
  跨维度 ThreadLocal 泄漏；同组合重复运行 hash 一致；移除任一模组后对应维度回到其独立基线。
- **产物建议**：协议和结果可放各项目 review 文档；未来再决定是否抽成单独兼容测试仓库。当前
  不新增生产依赖，也不把另一个模组 jar 打进发布包。

### FB-007：density graph 代数与精确空 cell 证明

- **优先级/状态**：P2，`OBSERVED`。
- **ETF 发现**：Minecraft 1.21.1 的部分 binary density fill 路径会分配第二数组；当输入值域有
  严格证明时，某些 `clamp(add(a,b), 0,1)` 可等价为 `max(a,b)`。ETF 仅在双方均为 `0..1` 且
  逐点等价时采用，值域变化就必须撤销证明。
- **建议方向**：RTF 先做 density graph 离线代数审查和逐点 fixture，不直接改复杂 cave、ore、
  river 或 aquifer 组合。高世界精确空 cell 快路径只能读取已经包含结构和第三方修改的最终缓存值，
  aquifer/未知 router 强制 fallback。
- **RTF 验收**：随机与边界输入逐位等价；固定 seed 方块 hash、结构、洞穴、矿脉、河流和流体
  不变；allocation/chunk 与 JFR 有实际收益；不能使用预测 surface、固定 margin、角点或全局
  `minValue/maxValue` 代替局部证明。

### FB-008：uplift 纯 scalar 与 Cell 解耦

- **优先级/状态**：P2，`ETF_PROVEN`。
- **ETF 证据**：2026-07-19 新增 `EndTerrainUpliftRuntime`，使用已有
  `ContinentSignalBuffer` 的 edge、landness、inlandness 和 corrected centre，输出独立 `[0,1]`
  uplift；已通过中心峰值、海岸回落、平移不变性、范围、信号闭环、完整 common 和双平台发布门禁。
- **RTF 当前事实**：`UpliftContinentGenerator` 的 `getSmoothVoronoiGradient` 与 uplift 计算仍位于
  `Cell`/`GeneratorContext` 绑定的大陆生成器中，并会把结果写入 `Cell.waterTable`；这对 RTF 主世界
  语义有效，但不适合直接成为跨模组的轻量采样接口。
- **建议方向**：在 RTF 独立分支评估一个可选纯数学 helper，输入已完成 ownership/centroid 采样的
  caller-owned primitive 结果，输出 uplift scalar；现有 `Cell.waterTable` 路径保留为兼容 adapter，
  不要求一次重写 RTF 大陆生成器。
- **RTF 验收**：默认主世界 uplift、water table、river cache 和 biome 结果逐位不变；纯 helper 与
  现有 gradient 在固定 seed/坐标上有明确 parity；无逐列 record 分配；四 worker、不同 tile 访问
  顺序和世界关闭后无 stale cache；JFR 必须证明成本没有转移到重复 ownership 采样。
- **非目标/风险**：不把 ETF 的末地 finite volume、外海、预览颜色、NeoForge UI 或 RTF 洞穴方案
  带回 RTF；ETF 当前只读 RTF 工作树，后续由独立 RTF 开发流程人工评审和迁移。

## 6. 不应反哺的 ETF 专有内容

以下内容没有通用 RTF 价值，不进入反哺队列：

- ETF 原版末地主岛保护、末地虚空海峡和浮空大陆 volume。
- ETF 自研 Spectacle-First Cave Pipeline；RTF 洞穴与 ETF 洞穴路线相互独立。
- ETF NeoForge-first/LDLib2 UI 布局和末地 Content Pack 产品设计。
- 仅为 ETF preview 诊断存在的模式、颜色和编辑器状态。
- 尚未在 ETF runtime、preview、客户端和性能门禁中成立的参数或算法。

## 7. 条目模板

新增反哺项时复制以下结构，并同步更新总表：

```markdown
### FB-XXX：标题

- **优先级/状态**：P?，`OBSERVED`。
- **ETF 证据**：固定 seed/坐标、测试、日志、截图或 profiler。
- **RTF 当前事实**：只读审查的分支、commit/工作树和相关类。
- **建议方向**：最小可验证改动。
- **RTF 验收**：正确性、兼容、性能、迁移和发布门禁。
- **非目标/风险**：明确不能顺手改变的行为。
- **关闭记录**：RTF commit、版本和验证结果；未关闭时省略。
```

## 8. 来源

- [`reviews/RTF_LATEST_WORKTREE_REVIEW_2026-07-18.md`](reviews/RTF_LATEST_WORKTREE_REVIEW_2026-07-18.md)
- [`reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md`](reviews/RTF_TERRAIN_REGION_ARCHITECTURE_REVIEW_2026-07-18.md)
- [`RTF_CORE_REUSE_RESEARCH.md`](RTF_CORE_REUSE_RESEARCH.md)
- [`TALL_WORLD_OPTIMIZATION_REVIEW.md`](TALL_WORLD_OPTIMIZATION_REVIEW.md)
- [`reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](reviews/OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md)
- ETF `MixinCompositionCompatibilityTest`、`TerrainRegionLayoutTest` 与相关 continuity/parity 测试。
