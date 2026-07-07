# 末地气候 Biome 分层 Spec

> 本 spec 在前一会话中已批准并实现，但因工作树回滚（HEAD 重置到 c5e7b03）导致所有代码 + spec 文档丢失。本文件恢复已批准的设计，作为重做依据。设计决策与原批准版本一致，无变更。

## Why

vanilla End 用 `TheEndBiomeSource`（几何分段，5 个 biome 按到原点距离分段），**不读 climate**——`noise_settings` 的 climate router 全零。RTF 走 `MultiNoiseBiomeSource`（依赖海陆 Continent 提供 moisture），End 两者都没有。stage 2.5a 已建好 `EndClimate` 温/湿度场，但无消费者。本变更在不破坏原版几何分段的前提下，叠加一个**可选的气候变体层**：每几何环可挂气候变体（温/湿度闭区间匹配），让 biome 在不改变原版 layout 的前提下按气候分化子类型。

## What Changes

- **新增** `BiomeVariant` record（已存在于 c5e7b03，但 CODEC 有编译 bug——JDK 21 类型推断失败，需显式类型见证 `<BiomeVariant>create`，已在本轮 b46e371 修复）
- **新增** `BiomeSlot` record：一个几何环的气候覆盖槽（nullable base + `List<BiomeVariant>`，`List.copyOf` 防御性拷贝）
- **新增** `BiomeClimateConfig` record：5 个 `BiomeSlot`（end/highlands/midlands/islands/barrens）+ `resolve(5 holders)` 一次性填充 null base
- **新增** `EndBiomeSelector`：纯逻辑 4 角双线性选择器（cell 4 角各采 `EndClimate`，按 fractional 位置双线性加权选胜出 biome）
- **新增** `EndClimateAccess`：volatile 静态持有者，把 `MixinRandomState` 在引导线程构造的 `EndClimate` 发布给 worker 线程
- **修改** `MixinRandomState`：在 `isEnd` 分支构造 `EndClimate` 后调 `EndClimateAccess.set(climate)`（1 行）
- **修改** `EndBiomeSource`：CODEC 加第 6 字段 `biome_climate`（`optionalFieldOf`，缺省 `EMPTY`，向后兼容）；`getNoiseBiome` 每个 ring 调 `selectRing`（双 fast-path + 委派 selector）；`collectPossibleBiomes` 加变体 biome
- **更新** ROADMAP.md：Stage 2.5c 标 `[x]`；Stage 5.2 拆为 5.2.1（`[x]`）/5.2.2（`[ ]`）/5.2.3（`[ ]`）；新增 §八固化设计决策

## Impact

- **Affected specs**: stage 2.5c（climate 作为 biome 子类型选择器）、stage 5.2（末地 biome/feature/surface rules）
- **Affected code**:
  - 新增：`common/.../biome/BiomeSlot.java`、`BiomeClimateConfig.java`、`EndBiomeSelector.java`
  - 新增：`common/.../climate/EndClimateAccess.java`
  - 修改：`common/.../biome/EndBiomeSource.java`（CODEC + 字段 + 构造函数 + `getNoiseBiome` + `collectPossibleBiomes` + 类 doc）
  - 修改：`common/.../mixin/MixinRandomState.java`（+1 行发布 climate）
  - 修改：`ROADMAP.md`
- **兼容性**：`the_end.json` 不改；旧 5 字段 `biome_source` JSON 仍合法解码（`biome_climate` 是 `optionalFieldOf`）；缺省 config 性能等同原版（fast-path 1 无气候采样）

## ADDED Requirements

### Requirement: 气候变体数据层
系统 SHALL 提供 `BiomeVariant`（biome + 温/湿度闭区间，`floatRange` 钳制 + `validate` 拒绝 min>max）、`BiomeSlot`（nullable base + `List<BiomeVariant>`，`List.copyOf` 防御性拷贝）、`BiomeClimateConfig`（5 个 `BiomeSlot` + `resolve` 填 null base）三个不可变 record，配 DFU `Codec`。

#### Scenario: 缺省 config 退化为原版
- **WHEN** biome-source JSON 省略 `biome_climate` 字段
- **THEN** 解码为 `BiomeClimateConfig.EMPTY`，5 个 `BiomeSlot.EMPTY`，`hasAnyVariants()` 返 false

#### Scenario: 防御性拷贝
- **WHEN** 调用方构造 `BiomeSlot(base, list)` 后修改 `list`
- **THEN** `BiomeSlot.variants()` 不受影响（`List.copyOf` 返回不可变副本）

#### Scenario: 反向区间被拒
- **WHEN** JSON 中 `temperature_min > temperature_max`
- **THEN** DFU 解码失败（`validate` 返 `DataResult.error`），不静默匹配空集

### Requirement: 4 角双线性选择器
系统 SHALL 提供 `EndBiomeSelector.select(cellX, cellZ, fracX, fracZ, climate, seed, ringSlot)` 纯逻辑静态方法，按 cell 4 角双线性加权选 biome。

#### Scenario: 无气候或无变体走 fast-path 1
- **WHEN** `climate == null` 或 `!ringSlot.hasVariants()`
- **THEN** 返回 `ringSlot.base()`，无气候采样

#### Scenario: 4 角一致走 fast-path 2
- **WHEN** 4 角候选 biome 全 `==` 相同
- **THEN** 返回 c00，无权重计算

#### Scenario: 慢路径双线性聚合
- **WHEN** 4 角不一致
- **THEN** 算 4 角权重 `(1-fx)*(1-fz)`/`fx*(1-fz)`/`(1-fx)*fz`/`fx*fz`，按 biome 引用聚合（`==` 比较，非 equals），选最大；平局按角序 00>10>01>11

