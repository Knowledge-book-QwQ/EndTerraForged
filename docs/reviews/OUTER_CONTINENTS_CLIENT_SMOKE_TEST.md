# 主岛外多大陆与大陆架客户端冒烟清单

> 文档状态：当前有效的手工验收清单。
> 最近更新：2026-07-17。
> 适用范围：P2 `OUTER_CONTINENTS` 的中央保护、P3 `FLOATING_SHELF` 有限体积、R2 分带与 Standard 性能验证。
> 不替代自动测试；结果应回写到 `PLAN.md` 的 P2 项或对应 issue。

## 0. P4.0 当前测试包

2026-07-17 自动门禁通过的 NeoForge 包：

```text
E:\项目仓库\EndTerraForged\neoforge\build\libs\endterraforged-0.1.7.jar
SHA-256: 5438711B558C7173412DF2A54D2EC26C04EF9FCA4660C833741A0896B901FCE1
```

开始本轮前必须确认测试实例中的 ETF jar 已替换成上述校验值。不要同时保留两个 ETF jar。
当前实例还包含：

- `reterraforged-0.0.6005R10.0-preview1-neoforge-1.21.1.jar`
- `c2me-neoforge-mc1.21.1-0.4.0-alpha.0.113.jar`

P4.0 最短顺序：

1. 校验 ETF jar SHA-256。
2. 新建固定 seed 世界，在 RTF 主世界生成新区块。
3. 进入末地验证中央原版区域和 `fallbackPresent=true`。
4. 到保护区外检查有限大陆架、直壁、一格平板和 `Y < -54` 岩浆。
5. 检查日志中的 `Redirect conflict`、`InjectionError` 与 C2ME delegate。
6. 记录一次首次传送卡顿和加载稳定后的性能，不能只报告平均 FPS。

### 0.1 当前静态兼容证据

以下结论已经对本清单列出的实际 jar 做过字节码审查，但不等于客户端通过：

- 最新 ETF jar 的 `endterraforged.mixin.MixinNoiseChunk` 使用
  `@Mixin(value=NoiseChunk.class, priority=1100)` 和 `@ModifyArg(index=0)` 修改
  `NoiseRouter.mapAll(Visitor)` 的 visitor 参数，不再使用 `@Redirect`。
- 当前 RTF R10 preview1 jar 的 `raccoonman.reterraforged.mixin.MixinNoiseChunk` 仍对同一个
  `NoiseRouter.mapAll(Visitor)` 调用使用 `@Redirect`，其 redirect 内部最终继续调用原始
  `noiseRouter.mapAll(visitor)`。
- 预期组合顺序是 ETF 先把原 visitor 替换为“ETF binding -> 原 visitor”，RTF 随后接管
  外层调用并把已经变换后的 visitor 交给原始 `mapAll`。该结构理论上同时保留 ETF
  placeholder binding、RTF 主世界 wrapper 和 vanilla per-chunk interpolation/cache。
- 当前 C2ME DFC `0.4.0-alpha.0.113` 对未知 density 类型使用 `DelegateNode`，并通过原始
  `DensityFunction.compute` / `fillArray` 执行。ETF 的 bound density 预计不会被丢弃，
  但可能失去部分 AST 优化，必须记录 `Generating DelegateNode for type` 并用 JFR 判断
  实际成本。
- 旧 `latest.log` 使用的是 SHA-256 为 `563EC...ECA7` 的旧 ETF jar，不能作为上述组合
  已经实机通过的证据。该日志也没有形成完整的主世界、末地 fallback 和外部大陆回归记录。

只有换成 SHA-256 为 `543871...FCE1` 的验证包，并完成主世界与末地新区块生成后，才能将
RTF/C2ME 同载项标记为通过。静态字节码、单元测试和编译通过都不能替代这一步。

### 0.2 创建世界高度规格

1. 在创建世界界面打开 ETF 设置，确认“最低可建造 Y”和“最高可建造 Y”是可调控件；已有世界暂停菜单中的同两项必须是只读。
2. 先使用 Standard：最低 `-256`、最高 `255`。创建后在末地确认实际建筑范围一致。
3. 再新建一个测试世界，将最低设为 `-512`、最高设为 `1023`。界面应显示黄色性能警告；创建后末地实际范围必须精确为 `-512..1023`。
4. 最高值只能落在 Minecraft 支持的 `...15` 刻度，例如 `1999` 或 `2015`，不能显示 `2000` 后再静默取整。
5. 在 RTF 主世界生成新区块，确认 ETF 只替换 End stem，没有改变主世界与下界生成器。

