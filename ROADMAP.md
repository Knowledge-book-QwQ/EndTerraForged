# EndTerraForged 开发企划

> 把 ReTerraForged（RTF）那套真实地形 / 水文侵蚀体系，改造成 **末地（The End）维度** 版本：可铺海、可破碎大陆、可浮空岛，高度对标 RTF（-2032..2032），并提供与 RTF 同级的可调界面。

## 一、项目定位

| 维度 | 主世界（RTF 现状） | 末地（本项目目标） |
|---|---|---|
| 拓扑 | 连续大陆 + 海洋 | **可切换**：大陆破碎 / 离散岛屿 |
| 海平面 | 有，驱动水位/海滩/含水层 | **可开关三态**：无海 / 有海+海床 / 有海无海床 |
| 浮空岛 | 无 | **可开关**（无海床时即浮空岛） |
| 河流 | 沿等高线 carving 至海洋 | 无海→「虚空裂隙」；有海→「海峡/岛心峡谷」 |
| 水力侵蚀 | 地表滴粒侵蚀 | 复用，作用于岛屿表面与边缘 |
| 高度范围 | 自定义 DimensionType，-2032..2032 | **同样**自定义 DimensionType，-2032..2032 |
| 可调界面 | RTF preset editor GUI | **同样**提供 preset editor（阶段 5） |
| ChunkGenerator | 共用 `NoiseBasedChunkGenerator` | **同样共用**，只换 `noise_settings` + 自定义 `BiomeSource` + 自定义 `DimensionType` |
| 接入方式 | Mixin 把 Cell 高度场桥接为 vanilla `DensityFunction` | 沿用同一套桥接，按维度隔离 Preset |

**核心判断**：末地与主世界共用 `NoiseBasedChunkGenerator`。本项目 **不重写 ChunkGenerator**，而是：
1. 自定义末地 `DimensionType`（`min_y=-2032, height=4064`）扩展高度。
2. 为末地构建一套 `noise_settings`（`final_density` 用我们的高度场 + 侵蚀场）。
3. 提供末地 `BiomeSource`（按拓扑/海平面/岛屿分布选 biome）。
4. 复用 RTF 的 Mixin 桥接（`CellSampler` → `DensityFunction`），按维度隔离 Preset。

## 二、设计矩阵（拓扑 × 海平面 × 海床）

这是本项目相对 RTF 的核心扩展。三轴正交，全部可调：

### 2.1 拓扑模式 `TopologyMode`
- `CONTINENTAL_SHATTERED`（大陆破碎）：连续的"大陆"高度场，被虚空裂隙/海峡切割成破碎状。视觉上接近"被撕裂的末地大陆"。
- `ISLANDS`（离散岛屿）：离散浮空岛屿，岛屿间是虚空。视觉上接近原版末地外岛但更壮观。

### 2.2 海平面模式 `SeaMode`
- `NONE`（无海）：纯虚空/浮岛，`seaLevel` 不生效。侵蚀作用于岛屿表面。
- `WITH_FLOOR`（有海 + 海床）：海平面以下有连续陆地（海床），类似主世界。陆地延伸到海底。
- `NO_FLOOR`（有海 + 无海床）：海平面以下直接是虚空，陆地只存在于海面以上，是**悬浮在海面上的浮岛**——最壮观/奇特的组合。

### 2.3 浮空岛开关 `FloatingIslands`
- `true`：在主地形之外额外生成独立的悬浮小岛（不论海平面模式）。
- `false`：不生成额外浮岛。

### 2.4 组合语义速查

| TopologyMode | SeaMode | FloatingIslands | 结果 |
|---|---|---|---|
| CONTINENTAL_SHATTERED | NONE | false | 破碎大陆 + 虚空裂隙（最"末地原版增强"） |
| CONTINENTAL_SHATTERED | WITH_FLOOR | false | 破碎大陆 + 海（最"RTF 主世界感"） |
| CONTINENTAL_SHATTERED | NO_FLOOR | false | 破碎大陆悬浮在海面上（奇观） |
| ISLANDS | NONE | false | 纯虚空浮岛（原版末地外岛增强） |
| ISLANDS | WITH_FLOOR | false | 海面 + 海底岛屿（岛顶露海面） |
| ISLANDS | NO_FLOOR | false | 浮岛悬浮在海面上 |
| *任意* | *任意* | true | 额外叠加独立悬浮小岛 |

### 2.5 对 RTF 耦合点的重新定位
之前视"海平面/海洋"为"要消除的耦合"。新设计下，`seaLevel` 变为 **`DimensionProfile` 的可配置参数**：
- `SeaMode.NONE`：`seaLevel` 不参与计算（`Levels` 退化为以"岛屿基准面 Y"为基准）。
- `SeaMode.WITH_FLOOR` / `NO_FLOOR`：`seaLevel` 生效，复用 RTF 的 `Levels`/含水层/海滩逻辑。
- `NO_FLOOR` 需新增"海平面以下置虚空"的密度函数后处理（RTF 没有，本项目原创）。

## 三、可借鉴来源（已核实协议）

| 来源 | 协议 | 可借鉴方式 |
|---|---|---|
| ReTerraForged / TerraForged | **MIT** | **直接搬代码**，保留 MIT 版权头 + NOTICE lineage |
| BetterEnd (paulevsGitch) | MIT | 岛屿 shape / `BiomeSource` 写法可参考或搬 |
| Nullscape | MIT | 虚空处理、`TheEndBiomeSource` 覆写思路 |
| The Aether | LGPL-3.0 | **仅思路**（浮空岛距离场），代码勿搬 |
| YUNG's Better End Island | LGPL-3.0 | **仅思路**（结构替换龙岛），代码勿搬 |

