# EndTerraForged 长期目标与阶段目标

> 文档状态：当前有效，目标模式唯一入口。
> 最近更新：2026-07-23。
> 权威范围：产品目标、阶段顺序、完成定义和执行门禁。
> 当前任务与短期顺序以 [`PLAN.md`](PLAN.md) 为准；工程规则以 [`AGENTS.md`](AGENTS.md) 为准。

## 一、长期总目标

将 EndTerraForged 建设为一个可稳定发布、可在超大型整合包中长期使用和演进的末地地形生成模组，而不是参数演示或局部地形原型。

最终产品必须同时满足以下目标：

1. 生成多个可精确配置的大型浮空大陆、群岛和虚空海峡，并通过高级大陆场、地貌区域规划、地貌族、形状化山脉/火山与统一侵蚀链形成可读的大尺度地表。
2. 提供自研的宏大地下系统，包括巨型洞厅、深渊洞口、真实地下河、长距离洞穴网络、多层洞穴、天然桥梁和石柱。
3. 提供 NeoForge-first、基于 LDLib2 的现代编辑器，以及与正式 worldgen 数学一致的实时预览。
4. 提供数据驱动的 ETF Worldgen Content Pack API，使核心负责几何，第三方包负责 biome、内容主题、surface palette、feature 与兼容映射。
5. 与主流末地模组、结构模组和性能模组共存，重点验证 C2ME 类并行区块生成环境。
6. 在确定性、性能、存档兼容、错误可见性和发布包完整性方面达到可公开发布标准。

局部功能通过测试不代表长期目标完成。只有各阶段的产品、运行时、兼容、性能、客户端和发布门禁全部满足，才能宣称项目完成。

## 二、不可变产品决策

### 2.1 平台与 UI

- NeoForge 是官方主平台，LDLib2 是正式高级 UI 主线。
- Fabric 只维持低维护编译、基础运行边界和社区移植基础，不引入第二套官方高级 UI。
- `common` 不得直接依赖 LDLib2、NeoForge、Fabric 或平台事件 API。
- UI 采用分页主编辑器与独立子编辑器；错误、警告、保存失败和生效条件必须可见。
- 不复制 RTF 的 UI、布局或视觉呈现。第一阶段预览能力可达到 RTF 的 2D 地图与剖面深度，但必须使用 ETF 原创交互与视觉结构。

### 2.2 RTF 使用边界

- RTF R9.3.6 作为详细参数、默认值、Codec 字段和配置语义的基线。
- RTF R9.6 作为稳定大陆、地貌和噪声核心的实现基线；当前 RTF 开发分支中的区域布局和形状化地貌只能按来源版本独立验证。用户已明确 RTF 火山仍未完成，因此 RTF volcano 不是 ETF 的候选实现来源；ETF 只能把火山保留为后续自研课题，不能把当前双方工作树中的火山草稿当作交付依赖。
- RTF 是同一维护方的 MIT 项目。无平台、注册表、`GeneratorContext`、`Cell`、水位或河流缓存耦合的纯数学实现可以直接移植；移植时保留版权头、记录来源与 ETF 改动，并更新 [`NOTICE.md`](NOTICE.md)。
- 地下系统不采用 RTF cave 方案。
- 本地 RTF 的大陆架、群岛和海岸过渡逻辑可作为浮空大陆架设计参考，但 ETF 必须重新设计为面向末地虚空的体积地形。
- RTF 的 terrain region、稳定 morphology variant、连续通道混合、ridge envelope、
  地貌资格、filter pipeline、climate region、surface signal 和 preview 任务生命周期可以
  作为模块化来源；compact/volcano 只保留为未成熟的设计参考。ETF 必须改造成 immutable
  runtime、caller-owned primitive buffer、稳定 resource key 与有界调度。
- 正式 worldgen 不复制 RTF 的私有 executor。Minecraft/C2ME 负责区块并行；ETF 只可为
  preview 和可选离线 tile 后处理使用受控、低优先级、有界辅助线程。
- 不移植 RTF UI、主世界水位、海洋、biome registry 绑定、可变/池化 `Cell`、`GeneratorContext`、`RiverCache` 或独占 `NoiseRouter.mapAll` 的注入方式。
- RTF 仓库在 ETF 任务中保持只读；可反哺的优化和通用算法先写入 ETF 文档，由独立 RTF 开发流程人工迁移。

