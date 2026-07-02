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
- [ ] 2.5c climate 场作为未来 biome source 的子类型选择器

### 阶段 3：接入 vanilla 末地生成（含高度扩展）
> 环境已通：gradle 8.14.4 + Java 21 + 沙箱 HTTP 代理（`GRADLE_OPTS` 设 `127.0.0.1:18080`）。三模块编译通过，174 测试全绿。MC 集成采用 RTF 的「占位符 DF + mapAll 晚绑定」模式：`EndDensityFunction` 占位符经 DFU 反序列化进 `noise_settings`，`MixinNoiseChunk` 在 `NoiseChunk.<init>` 的 `router.mapAll` 处用 `EndDensityVisitor` 把占位符换成携带 seed+`EndDensity` 的 `Bound` 实例。
- [x] 3.1 自定义末地 `DimensionType`（`min_y=-2032, height=4064`，对标 RTF）— 数据包覆盖 `data/minecraft/dimension_type/the_end.json`
- [x] 3.2 `noise_settings` 覆盖 `the_end.json`，`final_density` 指向 `endterraforged:end_density`；`EndDefaults` 作为 stage-3.2 `EndPreset` 的前身提供默认 profile — 数据包 + 代码
- [x] 3.3 `EndDensityFunction`（占位符 + `Bound`）+ `EndDensityVisitor`（mapAll 替换）+ 注册到 `DENSITY_FUNCTION_TYPE` — 编译验证通过
- [x] 3.4 Mixin 维度隔离：`MixinRandomState` 接口注入 `EndRandomStateAccess`（ThreadLocal 捕获 seed+isEnd），`MixinNoiseChunk` `@Redirect` `NoiseRouter.mapAll` 注入 visitor — 编译验证通过
- [x] 3.5 `EndBiomeSource`：几何环形分段（`100-8·sqrt` 径向衰减 + Simplex 扰动），5 个 vanilla End biome 显式 `Holder<Biome>` 字段，codec 注册到 `BIOME_SOURCE`，dimension JSON 覆盖 `biome_source.type` — 编译验证通过（climate 变体留待后续 seed 注入）
- [ ] 3.6 浮空岛 `FloatingIslands` 生成器（独立密度函数叠加）

### 阶段 4：河流系统（End 版，独立于海）
> 调研结论：RTF 河流是 2D heightmap 雕刻（非 3D 体积水流），依赖海平面（水位基准=海，终点=海）。End 版重新设计：源头=岛峰，终点=岛缘虚空，水位沿程下降。借鉴 RTF 的 zone1-4 雕刻轮廓 + RiverWarp 路径扭曲 + 按列放水/瀑布检测。
- [x] 4.0 `River` 2D 线段几何（距离/投影/法向量）— 纯逻辑
- [x] 4.1 `EndRiverMap`：worley cell 河流网络 + zone 雕刻 + 沿程水位下降 — 纯逻辑
- [x] 4.2 河流组合进 `EndHeightmap`：`withRivers(EndRiverMap)` 注入，`getHeight` 走 post-river，`getTerrainHeight` 暴露 raw 供 carver 内部采样避免递归 — 纯逻辑
- [x] 4.3 `SeaMode.NONE`：河流终点=岛缘虚空，水位基准=岛屿基准面（`surface=islandBaselineY`，carver 读 `levels.surface`，契约已固化）
- [x] 4.4 `SeaMode.WITH_FLOOR/NO_FLOOR`：河流终点=海（`surface=seaLevelY`，NO_FLOOR 与 WITH_FLOOR 共享 surface，仅 floor 不同）
- [x] 4.5 湖泊：`EndLakeMap` worley cell 圆形盆地 + hermite 岸线 falloff + 当地水位（`centerHeight - depth`，不依赖海）；`EndRiverMap.modifyHeight` 改链式签名（接受 `inputHeight`），`EndHeightmap` 串联 river→lake — 纯逻辑
- [x] 4.6 河流分叉：每条主河流可在 `forkPoint` 处分叉出一条支流（最多一层），水位从 fork 点而非源头下降（`tNormalized` 映射），`RiverSegment` 记录 fork 元数据，`sampleNearestRiver` 遍历 main+fork 找最近 — 纯逻辑
- [ ] 4.7 放水/瀑布（stage 3 MC 集成后，借鉴 RTF placeRiverWater）

### 阶段 5：可调界面 + 打磨
- [ ] 5.1 `EndPreset` 配置界面（参考 RTF preset editor，暴露 `TopologyMode`/`SeaMode`/`FloatingIslands`/高度范围/侵蚀参数）
- [ ] 5.2 末地 biome / feature / surface rules
- [ ] 5.3 性能调优 + 多加载器联调

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
