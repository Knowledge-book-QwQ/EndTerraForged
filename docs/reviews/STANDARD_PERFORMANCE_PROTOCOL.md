# Standard 世界性能采集协议

> 文档状态：当前有效的手工性能验收清单，不是优化完成声明。
> 最近更新：2026-07-13。
> 适用范围：阶段 B 的 Standard `OUTER_CONTINENTS + FLOATING_SHELF` 性能基线、RTF 同载开销与 C2ME 并行对照。
> 地形正确性先按 [`OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md`](OUTER_CONTINENTS_CLIENT_SMOKE_TEST.md) 验收；错误输出的快速构建不能作为优化结果。

## 1. 目标

用同一 seed、preset、坐标和 JVM 条件区分以下成本：

- 集成服务器 `Server thread` 的新区块 worldgen。
- 客户端渲染线程的 chunk mesh、上传与画面刷新。
- vanilla、Architectury、RTF、C2ME worker 的并行任务。
- 对象分配、GC pause、live heap 增长与资源重载。

一次“突然很卡”只算线索。只有线程、时间段和调用栈能稳定复现，才进入优化队列。

## 2. 固定条件

| 项目 | 固定值 |
| --- | --- |
| Minecraft / 加载器 | 1.21.1 / 当前 NeoForge |
| Java | JDK 21 |
| 世界规格 | Standard，`min_y=-256`、`height=512` |
| preset | `format_version=2`、`OUTER_CONTINENTS`、`FLOATING_SHELF` |
| seed | `7286241398135878839` |
| 游戏内存 | 6 GB |
| 视距 / 模拟距离 | 12 / 8 |
| 调试显示 | 关闭 F3 饼图、FPS/TPS 图表与额外调试 overlay |

每轮使用新世界和相同 ETF jar，记录 jar 大小、修改时间或 SHA-256。不要复用已生成区块，也不要把历史 `LEGACY_COLUMN` 世界的数据混入当前默认基线。

## 3. 测试矩阵

按顺序执行，前一组无法稳定复现时不要继续叠加变量：

1. **A：ETF only**：建立正确地形与基础成本。
2. **B：ETF + RTF**：RTF 只修改主世界，比较进入末地前后的额外成本与 Mixin 日志。
3. **C：ETF + C2ME**：比较固定坐标输出、worker 调度、吞吐和 heap；不能只检查“不崩溃”。
4. **D：ETF + RTF + C2ME**：验证两个生成器的共享 Mixin 与 C2ME DFC/worker 同时存在时仍保持维度隔离和固定坐标一致。
5. **E：目标整合包**：仅在 A-D 可解释后执行，用于发现渲染、远景、预生成、地图与资源重载造成的交互放大。

每组至少重复两次。首轮用于 JVM、类加载和 shader/resource warm-up，第二轮才作为比较样本；一次性启动峰值单独记录。
首轮归因不得直接从包含 Voxy、shader、Chunky、地图和泄漏诊断工具的完整实例开始，否则客户端 mesh、后台预生成和诊断扫描会与 worldgen 样本重叠。

## 4. JFR 采集

使用同一 JDK 21 的 `jcmd.exe`。先列出 Java 进程：

```powershell
& "$env:JAVA_HOME\bin\jcmd.exe" -l
```

对 Minecraft PID 开始 profile 录制，输出文件放在仓库外：

```powershell
$minecraftPid = 12345 # Replace with the Minecraft PID reported above.
& "$env:JAVA_HOME\bin\jcmd.exe" $minecraftPid JFR.start name=ETF-Standard settings=profile filename=C:\temp\etf-standard.jfr dumponexit=true maxsize=512m
```

完成测试阶段后落盘并停止：

```powershell
& "$env:JAVA_HOME\bin\jcmd.exe" $minecraftPid JFR.dump name=ETF-Standard filename=C:\temp\etf-standard.jfr
& "$env:JAVA_HOME\bin\jcmd.exe" $minecraftPid JFR.stop name=ETF-Standard
```

如果外部 profiler 与 JFR 同时使用会明显改变结果，分两轮采集。profile、日志、截图和 heap dump 不进入 Git 工作树。

