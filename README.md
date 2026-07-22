# EndTerraForged

EndTerraForged 是面向 Minecraft 1.21.1 的末地地形生成模组，目标是在保持原版末地核心流程可用的前提下，生成可配置的大型浮空大陆、群岛、气候地貌和宏大地下空间。

项目仍处于 `0.1.7` 开发阶段，尚未达到公开稳定版标准。当前正式路线是 NeoForge-first + LDLib2；Fabric 保留低维护编译和社区移植边界。

## 当前方向

- 默认 Standard 世界规格：`min_y=-256`、`height=512`。
- 多个大型浮空大陆、虚空海峡、群岛、地貌区域、山系、高原、火山、气候、侵蚀和群系布局；新默认大陆架具有有限 underside，不再从地表自动填充到世界底部。
- 当前先验收“原版中央区保护 + 主岛外多大陆 + 有限体积”的最新修复，随后按高级大陆场、`TerrainRegionPlan`、成熟地貌族、有限山系/火山、群岛海岸和侵蚀排水重建地表。现有单一 selector + 通用噪声层只作为 legacy 原型。
- 原版主岛、龙战、黑曜石柱、返回门、网关及其外围区域保持冻结，尚不属于改造范围。
- 自研 Spectacle-First Cave Pipeline，不采用 RTF cave。
- 2D 地图与 X/Z 垂直剖面实时预览；可旋转 3D 预览属于后续阶段。
- 数据驱动的 ETF Worldgen Content Pack API，用于第三方 biome、内容主题、surface palette、feature 和末地模组兼容。
- 重点考虑 C2ME、主流末地模组和超大型整合包环境。

RTF R9.3.6 作为参数与配置语义基线，R9.6 作为稳定核心实现基线。RTF 是同一维护方的 MIT 项目，可按许可直接移植经过审查的纯数学实现；ETF 不复制 RTF UI、主世界水位/海洋、`GeneratorContext`、可变 `Cell`、river cache 或 RTF cave。

## 文档

- [长期目标与阶段目标](GOAL.md)
- [当前执行计划](PLAN.md)
- [架构说明](docs/ARCHITECTURE.md)
- [RTF 核心复用与高质量地表重构](docs/RTF_CORE_REUSE_RESEARCH.md)
- [Content Pack API 规格](docs/CONTENT_PACK_SPEC.md)
- [Terra / ReimagEND 内容兼容调研](docs/TERRA_CONTENT_COMPATIBILITY_RESEARCH.md)
- [地下系统调研](UNDERGROUND_RESEARCH.md)
- [完整文档索引](docs/DOCUMENTATION_INDEX.md)
- [开发与贡献](DEVELOPMENT.md)

开始大段开发前应按文档索引规定的顺序阅读，历史 `ROADMAP.md` 不再是当前路线来源。

## 模块

```text
common/    跨平台配置、worldgen、runtime、preview、通用 GUI 和测试
neoforge/  官方 NeoForge 入口、运行时适配与 LDLib2 bridge
fabric/    Fabric 入口与低维护兼容边界
```

## 构建

需要 JDK 21。使用仓库内 Gradle Wrapper，不要并行运行同一工作树中的多个 Gradle 构建。

```powershell
.\gradlew.bat :common:test --configure-on-demand --no-daemon
.\gradlew.bat :neoforge:compileJava --configure-on-demand --no-daemon
.\gradlew.bat :fabric:compileJava --configure-on-demand --no-daemon
```

发布级验证不得使用 `--configure-on-demand`：

```powershell
.\gradlew.bat :verifyReleaseArtifacts --no-daemon
```

Windows 中文路径导致 Gradle/Architectury worker 异常时，按 [`AGENTS.md`](AGENTS.md) 使用同一 PowerShell 会话内的 ASCII `subst` 或 junction 路径。

## 许可

本项目采用 [LGPL-3.0-or-later](LICENSE) 许可。上游参考与第三方说明见 [`NOTICE.md`](NOTICE.md)。