> 搬运 MIT 代码时，在每个文件头部保留原 MIT 版权声明，并在 `NOTICE.md`（后续建立）记录 lineage：`TerraForged (dags) → ReTerraForged (raccoonman) → EndTerraForged`。

## 四、水文侵蚀在 RTF 中的真相

调研确认，RTF 里"侵蚀/水文"实为 **五个不同概念**，移植时必须分清：

| 概念 | RTF 位置 | 本质 | 末地移植决策 |
|---|---|---|---|
| **水力滴粒侵蚀** | `tile/filter/Erosion.java` | 真正的高度场 droplet erosion 仿真 | **本期重点移植**，相对独立 |
| 河流 carving | `UpliftRiverCarver` | 高度场径向变形（4 区） | 后期改造为「虚空裂隙/海峡」 |
| `ContinentalHydrology` | `cell/rivermap/ContinentalHydrology` | 静态查找表，强耦合"内陆度/海平面" | `WITH_FLOOR` 模式可复用；`NONE` 模式弃用 |
| 热力侵蚀噪声 | `noise/module/Erosion.java` | Worley 风格程序化噪声 | 可选移植，参与岛屿 shape |
| `ErodeFeature` | `feature/ErodeFeature.java` | 方块级表面装饰 | 后期按末地材质重写 |

**"水文侵蚀非常真实"** 主要来自第一项：`Erosion.java` 的 droplet 算法（沉积容量、brush 权重、速度/水量衰减）。它 **完全自包含**，唯一海平面耦合点是 `Factory` 里 `Modifier.range(levels.ground, levels.ground(15))`——只需把"地表基准"抽象为 `DimensionProfile` 提供的基准面即可解耦。

## 五、分阶段路线图

### 阶段 0：地基（已完成）
- [x] 仓库初始化（LGPL-3.0、Architectury 多加载器脚手架）
- [x] 调研 RTF 架构与协议
- [x] 本企划 + 开发规范
- [x] 第一小步：移植 `FastRandom` + `NoiseUtil`（已编译验证）

### 阶段 1：移植水力滴粒侵蚀（已完成）
目标：在末地高度场上跑通 droplet erosion，作为独立滤波器，暂不接入 chunk 生成。

- [x] 1.1 移植纯工具类：`FastRandom`、`NoiseUtil.seed/clamp`
- [x] 1.2 移植 `Cell` 的最小字段集（`height`/`sediment`/`heightErosion`/`erosionMask`）
- [x] 1.3 移植 `Filter` / `Filterable` / `Size` / `Modifier` 接口
- [x] 1.4 移植 `ErosionConfig`（纯 POJO；Codec 化与其余 DFU 桥接一并延后到阶段 3）
- [x] 1.5 移植 `Erosion` 算法本体，`Factory` 改用 `DimensionProfile` 基准面
- [x] 1.6 单元测试：固定种子下 droplet 在合成高度场上的侵蚀/沉积行为
- [x] 1.7 编译通过 + 提交

### 阶段 2：维度抽象 + 末地高度场骨架
- [x] 2.1 `DimensionProfile`：收口 `seaLevel`/`worldHeight`/`worldDepth`/`SeaMode`/`TopologyMode`/`FloatingIslands`/`defaultFluid`
- [x] 2.2 `EndLevels`：`SeaMode.NONE` 时以"岛屿基准面 Y"为基准；`WITH_FLOOR/NO_FLOOR` 时复用 RTF `Levels`
- [x] 2.3 `Continent` 三件套：
  - `ContinentalShatteredContinent`（连续大陆 + 裂隙噪声，委托 `WorleyEdge.sample`）
  - `IslandsContinent`（离散岛屿距离场，单扫描保证门控+falloff 同步）
  - `Continent extends Noise`（输出 `[0,1]` landness，解耦 Cell/rivers）
- [x] 2.4 噪声模块移植（`Noises` 的 perlin/simplex/worley/warp 子集 + 组合算子 + Domain Warp）
- [x] 2.5 `EndHeightmap` + `EndMountains`：`continent × mountains` 组合，`EndLevels` 缩放到世界高度
- [x] 2.6 `EndDensity`：`SeaMode` 三态的 solid/void 列决策（NO_FLOOR/NONE 表面以下置虚空，WITH_FLOOR 填海床）

### 阶段 2.5：Climate 子系统（独立 Noise 路线）
> 调研结论：RTF 的 climate 管线（`ClimateModule`→`CellSampler`→`MultiNoiseBiomeSource`）依赖海陆 Continent 和 MultiNoise biome source，End 两者都没有；vanilla End 用专属 `the_end` biome source（几何分段，不读 climate）。因此**不直接搬 RTF 管线**，走"气候场作为独立 Noise 节点"路线。
- [x] 2.5a `EndClimate`：temperature/moisture/wind 作为独立 `Noise` 节点输出 `[0,1]`，不耦合 Continent/Levels；temperature=径向基带（中心热→外环冷）+ simplex 扰动，moisture/wind=独立 simplex — 纯逻辑
- [x] 2.5b climate 场作为 heightmap 的可选调制器：`ClimateModulator` 缩放 elevation-above-surface（冷加成/wet 侵蚀），`EndHeightmap.withClimate` 注入，链式 raw→climate→river→lake — 纯逻辑
- [x] 2.5c climate 场作为 biome source 的子类型选择器（stage 5.2.1 落地：`BiomeVariant`/`BiomeSlot`/`BiomeClimateConfig` 数据层 + `EndBiomeSelector` 4 角双线性选择器 + `EndClimateAccess` volatile 跨线程发布 + `EndBiomeSource` 接入 `biome_climate` codec 字段）

