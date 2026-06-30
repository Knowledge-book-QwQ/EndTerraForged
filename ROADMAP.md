# EndTerraForged 开发企划

> 把 ReTerraForged（RTF）那套真实地形 / 水文侵蚀体系，改造成 **末地（The End）维度** 版本：浮空岛屿、无海洋、无海平面。

## 一、项目定位

| 维度 | 主世界（RTF 现状） | 末地（本项目目标） |
|---|---|---|
| 拓扑 | 连续大陆 + 海洋 | 浮空岛屿 + 虚空 |
| 海平面 | 有，驱动水位/海滩/含水层 | **无**，`sea_level = 0` |
| 河流 | 沿等高线 carving 至海洋 | 改造为「岛屿间虚空裂隙 / 岛心峡谷」 |
| 水力侵蚀 | 地表滴粒侵蚀 | 复用，作用于岛屿表面与边缘 |
| ChunkGenerator | 共用 `NoiseBasedChunkGenerator` | **同样共用**，只换 `noise_settings` + 自定义 `BiomeSource` |
| 接入方式 | Mixin 把 Cell 高度场桥接为 vanilla `DensityFunction` | 沿用同一套桥接，路由到末地维度 |

**核心判断**：末地与主世界共用 `NoiseBasedChunkGenerator`，原版末地 `sea_level=0`、`aquifer_enabled=false`。
因此本项目 **不需要重写 ChunkGenerator**，只需：
1. 为末地构建一套 `noise_settings`（`final_density` 用我们的高度场 + 侵蚀场）。
2. 提供末地 `BiomeSource`（按岛屿分布选 biome）。
3. 复用 RTF 的 Mixin 桥接（`CellSampler` → `DensityFunction`），但按维度隔离 Preset。

## 二、可借鉴来源（已核实协议）

| 来源 | 协议 | 可借鉴方式 |
|---|---|---|
| ReTerraForged / TerraForged | **MIT** | **直接搬代码**，保留 MIT 版权头 + NOTICE lineage |
| BetterEnd (paulevsGitch) | MIT | 岛屿 shape / `BiomeSource` 写法可参考或搬 |
| Nullscape | MIT | 虚空处理、`TheEndBiomeSource` 覆写思路 |
| The Aether | LGPL-3.0 | **仅思路**（浮空岛距离场），代码勿搬 |
| YUNG's Better End Island | LGPL-3.0 | **仅思路**（结构替换龙岛），代码勿搬 |

> 搬运 MIT 代码时，在每个文件头部保留原 MIT 版权声明，并在 `NOTICE.md`（后续建立）记录 lineage：`TerraForged (dags) → ReTerraForged (raccoonman) → EndTerraForged`。

## 三、水文侵蚀在 RTF 中的真相

调研确认，RTF 里"侵蚀/水文"实为 **五个不同概念**，移植时必须分清：

| 概念 | RTF 位置 | 本质 | 末地移植决策 |
|---|---|---|---|
| **水力滴粒侵蚀** | `tile/filter/Erosion.java` | 真正的高度场 droplet erosion 仿真 | **本期重点移植**，相对独立 |
| 河流 carving | `UpliftRiverCarver` | 高度场径向变形（4 区） | 后期改造为「岛心裂谷」 |
| `ContinentalHydrology` | `cell/rivermap/ContinentalHydrology` | 静态查找表，强耦合"内陆度/海平面" | **弃用**，末地无海洋语义 |
| 热力侵蚀噪声 | `noise/module/Erosion.java` | Worley 风格程序化噪声 | 可选移植，参与岛屿 shape |
| `ErodeFeature` | `feature/ErodeFeature.java` | 方块级表面装饰 | 后期按末地材质重写 |

**"水文侵蚀非常真实"** 主要来自第一项：`Erosion.java` 的 droplet 算法（沉积容量、brush 权重、速度/水量衰减）。它 **完全自包含**，唯一海平面耦合点是 `Factory` 里 `Modifier.range(levels.ground, levels.ground(15))`——只需把"地表基准"抽象为「岛屿基准面 Y」即可解耦。

