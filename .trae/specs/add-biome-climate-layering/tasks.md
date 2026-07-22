# Tasks

> 文档状态：已完成的历史任务列表，不作为当前待办。
> 当前执行队列见 [`../../../PLAN.md`](../../../PLAN.md)。
> 落地顺序：每步独立可编译、可测、可回滚（DEVELOPMENT.md §5「小步快走」）。步骤 1（BiomeVariant 编译 bug 修复）已在本轮 b46e371 提交。

- [x] Task 1: 修复 BiomeVariant.java 编译 bug (commit b46e371)
  - `RecordCodecBuilder.create` → `RecordCodecBuilder.<BiomeVariant>create`（显式类型见证，打破 `.validate()` 链的 JDK 21 推断循环）
  - javac --release 21 + DFU 8.0.16 验证编译过

- [x] Task 2: BiomeSlot + BiomeClimateConfig 数据层 + 单测 (commit 588711f)
  - 新建 `BiomeSlot` record（`Holder<Biome> base` nullable + `List<BiomeVariant> variants`），compact constructor `List.copyOf` 防御性拷贝，`hasVariants()`，`EMPTY`，DFU `Codec`（base 用 `Biome.CODEC.optionalFieldOf("base", null)`，variants 用 `BiomeVariant.CODEC.listOf().optionalFieldOf("variants", List.of())`）
  - 新建 `BiomeClimateConfig` record（5 个 `BiomeSlot`），`EMPTY`，`hasAnyVariants()`（OR 5 槽），`resolve(5 holders)`，DFU `Codec`（5 字段 `optionalFieldOf(..., BiomeSlot.EMPTY)`）
  - 新建 `BiomeSlotTest` + `BiomeClimateConfigTest`（`@BeforeAll` bootstrap MC；`Holder.direct(null)` stub；测 hasVariants/hasAnyVariants/resolve 填 null/保留非 null/EMPTY 解析；`assertSame` 引用比较）
  - 编译 + 跑 :common:test 确认绿 + 提交 `feat(stage5.2.1): BiomeSlot + BiomeClimateConfig data layer`

- [x] Task 3: EndBiomeSelector 纯逻辑选择器 + 单测 (commit 75f2ee0)
  - 新建 `EndBiomeSelector`，`select(cellX, cellZ, fracX, fracZ, climate, seed, ringSlot)` 静态方法。fast-path 1：`climate == null || !ringSlot.hasVariants()` → base。fast-path 2：4 角候选 biome 全 `==` 相同 → c00。慢路径：4 角双线性权重 + 手动展开 4-corner 聚合用 `==` 比较 + 角序 00>10>01>11 tie-break
  - 新建 `EndBiomeSelectorTest`（`@BeforeAll` bootstrap；test-only `PointNoise` record；`Holder.direct(null)` stub；测 climate null/无变体/4 角一致变体/4 角一致 base/单角主导/聚合胜过单角最大/平局角序/确定性，13 测试）
  - 编译 + 测试 + 提交 `feat(stage5.2.1): EndBiomeSelector four-corner bilinear blend`

- [x] Task 4: EndClimateAccess + MixinRandomState 注入 + 单测 (commit e4f517b)
  - 新建 `EndClimateAccess`：`private static volatile EndClimate current` + `set/get/clear`。Javadoc 说明 volatile 发布不可变 record 的线程安全语义、clear 仅供测试
  - 修改 `MixinRandomState.endTerraForged$initCapture`：在 `isEnd` 分支构造 `EndClimate climate = EndClimate.defaults(noiseSeed)` 之后加 `EndClimateAccess.set(climate);`（1 行）
  - 新建 `EndClimateAccessTest`（set/get 契约 + clear 后 null + set 覆盖旧值 + set(null) 等价 clear，5 个测试）
  - 编译 + 测试 + 提交 `feat(stage5.2.1): EndClimateAccess volatile holder + MixinRandomState wiring`

- [x] Task 5: EndBiomeSource 接入 selector + 单测 (commit eaf3914)
  - 修改 `EndBiomeSource`：CODEC 加 `biome_climate` 字段；加字段 `biomeClimate`/`fracNoiseX`/`fracNoiseZ`；构造函数加 `BiomeClimateConfig` 参数 + `resolve()` + fracNoise 初始化；`collectPossibleBiomes` 加变体 stream flatMap；`getNoiseBiome` 每个 ring 改调 `selectRing`；新增 `selectRing` 私有方法（双 fast-path + 委派 selector）；类 doc 更新
  - 新建 `EndBiomeSourceTest`（`@BeforeEach`/`@AfterEach` 调 `EndClimateAccess.clear()` 隔离；`PointNoise` stub；`Holder.direct(null)` stub；测 EMPTY→fast-path 1、variants 无 climate→fast-path 2、全范围变体+气候→变体、窄范围+不匹配→base、确定性、collectPossibleBiomes EMPTY=5、含变体=7；**collectPossibleBiomes 断言用 List + `anyMatch(h -> h == expected)` 避免 Holder.direct equals 塌缩**；7 个测试）
  - 编译 + 全 common 测试 + 提交 `feat(stage5.2.1): EndBiomeSource selector wiring + biome_climate codec field`

- [x] Task 6: ROADMAP 更新 + 收尾 (commit 966a5bb)
  - 更新 ROADMAP.md：Stage 2.5c `[ ]`→`[x]`；Stage 5.2 拆为 5.2.1（`[x]`）/5.2.2（`[ ]`）/5.2.3（`[ ]`）；新增 §八「气候 biome 分层设计」固化本 spec 决策
  - 确认 `the_end.json` 无需改动；编译 + 全量 :common:test 确认全绿（253 测试）；提交 `docs(roadmap): mark 5.2.1 complete + add §8 climate biome layering design`
  - 验证 checklist.md 所有检查点（48 项全 [x]）

# Task Dependencies

- Task 3 依赖 Task 2（EndBiomeSelector 入参 BiomeSlot/BiomeVariant）
- Task 5 依赖 Task 2 + Task 3 + Task 4（EndBiomeSource 用 BiomeClimateConfig + 委派 EndBiomeSelector + 读 EndClimateAccess）
- Task 4 与 Task 2/Task 3 无强依赖，可并行
- Task 6 依赖 Task 2 + Task 3 + Task 4 + Task 5 全部完成