### 阶段 3：接入 vanilla 末地生成（含高度扩展）
> 环境已通：gradle 8.14.4 + Java 21 + 沙箱 HTTP 代理（`GRADLE_OPTS` 设 `127.0.0.1:18080`）。三模块编译通过，174 测试全绿。MC 集成采用 RTF 的「占位符 DF + mapAll 晚绑定」模式：`EndDensityFunction` 占位符经 DFU 反序列化进 `noise_settings`，`MixinNoiseChunk` 在 `NoiseChunk.<init>` 的 `router.mapAll` 处用 `EndDensityVisitor` 把占位符换成携带 seed+`EndDensity` 的 `Bound` 实例。
- [x] 3.1 自定义末地 `DimensionType`（`min_y=-2032, height=4064`，对标 RTF）— 数据包覆盖 `data/minecraft/dimension_type/the_end.json`
- [x] 3.2 `noise_settings` 覆盖 `the_end.json`，`final_density` 指向 `endterraforged:end_density`；`EndDefaults` 作为 stage-3.2 `EndPreset` 的前身提供默认 profile — 数据包 + 代码
- [x] 3.3 `EndDensityFunction`（占位符 + `Bound`）+ `EndDensityVisitor`（mapAll 替换）+ 注册到 `DENSITY_FUNCTION_TYPE` — 编译验证通过
- [x] 3.4 Mixin 维度隔离：`MixinRandomState` 接口注入 `EndRandomStateAccess`（ThreadLocal 捕获 seed+isEnd），`MixinNoiseChunk` `@Redirect` `NoiseRouter.mapAll` 注入 visitor — 编译验证通过
- [x] 3.5 `EndBiomeSource`：几何环形分段（`100-8·sqrt` 径向衰减 + Simplex 扰动），5 个 vanilla End biome 显式 `Holder<Biome>` 字段，codec 注册到 `BIOME_SOURCE`，dimension JSON 覆盖 `biome_source.type` — 编译验证通过（climate 变体留待后续 seed 注入）
- [x] 3.6 浮空岛 `FloatingIslands` 生成器（独立密度函数叠加）— `FloatingIslandsField`（worley cell + 径向 hermite falloff × 垂直高斯 lens）+ `FloatingIslandsFunction`（占位符 + Bound DF 包装）+ `EndDensityVisitor` 扩展（field=null 时 gating）+ `EndRandomStateAccess`/`MixinRandomState` 暴露 field + `MixinNoiseChunk` 传 field 给 visitor + `EndTerraForged` 注册 codec + `noise_settings` final_density 用 `add`+`clamp` 组合（OR 语义）— 编译验证通过，10 个新单测全绿

### 阶段 4：河流系统（End 版，独立于海）
> 调研结论：RTF 河流是 2D heightmap 雕刻（非 3D 体积水流），依赖海平面（水位基准=海，终点=海）。End 版重新设计：源头=岛峰，终点=岛缘虚空，水位沿程下降。借鉴 RTF 的 zone1-4 雕刻轮廓 + RiverWarp 路径扭曲 + 按列放水/瀑布检测。
- [x] 4.0 `River` 2D 线段几何（距离/投影/法向量）— 纯逻辑
- [x] 4.1 `EndRiverMap`：worley cell 河流网络 + zone 雕刻 + 沿程水位下降 — 纯逻辑
- [x] 4.2 河流组合进 `EndHeightmap`：`withRivers(EndRiverMap)` 注入，`getHeight` 走 post-river，`getTerrainHeight` 暴露 raw 供 carver 内部采样避免递归 — 纯逻辑
- [x] 4.3 `SeaMode.NONE`：河流终点=岛缘虚空，水位基准=岛屿基准面（`surface=islandBaselineY`，carver 读 `levels.surface`，契约已固化）
- [x] 4.4 `SeaMode.WITH_FLOOR/NO_FLOOR`：河流终点=海（`surface=seaLevelY`，NO_FLOOR 与 WITH_FLOOR 共享 surface，仅 floor 不同）
- [x] 4.5 湖泊：`EndLakeMap` worley cell 圆形盆地 + hermite 岸线 falloff + 当地水位（`centerHeight - depth`，不依赖海）；`EndRiverMap.modifyHeight` 改链式签名（接受 `inputHeight`），`EndHeightmap` 串联 river→lake — 纯逻辑
- [x] 4.6 河流分叉：每条主河流可在 `forkPoint` 处分叉出一条支流（最多一层），水位从 fork 点而非源头下降（`tNormalized` 映射），`RiverSegment` 记录 fork 元数据，`sampleNearestRiver` 遍历 main+fork 找最近 — 纯逻辑
- [x] 4.7 放水/瀑布（stage 3 MC 集成后，借鉴 RTF placeRiverWater）— `EndRiverMap` 暴露 `waterLevel()`+`rivernessAt()`（复用 worley 扫描），`EndRiverWater` 纯逻辑层输出 `WaterInfo(waterTop, waterBottom, isWaterfall)` 归一化水位 band 契约（水只在 `riverness==1` 的 bed 中心填充，bank 保持干涸；waterBottom=post-carve 地形；瀑布=邻居水位 > 当前列地形+drop，仅 river-hosting 列可标；riverMap 未挂载时 fail-fast）；`MixinRandomState` 接入气候/河流/湖泊 post-processor（4.x 算法成果在生产可达），13 个新单测

