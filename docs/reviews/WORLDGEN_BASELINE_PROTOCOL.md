# P1 真实 Worldgen 基线采集

> 文档状态：当前有效的手工采集清单，不是路线文档。
> 当前优先级见 [`../../PLAN.md`](../../PLAN.md)。本清单只记录可复现证据，不能以预览或单元测试代替正式世界结果。

## 目的

为 P1 固定一个无其他末地模组的 NeoForge 开发环境，分别验证：

1. Standard 世界内大陆、空洞和底部方块的实际生成。
2. ETF 是否绑定了正式 End density，而不是 fallback。
3. 新区块生成卡顿的固定坐标、MSPT 和 profiler 证据。

完成前不得将大陆缺失、岩浆或卡顿归因到 ETF 的任何单一模块。

## 固定环境

| 项目 | 取值 |
| --- | --- |
| 加载器 | 当前 NeoForge 1.21.1 开发客户端 |
| 末地模组 | 第一轮仅 ETF；不加载 BetterEnd、Nullscape、C2ME 或其他修改 End 的模组 |
| 世界规格 | Standard，`min_y=-256`、`height=512` |
| seed | `7286241398135878839` |
| 视距 / 模拟距离 | 12 / 8 |
| 游戏内存 | 6 GB |
| 采样区块 | 未生成的中央区与外围区 |

新建一个专用测试世界，不复用 `neoforge/run/saves/新的世界*` 的旧 active preset。
这些存档请求过旧高度，且曾在 `Y=1800` 采样；该高度超出 Standard 的有效范围，不能作为视觉基线。

## 采集步骤

1. 用固定 seed 新建 Standard 单人世界，记录创建时间和当前工作树版本。
2. 进入末地后，在 `latest.log` 保存 ETF bootstrap 行。它必须包含
   `densityPresent=true`、`fallbackPresent=true`，并记录实际 world bounds。
   当前中央保护要求 ETF 保留 vanilla End `final_density`；`fallbackPresent=false`
   属于中央保护完成前的旧基线，不能再作为通过条件。
   同时保存 `EndTerraForged captured loaded End noise settings` 行，确认实际
   `minY`、`height`、`seaLevel`、`aquifersEnabled`、`defaultBlock` 和
   `defaultFluid`；不得用仓库中的数据包 JSON 代替这项运行时证据。
3. 使用旁观模式，在每个位置分别截取 F3 信息、地表画面与垂直视角：
   - `/tp @s 0 192 0`
   - `/tp @s 4096 192 4096`
   - `/tp @s 4096 -224 4096`
4. 只记录实际方块，不以雾、天空盒、远距离未加载区块或预览色图判断流体。对疑似岩浆的位置，使用 F3 方块信息或命令确认方块 id。
5. 从未生成区域开始连续飞行至少 60 秒；记录开始和结束坐标、平均/峰值 MSPT、内存和 GC 现象。关闭 F3 饼图和 FPS/TPS 图表，优先使用 JFR 或已明确记录开销的 profiler；第一轮不要额外加入影响 End 生成的模组。
6. 保存 `latest.log` 的 ETF、Mixin、异常和 `Can't keep up!` 片段，以及 profiler 输出位置。

## 通过与失败记录

每次执行都回填以下字段：

- 日期、客户端/NeoForge/ETF 版本与 JVM 参数。
- 世界目录、seed、active preset 内容和实际数据包 world bounds。
- 每个坐标的截图文件、方块 id、是否已有区块。
- density/bootstrap/fallback 日志片段。
- 60 秒新区块生成的 MSPT、内存、GC 与 profiler 结果。

只有相同环境能稳定复现后，才可为大陆视觉、异常流体或卡顿建立 issue 与修复测试。第二轮再在同一基线加入 C2ME，单独比较并行生成的结果与成本。

## 2026-07-12 执行记录

- 使用固定 seed 和 Standard 新世界完成 `0,192,0`、`4096,192,4096` 与 `4096,-224,4096` 采样。
- runtime 记录 `minY=-256`、`height=512`、`aquifersEnabled=false`、`defaultBlock=end_stone`、`defaultFluid=water`。
- ETF density 成功绑定，未发生 bootstrap fallback 或 sampling exception。
- 当日的 `4096,-223,4096` 为 `minecraft:end_stone`，目标流体为 `minecraft:empty`；这只说明该坐标当时未命中空密度单元。2026-07-13 随后确认，有限大陆下方的空密度会被 vanilla disabled aquifer 的全局 `Y < -54` 岩浆规则填充；现已加入 ETF End 专用空气 picker，必须在新生成区块复测。
- 初进末地记录 `8548 ms / 170 ticks behind`；外围新区客户端曾降至约 10 FPS，稳定后恢复约 95 FPS，服务端当时约 3 ms/tick。
- F3 图表期间日志出现 `gameRenderer.level`、`gui.debug` 与 `updateDisplay` 卡帧，因此后续必须关闭这些图表并分离客户端/服务端 profile。
- 之后系统发生强制重启，但 Minecraft 已先正常停止并保存；没有 JVM crash、OOM 或显示驱动重置记录，不能把系统重启单独归因 ETF。
- 本轮证据足以进入主岛外大陆根因修复。P2 多大陆与 P3 有限大陆架的代码闭环已经完成；现在应按 [`OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md) 重新执行固定 seed 基线，采集修复后的中央区、外部 shelf、JFR/GC 和可选并行生成对照。不得把旧全覆盖/填底世界的性能数据与当前默认 shelf 模型混用。