## 四、分阶段路线图

### 阶段 0：地基（进行中）
- [x] 仓库初始化（LGPL-3.0、Architectury 多加载器脚手架）
- [x] 调研 RTF 架构与协议
- [x] 本企划 + 开发规范
- [ ] 建立包结构骨架与维度抽象（`DimensionProfile`、`EndLevels`）
- [ ] 打通本地构建验证链路

### 阶段 1：移植水力滴粒侵蚀（首个可工作增量）
目标：在末地岛屿高度场上跑通 droplet erosion，作为独立滤波器，暂不接入 chunk 生成。

- [ ] 1.1 移植纯工具类：`FastRandom`、`NoiseUtil.seed/clamp`（维度无关）
- [ ] 1.2 移植 `Cell` 的最小字段集（`height`/`sediment`/`heightErosion`/`erosionMask`）
- [ ] 1.3 移植 `Filter` / `Filterable` / `Size` / `Modifier` 接口
- [ ] 1.4 移植 `FilterSettings.Erosion` 配置（Codec 化）
- [ ] 1.5 移植 `Erosion` 算法本体，`Factory` 改用 `EndLevels`（岛屿基准面 Y）
- [ ] 1.6 单元测试：固定种子下 droplet 在合成高度场上的侵蚀/沉积行为
- [ ] 1.7 编译通过 + 提交

### 阶段 2：末地高度场骨架
- [ ] 2.1 `EndLevels`：用「岛屿基准面 Y」替代 `seaLevel`，重定义 `ground`/`elevation`
- [ ] 2.2 `FloatingIslandContinent`：替代 `ContinentGenerator`，输出「岛心度/岛缘度/到虚空距离」
- [ ] 2.3 噪声模块移植（`Noises` 的 perlin/simplex/worley/warp 子集）
- [ ] 2.4 `EndHeightmap`：单层（岛心/岛缘），删除海洋三层

### 阶段 3：接入 vanilla 末地生成
- [ ] 3.1 `EndPreset` + `EndNoiseGeneratorSettings`（注册到末地维度）
- [ ] 3.2 `EndNoiseRouterData`：重建末地 `NoiseRouter`，`final_density` 用高度场 + 侵蚀场
- [ ] 3.3 Mixin 维度隔离：扩展 `MixinRandomState` 按 `Level` 选 Preset（overworld vs end）
- [ ] 3.4 `EndBiomeSource`：按岛屿分布选 biome，注入 `#minecraft:is_end` 标签

### 阶段 4：河流 → 虚空裂隙改造
- [ ] 4.1 复用 `UpliftRiverCarver` 几何（zone1-4 高度调制），水位语义改为「岛面基准」
- [ ] 4.2 河流终点从「海洋」改为「岛屿边缘/虚空」
- [ ] 4.3 「虚空裂隙」：低于阈值的方块置空，形成岛屿间走廊

### 阶段 5：打磨与配置 UI
- [ ] 5.1 `EndPreset` 配置界面（参考 RTF preset editor）
- [ ] 5.2 末地 biome / feature / surface rules
- [ ] 5.3 性能调优 + 多加载器联调

## 五、架构原则

1. **维度抽象优先**：所有「海平面/海洋/大陆」概念收口到 `DimensionProfile`，末地主世界两套实现，避免散落耦合。
2. **算法与 MC 解耦**：droplet erosion、噪声等纯算法放 `common` 的纯逻辑包，不依赖 MC 类，便于单测。
3. **Mixin 按维度隔离**：`MixinRandomState` 等核心 Mixin 通过 `Level` 判定走哪套 Preset，末地与主世界互不干扰。
4. **小步提交**：每个子任务独立可编译、可测、可回滚。

## 六、验收标准（阶段 1）
- `./gradlew :common:build` 通过
- droplet erosion 单测在固定种子下输出确定且合理（沉积发生在上坡、侵蚀发生在下坡）
- `Erosion` 类零 `seaLevel`/`ocean`/`continent` 依赖