### 阶段 5：可调界面 + 打磨
- [x] 5.1 `EndPreset` 配置界面（参考 RTF preset editor，暴露 `TopologyMode`/`SeaMode`/`FloatingIslands`/高度范围/侵蚀参数）— **数据层 + GUI 层完成**：`EndPreset` record 实现 `DimensionProfile` + DFU `Codec`（`RecordCodecBuilder`，8 字段，全字段 `optionalFieldOf` 默认值→部分 preset 文件合法，紧凑编码）；`ErosionConfig` 加 `Codec` + value `equals`；enum 开关 `flatXmap` 优雅报错（拼错 mode 名不再崩 worldgen 引导）；`MixinRandomState` 改用 `EndPresetAccess.getOrDefault()`（stage 5.3 接入 GUI 持久化），删除 `EndDefaults`（消除重复，单一真源）；17 个新单测（`EndPresetCodecTest` round-trip/部分/错误处理 11 + `SeaModeTest` 三态矩阵 6）。**GUI 层完成**（stage 5.3 并入）：`SliderScale` + `EndSlider` + `EndPresetEditorScreen`（7 字段全暴露）+ `ErosionConfigEditorScreen`（6 字段子编辑器）+ `MixinCreateWorldScreen`/`CreateWorldScreenInvoker`（RTF 模式拦截 onCreate）+ `EndPresetAccess`（volatile holder）+ en_us/zh_cn lang 文件
- [x] 5.2.1 末地 biome 气候变体分层：在几何环形分段（`EndBiomeSource` ring layer）之上叠加可选的气候变体层。`biome_climate` codec 字段（`optionalFieldOf` 缺省 `EMPTY`）让一个 biome_source JSON 可以为每个几何环挂温/湿度闭区间匹配的变体 biome。`EndClimate` 由 `MixinRandomState` 在引导线程构造并通过 `EndClimateAccess`（volatile 静态持有者）发布给 worker 线程；`EndBiomeSelector` 4 角双线性选择器按 cell 4 角气候采样加权选胜出 biome，避免 4-block 阶梯。缺省 config 退化为原版（fast-path 1 无气候采样，性能等同 vanilla）。39 个新单测覆盖数据层/选择器/接入层
- [x] 5.2.2 末地 feature / surface rules（气候变体的 surface/feature 消费）— `ClimatePredicate` 纯逻辑层（温/湿度闭区间判断，可独立单测）；`ClimateTemperatureCondition` / `ClimateMoistureCondition` 自定义 `SurfaceRules.ConditionSource`（注册到 `MATERIAL_CONDITION`，读取 `EndClimateAccess` 的温/湿度场，让 `surface_rule` JSON 可以按气候门控表面方块）；`ClimatePlacementFilter` 自定义 `PlacementModifier`（注册到 `PLACEMENT_MODIFIER_TYPE`，按温/湿度双轴门控 feature 放置）；`noise_settings/the_end.json` 加 `surface_rule` 示范（低温区 smooth_basalt 表面，其余 end_stone）；access widener 暴露 `SurfaceRules.Context.blockX/blockZ` 和 `SurfaceRules.Condition`；15 个新单测
- [x] 5.2.3 biome_climate 预设包（数据包样例 + GUI 暴露）— **数据层完成**：3 个 dimension 预设样例（`cold_end.json` / `temperate_end.json` / `hot_end.json`）放 `data/endterraforged/presets/dimension/`，每个文件主世界+下界 biome 混用（spec 决策），按 EndClimate 温/湿度闭区间匹配变体；`BiomeClimatePresetTest` 16 个结构校验（Gson-level 解析、ring 字段完整、变体字段完整、闭区间不变式 min<=max、单位区间、`minecraft:` 命名空间、overworld+nether 混用不变式）；codec round-trip 因 `BuiltInRegistries.BIOME` 在当前 Loom 映射下不可直接引用而推迟到集成测试层（stage 5.3 multi-loader runClient 验证）。**GUI 层并入 5.3**（用户决策：本期跳过，并入 5.3）
- [~] 5.3 性能调优 + 多加载器联调 — **性能基线 + GUI 层 + preset 持久化 + ErosionConfig 子编辑器完成**：`PerformanceBenchmarkTest` 9 个轻量 JUnit 微基准（5_000 warmup + 50_000 measure，无 JMH 依赖），覆盖 EndBiomeSelector 3 条路径（fast-path 1 无变体 / fast-path 2 四角同源 / slow path 四角双线性聚合）、EndHeightmap 2 条路径（`getTerrainHeight` raw / `getHeight` climate+river+lake 全链）、ClimatePredicate 4 条路径（bothInRange 发布 / temperatureInRange 发布 / moistureInRange 发布 / null climate fast-false）。DCE guard 通过 checksum 累加防止 JIT 死代码消除；无硬编码阈值（数字仅 System.out 打印供回归比对）。基准 ns/op（沙箱观测值）：fast-path 1 ≈ 48.5、fast-path 2 ≈ 1478.4、slow path ≈ 1764.0、raw height ≈ 853.4、full chain height ≈ 1348.3、bothInRange 发布 ≈ 572.3、temperatureInRange ≈ 317.3、moistureInRange ≈ 152.0、null climate fast-false ≈ 47.6。**GUI 层完成**：`SliderScale` sealed interface（纯逻辑，零 MC 依赖）+ `IntSliderScale` / `FloatSliderScale` record 实现（位置↔值映射 + step 离散化 + 端点钳位 + 退化处理）+ 24 单测；`EndSlider`（vanilla `AbstractSliderButton` 适配器，委托 math 给 SliderScale）；`EndPresetEditorScreen` 7 字段全暴露（4 int slider + 2 enum cycle + 1 boolean toggle）+ Done/Cancel；`MixinCreateWorldScreen` @Inject(method="onCreate", at=HEAD, cancellable=true) + `CreateWorldScreenInvoker` @Invoker("onCreate") RTF 模式拦截 create-world 流程；en_us/zh_cn lang 文件 11 GUI key。**preset 持久化完成**：`EndPresetAccess`（4-method volatile holder，镜像 `EndClimateAccess` 模式）+ 8 单测；`MixinRandomState` 读 `EndPresetAccess.getOrDefault()` 替代 `EndPreset.defaults()`；GUI Done button 调 `EndPresetAccess.set(built)` 发布编辑后的 preset。**ErosionConfig 子编辑器完成**：`ErosionConfigBuilder`（mutable builder，6 字段 fluent setters + reset）+ 7 单测；`ErosionConfigEditorScreen`（6 EndSlider：2 int + 4 float continuous）；EndPresetEditorScreen 加 "Erosion..." 按钮打开子编辑器，子编辑器 Done 把结果写回 builder；7 lang key（en_us/zh_cn）。339 单测全绿。**待做**：多加载器 runClient 联调（codec round-trip 含 `HolderLookup.Provider`）；集成测试层接入

