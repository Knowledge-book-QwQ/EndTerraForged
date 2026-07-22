# ETF Worldgen Content Pack API v1 设计规格

> 文档状态：当前有效，待实现。
> 最近更新：2026-07-12。
> 权威范围：主岛外群系、内容主题、表面材料、feature 与兼容包的数据契约。
> 本规格不表示功能已经落地；实现状态以源码、测试和 `PLAN.md` 为准。

## 1. 目标

ETF Content Pack API 用于把“空间几何由谁生成”和“这个空间呈现什么生态与内容”分开：

- ETF 核心负责 dimension、noise、density、大陆体积、洞穴、气候和稳定地貌信号。
- Content Pack 把稳定信号映射为 Minecraft biome、内容主题、surface palette 和 feature。
- 简单内容包只使用数据包；复杂外部系统通过独立 adapter 接入。
- 核心始终提供原版 fallback，缺少第三方资源时不得返回空 biome 或生成半配置世界。

Content Pack 不拥有 ETF density，也不得修改当前冻结的原版主岛、龙战和外围区域。中央保护区在 runtime 中由 vanilla density 决定；pack selector 在此区域必须使用原版 fallback profile，不能通过 biome、palette、feature 或 structure 旁路改写保护语义。

## 2. 非目标

v1 不负责：

- 解释任意 Terra YAML、TerraScript 或外部噪声表达式。
- 让两个 chunk generator 同时接管同一 End dimension。
- 在资源重载阶段动态注册新的 Minecraft biome。
- 把 biome holder、registry holder 或 UI 状态写入 `EndPreset`。
- 用一个巨型 JSON 重建完整 feature、structure 或脚本引擎。
- 自动复制第三方 GPL 配置、结构或资源。

## 3. 分层模型

### 3.1 Biome Layer

Biome Layer 只决定服务端返回哪个已注册 biome：

- 目标可以是 biome id 或 biome tag。
- tag 在资源重载时展开为按资源位置稳定排序的候选列表。
- 选择必须由 world seed、稳定空间单元和规则 id 决定。
- 缺失候选时沿 fallback 链回退到 `endterraforged:vanilla`。

### 3.2 Content Profile

Content Profile 是独立于 Minecraft biome holder 的 ETF 内容主题，作用类似 Terra 的虚拟 biome。它至少可以描述：

- 一个 Minecraft biome 或 biome tag fallback。
- 顶面、底面、洞底、洞顶和虚空边缘的 palette 规则。
- 已注册 configured/placed feature 引用。
- 地形、气候、深度和 surface kind 条件。
- 展示名称、调试颜色和依赖声明。

多个 Content Profile 可以复用同一个 Minecraft biome，但仍拥有不同 palette 和 feature。这是兼容 ReimagEND 类虚拟 biome 的关键边界。

### 3.3 Adapter Layer

Adapter 用于普通数据包无法表达的兼容：

- 目标模组的 Java API、动态注册表或专用结构系统。
- 外部 pack 格式的受限转换。
- BetterEnd、Nullscape、ReimagEND 等版本化兼容。

Adapter 必须是可选模块或独立兼容包。`common` 不能因此获得 NeoForge、Fabric、Terra 或目标模组的编译依赖。

## 4. 资源布局

建议资源路径：

```text
data/<namespace>/endterraforged/content_packs/<path>.json
data/<namespace>/endterraforged/content_profiles/<path>.json
data/<namespace>/tags/worldgen/biome/<path>.json
```

pack id 与 profile id 均使用资源位置。内置 fallback 固定为：

```text
endterraforged:vanilla
```

pack 负责选择和 fallback 关系；profile 负责 biome、palette 和 feature 内容。二者不得循环引用。

## 5. 选择上下文

`ContentSelectionContext` 必须由 runtime 提供稳定、可测试且尽量无分配的字段：

| 字段 | 语义 |
| --- | --- |
| `x/y/z` | quart 或 block 坐标；实现时必须明确单位，禁止混用 |
| `macro_zone` | 主岛外宏观大陆区、海峡、群岛等稳定区域 |
| `temperature/moisture` | ETF 气候标量 |
| `surface_depth` | 相对当地 top/underside/cave surface 的有符号深度 |
| `surface_kind` | 当前采样对应的表面类型 |
| `terrain_tags` | shelf edge、mountain、rift、chamber 等公开稳定标签 |
| `seed_cell` | 用于确定性权重选择的稳定空间单元 |

首批公开 `surface_kind`：

```text
TOP_SURFACE
UNDERSIDE
CAVE_FLOOR
CAVE_CEILING
VOID_EDGE
```

内部 preview mode、调试 mask 和尚未稳定的洞穴节点类型不得自动暴露为公共 tag。

## 6. 资源加载生命周期