### 2.3 世界规格

默认世界规格锁定为：

| 规格 | 包络 | 产品定位 |
| --- | --- | --- |
| Standard | `min_y=-256`、`height=512` | 默认单人、服务器和整合包规格 |
| Extended | `height=1024` | 更深地下与大型洞厅 |
| Grand | `height=2048` | 高性能服务器和大型整合包 |
| Epic | `height=4064` | 实验性超高世界，必须显式警告 |
| Custom | 合法的自定义范围 | 专家模式，创建世界时配置 |

世界高度是创建世界时的 dimension/noise 规格，不是已有世界中可以即时修改的普通 preset 参数。编辑器可展示目标规格，但必须遵守以下规则：

- Standard 是唯一默认值。
- 1024 及以上给出黄色性能警告。
- 只有匹配的 `dimension_type` 和 `noise_settings` 已在创建世界时加载，worldgen 才能使用对应高度。
- 已有世界不得通过保存 preset 伪装为已经扩容。
- runtime 始终以 Minecraft 实际加载的世界边界为准；不匹配时明确拒绝或降级，禁止静默截断。

### 2.4 地表与中央区域

- 默认地表由多个直径约 4000-8000 格的大型大陆组成，以虚空海峡和群岛分隔。
- 新建 preset 使用 `format_version=3`、`OUTER_CONTINENTS`、显式 `FLOATING_SHELF` 与显式大陆分带。缺失 `format_version` 的历史 compact preset 保持 `ISLANDS`；缺失 `continent.volume_mode` 的历史配置保持 `LEGACY_COLUMN`；v0-v2 缺少大陆分带时保持 legacy passthrough，不得因升级被静默重解释；未知版本必须拒绝加载。
- 阶段 B 的高质量 `REGION_PLANNED` 路线在配置闭环完成后使用新的
  `format_version=4`。现有 v3 世界永久保留原大陆算法、volume 与
  `LEGACY_SELECTOR`，不得随 jar 更新静默改变未生成区块。
- 原版主岛、末影龙、黑曜石柱、返回门、折跃门及外围区域当前冻结为原版行为，现阶段不得修改或重塑。实现上这不是一句 UI 或文档约定：中央保护区的正式 density 必须委托给原版 fallback density；ETF 宏观大陆只可在保护区外开始采样。
- 保护区外不能以固定半径硬切出实体墙或悬空平面。大陆拓扑必须保留一个由确定性 landness 控制的外部过渡带，使原版中央流程、中央缓冲虚空和 ETF 外部大陆彼此隔离。
- 主岛整合属于最后阶段；可选 ETF 主岛重做是扩展目标，不得阻塞首个公开版本。
- 大陆基础形态：
  - `FLOATING_SHELF`：当前新默认浮空大陆架，主体默认厚 160 格、边缘默认厚 48 格；二者均可配置，边缘随 landness 平滑变薄。
  - `DEEP_MASS`：深厚岩体，适合地下系统。
  - `LAYERED`：多层大陆与垂直探索。
  - `FLOATING_SHELL`：较薄浮岛壳体。
- 参数应达到 RTF 同等级的精确可调性，但只有存在 runtime 消费者和可观察预览的字段才能进入正式 preset。

### 2.5 地下体验

地下系统采用 Spectacle-First Cave Pipeline，优先级固定为：

1. 巨型洞厅。
2. 深渊洞口。
3. 地下河流。
4. 长距离洞穴网络。
5. 多层洞穴。
6. 天然桥梁和石柱。

核心技术路线：确定性 region graph 负责大尺度结构，SDF/metaball/ellipsoid 负责洞厅，spline/capsule 负责通道，CSG 负责组合和切削。算法应能扩展到约 1000 格以上的垂直空间，但默认 Standard 规格必须保持普通玩家可承受的成本。

第一版地下河必须生成真实河道与水体；深层可少量生成熔岩。预览候选 mask 不得被描述成已实现的正式液体方块生成。

### 2.6 Content Pack 与兼容接管