## 六、架构原则

1. **维度抽象优先**：所有「海平面/海洋/大陆/高度」概念收口到 `DimensionProfile`，三轴（拓扑/海/浮岛）正交可调。
2. **算法与 MC 解耦**：droplet erosion、噪声等纯算法放 `common` 的纯逻辑包，不依赖 MC 类，便于单测。
3. **Mixin 按维度隔离**：`MixinRandomState` 等核心 Mixin 通过 `Level` 判定走哪套 Preset，末地与主世界互不干扰。
4. **模式正交**：`TopologyMode` × `SeaMode` × `FloatingIslands` 任意组合都要可工作，禁止某组合走不通的硬编码。
5. **小步提交**：每个子任务独立可编译、可测、可回滚。

## 七、验收标准（阶段 1）
- `./gradlew :common:build` 通过
- droplet erosion 单测在固定种子下输出确定且合理（沉积发生在上坡、侵蚀发生在下坡）
- `Erosion` 类零 `seaLevel`/`ocean`/`continent` 直接依赖（通过 `DimensionProfile` 间接获取基准面）

## 八、气候 biome 分层设计（stage 5.2.1 固化）

> 本节固化 stage 5.2.1 落地的设计决策，作为后续 stage 5.2.2/5.2.3 的前置约束。完整 spec 见 `.trae/specs/add-biome-climate-layering/spec.md`。

### 1. 两层正交模型：几何环 × 气候变体

- **几何环层**（`EndBiomeSource.getNoiseBiome` 的径向衰减 + simplex 扰动）决定 cell 属于哪个 ring（end/highlands/midlands/islands/barrens）。
- **气候变体层**（`BiomeClimateConfig` + `EndBiomeSelector`）在 ring 内部可选地按气候采样选变体 biome。
- 两层独立：缺省 `biome_climate`（`BiomeClimateConfig.EMPTY`）→ `hasAnyVariants()` false → fast-path 1 跳过所有气候工作，性能等同 vanilla。
- biome_climate 放 biome_source JSON（不是 EndPreset）：biome 是注册表引用，属维度生成配置；EndPreset 是地形配置，不应混入 biome 引用。

### 2. 4 角双线性选择器（避免 4-block 阶梯）

- vanilla biome API 只给整数 cell 坐标，单点采样会产生 4-block 阶梯（cell 边界突变）。
- `EndBiomeSelector.select(cellX, cellZ, fracX, fracZ, climate, seed, ringSlot)` 采样 cell 4 角气候，按 fractional 位置双线性加权选胜出 biome。
- 两个 fast-path：climate==null 或无变体 → base；4 角候选全 == 相同 → c00。
- 慢路径用 `==` 引用比较聚合权重（非 equals），平局按角序 00>10>01>11。
- `EndBiomeSelector` 是独立纯逻辑类（只依赖 EndClimate + BiomeSlot + Holder），可独立单测，与 MC 集成类（EndBiomeSource）分离，符合 §六.2「算法与 MC 解耦」。

### 3. 跨线程发布 EndClimate（volatile 静态持有者）

- `EndClimate` 由 `MixinRandomState` 在引导线程构造（`EndClimate.defaults(noiseSeed)`），worker 线程在 `EndBiomeSource.getNoiseBiome` 中消费。
- 选 volatile 静态持有者（`EndClimateAccess`）而非扩展 `EndRandomStateAccess` 接口：`EndBiomeSource` 由 codec 构造，构造时 `RandomState` 还不存在；扩展接口注入字段会迫使 EndBiomeSource 持有 RandomState 引用，破坏「codec 构造即可用」的简洁。
- `EndClimate` 是不可变 record，volatile 发布不可变图是 JMM 标准做法（happens-before）；一个 writer（引导线程）+ 多个 reader（worker 线程），无需进一步同步。
- `clear()` 仅供测试 teardown；生产代码用 `set(newClimate)` 原子覆盖旧引用。

### 4. BiomeSlot nullable base + resolve() 一次性填充

- JSON 可省略 slot 的 base（null = 继承几何环 biome）。
- `EndBiomeSource` 构造时调 `BiomeClimateConfig.resolve(5 holders)` 一次性填 null base。
- 热路径无 null 检查（resolved config 的每个 slot base 都非 null）。

### 5. fracNoise 提供亚 cell fractional 位置

- vanilla biome API 无亚 cell 位置，用两个 simplex（`Noises.map(simplex(1339/1340, 50, 2), 0, 1)`）提供稳定的 cell 内 fractional 位置。
- 固定 seed（本期 biome layout 不绑世界 seed，与 ringNoise/outerNoise 一致）。
- per-call seed 传 0：世界 seed 已 baked 进 `EndClimate` 的 noise trees，per-call seed 是 vestigial。

### 6. 测试设计

