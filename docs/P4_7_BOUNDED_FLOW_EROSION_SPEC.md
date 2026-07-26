# P4.7 Bounded Flow Erosion 技术规格

> 文档状态：当前有效；test-only 候选实现规格，不代表正式算法已选定或接入 production density。
> 最近更新：2026-07-26。
> 算法定位：bounded Priority-Flood + adaptive flow + stream-power dry incision。

## 1. 目标与边界

本候选用于比较“有方向的排水切削”是否能以可接受的 CPU、heap 和分块连续性成本改善
`REGION_PLANNED` 地表。它消费 canonical primitive input tile，不改变大陆、AREA ownership、RIDGE
identity、archipelago identity 或 finite volume authority。

本切片明确不实现：

- authoritative receiver/reach graph；
- 无限上游 catchment 或跨 domain discharge；
- river bed/water profile、lake、pool、step、cascade 或真实水体；
- production cache、single-flight、private executor、preset、Codec、Builder、preview 或 UI。

完整 depression hierarchy、terminal model、receiver DAG、physical-area accumulation 和共享 profile 仍属于
`format_version=5` 的 P5，不得从本候选输出反推长期 hydrology API。

## 2. 固定流水线

```text
immutable source top + ownership/thickness/protection masks
-> bounded minimax Priority-Flood query
-> local routing top + escape direction
-> adaptive D8/top-two flow routing
-> fixed-step bounded accumulation
-> stream-power dry incision
-> immutable diagnostic artifact
```

Priority-Flood 只解决局部 depression/spill 诊断，不直接产生侵蚀外观。stream-power 只消费本候选的
bounded accumulation 和 slope，不发布正式河网或水位。

## 3. 固定常量与 halo

- sample distance：沿用 key，当前 fixture 为 4 blocks。
- local Priority-Flood radius：3 samples。
- flow propagation：12 synchronous steps。
- routing stencil：8 邻域，正交距离 1、对角距离 `sqrt(2)`。
- required halo：`priorityRadius + flowSteps + routingRadius = 16` samples。
- 128/256 block core：继续使用 64 x 64 与 96 x 96 sample tile。
- maximum incision：10 blocks，同时受 `availableThickness * 0.20` 限制。
- activation：中央保护、void、无 AREA ownership、低 landness、薄 shelf、保护 mask 和
  archipelago-dominant 单元严格为零。

常量只属于 test-only runtime，不进入 v3/v4 preset。任何未来玩家参数必须重新完成完整配置闭环。

## 4. Bounded Priority-Flood

只有没有自然下降邻居、且邻域并非逐位 flat 的 sink candidate 运行半径 3 的 minimax flood；已有下降
方向的 sample 直接保留 source top，flat sample 保持 local terminal。flood 从中心出发，以“路径上最高
source top”作为 priority，找到局部窗口边界的最低 barrier。输出：

- `routingTopBlocks = max(sourceTopBlocks, localSpillBlocks)`；
- `spillDepthBlocks = routingTopBlocks - sourceTopBlocks`；
- 到最低 barrier path 的第一个 8 邻域方向。

实现使用 worker-owned fixed primitive heap，不创建 `PriorityQueue`、node 对象或每格集合。equal barrier
使用 world seed 与全局 sample X/Z 的稳定 tie-break；不得使用 tile-local index、请求顺序或 worker id。

该查询只能证明固定半径内的 depression escape，不知道无限上游或大陆级 terminal。窗口不足不是 lake
证据，也不能发布为 P5 depression hierarchy。

## 5. Adaptive Flow

自然下降优先使用 `routingTopBlocks` 的 8 邻域 slope：

- 只有一个有效下降方向或最强方向明显占优时使用单 receiver；
- gentle split 中第二方向达到固定比例时，按 slope 比例分配给 top two receiver；
- 没有自然下降但 `spillDepth > 0` 时，可使用 Priority-Flood escape direction；相等 routing top 只允许
  流向更低的稳定 world-space tie rank，防止 cycle；
- flat 且无 spill 的 sample 是 local terminal，不通过 tie-break 人造排水。

flow 使用固定 12 次同步传播。每个 source sample 初始贡献一个物理 sample area；本候选 accumulation
表示最多 12 条边内的 bounded contributing area，不是完整 catchment。inactive receiver 作为导出 terminal，
不继续传播。

## 6. Stream-Power Incision

dry incision 消费：

- bounded accumulation；
- source/routing slope；
- roughness；
- activation；
- `1 - erosionResistance`；
- available thickness。

切削只允许降低 top，不沉积材料。最大切削为
`min(10 blocks, availableThickness * 0.20) * activation * (1 - resistance)`。flat、无 routing、无
upstream contribution 或 slope 为零时保持逐位不变。该输出是视觉候选，不是河床 profile。

