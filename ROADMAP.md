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

### 阶段 1：移植水力滴粒侵蚀（进行中）
目标：在末地高度场上跑通 droplet erosion，作为独立滤波器，暂不接入 chunk 生成。

- [x] 1.1 移植纯工具类：`FastRandom`、`NoiseUtil.seed/clamp`
- [ ] 1.2 移植 `Cell` 的最小字段集（`height`/`sediment`/`heightErosion`/`erosionMask`）
- [ ] 1.3 移植 `Filter` / `Filterable` / `Size` / `Modifier` 接口
- [ ] 1.4 移植 `FilterSettings.Erosion` 配置（Codec 化）
- [ ] 1.5 移植 `Erosion` 算法本体，`Factory` 改用 `DimensionProfile` 基准面
- [ ] 1.6 单元测试：固定种子下 droplet 在合成高度场上的侵蚀/沉积行为
- [ ] 1.7 编译通过 + 提交

### 阶段 2：维度抽象 + 末地高度场骨架
- [ ] 2.1 `DimensionProfile`：收口 `seaLevel`/`worldHeight`/`worldDepth`/`SeaMode`/`TopologyMode`/`FloatingIslands`/`defaultFluid`
- [ ] 2.2 `EndLevels`：`SeaMode.NONE` 时以"岛屿基准面 Y"为基准；`WITH_FLOOR/NO_FLOOR` 时复用 RTF `Levels`
- [ ] 2.3 `ContinentModule` 双实现：
  - `ContinentalShatteredContinent`（连续大陆 + 裂隙噪声）
  - `IslandsContinent`（离散岛屿距离场）
- [ ] 2.4 噪声模块移植（`Noises` 的 perlin/simplex/worley/warp 子集）
- [ ] 2.5 `EndHeightmap`：按 `TopologyMode` 选择单层/分层，按 `SeaMode` 决定是否海洋分层
- [ ] 2.6 `NO_FLOOR` 后处理：海平面以下密度强制置虚空（原创密度函数）

### 阶段 3：接入 vanilla 末地生成（含高度扩展）
- [ ] 3.1 自定义末地 `DimensionType`（`min_y=-2032, height=4064`，对标 RTF）
- [ ] 3.2 `EndPreset` + `EndNoiseGeneratorSettings`（注册到末地维度）
- [ ] 3.3 `EndNoiseRouterData`：重建末地 `NoiseRouter`，`final_density` 用高度场 + 侵蚀场 + `NO_FLOOR` 后处理
- [ ] 3.4 Mixin 维度隔离：扩展 `MixinRandomState` 按 `Level` 选 Preset（overworld vs end）
- [ ] 3.5 `EndBiomeSource`：按拓扑/海平面/岛屿分布选 biome，注入 `#minecraft:is_end` 标签
- [ ] 3.6 浮空岛 `FloatingIslands` 生成器（独立密度函数叠加）

### 阶段 4：河流 → 裂隙/海峡改造
- [ ] 4.1 复用 `UpliftRiverCarver` 几何（zone1-4 高度调制）
- [ ] 4.2 `SeaMode.NONE`：河流终点改为「岛屿边缘/虚空」，水位语义改为「岛面基准」，形成虚空裂隙
- [ ] 4.3 `SeaMode.WITH_FLOOR/NO_FLOOR`：河流终点改为「海」，形成海峡/岛心峡谷

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