1. 服务端资源重载时发现 pack 与 profile。
2. 解析 schema 并检查 id、fallback 环和依赖。
3. 解析 biome/tag、block state、feature 和其他 registry key。
4. 将 tag 展开为稳定排序列表。
5. 把条件、权重和 palette 编译为 immutable runtime。
6. 完整成功后原子发布；失败的可选包禁用并回退。
7. worldgen 热路径只读取已编译 runtime，不解析 JSON/YAML，不做磁盘 IO。

错误必须在资源加载阶段汇总，禁止每次 biome 或 density sample 重复打印。

## 7. 确定性与性能

- 相同 world seed、坐标、资源集合和 pack 选择必须返回相同结果。
- 权重选择使用稳定 hash，不使用依赖遍历顺序的共享 `RandomSource`。
- selector、palette 和 placement filter 构造后 immutable。
- 高频采样禁止临时 `Map`、装箱集合和无界按坐标缓存。
- profile/tag 候选在加载阶段展平，热路径不执行 registry 搜索。
- C2ME 验证必须比较不同区块访问顺序的固定坐标结果。

## 8. Palette 边界

Palette 只决定已有实体表面的方块材料，不改变大陆或洞穴 density。

- 规则可读取 biome/profile、climate、surface kind、surface depth 和公开 terrain tag。
- 默认方块状态必须存在；所有条件不匹配时使用 profile fallback。
- 顶面与底面必须是独立条件，不能把原版 `floor` surface rule 当成全部表面。
- palette 执行应复用 Minecraft SurfaceRules 或 ETF 编译后的等价只读规则。
- 不允许在每个方块执行通用脚本解释器。

## 9. Feature 与结构边界

v1 优先引用 Minecraft 已注册的 configured/placed feature，并通过 ETF placement filter 限定 Content Profile 和锚点。

首批 placement anchor：

- top surface。
- underside。
- cave floor。
- cave ceiling。
- void edge。

结构继续使用 Minecraft structure、biome tag 和 placement 体系。需要 TerraScript、Sponge schematic 或目标模组专用结构 API 时，交给独立 adapter；不得把脚本引擎塞入通用 schema。

## 10. Pack 选择与持久化

pack id 不进入 `EndPreset`，避免地形参数与 registry 生命周期耦合。最终持久化位置在实现前通过创建世界和已有世界生命周期验证，至少满足：

- 每个世界选择可复现。
- pack 缺失时给出可见诊断并回退。
- 切换内容包不修改 terrain preset。
- 服务端决定最终 pack；客户端只接收 UI 所需摘要。
- 需要数据包重载或重进世界时明确提示。

## 11. ReimagEND 与 Terra 兼容策略

- Terra 与 ETF 都是主生成器，不能在同一 End dimension 中同时接管 density。
- ETF v1 不实现完整 Terra pack 解释器。
- ReimagEND 的 dragon-island、dragon-pit 和中央 buffer 不进入当前适配范围。
- 可兼容的是主岛外内容语义：虚拟 biome、palette、feature、结构主题和 3D 内容分层。
- 推荐由原 pack 作者提供 ETF 版 Content Pack。
- 若适配包复制或改写 ReimagEND GPL-3.0 内容，必须独立发布并满足 GPL；ETF 核心不得直接打包这些内容。

详细依据见 [`TERRA_CONTENT_COMPATIBILITY_RESEARCH.md`](TERRA_CONTENT_COMPATIBILITY_RESEARCH.md)。

## 12. 分阶段实现

### v1A：选择器骨架

- schema、loader、diagnostics、fallback。
- Content Profile id 与 registered biome 映射。
- 3D `x/y/z`、climate、depth 和 terrain tag 条件。
- vanilla fallback 包。

### v1B：Palette

- surface kind。
- top/underside/cave palette。
- runtime 与 preview 调试叠加。

### v1C：Feature

- placed feature 引用。
- profile placement filter。
- top/underside/cave/void-edge anchor。

### v1D：兼容原型

- 一个注册表型末地模组兼容包。
- 一个 ReimagEND 类外部内容适配原型。
- 缺失依赖、版本不匹配和 fallback 验证。

## 13. 测试门禁

- 默认 vanilla pack 可独立加载。
- JSON、fallback 环和 registry 错误可定位。
- 缺失第三方 biome/feature 不崩溃、不返回空候选。
- 相同 seed 与坐标确定性一致。
- Y、surface depth 和 surface kind 会产生可观察差异。
- palette 不改变 density。
- feature placement 不依赖区块访问顺序。
- runtime 与 preview 使用相同 profile 选择 primitive。
- common 不出现 Terra、NeoForge、Fabric 或 LDLib2 直接依赖。

## 14. 冻结前待决策

1. pack 选择的最终世界持久化位置。
2. `surface_depth` 的单位和正负方向。
3. 第一批公开 terrain tag 的最小集合。
4. palette 使用 SurfaceRules 扩展还是独立编译规则。
5. placed feature gating 的生命周期与性能预算。
6. 独立兼容包的版本发现与依赖声明格式。
