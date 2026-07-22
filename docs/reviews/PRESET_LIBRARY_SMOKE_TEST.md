# Preset Library 真实客户端烟测

> 文档状态：当前有效的手工验收清单，不是路线文档。
> 执行结果应回填日期、版本、测试世界和失败项；当前优先级见 [`../../PLAN.md`](../../PLAN.md)。

## 目的

本记录用于验证 NeoForge 单人世界中的已有世界编辑入口、Preset
Library、active preset 保存和重进世界后的生效条件。它不能用单元测试、
日志启动成功或预览截图替代。

## 前置条件

1. 使用 `:neoforge:runClient --no-daemon` 启动当前源码的开发客户端。
2. 创建新的本地测试世界，不在重要存档上执行损坏文件场景。
3. 记录世界目录。以下路径均相对该目录：
   - `endterraforged_preset.json`
   - `endterraforged_presets/<name>.json`
   - `endterraforged_exports/<name>.json`
4. 记录开始前 `latest.log` 中是否存在 ETF、Mixin 或 LDLib2 异常。

## 单人世界入口

1. 进入本地单人世界并打开暂停菜单。
2. 确认出现 EndTerraForged preset 编辑器入口，且不覆盖已有暂停菜单按钮。
3. 打开编辑器，切换到“更多”页。
4. 确认出现 Preset Library 入口。
5. 在创建世界阶段的编辑器确认不显示 Preset Library。

## 命名库操作

1. 打开 Preset Library，输入 `smoke_library`。
2. 点击“保存副本”，确认出现成功状态，并确认
   `endterraforged_presets/smoke_library.json` 已创建。
3. 修改一个预览中可观察的参数，例如大陆形态或地下开关；重新打开 Library，
   选择 `smoke_library` 并加载。
4. 确认编辑器参数和实时预览回到保存快照，且此时
   `endterraforged_preset.json` 没有被提前写入或替换。
5. 再次打开 Library，导出 `smoke_library`，确认
   `endterraforged_exports/smoke_library.json` 已创建。
6. 删除命名预设，确认列表刷新、成功状态可见且命名文件已消失。
7. 使用同名导入交换目录 JSON，确认编辑器回填导入快照，命名库不会被自动重建。
8. 分别尝试空名称、未知名称和手工损坏的导入 JSON，确认状态可见且没有静默回退。

## Active Preset 与错误处理

1. 在编辑器中设置一个可观察参数后点击 Done。
2. 确认 active preset 写入 `endterraforged_preset.json`，并显示“重进世界或重启
   服务器后生效”的 toast。
3. 在编辑器中修改参数后点击 Cancel，确认 active preset 的内容和修改时间不变。
4. 退出测试世界后，将 active preset 临时替换为无效 JSON。
5. 重新进入世界、打开暂停菜单并点击编辑器入口。
6. 确认显示打开失败 toast，编辑器不打开，损坏文件没有被默认值覆盖。
7. 恢复有效 active preset 或删除测试世界。

## 重载与多人边界

1. 保存一个明显不同的 preset 后退出并重新进入测试世界。
2. 在未生成区域确认新生成结果使用保存后的 preset；不要把已生成区块不变误判为
   保存失败。
3. 连接多人服务器客户端，确认暂停菜单不显示已有世界编辑入口。

## 通过条件

所有步骤均无崩溃、无静默失败、无 active preset 提前写入；`latest.log` 不出现
新的 ETF、Mixin、LDLib2 或 worldgen ERROR/FATAL。记录测试日期、世界类型、结果和
相关截图或日志片段后，才可以将阶段 A 的真实交互验收标记为通过。
