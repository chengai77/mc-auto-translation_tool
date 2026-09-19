# Fabric 与 Forge/NeoForge 功能逐项对照

本清单以同一 Minecraft 版本的 Fabric 实现为基准，核对 Forge 和 NeoForge 的入口、运行时逻辑、配置项、Mixin 与资源。包名、映射名称和加载器事件本身不计为功能差异。

## 版本矩阵

| Fabric 基准 | Forge 对应 | NeoForge 对应 | 当前状态 |
| --- | --- | --- | --- |
| 1.20.1 | 1.20.1 | - | 代码对照完成；Forge F3 有专用补偿 |
| 1.20.4 | 1.20.4 | - | 代码对照完成；Forge F3 有专用补偿 |
| 1.21.1 | - | 1.21.1 | 代码对照完成 |
| 1.21.4 | 1.21.4 | 1.21.4 | 代码对照完成 |
| 1.21.10 | - | - | Fabric 已新增；源码功能与 1.21.11 全量同步 |
| 1.21.11 | 1.21.11 | 1.21.11 | 代码对照完成 |
| 26.1 | 26.1 | 26.1 | 代码对照完成 |
| 26.2 | 26.2 | 26.2 | 代码对照完成 |
| 26.3 | - | - | 仅 Fabric 端；未建立 Forge/NeoForge 工程 |
| 1.16.5 | - | - | 仅 Fabric 端；未建立 Forge/NeoForge 工程 |

旧版 Forge 1.8.9 和 1.12.2 没有对应的 Fabric 基准，使用 `platform-forge-legacy-common` 的 ASM、旧版 GUI 和字体注入实现；这些版本单独标记为 API 限制，不与现代版本强行宣称等价。

## 功能清单