通过条件：创建世界显示的最低/最高 Y 与末地实际建筑边界一致，重进世界后不丢失；已有世界不能修改边界，其他维度不受影响。

## 1. 固定环境

- NeoForge 开发客户端，Minecraft 1.21.1。
- 使用新建单人世界或明确未生成的外部新区块，Standard 规格：`min_y=-256`、`height=512`。旧区块不会因替换 jar 自动重塑，不能用它判断大陆架直壁是否已修复。
- 使用为本清单建立的新 R2 测试 preset：`format_version=3`、`OUTER_CONTINENTS`、
  `RTF_MULTI`、`FLOATING_SHELF`，并显式启用 `continent.bands`。默认算法仍可能是
  `LEGACY_RADIAL`，不能用它替代 R2 分带验收。当前大陆子编辑器可直接选择
  `RTF_MULTI`、开启大陆分带并调整五个阈值。
- 固定 seed：`7286241398135878839`。
- 首轮关闭其他末地 generator/content 模组；性能基线使用 6 GB 游戏内存、视距 12、模拟距离 8，关闭 F3 饼图和 FPS/TPS 图表。

不要用已有旧世界、旧 compact preset 或越出实际 world bounds 的 Y 坐标作为本轮结论。

## 2. 中央区域

1. 正常进入末地，不先执行远距离传送。
2. 检查原版主岛地表、黑曜石柱、末影龙战、返回门和折跃门。
3. 在中央保护区内移动并重登一次，确认没有 ETF 大陆、浮岛覆盖或固定半径实体墙。
4. 在 `latest.log` 中确认同时存在 `EndTerraForged captured loaded End noise settings` 与
   `EndTerraForged binding End density`，后者必须包含 `fallbackPresent=true`；将这两行与截图一同记录。
5. 确认日志中没有以下拒绝前缀：`EndTerraForged refused direct End RandomState creation`、
   `EndTerraForged refused End RandomState creation`、`EndTerraForged refused End chunk generation`。
   任一前缀都表示 ETF 为保护原版中央区主动停止生成，不是可接受的降级结果；保留完整堆栈并停止本轮 P2 验收。

通过条件：中央区域的视觉和玩法流程保持原版预期，且日志证明 vanilla fallback 已绑定。失败时停止后续 P2 宣称，记录坐标、seed、preset、日志和截图。

## 3. 外部大陆

1. 从中央区向至少两个不同方向传送到保护带外，例如 X 或 Z 绝对值大于 `2048` 的坐标。
2. 每个方向在多个相邻区块观察：应出现独立宏观大陆、可飞越的虚空海峡与空白区域，而不是连续全覆盖实体。
3. 观察大陆边缘、顶部和底部。`FLOATING_SHELF` 必须只在 top 与 underside 之间保留实体，底部应回到末地虚空；landness 接近零的最外缘可因不足一格厚而自然消失，但不得出现数十格高的固定厚度直墙、填至世界底部或中央保护边界被实体填满。
4. 从海峡向内陆连续飞行，检查 `void_outer -> shelf -> rim -> coast -> inland` 的结构变化：大陆边缘应较低、较薄且有破碎过渡，内陆才逐步获得完整的山地和高原 relief。火山不属于当前客户端验收。不得出现大面积一格厚平板、完全平顶大陆或分带边界上的硬切线。
5. 本节使用“无海（浮空）”。在一个大陆下方和一个虚空海峡分别检查 `Y=-64` 与 `Y=-224`：两处都必须是空气/末地虚空，不得出现原版全局 fluid picker 造成的整层岩浆。
6. 保存至少一张加载稳定后的截图；若出现直壁、突变或断裂，记录坐标、朝向、渲染距离和是否在区块完全加载后仍存在。特别记录是否与 `16` 格 chunk 边界或 `512` 格 region 边界重合。
7. 使用 seed `123` 和截图对应 preset，在 `(11069, 148, -2573)` 周边至少观察 `384 x 384` 格未生成区域。旧实现曾在约 `(11056, -2534)` 的相邻列产生约 `153.48` 格高度跳变；新包不得再出现一格宽的巨大连接墙。自动测试仅证明数学连续性上限，侧面轮廓是否自然仍以本项截图为准。

通过条件：中央区外可稳定找到多个由虚空分隔的陆块，底部是有限大陆架而非贯穿世界底部的实体柱，且切换方向后拓扑仍然确定。`CONTINENTAL` 的历史全覆盖行为不应被当成默认 `OUTER_CONTINENTS` 的验收结果。

### 3.1 海洋模式

使用同一 seed 分别新建“有底海洋（下方实体）”与“无底海洋（下方虚空）”世界，不得用第 3 节的无海旧区块判断。