- `PointNoise` test-only record（`BiFunction<Float,Float,Float>` lambda）让测试精确控制每个角的气候值。
- `Holder.direct(null)` stub biome：全部 `equals()`-相等（值都是 null），Set/contains/Map 会塌缩；测试用 `==` 引用比较 + `List` + `anyMatch(h -> h == expected)`。
- `@BeforeAll` 调 `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`（1.21.1 official Mojang mappings 中是 `net.minecraft.server.Bootstrap`）；`tryDetectVersion` 必须先于 `Bootstrap.bootStrap`。
- `EndBiomeSourceTest` 在 `@BeforeEach`/`@AfterEach` 调 `EndClimateAccess.clear()` 隔离 process-wide 静态状态。

### 7. 兼容性

- `the_end.json`（biome_source JSON）无需改动：`biome_climate` 是 `optionalFieldOf`，旧 5 字段 JSON 仍合法解码。
- 缺省 config 性能等同原版（fast-path 1 无气候采样）。
- 非 End 维度不发布 climate（`MixinRandomState` 的 `isEnd` 分支 gating），`EndBiomeSource` fast-path 2 返 base。

### 阶段 6：持久化 + 发布准备（进行中）

> Stage 5 完成了可调界面 + 性能基线。Stage 6 聚焦两个核心问题：(1) GUI 编辑的 preset 跨会话持久化（当前 `EndPresetAccess` 是进程内 volatile holder，JVM 重启即丢失）；(2) 发布前打磨（错误处理、边界条件、打包验证）。**进度**：6.1 完成（持久化闭环已落地）；6.3 完成（验证层 + datapack UX + Mixin fallback + 代码审查修复 三层全部落地）；6.2 集成测试 + 6.4 发布打包 待做。

- [x] 6.1 Preset 持久化到世界存档
  - **问题**：`EndPresetAccess` 是静态 volatile holder — 单人创建世界时 GUI Done 发布 preset → MixinRandomState 读取 → 世界正常生成；但关闭游戏后重开世界，holder 为 null → MixinRandomState 读 `EndPreset.defaults()` → 地形参数与创建时不一致（如果用户改过高度/海平面等）。
  - **方案 A（推荐，已落地）**：Mixin 注入 `MinecraftServer` 的世界加载/保存路径，在世界存档目录读写 `endterraforged_preset.json`。
  - **方案 B**：Fabric `AttachmentType` / NeoForge `Capability` — loader-specific，更干净但需双份实现。Architectury 提供抽象层但需要额外依赖。
  - **技术难点**：`RandomState.create` 在世界加载早期被调用，此时 world 目录可能尚未完全初始化；需要找到正确的注入点（`MinecraftServer` 初始化后、`RandomState` 构造前）。
  - **完成**（2026-07-04，两部分落地）：
    - **纯逻辑层**（commit `b43722f`）：`EndPresetStorage`（pure-logic IO + codec 层，零 MC 依赖除 DFU+Gson 已在 classpath）+ 29 单测。文件位置 `<worldDir>/endterraforged_preset.json`（top-level，与 `level.dat` 同级）；原子写（temp-file-then-`Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`，不支持时 fallback 非原子 replace）；`load` 返 `Optional<EndPreset>` 永不抛（缺失/损坏/类型不匹配/IO 错误都返 empty）；`save`/`delete` 返 boolean 永不抛；`null` path/preset 抛 NPE fail-fast（programmer error，非 runtime IO 条件）；pretty-printed JSON（git-friendly）。
    - **MC 集成层**（commit `063be26`）：`MixinMinecraftServer`（单 Mixin，`@Shadow @Final storageSource` 读 `LevelStorageSource.LevelStorageAccess`）+ 注册到 `mixins` 数组（非 `client`，dedicated server 也要跑）。**load 注入点**：`@Inject(method="createLevels", at=@At("HEAD"))` — `createLevels(ChunkProgressListener)` 是构造 `ServerLevel` 的入口（→ `ChunkGenerator.createState` → `RandomState.create`），HEAD 保证文件加载在 `MixinRandomState.<init>` 读 `EndPresetAccess.getOrDefault()` 前；fresh world → 文件不存在 → `load` 返 empty → `ifPresent` no-op → GUI-published preset 流过；world re-open → 文件存在 → `set(loaded)`。**save 注入点**：`@Inject(method="saveEverything", at=@At("RETURN"))` — `saveEverything(boolean,boolean,boolean)` 在 auto-save tick（每 ~5 min）和 `halt` 时调用，RETURN 保证我们的写在 vanilla chunk-save IO 之后；`EndPresetAccess.get() == null` 时 skip（不污染从未配置过的 fresh world）。**验证**：`common`/`fabric` 编译通过；refmap 正确映射 `createLevels`→`method_3786` / `saveEverything`→`method_39218`；368 单测全绿。NeoForge offline `unprotect:1.3.1` 是预存依赖问题（与本改动无关）。**待做**（后续 stage 6.2 集成测试）：`runClient` 端到端验证 GUI Done → createLevels HEAD load（no-op）→ saveEverything RETURN save → 重启 → createLevels HEAD load（命中文件）的完整闭环。
- [ ] 6.2 多加载器 runClient 集成测试
  - `gradle :fabric:runClient` + `gradle :neoforge:runClient` 联调（沙箱无显示，需本地执行）
  - 验证 GUI 渲染、滑块拖动、子编辑器导航、Done/Cancel 流程
  - 验证 codec round-trip（`EndPreset.CODEC` + `HolderLookup.Provider`）
  - 验证 biome_climate 预设包在游戏内的实际效果