| # | Fabric 功能基准 | Fabric 入口 | Forge 对应 | NeoForge 对应 | 结论 |
| ---: | --- | --- | --- | --- | --- |
| 1 | 客户端初始化、配置加载、运行时关闭 | `UniversalTranslatorFabricClient`、`FabricTranslationRuntime` | `UniversalTranslatorForgeMod`、`UniversalTranslatorForgeClient`、`ForgeTranslationRuntime` | `UniversalTranslatorNeoForgeMod`、`UniversalTranslatorNeoForgeClient`、`NeoForgeTranslationRuntime` | 平台 API 差异但行为等价 |
| 2 | U 键打开主设置页 | Fabric key mapping、`UniversalTranslatorConfigScreen` | Forge `RegisterKeyMappingsEvent`、同名设置页 | NeoForge 客户端事件、同名设置页 | 已等价 |
| 3 | F8 开关翻译、失败回滚、状态通知 | Fabric tick event | Forge `ClientTickEvent.Post` | NeoForge tick event | 已等价；失败时恢复旧运行时 |
| 4 | I 键打开翻译日志 | Fabric tick event、`TranslationLogScreen` | Forge 客户端 tick、同名日志页 | NeoForge 客户端 tick、同名日志页 | 已等价 |
| 5 | 配置读取、迁移、默认值、范围校验 | `FabricConfig` | `ForgeConfig` | `NeoForgeConfig` | 字段和校验逻辑等价 |
| 6 | 目标语言、仅翻译英文、原文/译文显示模式、译文颜色 | `FabricConfig`、设置页 | `ForgeConfig`、设置页 | `NeoForgeConfig`、设置页 | 已等价 |
| 7 | 离线模型、自动下载、下载进度和失败提示 | `LlamaCppOfflineProvider`、`DownloadStatusOverlay` | 同核心 provider、Forge overlay | 同核心 provider、NeoForge overlay | 已等价；运行时提示走平台入口 |
| 8 | LibreTranslate、腾讯混元、官方 API、DeepSeek、LLM、自定义 API | provider 设置页 | 对应 Forge provider 设置页 | 对应 NeoForge provider 设置页 | 已等价 |
| 9 | 缓存导入、导出、清空、磁盘缓存 | `UniversalTranslatorCacheScreen`、runtime cache operations | Forge 同名页面和 runtime | NeoForge 同名页面和 runtime | 已等价 |
| 10 | 出站聊天翻译、命令排除、发送顺序、256 字限制 | `ClientSendMessageEvents.ALLOW_CHAT` | `ClientChatEvent` | NeoForge 客户端聊天事件 | 平台 API 差异但行为等价 |
| 11 | 出站翻译失败、断线、过长和恢复原文提示 | Fabric client completion callback | Forge client completion callback | NeoForge client completion callback | 已等价 |
| 12 | 玩家聊天显示、样式保留、玩家名保护 | `GuiMessageMixin`、`ChatHudMixin`、`StyledChatText` | 同名 Forge Mixin 与类 | 同名 NeoForge Mixin 与类 | 已等价 |
| 13 | 系统消息、数据包长公告、玩家消息分类 | `TextKind.SYSTEM_MESSAGE`、聊天重建 | 同一 `TextKind` 和聊天重建 | 同一 `TextKind` 和聊天重建 | 已等价 |
| 14 | 标题和副标题异步翻译、重复消息合并、原文回退 | `InGameHudContextMixin`、urgent HUD queue | Forge 对应 HUD Mixin、urgent HUD queue | NeoForge 对应 HUD Mixin、urgent HUD queue | 已等价 |
| 15 | 动作栏和手持物品名称 | HUD overlay、`TextKind.ACTION_BAR`/`ITEM_NAME` | HUD overlay；旧版入口另有 `LoaderInGameHudContextMixin` | HUD overlay；旧版入口另有 loader Mixin | 平台补偿；行为目标等价 |
| 16 | BossBar | `BossBarHudContextMixin` | 同名 Forge Mixin | 同名 NeoForge Mixin | 已等价 |
| 17 | 计分板标题和行 | `InGameHudContextMixin`、`TextKind.SCOREBOARD_*` | 同名 Forge HUD Mixin | 同名 NeoForge HUD Mixin | 已等价 |
| 18 | 玩家列表标题、页脚和玩家名保护 | HUD tab-list context、通用字体入口 | HUD tab-list context、通用字体入口 | HUD tab-list context、通用字体入口 | 已等价 |
| 19 | F3 调试信息保护 | `DebugHudMixin` | `DebugHudMixin`；1.20.1/1.20.4 另有 `ForgeGuiDebugTextMixin` 保护 `ForgeGui.renderHUDText` | 对应版本使用 `DebugHudMixin` 或平台 HUD 入口 | Forge 专用补偿；用于修复 F3 不应被翻译的问题 |
| 20 | 实体名称、名称牌和玩家跟随全息文本 | `EntityNameRenderStateMixin`、`TextDisplay*Mixin`、`HologramTextDisplayGroups` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价 |
| 21 | 书本阅读、书本编辑、分页和换行 | `BookScreenMixin`、`BookEditBoxMixin`、`BookTextStyler` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价 |
| 22 | 告示牌编辑与渲染、宽度测量、居中和特殊槽位 | `SignEditScreenMixin`、`SignRendererContextMixin`、`SignTranslationContext` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价 |
| 23 | Tooltip、物品名称、Lore 逐行翻译 | `DrawContextMixin`、`TextKind.TOOLTIP`/`ITEM_LORE` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价 |
| 24 | TextDisplay/全息文本分组、排序、换行、玩家跟随 | `TextDisplayRendererMixin`、`TextDisplayRenderStateMixin`、`HologramTextDisplayGroups` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价；旧版 Forge 无 TextDisplay API |
| 25 | 内置贴图/图标标记的保护、恢复和样式保留 | `InlineTextureText`、`RenderedTextBridge` | 同名 Forge 实现 | 同名 NeoForge 实现 | 已等价 |
| 26 | 文本渲染线程上下文和本地 UI 防二次翻译 | `TranslationRenderContext`、`FabricLocalTextGuard`、`ScreenMixin` | `TranslationRenderContext`、`ForgeLocalTextGuard`、`ScreenMixin` | `TranslationRenderContext`、`NeoForgeLocalTextGuard`、`ScreenMixin` | 已等价；非 Fabric 增加加载器路径保护 |
| 27 | 配置页、provider 页、诊断页、缓存页不被再次翻译 | `TranslationRenderContext`、`LocalTranslationScreen` | Forge 对应 screen guard | NeoForge 对应 screen guard | 已等价 |
| 28 | U 键设置页右下角水印“由承挨二次开发” | 各版本 `UniversalTranslatorConfigScreen` | 各版本 Forge 主设置页 | 各版本 NeoForge 主设置页 | 已同步；旧版共享 GUI 也已保留 |
| 29 | 语言资源 | Fabric `en_us`、`zh_cn`、`zh_tw` | Forge 对应三套资源 | NeoForge 对应三套资源 | 现代版本键集合和值已对齐 |
| 30 | Mixin 配置注册与目标类存在性 | Fabric Mixin JSON | Forge Mixin JSON、Forge loader Mixin JSON | NeoForge Mixin JSON、NeoForge loader Mixin JSON | 通用入口已对齐；loader Mixin 是平台补偿 |