### Requirement: 跨线程发布 EndClimate
系统 SHALL 通过 `EndClimateAccess`（`private static volatile EndClimate current` + set/get/clear）把 `MixinRandomState` 在引导线程构造的 `EndClimate` 发布给 worker 线程。`EndClimate` 是不可变 record，volatile 发布足以让 worker 无锁读（JMM happens-before）。`clear()` 仅供测试 teardown。

#### Scenario: 非 End 维度不发布
- **WHEN** `RandomState.create` 的 key ≠ `NoiseGeneratorSettings.END`
- **THEN** `EndClimateAccess.get()` 返 null，`EndBiomeSource` 走 fast-path 2 返 base

#### Scenario: worker 线程可见
- **WHEN** 引导线程调 `set(climate)` 后 worker 线程调 `get()`
- **THEN** worker 线程读到非 null climate（volatile happens-before 保证）

### Requirement: EndBiomeSource 接入 climate selector
系统 SHALL 在 `EndBiomeSource` CODEC 加第 6 字段 `biome_climate`（缺省 `EMPTY`），构造时 `resolve(5 holders)` 填 null base，`getNoiseBiome` 每个 ring 调 `selectRing`（双 fast-path + 委派 selector）。

#### Scenario: 缺省兼容原版
- **WHEN** biome-source JSON 省略 `biome_climate`
- **THEN** `getNoiseBiome` 走 fast-path 1（`!hasAnyVariants()`），返 ringSlot.base()，性能等同原版

#### Scenario: 变体 + 气候触发慢路径
- **WHEN** `biome_climate` 含变体 AND `EndClimateAccess.get()` 非 null
- **THEN** 采样 fracNoise + 委派 `EndBiomeSelector.select`，返回变体或 base

### Requirement: 测试覆盖
系统 SHALL 提供 6 个测试类共 36 个测试：`BiomeSlotTest`（6）、`BiomeClimateConfigTest`（6）、`EndBiomeSelectorTest`（12）、`EndClimateAccessTest`（5）、`EndBiomeSourceTest`（7）+ BiomeVariant 已有。测试用 test-only `PointNoise`（`BiFunction<Float,Float,Float>` lambda）构造可控 `EndClimate`，`Holder.direct(null)` stub biome，`==` 比较避开 equals 塌缩，`@BeforeAll` 调 `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`（`net.minecraft.server.Bootstrap`）。

## MODIFIED Requirements

### Requirement: EndBiomeSource 类文档
类级 Javadoc 增加「两层正交模型：几何环 × 可选气候变体」段落，替换原「Stage 2.5c may later add an optional climate-variant selector」过期段。

### Requirement: MixinRandomState End 引导
在 `isEnd` 分支构造 `EndClimate climate = EndClimate.defaults(noiseSeed)` 后（已存在，用于 `withClimate`），新增 `EndClimateAccess.set(climate)` 1 行发布给 biome source。

## REMOVED Requirements

### Requirement: 「climate 延后」占位
**Reason**: 本 spec 落地气候变体层，"延后"注释过期。
**Migration**: 类 doc 改为「两层正交模型」描述。

## 设计决策与依据

1. **静态 volatile 持有者 vs 扩展 EndRandomStateAccess 接口**：选 volatile 持有者。`EndBiomeSource` 由 codec 构造，构造时 `RandomState` 还不存在，无法通过构造函数传 climate；扩展接口注入字段会迫使 EndBiomeSource 持有 RandomState 引用，破坏「codec 构造即可用」的简洁。volatile 发布不可变 record 是 JMM 标准做法。

2. **4 角双线性 vs 单点采样**：选 4 角双线性。vanilla biome API 只给整数 cell 坐标，单点采样会产生 4-block 阶梯（cell 边界突变）；4 角双线性按 fractional 位置加权，cell 内过渡平滑。

3. **EndBiomeSelector 独立类 vs 内联**：选独立类。纯逻辑（只依赖 `EndClimate` + `BiomeSlot` + `Holder<Biome>`），可独立单测；与 MC 集成类（EndBiomeSource）分离，符合 §六.2「算法与 MC 解耦」。

4. **biome_climate 放 biome_source JSON 而非 EndPreset**：biome 是注册表引用，属维度生成配置；EndPreset 是地形配置，不应混入 biome 引用。

5. **测试 stub `Holder.direct(null)` + `==` 比较**：`Holder.direct(null)` stub 全部 `equals()`-相等（值都是 null），Set/contains/Map 会塌缩；用 `==` 引用比较保持 stub 独立。

6. **MC bootstrap**：1.21.1 official mappings 是 `net.minecraft.server.Bootstrap`（非旧版 `net.minecraft.Bootstrap`）；`tryDetectVersion()` 必须先于 `Bootstrap.bootStrap()`，否则 `DataFixers.<clinit>` 报 "Game version not set"。

7. **Codec round-trip 延后**：纯 DFU 测试无法解析 `Holder<Biome>` 名字（需运行时 biome 注册表），延后到 MC 运行时测试。

8. **fracNoiseX/Z**：vanilla biome API 无亚 cell 位置，用两个 simplex（`Noises.map(simplex(1339/1340, 50, 2), 0, 1)`）提供稳定的 cell 内 fractional 位置；固定 seed（本期 biome layout 不绑世界 seed）。

9. **seed 传 0**：世界 seed 已 baked 进 `EndClimate` 的 noise trees（`MixinRandomState` 构造时 `EndClimate.defaults(noiseSeed)`），per-call seed 是 vestigial，传 0 即可。