- [x] 6.3 错误处理 + 边界条件打磨
  - **验证层完成**（2026-07-04，三个 commit）：
    - `ErosionConfigValidator`（commit `87a9251`，纯逻辑 + 23 单测）：6 字段物理约束（`dropletsPerChunk >= 0` / `dropletLifetime >= 1` / `dropletVolume >= 0` / `dropletVelocity >= 0` / `erosionRate` ∈ [0,1] / `depositRate` ∈ [0,1]），fail-fast 第一违例报错（非全列表），`null` arg 抛 NPE。RTF 上游无等价设施（裸 `Codec.INT`/`Codec.FLOAT` 全靠 vanilla `DimensionType` 晚期 `IllegalArgumentException` 报错），EndTerraForged 借自家 `SEA_MODE_CODEC` `flatXmap` 先例闭合此缺口。
    - `EndPresetValidator`（commit `0a1191e`，纯逻辑 + 26 单测）：7 字段 + 跨字段约束（`worldHeight` ∈ [16, 4064] 且 16 倍数；`minY >= -2032` 且 16 倍数；`minY + worldHeight <= 2032` vanilla 顶部限；`seaLevelY` / `islandBaselineY` ∈ `[minY, minY + worldHeight - 1]` 无条件校验）；embedded `ErosionConfig` 委托 `ErosionConfigValidator`（错误信息前缀 `erosion config invalid:` 让用户定位到子配置）；fail-fast，same-instance-on-success。
    - **Codec 接入**（commit `82bd453`，+ 23 codec 测试）：`ErosionConfig.CODEC` = `BASE_CODEC.flatXmap(ErosionConfigValidator::validate, c -> DataResult.success(c))`；`EndPreset.CODEC` 同模式接 `EndPresetValidator::validate`。为何拆 `BASE_CODEC` + `CODEC` 两步：javac 类型推断对 `RecordCodecBuilder.create(...).apply(instance, instance.stable(...)).flatXmap(...)` 链式调用解析失败（编译器把 `instance.group(...).apply` 的结果类型推断为 `Object`），拆开后类型固定再 `flatXmap` 才能编译。Encode 侧 identity（`DataResult::success`），因为构造时已满足约束 — 校验只 gate decode 方向。440 单测全绿。
  - **数据包错误 UX 完成**（2026-07-04，两个 commit）：
    - `EndPresetStorage.load(Path, Consumer<String>)` overload（commit `c213489`，纯逻辑 + 11 单测）：调用方传入 error handler，文件存在但解码失败时回调，消息带 codec 验证层的字段定位（如 `"world_height must be a multiple of 16, got 100"`）；文件缺失/路径是目录 → 不回调（fresh world 不应刷日志）；`load(Path)` 委托 `load(Path, msg -> {})` 保持向后兼容（既有 29 测试不变）。
    - `MixinMinecraftServer` 接入（commit `135109b`）：`createLevels` HEAD 注入改为调用新 overload，error handler 用 `EndTerraForged.LOGGER.warn` 输出 — 一个手编辑出错的 `endterraforged_preset.json` 现在会在 server log 里看到完整的字段级错误（"Preset file failed to decode: world_height must be a multiple of 16, got 100"）而不是静默退化到 defaults。WARN 级别（非 ERROR）：世界仍正常加载，只是该次 load 丢了用户配置；下次 save tick 会写出新的有效文件覆盖坏的。451 单测全绿。
  - **Mixin 失败 fallback 完成**（2026-07-04，两个 commit）：
    - `EndWorldgenBootstrap` 纯逻辑层（commit `1b808fa`，+ 10 单测）：把 climate → heightmap → density → floating islands 的构造序列封装进单一 try/catch。失败时 (1) 日志 WARN（世界仍加载，仅 End 自定义地形丢失），(2) 回滚 `EndClimateAccess` 到 null（防止部分构造的 stale climate 通过 volatile holder 泄漏到下次 End load），(3) 返 `Result(degraded=true, endDensity=null, floatingIslandsField=null)`。`catch (Exception)` 而非 `catch (Throwable)`（`OutOfMemoryError`/`StackOverflowError` 不应被 catch，让 JVM fail fast）。包私有重载 `bootstrap(int, EndPreset, BiFunction, Function)` 接受工厂 lambda，让测试注入会抛的工厂固化降级契约。为何抽纯逻辑类而非 inline：MixinRandomState `<init>` inject 是 Mixin processor 运行时编织的字节码，纯 JUnit 无法 instantiate；抽纯逻辑后端到端 fallback 契约可单测。10 个测试覆盖：成功路径（默认 profile / floating islands 启用 / climate 发布）/ 失败路径（heightmap 工厂抛 / floating islands 工厂抛 / NPE / IAE / RTE / climate 回滚）/ 无状态幂等。
    - `MixinRandomState` 接入（commit `1d3d25f`）：`<init>` 内联构造序列替换为 `EndWorldgenBootstrap.bootstrap(noiseSeed, profile)` 调用。`Result.degraded()` 时把 `isEnd` 设回 false — 这样 `MixinNoiseChunk` 的 redirect 跳过 `EndDensityVisitor` 调用（否则 `EndDensityFunction.Bound.compute` 在第一个 chunk 上 NPE 再次崩溃 worldgen）。End 维度此时用 vanilla 生成（占位符 `EndDensityFunction.INSTANCE` 返 0 → chunks 全空气；`EndBiomeSource` 走 fast-path 1 因 `EndClimateAccess` 是 null → 返 vanilla End biomes）。世界保持可加载状态而非崩溃。461 单测全绿。
  - **代码审查修复完成**（2026-07-04，5 个 commit + 1 个被外部流程合并）：Stage 6.3 三层落地后主动审查代码库，发现 8 个优先级问题。已修复 P1/P2/P3/P4/P6/P7（6 项），P5/P8 评估后延期。
    - **P1（HIGH，功能 bug — `EndPresetAccess` 跨世界 stale preset 泄漏）**（commit `8de5555`）：`MixinMinecraftServer` 加 `@Inject(method="halt", at=@At("RETURN"))` 调 `EndPresetAccess.set(null)`。否则 holder 在同一 JVM 会话内跨世界泄漏（单人：创建世界 A → 保存退出 → 加载世界 C → `MixinRandomState` 为世界 C 读到 presetA）。`halt(boolean)` 是 vanilla 1.21.1 服务端停止入口（`/stop` / 专用服退出 / 单人「保存并退出」）；RETURN 保证服务端线程完全停止。`EndPresetAccess` 生命周期 Javadoc 同步更新。
    - **P2（HIGH — chunk 采样异常崩溃 worldgen，两 commit 落地三层 fallback）**：
      - **P2a 绑定时 fallback**（commit `76623bc`）：`MixinNoiseChunk.endTerraForged$wrapMapAll` 的 `router.mapAll(new EndDensityVisitor(...))` 包进 `try (Exception) catch`。失败时 log WARN + 不设 `endTerraForged$bound`（允许下次 mapAll 重试）+ 用未修改的 router 走 fall-through（占位符 INSTANCE → 0.0 → 该 chunk 全空气）。`NoiseRouter.mapAll` 非变更（返新 router），throw 后原 router 参数完整。
      - **P2b 采样时 fallback**（commit `916f8d8`）：`EndDensityFunction.Bound.compute` 的 `endDensity.density(...)` 包进 `try (Exception) catch`。失败时 log WARN（`AtomicBoolean FIRST_SAMPLING_FAILURE_LOGGED` 网关只 log JVM 会话首次，避免 hot path 百万次调用刷屏）+ 返 0.0（该 block 变空气）。`compute` 是 hot path（每 chunk 百万次调用），AtomicBoolean 因并行 chunk-gen 线程。
      - **三层 fallback 哲学（至此完整）**：构造失败（`EndWorldgenBootstrap`）→ 整个 End 维度退化为 vanilla 生成；绑定失败（`MixinNoiseChunk` redirect）→ 一个 chunk 退化为占位符（空气）；采样失败（`Bound.compute`）→ 一个 block 退化为 void（空气）。
    - **P3（MID — `EndRandomStateAccess` Javadoc drift）**（commit `8de5555`）：完全重写 Javadoc 修复与实现的偏差 — `@At("HEAD")`（非 RETURN，附 ThreadLocal 捕获解释）；eagerly built via `EndWorldgenBootstrap`（非 lazy）；profile 源是 `EndPresetAccess.getOrDefault()`（非 "default DimensionProfile"）。新增「Stage 6.3 fallback」节解释 `isEnd()` 在退化时对 End 维度返 false。`endTerraForged$isEnd()` / `endTerraForged$getEndDensity()` Javadoc 同步更新警告调用方。
    - **P4（MID — package-private overload 成功路径测试覆盖）**（commit `8de5555`）：`EndWorldgenBootstrapTest` 加 `bootstrapSucceedsWhenFloatingIslandsFactoryReturnsNullDirectly` 固化 package-private overload 接受 `p -> null` floating-islands 工厂作为成功路径（非退化）的契约 — 与 public overload 默认行为一致（`floatingIslandsEnabled == false` 时默认工厂返 null）。
    - **P6（MID — `MixinRandomState` ThreadLocal 在 Error 路径下泄漏）**（commit `3c18e59`）：`endTerraForged$initCapture` 主体包进 `try/finally`，finally 块清 `END_TERRAFORGED_CAPTURE` ThreadLocal。`EndWorldgenBootstrap` catch Exception 但 Error（OOM/SOE）会传播 — 原 clear 在 try 外，Error 跳过 clear 导致 ThreadLocal 残留。实际上 `captureCreate` 总在下次读前覆盖，stale 值不会导致功能 bug，但 try/finally 让管理「by construction」正确而非「by coincidence」。
    - **P7（LOW — `==` vs `.equals()` for ResourceKey）**（commit `b14b168`）：`MixinRandomState.endTerraForged$captureCreate` 的 `key == NoiseGeneratorSettings.END` 改为 `NoiseGeneratorSettings.END.equals(key)`。Vanilla 通过 `ResourceKey.intern()` 实习 ResourceKey 实例，`==` 实践中可行；但契约是 `.equals` — 非 interned 的 key（其他 mod 或测试构造）会 `==` 失败但 `.equals` 匹配。顺序换成 `END.equals(key)` 避免 null key NPE（防御）。
    - **P5 延期**（MID — `EndPresetStorage` IO 错误路径无测试）：现有 34 个测试覆盖所有解码路径（corrupt JSON / wrong shape / type mismatch / unknown enum / constraint violation via handler overload / null args NPE）。未覆盖的 IO exception 路径（文件不可读 / 磁盘满 / `AtomicMoveNotSupportedException` fallback）需文件系统 mock 或自定义 FileSystem — 复杂度 vs 收益（catch 块是 3 行简单 return）不成比例。延期至需要时。
    - **P8 延期**（LOW — `EndClimateAccess` 非 End 维度清理）：语义问题，无真实 bug — `EndWorldgenBootstrap` 在下次 End load 时 `EndClimateAccess.set(newClimate)` 覆盖 stale 值，任何 reader 看到前先被覆盖。非 End 维度不读 `EndClimateAccess`（`EndBiomeSource` 是 End 专属）。P1 的 `halt` clear 已处理 `EndPresetAccess` 的现实泄漏场景；`EndClimateAccess` 的等价 clear 是一致性 polish，无功能影响。
- [ ] 6.4 发布打包
  - `gradle build` 产出 Fabric + NeoForge 双 jar
  - changelog 整理
  - modrinth/curseforge 发布元数据