## Mixin 对照结果

现代 26.1 和 26.2 的 Fabric、Forge、NeoForge 通用 Mixin 集合一致（26.3 只有 Fabric 端，通用 Mixin 集合与 26.2 对齐）：

`ChatInputSuggestorMixin`、`DebugHudMixin`、`BookEditBoxMixin`、`BookScreenMixin`、`DrawContextMixin`、`EntityNameRenderStateMixin`、`ScreenMixin`、`SignEditScreenMixin`、`TextFieldWidgetMixin`、`TextDisplayRenderStateMixin`、`TextDisplayRendererMixin`、`TextRendererMixin`、`GuiMessageMixin`、`ChatComponentAccessor`、`SignRendererContextMixin`、`InGameHudContextMixin`、`BossBarHudContextMixin`。

Forge/NeoForge 1.20.x 到 1.21.11 额外存在 `LoaderInGameHudContextMixin`，用于加载器专用的手持物品名称渲染入口，不代表 Fabric 功能缺失。Forge 1.20.1 和 1.20.4 的 `ForgeGuiDebugTextMixin` 是 F3 路径保护所必需的补偿。

## 发现项与验证边界

- 逐版本文件核对未发现 Fabric 有而 Forge/NeoForge 没有的业务功能；Fabric 独有的 `SelfTest` 文件属于测试，不是运行时功能。
- Forge/NeoForge 的运行时保护分支比 Fabric 更严格：`RenderedTextBridge` 和 runtime 会在抑制上下文中直接返回原文，这是防止 F3、手持物品底层文本二次进入通用字体入口的保护，不是功能缺失。
- 旧版 Forge 可以复用通用字体翻译、配置、provider、日志和缓存，但不能完全复现现代 TextDisplay 玩家跟随特例；旧版 API 也不能可靠区分所有玩家聊天与系统消息。
- 现代语言资源已核对为 204 个键，键集合和值一致。
- `build/resources` 下部分历史构建产物仍是 189 个键；这不代表源码缺失，必须重新构建后再检查最终 JAR 资源。
- 1.21.10、1.21.11、26.1、26.2 和 26.3 在本机环境已完成编译与重混淆；实机启动和服务器内回归仍需在可用客户端环境中完成。
- 1.16.5 已完成干净构建、重混淆、共享核心自测与启动到主菜单，Mixin 注入日志无失败项；
  该版本界面层按旧 API 重写（`MatrixStack`、`onClose`/`isPauseScreen`、`TextFieldWidget`、
  `ButtonWidget.TooltipSupplier`、`clearChildren` 缺失时的等价实现），且依赖项 id 在 1.16.5 时代为
  `fabric`，与之后版本的 `fabric-api` 不同。受 API 限制不提供 TextDisplay 全息文本翻译。

## 后续验收顺序

1. 逐平台执行干净构建和产物内容检查。
2. 在服务器内依次验证 U/F8/I、聊天、出站聊天和断线回退。
3. 验证标题、副标题、动作栏、BossBar、计分板、玩家列表和 F3 不被误翻译。
4. 验证书、告示牌、Tooltip、Lore、TextDisplay、内置贴图和换行布局。
5. 在不同 GUI 分辨率确认 U 键设置页右下角水印不遮挡控件。
