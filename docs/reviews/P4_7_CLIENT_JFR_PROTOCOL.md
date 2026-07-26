# P4.7-0B Client/JFR Protocol

> 文档状态：当前有效的手工实机基线协议，不代表 P4.7 算法已选定或接入 production。
> 适用范围：NeoForge 1.21.1、P4.6 非持久化 smoke profile、P4.7 selection 前的四组合基线。

## 1. 固定条件

| 项目 | 固定值 |
| --- | --- |
| seed | `123456789` |
| JVM property | `-Dendterraforged.p46_archipelago_smoke_test=true` |
| runtime | `OUTER_CONTINENTS + WITH_FLOOR + REGION_PLANNED + archipelago` |
| world | 每组使用独立全新世界 |
| 视距 / 模拟距离 | 12 / 8 |
| 调试显示 | 关闭 F3 饼图、FPS/TPS 图表和额外 debug overlay |
| shader / pregen | 录制期间不主动切换 shader，不启动 Chunky 等预生成任务 |

P4.7-0B 测量当前 production runtime，不启用 analytical、thermal、hydraulic 或 bounded-flow test-only
候选。候选代码进入 common jar 不等于存在 production caller。

## 2. 固定顺序

1. `ETF`
2. `ETF_C2ME`
3. `ETF_RTF`
4. `ETF_RTF_C2ME`

除 RTF/C2ME 启用状态外，实例、ETF jar、Java、内存、视距和其他模组必须保持不变。RTF 组先在主世界
确认 RTF 实际加载，再进入末地。每组至少执行一次有效录制；需要比较启动/JIT warm-up 时再补第二轮，
不能把两轮世界混在同一个 JFR 中。

## 3. 准备组合

关闭 Minecraft 后，在仓库根目录执行：

```powershell
$instance = '<minecraft-instance-directory>'
.\tools\p47-client-baseline.ps1 -Action Prepare -InstancePath $instance -Combination ETF
```

`Prepare` 会完成：

- 把当前 `neoforge/build/libs/endterraforged-0.1.7.jar` 同步到实例并校验 SHA-256；
- 只通过 `.disabled` 后缀切换 RTF/C2ME，不删除 jar；
- 输出实际组合、ETF hash 和两项模组状态。

后续三组只替换 `-Combination`。游戏未关闭时不得切换。

## 4. 启动 JFR

正常通过启动器进入主菜单后，列出 Java PID：

```powershell
.\tools\p47-client-baseline.ps1 -Action ListJava -InstancePath $instance
```

根据启动时间、Java 路径、工作集和窗口标题选择 Minecraft PID，不读取或记录启动命令行。开始录制：

```powershell
.\tools\p47-client-baseline.ps1 -Action StartJfr -InstancePath $instance -ProcessId 12345
```

JFR、manifest、日志副本和摘要写入实例的 `logs/p47-0b`，不进入 Git 工作树。

## 5. 固定时间线

1. 主菜单或主世界空闲 60 秒，建立 client/GC idle 基线。
2. 使用本组全新世界进入末地，在中央区域不移动 60 秒。
3. 执行：

   ```mcfunction
   /execute in minecraft:the_end run tp @s 8192 120 8192
   ```

4. 等待新区块稳定 60 秒，记录首次传送的 MSPT、FPS、heap 和最长卡顿。
5. 沿固定方向持续飞行 60 秒；不要在不同组改变速度、视距或 shader。
6. 检查山地、海洋、群岛有限体积、海床、底部岩浆、直壁和未加载区块边界。
7. 传送到负坐标新区：

   ```mcfunction
   /execute in minecraft:the_end run tp @s -8192 120 8192
   ```

8. 再等待 60 秒并飞行 60 秒。
9. 保存并退出世界，回到主菜单后停止 JFR。

停止并收集：

```powershell
.\tools\p47-client-baseline.ps1 -Action StopJfr -InstancePath $instance
```

## 6. 每轮必查

日志状态必须包含：

- `topology=OUTER_CONTINENTS`
- `seaMode=WITH_FLOOR`
- `terrainLayout=REGION_PLANNED`
- `archipelago=true`

摘要额外记录以下出现次数：

- `InjectionError`
- `Redirect conflict`
- `OutOfMemoryError`
- `Can't keep up!`
- `DelegateNode`

`DelegateNode` 只说明 C2ME 使用 delegate，不代表失败或性能通过。客户端 FPS 峰值必须和 `Server thread`
worldgen、render/mesh、buffer upload、GC 和 worker allocation 分开归因。

## 7. 有效样本条件

- 使用当前精确 ETF jar，manifest hash 与准备阶段一致。
- 使用全新世界和未生成坐标；复用旧区块则该段样本作废。
- 无 crash、Mixin 注入失败、无限加载或明显错误地形。
- JFR 覆盖完整固定时间线，`latest.log` 副本与同一组合对应。
- RTF/C2ME 实际状态与 manifest 一致。

出现崩溃时先保留 crash report、JFR 和 `latest.log`，分析根因；不得通过删除测试或静默关闭 ETF 功能
制造“通过”。

## 8. 结果模板

```text
日期 / 组合：
ETF SHA-256：
NeoForge / RTF / C2ME 版本：
Java / heap / 视距 / 模拟距离：
世界名 / seed：
中央进入峰值：
8192 传送峰值：
负坐标传送峰值：
Server thread p50 / p95 / max：
Render thread max / mesh-upload hotspot：
allocation / GC pause / peak live heap：
C2ME delegate / worker 观察：
山地 / 海洋 / 群岛 / 海床 / 岩浆 / 直壁：
日志 marker 与错误计数：
JFR / log / screenshot 路径：
结论：通过 / 失败 / 数据不足
```