- ETF 强制接管 End dimension/noise/density 主链。
- 其他模组通过 registered biome、Content Profile、feature、structure、tag、surface rule 和专用适配器混合。
- 核心模组内置原版 fallback Content Pack。
- Content Profile 独立于 biome holder；多个主题可以复用同一个 Minecraft biome，并拥有不同 palette 与 feature。
- 第三方可用数据包制作基础 ETF Content Pack；官方后续提供 BetterEnd、Nullscape 等兼容包。
- Terra/ReimagEND 类完整 generator 不能与 ETF 同时接管同一维度，只兼容其主岛外内容语义，不解释任意 Terra 脚本或 density。
- biome holder、registry holder 和 UI 视角状态不得写入 `EndPreset` JSON；运行时通过资源键和注册表解析。
- 无法安全混合的冲突必须明确记录并提示，禁止静默覆盖。

详细规格见 [`docs/CONTENT_PACK_SPEC.md`](docs/CONTENT_PACK_SPEC.md)，Terra/ReimagEND 边界见 [`docs/TERRA_CONTENT_COMPATIBILITY_RESEARCH.md`](docs/TERRA_CONTENT_COMPATIBILITY_RESEARCH.md)。

## 三、架构与质量硬门禁

### 3.1 配置闭环

每个新增可序列化 preset 参数必须同时具备：

- `EndPreset` 接入和兼容默认值。
- Codec 与旧 JSON 缺省兼容策略。
- Validator 与清晰、可翻译的错误。
- Builder setter、reset、copy/load 和非法状态行为。
- runtime/worldgen 消费路径。
- 实时 preview 消费路径。
- 默认 decode/encode、custom round-trip、invalid validation、builder 行为测试。
- worldgen 或 preview 的可观察差异测试。

只允许稳定的 scalar、threshold、range、enum 和经过审查的资源键进入持久化配置。未支持的 JSON 字段必须拒绝或报错，不能静默忽略。

### 3.2 性能与并发

- 高频 density、climate、cave 和 preview 路径避免临时对象、装箱集合和重复噪声计算。
- 只由 immutable config 决定的派生值在 runtime 构造期缓存。
- 坐标缓存必须有界、按世界或配置隔离，不能跨线程共享可变状态。
- 相同 seed、world spec 和 preset 必须产生相同结果；自适应降级不得改变正式 worldgen。
- 预览采用异步生成：拖动时低清、松手后高清、取消过期任务；不得占用服务端区块生成线程。

基准环境：6 核 CPU、16 GB 内存、游戏分配 6 GB、视距 12、模拟距离 8。Standard 持续生成新区块时目标 MSPT 低于 40，不能长期超过 50，也不能出现持续 PPT 卡顿。

### 3.3 兼容层级

1. 用户实际超大型整合包逐模组测试。
2. 主流末地、结构和性能模组自动或固定流程验证，优先 C2ME。
3. 未知模组使用保守注入边界、明确日志和可诊断失败。

C2ME 兼容的最低要求：没有依赖线程顺序的随机数，没有跨 worker 可变缓存，没有按区块生成时写全局 preset/runtime 状态，没有假设 vanilla 单线程访问。

### 3.4 固定验证顺序

每个功能块完成后按风险依次执行：

1. 相关定点单元测试。
2. `:common:test`。
3. `:neoforge:compileJava`。
4. 触及 common 或平台边界时执行 `:fabric:compileJava`。
5. 发布级验证执行 `:verifyReleaseArtifacts --no-daemon`，不得带 `--configure-on-demand`。
6. 重要 worldgen、LDLib2 UI 和已有世界编辑进行真实客户端冒烟。
7. 发布前执行仓库卫生报告和严格门禁。

同一工作树不得并行运行多个 Gradle 构建。Windows 中文路径异常时使用同一会话内的 ASCII `subst` 或 junction 路径。

## 四、阶段目标

### 阶段 A：0.1.x 发布基础收束

目标：把当前代码从“测试覆盖较多的开发工作树”收束为可重复验证的基线。

完成定义：

- Standard 512 数据包、preset runtime 边界和默认生成行为一致。
- 新世界实测能看到预期大陆，不出现意外整层流体或错误地表高度。
- 已有世界暂停菜单入口、单人限制、损坏 preset、保存、导入导出和重进世界提示完成真实客户端验证。
- `:common:test`、NeoForge/Fabric 编译和 release artifact gate 顺序通过。
- 工作树中的源码、测试、资源和文档被正确纳入版本控制；构建产物和运行日志保持忽略。
- 建立可重复的性能采样方法和 Standard 基线数据。

### 阶段 B：0.2 主岛外高质量地表、Standard 性能与 2D 编辑体验