## 7. Primitive 数据布局

runtime immutable、线程安全。scratch worker-owned、非线程安全，至少包含：

- routing top、spill depth；
- dominant/secondary/escape direction；
- dominant weight；
- current/next/bounded accumulated flux；
- 半径 3 flood query 的 fixed heap、best barrier、parent 与 heap-position primitive arrays。

output artifact immutable，固定发布：

- `finalTopBlocks float[]`；
- `deltaBlocks float[]`；
- `erosionStrength float[]`；
- `drainagePotential float[]`；
- `activation float[]`；
- `dominantDirection byte[]`，仅作非权威诊断。

builder 的 peak primitive bytes 必须精确包含 input artifact、scratch、output artifact；runtime resident
offset/distance 表单独报告。

## 8. 确定性与分块契约

- source、Priority-Flood tie-break、routing 和 cache key 全部使用全局 sample 坐标。
- 所有 flow pass 同步执行，禁止原地传播造成扫描方向依赖。
- core 输出最大依赖半径不得超过 16 samples。
- 一个 256 core 与四个 128 core 的重叠世界坐标必须对 final top、delta、strength、drainage、activation
  和 dominant direction 逐位一致，同时覆盖负坐标。
- 1/2/4/6 worker、请求重排、cache eviction、owner swap 和 duplicate build 不能改变 checksum。

若该契约无法通过，候选直接标记为“不适合 chunk/tile production”，不得扩大无界 halo 或用访问顺序
缓存掩盖问题。

## 9. 测试与性能证据

固定覆盖：flat、plane、long plane、spike、ridge、plateau、closed basin、watershed、coast/thin shelf、
archipelago。至少证明：

1. flat 逐位不变且 drainage 为零；
2. plane/watershed 有方向 flow 与有限 incision；
3. closed basin 产生 spill 诊断但不冒充 lake；
4. ridge/plateau resistance 有效；
5. coast/thin shelf/archipelago 严格零影响；
6. receiver direction 无 cycle，输出 finite，切削不超过预算；
7. 128/256、正负坐标和 worker 数逐位一致；
8. cold/warm p50/p95、allocation、artifact/scratch/build peak、heap pushes、flow transfers、terminal exports、
   incision cells、16-slot resident 与 6-worker 估算均被记录。

性能数据只作为候选证据，不设置跨硬件时间阈值。P4.7-0B 客户端/JFR 仍是 selection 和 production
integration 的硬阻塞。

## 10. 当前实现结果

2026-07-26 test-only 实现已完成：

- fixed watershed checksum：`5601421594159001151L`；
- flat、plane、long plane、spike、ridge、plateau、closed basin、watershed、coast/thin shelf、
  archipelago 与 periodic `FLOW_FIELD` 门禁通过；
- dominant receiver 无环；一个 256 core 与四个 128 core 在正负世界坐标的六通道逐位一致；
- generic cache 的 owner swap、eviction、request rotation、1/2/4/6 worker checksum 与 duplicate builds 通过。

hydraulic 与 bounded-flow benchmark 已统一使用 `FLOW_FIELD`。多次 focused JDK 21 观测范围：

| Candidate | Core | Cold p50/p95 range | Output | Scratch | Build peak |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hydraulic | 128 | `3.570-4.242 / 4.156-5.023 ms` | `81,920 B` | `65,536 B` | `315,392 B` |
| Hydraulic | 256 | `4.685-5.234 / 5.152-5.921 ms` | `184,320 B` | `147,456 B` | `709,632 B` |
| Bounded flow | 128 | `3.950-4.684 / 5.341-5.717 ms` | `86,016 B` | `111,768 B` | `365,720 B` |
| Bounded flow | 256 | `2.286-2.810 / 2.835-3.362 ms` | `193,536 B` | `250,008 B` | `821,400 B` |

bounded-flow 128 core 的 16-slot resident 为 `1,376,256 B`，6-worker 估算 `8,928,792 B`，cold allocation
为 `254,552 bytes/build`，warm hit 为 `0 bytes`。同一 workload 记录 64 priority queries、2,798/1,334
heap push/pop、2,952 routed cells、2,833 split cells、15,959 flow transfers、2,190 incision cells、
`12,284` terminal-export area、`929.375` blocks total cut 和 `30.591` blocks maximum local spill。

hydraulic 128 core 的 cold allocation 为 `250,432 bytes/build`，warm hit 为 `0 bytes`；同一
`FLOW_FIELD` workload 的 `eroded/deposited/exported` 为
`12,889.447 / 8,647.161 / 4,242.287`，在测试浮点容差内满足质量守恒。

这些数字是 synthetic test-only 观测，运行顺序和 JIT 会造成抖动；不能据此宣告算法胜出，也不能替代
Minecraft、C2ME、客户端视觉或 JFR。