## 5. 固定时间线

录制开始后人工记录每一步的本地时间：

1. 在主世界或菜单空闲 60 秒，建立 idle 与 GC 基线。
2. 首次进入末地，不移动 60 秒，记录中央区加载峰值。
3. 传送到 `/tp @s 8192 192 8192`，等待区块完全加载 60 秒。
4. 从未生成区域持续飞行 60 秒，保持方向和速度一致。
5. 传送到 `/tp @s -8192 192 8192`，重复等待和飞行。
6. 保存并退出世界，记录资源卸载、服务器停止和最终 GC。

坐标超出实际世界 Y 包络或已经生成时，本轮样本作废。RTF 同载组还要先在主世界生成新区块，证明 RTF 实际生效，再进入末地。

## 6. 分析与归因

至少记录：

- `Server thread` 的 wall time、CPU time、最长事件和新区块阶段热点。
- `Render thread` 的 mesh build、buffer upload、shader/resource 和画面刷新热点。
- worker 线程名称、任务队列、等待/锁和 ETF density/cave/continent 调用栈。
- allocation rate、热点类型、GC pause、GC cause、峰值 live heap。
- `Can't keep up!`、ETF bootstrap/binding、Mixin conflict、异常和资源重载日志。
- 相同固定坐标的正式方块、heightmap、大陆/海峡拓扑是否一致。

判断规则：

- 服务端 MSPT 高而客户端 FPS 正常：优先检查 worldgen、结构、density 和 worker。
- 服务端稳定而客户端 FPS 暴跌：优先检查 chunk mesh、上传、调试 overlay 和渲染模组。
- 分配率先上升后出现长暂停：先定位分配热点和 live-set，不直接增加内存掩盖问题。
- 只在 RTF/C2ME/整合包组出现：先回到上一矩阵确认交互边界，不能直接归因 ETF 核心。
- C2ME DFC 报告 `Generating DelegateNode for type`：表示该自定义 density 没有被编译为原生 AST，而是通过 delegate 保留调用。它不是语义失败，也不是性能通过；记录具体类型，并在 JFR 中比较 delegate 调用成本。
- C2ME `HookCompatibility` 报告某个事件订阅使优化失效：单独记录订阅方和被禁用能力，不得把整组性能下降直接归因 ETF density。
- Voxy/渲染 worker 的异步节点或 mesh 警告属于客户端路径；只有服务端 worker/JFR 同期出现 ETF 调用栈时，才能归入 worldgen。
- 泄漏诊断工具在世界仍打开时报告的 `ServerLevel`、chunk 或 player 引用只是候选。必须退出世界、等待清理并比较重复进出后的 live heap，才能形成泄漏结论。

## 7. 通过条件

- Standard 持续生成新区块目标低于 40 ms/tick，不得长期超过 50 ms/tick。
- 不出现可重复的多秒 `Server thread` 停顿；一次性进入或资源加载峰值必须单独解释。
- 60 秒连续飞行中 heap 不持续无界增长，世界退出后长期 runtime/cache 可释放。
- ETF only 与 C2ME 组在相同 seed、preset、坐标产生相同正式方块和拓扑。
- RTF 组的主世界由 RTF 生成、末地由 ETF 生成，且没有注入冲突或 wrapper 被静默跳过。

未满足时，先由 JFR 热点建立一个可复现 issue，再修改代码。禁止预测最高地表、缩短 `NoiseChunk`、静默关闭功能或用少生成内容换取数字。

## 8. 记录模板

```text
日期与测试组：
ETF / NeoForge / RTF / C2ME 版本：
ETF jar SHA-256：
JVM 与内存：
seed / preset / world spec：
各阶段开始与结束时间：
Server thread 中位数 / p95 / 最大停顿：
Render thread 最长停顿：
worker 热点与等待：
allocation rate / GC pause / peak live heap：
固定坐标 parity：
latest.log 关键行：
JFR、截图和日志路径：
结论：通过 / 失败 / 数据不足
下一项可复现问题：
```