目标：把当前地表原型重建为可读、可探索、可精确配置且性能可控的大型浮空大陆系统。

完成定义：

- 该阶段不得修改当前冻结的原版主岛和外围区域。
- 运行时必须先验证中央保护：保护区内读取原版 fallback density，而不是 ETF `EndDensity`；没有可靠 fallback 时必须拒绝启用外部大陆接管并给出可诊断错误，不能悄悄把中央区域置空。
- “大陆”生成多个由末地虚空分隔的宏观陆块，不得继续使用全图实体覆盖作为默认语义。
- `CONTINENTAL` 只保留为旧版全覆盖兼容模式；正式默认和新编辑器语义使用 `OUTER_CONTINENTS`，其 `outer_continent_scale` 独立于破碎大陆的 `continent_scale`。
- 高质量模式使用经过 golden fixture 验证的高级大陆场，输出稳定的 edge、landness、inlandness、continent id 与大陆中心；当前径向/低成本模式只保留为兼容或性能回退。
- 地貌不能继续由单个选择噪声在若干通用 Perlin 层之间切换。正式链必须先生成不可变 `TerrainRegionPlan`，再按区域选择平原、丘陵、高原和山脉等成熟地貌族；火山必须在独立自研规格通过后再进入该链。
- 首个生产 `TerrainRegionPlan` 由所有正权重 `AREA` 地貌建立统一、无空洞 ownership 分区；`weight` 表示 AREA 的近似面积占比，region size 只控制单个重复区域尺寸。RIDGE 只增加有界物理形态，不夺取宏观 ownership。ownership identity、underlay、visible family 与 physical influence 必须分别表达。
- 山脉使用 AREA ownership 上的有限山脊；高原、丘陵和其他地貌拥有各自成熟的形态函数。火山的有限紧凑 footprint 只属于后续独立阶段。物理影响在 footprint 外严格归零并回到 underlay，所有地貌不得在每个坐标全局叠加。
- `FLOATING_SHELF` 可生成多个大型大陆，边缘、厚度、海峡、附属群岛和离岸岛链均可配置并预览；附属群岛与高空浮岛是两套独立系统。
- 正式大陆具有 top、underside 和 edge thickness，不从地表无条件填充到世界底部。
- 海岸分带、群岛、侵蚀和排水必须消费同一套大陆/地貌区域信号；不得按大陆中心距离额外
  生成宏观抬升穹顶，也不得维护与正式 runtime 分叉的第二套公式。
- 高 relief 地貌必须经过 `EndTerrainEligibilityPolicy`：在 shelf/rim、厚度不足或
  void edge 处只缩放物理形态或回到 underlay，不改变 continent/region/ownership identity。
- 地表后处理顺序固定为 raw top -> erosion -> smoothing -> slope/curvature/edge metrics
  -> continuity correction -> volume；hydraulic erosion 只能是有界 tile 实验能力。
- 气候区域输出稳定 id、center、edge、temperature 与 moisture；Content Pack 使用稳定
  resource key 和有界 fallback，不依赖 terrain entry 列表下标或无界全局缓存。
- surface、feature 与 structure 消费版本化只读 `SurfaceContext` 和 placement anchors，
  不重新猜测地貌，也不反向修改 density。
- 大陆、地形层、山地、高原、火山、气候、侵蚀和群系布局中的正式参数都有 runtime 与 preview 消费者。
- 2D 高度/着色地图、X/Z 垂直剖面和地貌/群系叠加可用。
- 预览具备低清交互、高质量重采样、generation id 过期结果丢弃、最后成功帧保留、
  有界任务队列和渲染线程 texture upload。
- Standard 性能达到基准目标。

洞穴可以实验性进入 0.2，但不得阻塞地表版本，也不得把预览骨架宣传成正式完整洞穴。

### 阶段 C：0.2.x ETF Worldgen Content Pack API

目标：让地形几何与 biome、palette、feature 和内容主题解耦，并为整合包兼容提供稳定入口。

完成定义：

- 定义并冻结 v1 资源格式、schema 校验、选择规则和 fallback 语义。
- 内置原版 fallback 包，缺失第三方 biome/profile/feature 时世界仍可加载并给出诊断。
- 3D profile 选择可读取 x/y/z、地形区、气候、当地深度、surface kind 和地貌标签，不改变 density 主链。
- profile、terrain 与 palette 使用稳定 resource key；资源加载期编译为数组索引和 bitset，
  运行时不解析 JSON、查磁盘或依赖列表位置。
