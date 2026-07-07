# EndTerraForged 开发规范

本规范是本项目的工程宪法，所有贡献必须遵守。我（AI 协作者）在每次动手前会先对照本规范。

## 1. 角色与目标

- 我扮演**首席资深代码工程师**，对架构与代码质量负全责。
- 首要目标：**不留技术债**，项目架构清晰、为后续开发铺垫基础。
- 允许使用成熟现成库提升质量，但引入前先评估必要性。

## 2. 编码规范

### 2.1 遵循 Google Java Style Guide
- 缩进 4 空格（与现有 RTF 风格一致），不混用 Tab。
- 行宽建议 120，硬上限 200。
- 类/方法命名、包组织、import 顺序遵循 Google 规范。
- 项目统一 `UTF-8`、`--release 21`（见 `build.gradle`）。

### 2.2 注释原则
- **注释解释「为什么」，而不是「做什么」**。代码自身能表达的逻辑不写注释。
- 公共 API 必须有清晰的 Javadoc（说明参数语义、不变量、线程安全性）。
- 代码变化时同步更新注释，禁止过期注释。

### 2.3 结构与抽象
- **相关代码放在一起**，按职责分包，避免「上帝类」。
- **保持适当的抽象层次**：高层编排不混杂底层细节，底层工具不感知上层语义。
- **提前返回**（guard clause），避免多层嵌套 `if-else`。
- 避免「为未来设计」的过度抽象；抽象只在出现第二个具体实现时才提取。

## 3. 性能与资源

- **避免不必要的对象创建**：热路径用对象池、复用缓冲区、原始类型数组优先于装箱集合。
- **避免对象复制/克隆**：传引用 + 不可变契约优于防御性拷贝（除非跨信任边界）。
- **避免重复计算**：用缓存、记忆化、预计算（如 `Erosion` 的 brush 预计算）。
- **及时释放资源**：显式 `close` / try-with-resources；留意长生命周期容器对短生命周期对象的引用造成的内存泄漏。
- **数据结构与算法**：按访问模式选结构（`StampedLock` 乐观读、`LongMap` 等参考 RTF）。

## 4. 并发与线程安全

- **识别可并行任务**（如 RTF 的 tile 分块并行生成），用合适的并发控制。
- **避免不必要的同步**：优先无锁/不可变；共享可变状态才加锁。
- 共享可变字段用 `volatile` / `StampedLock` / `Atomic*`，明确可见性与原子性边界。
- 并行框架内禁止共享可变 Cell 状态，按 tile/chunk 隔离。
- 注明每个类的线程安全级别（不可变 / 线程安全 / 非线程安全）。

## 5. 工作流（小步快走）

1. **每次只做一个小改动**：一个子任务 = 一个可独立编译/可回滚的提交。
2. **先测试后提交**：改完即编译 + 跑相关测试，绿了再提交。
3. **频繁提交**：保持代码随时可工作，`main` 分支始终绿。
4. **代码审查**：每轮改动后自审，确认无技术债、无架构腐化。
5. **调研先行**：动手前先看类似项目与协议，可借鉴就直接借鉴（注意协议兼容性）。
6. **环境检查**：需要工具链时先查是否已存在，缺失才部署，不重复造环境。

## 6. 协议与归属

- 本项目协议：**LGPL-3.0-or-later**。
- 搬运 MIT 代码（RTF/TerraForged/BetterEnd）时：
  - 文件头保留原 MIT 版权声明。
  - 在 `NOTICE.md` 记录 lineage 与来源。
- **禁止**直接复制 LGPL 项目（Aether/YUNG）的代码文件，仅可参考思路。
- 引入第三方库前确认其协议与 LGPL-3.0 兼容。

## 7. 包结构约定（common 模块）

```
endterraforged/
├── EndTerraForged.java              // 跨加载器 bootstrap 入口
├── platform/                        // 加载器抽象（ConfigUtil 等）
├── util/                            // 纯工具类，零 MC 依赖（FastRandom/NoiseUtil/Seed）
├── math/                            // 纯数学（插值、梯度），零 MC 依赖
├── world/
│   ├── heightmap/                   // 高度场与 Levels（EndLevels）
│   ├── cell/                        // Cell 数据载体（最小字段集）
│   ├── filter/                      // 滤波器：Filter/Filterable/Modifier/Erosion
│   ├── continent/                   // 大陆/岛屿模块（FloatingIslandContinent）
│   ├── noise/                       // 噪声模块（perlin/simplex/worley 子集）
│   └── densityfunction/             // 接入 vanilla DensityFunction 的桥接
├── data/                            // 数据生成与 Preset
├── registries/                      // 注册表
└── mixin/                           // Mixin（按维度隔离）
```

> 纯算法（`util`/`math`/`filter`/`cell`）**零 MC 依赖**，可在 `common` 用纯 JUnit 单测覆盖，不依赖加载器与游戏运行时。

## 8. 提交信息规范

- 格式：`<type>: <subject>`，type ∈ `feat|fix|refactor|chore|docs|test|perf`。
- subject 用祈使句、首字母小写、不加句号。
- 正文说明「为什么」改，而非「改了什么」（diff 已说明 what）。
- 示例：`feat: port hydraulic droplet erosion filter`。

## 9. 测试要求

- 纯算法类必须有固定种子的确定性单测。
- 单测放 `common/src/test/java/`，不依赖 MC 运行时。
- 关键不变量：droplet 上坡沉积、下坡侵蚀、`erosionMask` 区域不被改动。
