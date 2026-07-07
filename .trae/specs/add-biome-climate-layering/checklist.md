# Checklist — 末地气候 Biome 分层

## 数据层（Task 2）
- [x] `BiomeSlot` record 存在：`Holder<Biome> base`（nullable）+ `List<BiomeVariant> variants`，compact constructor 用 `List.copyOf` 防御性拷贝（unmodifiable + null-hostile）
- [x] `BiomeSlot.hasVariants()` 返回 `!variants.isEmpty()`
- [x] `BiomeSlot.EMPTY` 常量 = `new BiomeSlot(null, List.of())`
- [x] `BiomeSlot.CODEC` 存在且 `base` 字段可省略（nullable）、`variants` 缺省为空列表
- [x] `BiomeClimateConfig` record 存在：5 个 `BiomeSlot`（end/highlands/midlands/islands/barrens）
- [x] `BiomeClimateConfig.EMPTY` = 5 个 `BiomeSlot.EMPTY`
- [x] `BiomeClimateConfig.hasAnyVariants()` = 5 槽 `hasVariants()` 的 OR
- [x] `BiomeClimateConfig.resolve(end, highlands, midlands, islands, barrens)` 返回新 config：null base 填对应几何环 biome，非 null 保留，variants 不变
- [x] `BiomeClimateConfig.CODEC` 存在，5 字段均 `optionalFieldOf(..., BiomeSlot.EMPTY)`
- [x] `BiomeSlotTest` + `BiomeClimateConfigTest` 全绿，`@BeforeAll` 调 `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`
- [x] 测试用 `Holder.direct(null)` stub + `assertSame`（引用比较），未用 equals 断言 stub 相等

## 选择器（Task 3）
- [x] `EndBiomeSelector.select(cellX, cellZ, fracX, fracZ, climate, seed, ringSlot)` 静态方法存在
- [x] fast-path 1：`climate == null || !ringSlot.hasVariants()` → 返回 `ringSlot.base()`
- [x] fast-path 2：4 角候选 biome 全 `==` 相同 → 返回 c00
- [x] 慢路径：4 角权重 = `(1-fx)*(1-fz)` / `fx*(1-fz)` / `(1-fx)*fz` / `fx*fz`
- [x] 4 角聚合用 `==` 比较 biome holder（非 equals，避免 Holder.direct 塌缩）
- [x] 平局 tie-break 按角序 00 > 10 > 01 > 11（确定性）
- [x] 各角候选 = `ringSlot.variants()` 首个 `matches(temp, moist)` 的变体，否则 `ringSlot.base()`
- [x] 气候采样坐标：4 角为 (cellX,cellZ)/(cellX+1,cellZ)/(cellX,cellZ+1)/(cellX+1,cellZ+1)
- [x] `EndBiomeSelectorTest` 全绿（13 测试，≥10）
- [x] 测试用 test-only `PointNoise`（`BiFunction<Float,Float,Float>` lambda）构造可控 `EndClimate`

## 跨线程发布（Task 4）
- [x] `EndClimateAccess` 存在：`private static volatile EndClimate current` + `set/get/clear`
- [x] Javadoc 说明 volatile 发布不可变 record 的线程安全语义 + `clear()` 仅供测试
- [x] `MixinRandomState.endTerraForged$initCapture` 在 `isEnd` 分支构造 `EndClimate` 后调 `EndClimateAccess.set(climate)`
- [x] 非 End 维度不发布（`isEnd` 门控，`current` 保持 null）
- [x] `EndClimateAccessTest` 全绿（5 测试，≥5）：set/get 契约、clear 后 null、set 覆盖旧值、set(null) 等价 clear

## EndBiomeSource 接入（Task 5）
- [x] `EndBiomeSource.CODEC` 有第 6 字段 `biome_climate`（`optionalFieldOf`，缺省 `BiomeClimateConfig.EMPTY`）
- [x] 构造函数加 `BiomeClimateConfig` 参数 + `this.biomeClimate = biomeClimate.resolve(...)`
- [x] `fracNoiseX`/`fracNoiseZ` 字段（`Noises.map(Noises.simplex(1339/1340, 50, 2), 0.0F, 1.0F)`）
- [x] `getNoiseBiome` 每个 ring 调 `selectRing(this.biomeClimate.<ring>(), x, z)`
- [x] `selectRing` 双 fast-path：`!slot.hasVariants()` → base（per-slot 粒度，比 config-level `!hasAnyVariants()` 更高效：无变体的 ring 跳过气候工作，即使其他 ring 有变体）；`EndClimateAccess.get() == null` → base；慢路径 sample fracNoise + 委派 `EndBiomeSelector.select(x, z, fracX, fracZ, climate, 0, ringSlot)`
- [x] `collectPossibleBiomes` 返回 5 base + 所有槽变体 biome（stream flatMap）
- [x] `EndBiomeSourceTest` 全绿（7 测试，≥7）
- [x] `EndBiomeSourceTest` 的 collectPossibleBiomes 断言用 `List` + `anyMatch(h -> h == expected)`（非 Set/contains，避免 Holder.direct equals 塌缩）
- [x] `@BeforeEach`/`@AfterEach` 调 `EndClimateAccess.clear()` 隔离测试

## 兼容性与收尾（Task 6）
- [x] `the_end.json` 未改（缺省无 `biome_climate` → `EMPTY` → 原版行为）
- [x] 旧 5 字段 `biome_source` JSON 仍合法解码（optionalFieldOf 向后兼容）
- [x] 缺省 config 的 `getNoiseBiome` 性能等同原版（fast-path 1 无气候采样）
- [x] ROADMAP Stage 2.5c 标 `[x]`
- [x] ROADMAP Stage 5.2 拆为 5.2.1（`[x]`）/5.2.2（`[ ]`）/5.2.3（`[ ]`）
- [x] ROADMAP 新增 §八 固化本 spec 决策（两层模型/cell 双线性/volatile 持有者/测试策略/兼容性）
- [x] 全 `:common:test` 全绿（253 测试），无回归
- [x] 每步独立提交（b46e371/588711f/75f2ee0/e4f517b/eaf3914），提交信息符合 DEVELOPMENT.md §8（`feat(stage5.2.1): ...`）

## 架构/技术债审查
- [x] 无新增 ThreadLocal 跨线程 bug（用 volatile 替代）
- [x] 热路径无对象分配（selector 4 角聚合手动展开，无 LinkedHashMap/boxed 权重）
- [x] 无 Holder.direct equals 陷阱残留（所有 stub 比较用 `==`）
- [x] 无过期注释（EndBiomeSource 类 doc 更新，反映两层模型而非「climate 延后」）
- [x] 纯逻辑类（EndBiomeSelector）与 MC 集成类（EndBiomeSource）分离，符合 §六.2

## 测试统计

| 测试类 | 测试数 | 状态 |
|--------|--------|------|
| BiomeSlotTest | 7 | ✅ |
| BiomeClimateConfigTest | 7 | ✅ |
| EndBiomeSelectorTest | 13 | ✅ |
| EndClimateAccessTest | 5 | ✅ |
| EndBiomeSourceTest | 7 | ✅ |
| **新增小计** | **39** | ✅ |
| 全 common 测试 | 253 | ✅ 无回归 |