- 数据包可添加 biome 映射、Content Profile 和基础 palette；无 Java 代码也能制作基础内容包。
- top、underside、cave floor、cave ceiling 和 void edge 具备稳定放置语义。
- `SurfaceContext` 至少暴露 ownership/visible family、terrain tags、structured feature
  influence、slope、curvature、erosion、sediment、drainage、landness/inlandness 和气候。
- 结构挂点至少支持 terrain region center、ridge crest/endpoint、volcano crater/flank、
  plateau interior/edge、coast/void edge，并为后续 cave graph anchors 留出版本化能力。
- 资源重载、注册表解析、服务端同步和确定性选择有测试。
- 至少完成一个注册表型末地模组兼容包和一个 ReimagEND 类主岛外内容适配原型。

### 阶段 D：0.3 正式宏大地下系统

目标：交付可探索、可导航、可配置的地下主体验。

完成定义：

- region graph 在区块边界连续，顺序无关且 deterministic。
- 巨型洞厅、深渊洞口、长距离网络、多层洞穴均进入正式 density carve。
- 地下河生成真实水体，深层少量熔岩具有清晰规则与安全边界。
- 天然桥梁和石柱由保留体积或后处理稳定生成。
- 2D 叠加与 X/Z 剖面使用同一 runtime 数学路径。
- 对 Standard 和 Extended 做性能、边界、种子稳定性与视觉冒烟。
- 配置默认保守，不破坏已有世界默认输出。

### 阶段 E：0.4 兼容、超大规格与整合包验证

目标：在真实超大型整合包中稳定运行，并扩展高级世界规格。

完成定义：

- BetterEnd、Nullscape、ReimagEND 类内容适配、主流结构模组与 C2ME 等形成版本化兼容矩阵。
- ETF 接管冲突、适配器启用和降级状态有明确日志。
- 用户实际整合包完成长时间新区块生成、重载和存档回归。
- Extended/Grand 达到明确性能预算；Epic 标记实验性并有硬警告。
- 本阶段仍不修改原版主岛；主岛整合继续后置。

### 阶段 F：0.5 至 1.0 完整体验与正式发布

目标：完成高级预览、生态扩展和稳定发布契约。

完成定义：

- 可旋转 3D 网格预览、剖切和洞穴层可视化可用，且不阻塞低配设备使用 2D 模式。
- 地下生态、装饰、结构挂点和官方 Content Pack 按 runtime -> preview -> UI 顺序接入。
- preset 与 Content Pack 格式具备版本迁移策略。
- GitHub 预发布、用户整合包抢先测试、反馈修复、Modrinth/CurseForge 发布流程可重复执行。
- 资源、metadata、语言文件、datapack、关键类、许可证和兼容矩阵全部通过发布门禁。

### 阶段 G：原版主岛与外围区域（最后阶段）

目标：在外部大陆、性能、Content Pack、地下和发布链稳定后，单独验证原版中央流程。

完成定义：

- 默认继续保持原版主岛、黑曜石柱、龙战、返回门和网关行为。
- 先建立独立 seed、结构和龙战流程回归，再评审任何最小整合改动。
- 可选 ETF 主岛重做默认关闭，不得影响原版兼容路径。
- 主岛工作不得倒逼外部大陆、Content Pack 或兼容层反向依赖中央实现。

## 五、目标模式执行规则

每次恢复上下文或开始一大段开发前，按以下顺序读取：

1. [`AGENTS.md`](AGENTS.md)：工程与仓库规则。
2. 本文件：长期目标、阶段顺序和完成定义。
3. [`MEMORY.md`](MEMORY.md)：长期决策、已知陷阱和兼容经验。
4. [`PLAN.md`](PLAN.md)：当前阶段、下一批任务和阻塞。
5. [`docs/DOCUMENTATION_INDEX.md`](docs/DOCUMENTATION_INDEX.md) 指向的相关技术规格。
6. 当前代码、测试、资源与 `git status`；工作树永远是实现事实来源。

执行时遵循：

