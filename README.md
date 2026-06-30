# EndTerraForged

一个受 [TerraForged](https://github.com/TerraForged/TerraForged) 与 [ReTerraForged](https://github.com/racoonman2/reterraforged) 启发的**末地（End）维度地形生成模组**，目标是把 RTF 那套成熟的噪声/地形/生境生成体系移植并改造到末地维度。

## 目标

- 用可配置的程序化噪声重写末地岛屿、外岛、空洞与生境分布
- 提供末地专属的生物群系、特性（feature）、地表规则与结构规则
- 同时支持 Fabric 与 NeoForge（基于 Architectury + Loom）

## 构建

需要 JDK 21。本项目使用 Gradle Wrapper，无需本地安装 Gradle：

```bash
# Fabric
./gradlew :fabric:build

# NeoForge
./gradlew :neoforge:build
```

产物位于各子模块的 `build/libs/` 下。

## 模块结构

```
common/    跨加载器共用代码（噪声、注册表、世界生成逻辑）
fabric/    Fabric 平台入口与混合
neoforge/  NeoForge 平台入口与混合
```

## 许可证

本项目基于 [GNU LGPL-3.0-or-later](./LICENSE) 协议开源。
