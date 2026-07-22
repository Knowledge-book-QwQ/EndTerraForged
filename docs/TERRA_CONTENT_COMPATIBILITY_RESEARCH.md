# Terra / ReimagEND 内容兼容调研

> 文档状态：当前有效的调研与架构依据，不表示兼容功能已经实现。
> 调研日期：2026-07-12。
> 权威范围：Terra 与 ReimagEND 的能力、许可、平台限制和 ETF 兼容策略。

## 1. 调研对象

- [PolyhedralDev/Terra](https://github.com/PolyhedralDev/Terra)：MIT，通用体素世界生成平台。
- [PolyhedralDev/ReimagEND](https://github.com/PolyhedralDev/ReimagEND)：GPL-3.0，基于 Terra 的末地配置包。
- [astrsh/Aeropelago](https://github.com/astrsh/Aeropelago)：GPL-3.0，ReimagEND 声明引用的浮岛配置资源之一。

本轮只读取公开仓库、README、pack metadata 和代表性配置，没有复制源码、配置、结构或资源到 ETF。

## 2. 已确认事实

### Terra

- 使用 MIT 许可。
- 官方平台为 Fabric 和 Bukkit/Paper；没有官方 NeoForge 实现。
- 通过 addon 提供 noise 3D、biome pipeline、extrusion、palette、feature、structure loader 和表达式等能力。
- Terra pack 是完整 generator 配置，不是普通 Minecraft biome 数据包。

### ReimagEND

- 使用 GPL-3.0，当前 README 明确标记为 WIP，并警告升级可能产生 chunk border。
- `pack.yml` 使用 `generator: NOISE_3D`，依赖大量 Terra addon。
- biome 是 Terra 虚拟 biome，可映射到相同的 vanilla biome，但拥有不同 terrain、palette 和 feature。
- biome distribution 包含 pipeline、3D extrusion、void biome、aether pocket 和环状分布。
- terrain 配置使用表达式、3D sampler、taper、undercarriage 和 threshold。
- 自带 dragon island、dragon pit、void buffer、gateway 和 end city 逻辑。
- README 已列出龙重生和 gateway 的已知问题。

## 3. 为什么不能直接叠加

ETF 与 Terra 都需要成为 End 的主 chunk generator，并控制 density、biome 与生成阶段。一个维度不能安全地同时使用两个互不知情的主生成器。

在 ETF 的 NeoForge-first 路线下，直接依赖 Terra 还存在平台缺口。即使未来存在 NeoForge Terra，实现任意 Terra pack 的完整兼容也意味着引入另一套 generator 生命周期、脚本和配置系统，破坏 ETF 的确定性、性能和维护边界。

因此下列方案不采用：

- 在 ETF density 后再运行 Terra density。
- 在 common 中嵌入 Terra 平台实现。
- 自研完整 Terra YAML、表达式和 TerraScript 解释器。
- 把 ReimagEND pack 文件直接复制进 ETF jar。

## 4. 截图效果的技术拆分

ReimagEND 类画面通常由多层共同产生：

1. 3D terrain 与 extrusion：上下多层陆块、穹顶、悬空体积。
2. 虚拟 biome：同一 vanilla biome 下使用不同 palette 和 feature。
3. 大型 feature/structure：树群、悬挂物、发光结构和建筑。
4. 资源包与光影：体积雾、Bloom、天空、曝光和发光材质。

ETF 可以提供前三层的生成接口，但不能把光影效果误报为 worldgen。整合包可把推荐 shader/resource pack 作为独立客户端内容管理。

## 5. 推荐兼容模型

### 核心层

ETF 保持 dimension/noise/density、大陆体积、洞穴和稳定地貌信号的唯一所有权。

### Content Pack 层

使用 ETF Content Profile 表达 Terra 虚拟 biome 的内容语义：

- 一个 profile 引用一个已注册 Minecraft biome 或 tag。
- 多个 profile 可以复用同一 biome holder。
- profile 独立决定 palette、feature 和 surface kind 条件。
- 3D context 支持 Y、当地深度、top/underside、cave floor/ceiling 和 void edge。

### Adapter 层

复杂目标使用独立兼容包：

- 版本化声明目标 pack/mod。
- 只接入明确支持的内容子集。
- 缺失依赖时禁用并回退。
- 不影响 ETF core 的许可证和平台边界。

## 6. ReimagEND 适配范围

允许的首个原型范围：

- 主岛外虚拟 biome/profile 映射。
- 主岛外 palette 主题。
- 已能映射到 Minecraft placed feature 或模板结构的内容。
- top、underside、cave 和 void-edge 放置锚点。

明确排除：

- dragon island、dragon pit 和中央 void buffer。
- Terra `NOISE_3D` 主 density。
- 任意表达式、TerraScript 或 Sponge loader 的通用执行。
- 未经许可复制 GPL 结构、配置和资源。

## 7. 许可与发布

- Terra 的 MIT 代码若直接改写，保留版权和许可说明。
- ReimagEND/Aeropelago 的 GPL 配置或资源不得进入 ETF LGPL 核心。
- 只做互操作映射的 adapter 仍需在发布前进行许可复核。
- 若复制或派生 GPL 内容，兼容包必须独立发布并满足 GPL 源码与许可义务。
- 最优路线是与 pack 作者合作，由作者维护 ETF 版 Content Pack。

## 8. 性能与兼容要求

- 资源重载时解析外部映射，worldgen 热路径不解析 YAML 或执行脚本。
- profile selector 构造后 immutable，适配 C2ME 并行访问。
- 大型 feature 必须有每区块预算、稀有度和边界测试。
- shader/resource pack 不得成为服务端 worldgen 的硬依赖。
- Terra 或目标包缺失时 ETF 仍能使用 vanilla fallback 加载世界。

## 9. 结论

ETF 应兼容“ReimagEND 这类内容模型”，而不是试图同时运行两个 generator。短期先完成主岛外大陆体积和 Content Pack 基础；随后制作主岛外、受限、独立发布的兼容原型。