1. 在中央保护区外寻找 `landness == 0` 的大陆间海峡，确认海平面以下出现水且没有 `Y < -54` 整层岩浆。
2. “有底海洋”向下检查到海床：海床应连续、具有缓慢起伏，海床上方为水；不得退化成贴着水面的薄片或固定水平截面。
3. “无底海洋”在相同类型海峡继续向下检查：应有水但没有实体海床，直到实际世界下界。
4. 飞到有限大陆架下方：underside 以下与外海连通的空间必须有水，不能再出现大陆投影整列断水形成的干燥巨型空腔。
5. 进入大陆架实体体积内部的洞穴；有底模式再检查海床以下的洞穴。两者都不能因为低于海平面而被外海选择器统一灌水。
6. 返回中央原版保护区，确认 ETF 海水没有覆盖主岛、黑曜石柱或中央虚空流程。

通过条件：两种海洋都能在外海产生水，只有有底模式生成海床；洞穴、中央保护区和非 ETF 维度保持原有流体语义。

## 4. ReTerraForged 同载边界

本节使用第二个新世界执行。RTF 只负责主世界，ETF 只负责末地；不得把两者配置为同时接管同一维度。

1. 在创建世界界面确认 RTF/原版使用的 `Customize` 入口仍可操作，同时存在独立的
   `EndTerraForged Preset...` 入口；二者不得重叠、互相覆盖或打开错误编辑器。
2. 先进入主世界并生成若干新区块，确认 RTF 地形实际生效。
3. 检查日志中不存在 `Redirect conflict`、`InjectionError`、RTF `0/1` 注入失败，
   也不存在旧 ETF handler `endTerraForged$wrapMapAll`。
4. 再进入末地，重复中央区与至少一个保护带外坐标的检查；日志必须出现 ETF
   `binding End density` 且包含 `fallbackPresent=true`。
5. 记录 ETF、RTF、NeoForge 的精确版本、两个入口截图、主世界与末地截图及日志片段。

通过条件：RTF 主世界和 ETF 末地均产生各自地形，两个编辑入口职责分离，且共享
`NoiseChunk` 调用链没有注入冲突或被任一方静默跳过。

## 5. 性能分离

1. 分别记录进入末地、首次远距离传送、区块加载稳定后三个时间点。
2. 同时观察服务端 tick、客户端 FPS、GC 和 `latest.log`；不要只以总 CPU 占用判断 worldgen 性能。
3. 若卡顿发生，先判断是服务端新区块生成、客户端 mesh/render、F3 调试图表还是资源重载。
4. 连续复现两次后再归类为 ETF 回归；一次性系统卡死、驱动或操作系统异常不得直接归因 ETF。

通过条件：不存在持续多秒的服务端新区块生成停顿。短暂客户端渲染峰值必须与服务端 worldgen 峰值分开记录，供 P3 性能收束使用。

## 6. 并行生成对照

仅当实际整合环境能够合法加载 C2ME 或其他并行生成实现时执行本节。不要为了测试而替换 NeoForge 主体验或改变 ETF 的世界生成接管边界。

1. 在与基线相同的 Minecraft、ETF、seed、preset、内存、视距和模拟距离下建立第二个新世界。
2. 记录并行生成实现的名称、版本、加载器/兼容层和启动日志中的 Mixin 或 delegate 诊断。
3. 重复中央区、至少两个保护带外方向与 60 秒未生成区飞行；比较固定坐标的方块 id、陆块/海峡拓扑、服务端 tick、客户端 FPS、GC 和 heap。
4. 出现差异时先禁用并行生成实现复测；只有同一环境连续两次出现的差异才记录为 ETF/C2ME 兼容问题。

通过条件：并行环境与基线在固定 seed/preset/坐标的正式方块和拓扑上保持一致；性能数据单独记录，不能用“不崩溃”代替确定性验证。

## 7. 记录模板

```text
日期：
ETF commit / 工作树状态：
NeoForge 与其他模组：
RTF 版本与主世界结果（如有）：
创建世界两个编辑入口：
世界 seed：
preset 来源与 format_version：
continent algorithm / bands：
中央区结果：
外部坐标与方向：
大陆 / 海峡结果：
直壁或截断：
进入、传送、稳定后三段性能（服务端 tick / 客户端 FPS / GC / heap）：
并行生成实现与版本（如有）：
latest.log 关键行：
fallback 绑定诊断 / 拒绝前缀：
Redirect conflict / InjectionError 检查：
截图路径：
结论：通过 / 失败 / 阻塞
```