- 先审查后修改；一个子任务形成一个可独立验证的功能块。
- 不为增加参数数量而增加没有消费者的字段。
- 新功能按 runtime -> preview -> UI 顺序接入；配置闭环同一阶段完成。
- 每个大段结束都进行代码审查、架构边界检查和性能风险检查。
- 不回滚或覆盖来源不明的工作树修改。
- 子智能体只处理互不冲突的范围，完成后及时关闭；Gradle 仍只能串行运行。
- 每轮报告必须包含：改动、验证、兼容/性能影响、剩余风险、下一步、是否更新中文文档和 `MEMORY.md`。

## 六、当前总体判断

- 当前版本：`0.1.7` 开发工作树。
- 当前阶段：阶段 B 的 P2 外部大陆与 P3 有限大陆架已形成代码闭环，但当前地表仍只是原型。`EndTerrainComposer` 主要依赖一个低频选择噪声在若干通用层之间切换，缺少区域级地貌规划、成熟地貌族和有限形状特征，因此不能把现有截图质量视为可继续微调的最终架构。
- 当前执行顺序：先完成最新 jar 的中央保护、底面、直壁、岩浆、RTF/C2ME 同载短回归；地表重建随后按“AREA 地貌族与资格策略 -> 曲线/多段山系 -> 附属群岛与海岸 -> analytical erosion/排水 -> 预览调度和编辑器 -> 性能兼容”的顺序推进。火山不再是 0.2.0 首个垂直切片的前置条件；只有完成独立地质设计、固定 seed 视觉验收和性能预算后才单列进入后续版本。每一层先完成正式 runtime，再接预览，最后才开放玩家 UI。
- 当前代码状态：`RTF_ADVANCED` 的纯数学、`Perlin2`、golden fixture、完整大陆信号以及
  受控 `EndHeightmap` / finite volume / preview 内部接线已经完成，并通过 common、双平台
  编译和发布包自动门禁；但 validator、Codec 和编辑器仍明确拒绝该算法，真实客户端、
  RTF/C2ME 同载、Standard/JFR 性能门禁也尚未完成，因此不得描述为正式启用功能。
- 当前 P4.4 状态：首个有限 RIDGE 已完成自动门禁；工作树中的
  `EndTerrainVolcanoRuntime` 只是一份封闭的内部几何草稿。用户已确认 RTF 火山仍未完成，
  因此该草稿冻结，不再以 RTF 火山为来源继续推进；它不是已完成火山功能，也不会进入玩家
  preset、`format_version=4` 或 UI。
- 版本策略：`0.1.8` 只作为兼容与安全基线；真正替换当前拙劣地表原型的是
  `format_version=4` 的 `0.2.0` 高质量路线。已有 v3 preset 永久保持 legacy，不得随
  jar 更新静默改变未生成区块。
- RTF 复用策略：优先吸收高级大陆、统一 terrain region、构造期 terrain catalog、区域
  稳定 morphology variant、成熟地貌 primitive、有限 ridge envelope 和后处理信号链。火山
  不从 RTF 当前开发线迁移；气候、surface、structure、预览与侵蚀只复用设计契约并改造成
  ETF immutable runtime、稳定 resource key 和 caller-owned primitive buffer。
- RTF 禁止边界：不移植 `GeneratorContext`、可变/池化 `Cell`、主世界水位与海洋、
  `RiverCache`、私有 worldgen executor、全局可变 biome cache、独占 Mixin、RTF UI 或
  RTF cave。
- 交付策略：不把 RTF 的所有地貌一次性搬入。`0.2.0` 先以平原/丘陵/高原、有限山系、
  群岛海岸和 analytical erosion 构成一个完整垂直切片；中心抬升穹顶明确不属于 ETF 路线；火山、badlands、
  torridonian、hydraulic erosion、复杂火山流槽、动态世界规格和 3D 旋转预览均在该切片
  通过真实客户端、JFR 与兼容矩阵后再扩展。
- 预计剩余工作量：约 28-40 个大段开发轮次，取决于高质量地表重建、真实客户端回归、Content Pack、C2ME/整合包兼容结果、宏大地下系统收束以及 3D 预览是否进入首个稳定版本。
- 当前最大风险：当前地表原型的自动测试覆盖可能掩盖视觉架构不足；中央原版保护、RTF 同载和最新有限大陆底面尚未完成真实客户端回归，Standard 也缺少 JFR 性能基线；Content Pack loader、动态世界规格和正式地下河仍未实现；长期工作树包含大量未跟踪实现，需要尽快形成可审查提交。
