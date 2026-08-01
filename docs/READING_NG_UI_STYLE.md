# Reading NG UI Style Guide

本文档定义阅读 NG 后续新增 UI 的统一设计方向。目标不是全量切换到 Material Design 3，也不是继续每个功能单独手搓界面，而是建立一套适合阅读 NG 的稳定设计语言。

主题到 View／Compose 组件的运行时数据流见 `NG_DESIGN_SYSTEM_ARCHITECTURE.md`。

## 文档状态

本文档采用“真实页面先验收、通过后再冻结规则”的方式维护，不把 Catalog 样板或尚未接入生产页面的组件视为视觉标准。

截至 2026-07-30，AI／朗读设置及 Provider／TTS 真实页面已经确认以下第一批公共能力：

| 能力 | 公共实现 | 已验证场景 | 冻结结论 |
| --- | --- | --- | --- |
| 页面／抽屉搜索 | `NgSearchBar` | Provider、模型列表、模型选择 BottomSheet | 44dp 胶囊搜索框，15sp 输入，统一搜索、清除和键盘行为 |
| 次要按钮 | `NgSecondaryButtonView` | Provider 详情、Skill 操作 | 高不透明白底、1dp 主题强调色描边、强调色文字；主操作仍使用强调色实底 |
| 详情页底部切换 | `NgFloatingTabBar` | Provider 配置／模型、TTS 引擎配置／发音人 | 48dp 悬浮栏、等宽项、纯图标居中；文字保留为无障碍描述 |
| 标题栏菜单 | `NgMenuPopup` + `NgActionPopup` | Provider、TTS 引擎列表菜单 | 标题栏只保留一个操作入口；新增类型与列表查看选项用 18dp 圆角菜单承载 |
| 选择型长列表抽屉 | `NgLongListBottomSheet` | AI 模型、发音人、TTS 引擎选择 | 透明筛选承载层、无描边搜索／过滤卡片；标题栏按需展开搜索或“搜索 + 业务过滤项”面板 |
| 强调色规则 | 当前主题 `accentColor` | 小节标题、按钮、选中 Tab | 直接保留主题原始强调色，不额外派生暗色文字替代视觉性格 |
| 图标选择规则 | 现有资源 + Iconify + 必要时单独设计 | AI 设置入口、Provider、Tab、菜单 | 不绑定单一 Material 图标库；统一的是尺寸、视口、线宽和语义，不是来源 |
| 页面标题栏 | `TitleBar` | AI 设置及同源二级页面 | 返回按钮保留完整触控区域，导航内容 inset 与额外标题前距统一为 0dp，不在单页增加位移补丁 |
| 一级设置项 | `NgSettingsItemView`／Compose `NgSettingsItem` | AI 设置、朗读设置 | View 与 Compose 共用相同几何、颜色和尾部语义；整页迁移只使用一个页面级 `ComposeView` |
| 管理列表卡片 | `NgManagementListCardView` | Provider、TTS 引擎、Prompt／Skill | 白色无边框卡片统一图标、标题、元数据、状态、摘要、尾部操作和选中指示；业务排序与点击仍由页面负责 |
| 状态 Tag | `NgStatusTagView` | Provider、TTS 引擎、Prompt／Skill | 标题下状态使用 24dp `REGULAR`；标题行右侧短元数据使用 20dp `COMPACT`，不混用尺寸职责 |

以上能力已经可以在同类页面复用，但仍按页面逐项验收。表单字段、Dialog、图片和通用状态页尚未完成同等级验收，不得因已存在类名或 Catalog 示例就标记为全局稳定。

`NgManagementListCardView` 与 `NgStatusTagView` 已在 Provider、TTS 引擎和 Prompt／Skill 三张真实列表通过真机验收，成为 View 版 `ManagementScreen` 基线。后续 Compose 实现必须复刻同一结构与语义，不另行重画；统一空／加载／错误状态仍需作为独立组件验收。

## 核心原则

阅读 NG 的 UI 采用：

> 结构参考 Material Design 3，配色和氛围保持 Reading NG 自有风格。

具体含义：

- 借用 MD3 的页面层级、组件语义、间距节奏和表单形态。
- 不直接照搬 MD3 / Material You 的动态色、默认紫色、灰紫背景和完整组件外观。
- 保留当前阅读 NG 已形成的透明主题背景、白色／半透明承载面、主题原始强调色、清爽工具卡片和阅读类 App 的柔和感。
- 设置类页面应允许主题背景图片透出：页面根布局默认透明，一级设置菜单使用 MD3 grouped list，二级内容列表再使用半透明圆角卡片。
- 新增 AI、调试、设置类 UI 时，优先复用本文档约定，不再按单个功能临时设计。

## 参考对象

可以参考：

- RikkaHub 的信息架构：设置首页、二级列表、详情页、底部 tab。
- RikkaHub 的组件语义：卡片、圆形图标、tag、Outlined 输入框、Switch、Segmented 控件。
- MD3 的结构层级：TopAppBar、List、Detail、Dialog、Card、Chip、OutlinedTextField。

不要照搬：

- RikkaHub 的紫色主色。
- Material You 动态取色。
- MD3 默认 `surfaceContainer` 灰紫色系。
- Compose 专属动效和组件实现。
- 与阅读 NG 首页、调试页冲突的高饱和或冷色主题。

## 视觉方向

阅读 NG 的整体视觉应保持：

- 温和：适合长时间阅读和设置。
- 清爽：避免厚重阴影、复杂边框和过多装饰。
- 工具化：调试、AI、规则编辑等页面应信息清楚、操作明确。
- 一致：新增功能必须优先匹配首页、搜索页、调试页已有风格。
- 文案克制：简单、可自解释的功能不要额外添加长描述；只有用户难以理解、存在明显风险或需要解释后果时，才补充说明文字。
- 多选项菜单文案尽量保持长度和语义层级一致，例如 `启用/禁用/编辑/删除`；如果无法保证完全等长，也不要出现上方长短不一、下方突然短项的割裂排布。
- 页面标题与返回图标之间由导航按钮自身宽度保留必要留白；公共 Toolbar 的 `contentInsetStartWithNavigation` 和额外标题前距统一使用 0dp。不得缩小返回按钮的标准触控区域，也不得在业务页面给标题增加负 margin。

当前视觉基准：

- 书架首页：主题背景、白色圆角搜索框、透明或白色内容承载区、当前主题强调色。
- 书源调试页：顶部搜索条、时间轴、阶段卡片、状态 tag、轻量分隔。
- 网络日志弹窗：清爽列表、紧凑信息、状态颜色明确。

## Design Tokens

这些 token 是方向约束，实际实现可映射到项目现有主题色、drawable 或资源。

### Color

| Token | 用途 | 建议 |
| --- | --- | --- |
| `ng_background` | 页面背景 | 当前主题背景或暖色渐变背景 |
| `ng_surface` | 实色承载面 | 白色或接近白色 |
| `ng_surface_card` | 设置卡片承载面 | 半透明白或半透明暗色，用于透出背景图 |
| `ng_surface_panel` | 强承载面 | 比卡片更不透明，用于输入区、底部操作区和重要面板 |
| `ng_surface_soft` | 次级承载面 | 暖白、半透明白、轻微灰白 |
| `ng_primary` | 主强调 | 当前主题原始强调色；暖色、竹影、雾霭分别保留自己的视觉性格 |
| `ng_on_surface` | 主文本 | 接近黑色，不使用纯黑过重 |
| `ng_on_surface_variant` | 次级文本 | 中性灰 |
| `ng_outline` | 细边框 | 暖灰、低对比 |
| `ng_success` | 成功状态 | 绿色 |
| `ng_warning` | 警告状态 | 橙色 |
| `ng_error` | 错误状态 | 红色 |
| `ng_info` | 信息状态 | 蓝色或灰蓝 |

约束：

- AI、调试、设置页的强调色优先使用 `ng_primary`，不要默认变成 MD3 紫色，也不要自行派生更暗颜色替换主题强调色。
- 原始强调色用于小节标题、主要按钮、次按钮文字／描边和选中状态；主正文仍使用 `ng_on_surface`，避免大段强调色文字影响阅读。
- 状态色只表达状态，不参与大面积装饰。
- 背景允许跟随主题和渐变，但承载面必须保证可读性。
- 透明度统一通过 `ng_surface_card`、`ng_surface_panel`、`ng_icon_container` 等 token 调整，不在单个页面里散写 `#AAFFFFFF` 之类的临时颜色。

当前阶段直接以已调好的 NG 真实页面确定透明度和描边，不在独立 Catalog 中重新设计。
透明风格必须保留标题、摘要和操作的可读性；出现文字消失、列表项高度异常或背景割裂时，
视为组件接入失败，应撤回该页面接入，而不是继续叠加主题兜底。

### Shape

| 组件 | 建议圆角 |
| --- | --- |
| 页面承载面 | 24dp 或按现有首页面板 |
| 搜索框 | 胶囊形或大圆角 |
| 列表卡片 | 14dp-18dp |
| 输入框 | 12dp-16dp |
| tag / chip | 胶囊形 |
| 小按钮 | 10dp-14dp |
| 弹窗内容块 | 12dp-16dp |

约束：

- 不要混用太多圆角等级。
- 功能按钮不应比内容卡片更抢眼，除非是主操作。

### Spacing

优先使用这些间距：

- 页面水平边距：16dp 或 18dp。
- 卡片内边距：14dp-18dp。
- 列表项垂直间距：8dp-14dp。
- 标题和摘要间距：4dp-6dp。
- 表单字段间距：12dp-18dp。
- 底部操作区高度：64dp-82dp。
- 与当前表单内容直接相关的次操作按钮组应距最后一个表单组件约 12dp，并随表单一起滚动；不能仅靠 `layout_weight` 固定在屏幕底部制造大段无意义空白。只有跨页面状态持续可用的主操作栏才固定在底部。

约束：

- 设置页可以比阅读页密一些，但不能挤。
- 调试页允许更高信息密度，但必须有清晰分组。

### Typography

当前字号基线：

| 层级 | 基准字号 | 用途 |
| --- | --- | --- |
| 页面标题 | 20sp | 二级页面和详情页 TitleBar；沿用现有 TitleBar 规则 |
| 弹窗标题 | 24sp | 独立 Dialog 标题，不用于普通页面卡片 |
| 卡片／设置项标题 | 16sp | 功能入口、Provider、模型和设置项 |
| 抽屉紧凑标题 | 17sp | `NgLongListBottomSheet` 的 compact 标题 |
| body | 15sp | 表单内容和必要正文 |
| summary／label | 13sp | 摘要、字段标签、分组标题和辅助信息 |
| button | 14sp | 小型主次按钮；弹窗按钮按既有资源使用 17sp |
| tag | 12sp | 状态标签 |
| mono | 跟随对应正文层级 | URL、日志、代码、JSON 使用等宽字体 |

约束：

- 中文界面优先使用系统无衬线，不强行引入复杂字体。
- 日志、URL、代码内容可使用等宽字体。
- 不要在紧凑卡片里使用过大的标题字号。
- 标题最多一行；摘要优先一行省略，只有业务内容确实需要时才允许多行。
- 常规功能不要添加“该功能如何在内部调用”一类说明。仅在误操作风险、权限原因或结果不可逆时增加短说明。

字体、图标和图片尺寸必须先在组件级固定，再用于页面：

- 设置项标题、摘要、尾部值分别使用统一 Token，页面不能单独放大或缩小。
- 同类操作统一图标家族、视口和容器尺寸，不因来源图片不同而视觉忽大忽小。
- 按钮统一最小高度、文字层级和具名 Variant，不新增页面私有颜色或视觉布尔参数。
- 图片组件统一占位、错误态、裁切和圆角；业务页面只提供资源与内容描述。

### Iconography

Reading NG 不限定只能使用 Material Symbols Rounded。图标可以来自现有项目资源、Provider 官方 Logo、Iconify，现有集合都没有合适方案时再单独设计。

统一要求：

- 同一组件内使用相同的视觉尺寸、viewBox、留白和线宽；来源不同不能导致图标忽大忽小。
- 工具栏图标使用 48dp 点击区域；常规内容图标使用统一容器，当前设置入口基准为 36dp 容器和 7dp 内边距。
- 主题设置抽屉的恢复、分享、保存等头部操作统一使用 44／48dp 触控区、36dp 可见圆形承载面和 20dp 图标；承载色沿用听书播放器顶部按钮的 `#88FFFFF9` 半透明暖白，不使用 `surfaceContainer` 灰底。
- Provider Logo 保留品牌原色，不强制套主题 tint；通用操作图标跟随 `ng_on_surface` 或当前主题强调色。
- 同一页面中不同语义不能因为“暂时找不到图标”重复使用同一图标；先列为待替换项，再从 Iconify 查找或单独设计。
- 禁止使用字符、Emoji 或随意缩放的位图代替正式操作图标。
- 纯图标按钮必须提供 `contentDescription`；不能依靠隐藏文字占位实现居中。

## 页面模式

### 一级设置页

用于 `AI 设置` 这类入口页。

结构：

- 外层使用现有 `TitleBar`。
- 页面根布局不使用实色背景，让主题背景图片或渐变可以透出。
- 内容区使用 MD3 grouped list 风格：一个圆角分组承载多个行式设置项。
- 普通设置入口不使用独立卡片；卡片只用于 provider、prompt、skill、模型等二级列表内容。
- 可直接操作的开关使用右侧 `Switch`。
- 进入二级页面的入口使用右侧箭头。

适用：

- 提供商
- 提示词
- 技能
- 默认模型
- 自动应用开关

### 二级列表页

用于 provider、prompt、skill、模型等列表。

结构：

- 顶部搜索统一使用 `NgSearchBar`，需要搜索的列表不再自行组合图标与 EditText。
- 列表操作放在 TitleBar；只有一种独立新增操作时可直接执行，同时存在新增类型和查看选项时统一收进一个 `NgMenuPopup` 更多菜单，不在搜索框旁再放孤立按钮。
- 管理型列表默认突出可用内容；已禁用项可默认隐藏，并通过菜单中的可勾选“显示已禁用”临时展开。该状态只影响当前页面展示，不隐式改写偏好。
- 搜索在可见性过滤之后执行；隐藏禁用项时，搜索不能把禁用项重新带回列表。
- 支持拖拽排序的过滤列表必须把可见顺序合并回完整数据，保留隐藏项及其相对位置，禁止直接保存过滤后的 Adapter 数据。
- 列表项使用统一 `ListCard`。
- 左侧图标，中间标题和摘要，右侧状态或操作。
- tag 放在摘要下或右侧，避免挤压标题。

适用：

- 提供商列表
- 提示词列表
- AI 技能列表
- 规则候选列表

### 详情页

用于配置单个 provider、prompt、skill。

结构：

- 顶部标题区：图标 + 名称 + 主操作。
- 内容区按分组展示。
- 表单使用 Outlined 风格输入框。
- 两个同级分区优先使用底部 `NgFloatingTabBar`；Provider 试点采用 48dp 纯图标配置／模型切换，图标在各自等宽区域物理居中并提供无障碍描述。
- `Segmented` 仅保留给内容区内的小范围过滤，不与页面底部悬浮导航混用。
- 保存、恢复默认、测试连接等操作位置固定，不要每页变化。

适用：

- Provider 配置
- Prompt 编辑
- Skill 配置
- 模型参数配置

### 确认弹窗

用于 AI 净化候选、规则候选、批量操作确认。

结构：

- 标题简短明确，默认不加描述文字。
- 列表内容清晰分块。
- 底部固定操作按钮。
- 不显示用户看不懂的隐藏风险逻辑；需要用户判断时，优先展示可对比的内容变化。
- 如果有风险或校验失败，才用明确、可理解的文本解释。

思考深度等可直接理解的档位选择弹窗只展示标题、当前值、图标和选择刻度，不再按 AI 助理、净化、听书等入口累加“用于何处／不影响何处”的提示。模型不支持该能力时继续在进入弹窗前用短提示反馈，不把兼容性说明常驻在弹窗内。

适用：

- 段落净化确认
- 章节净化候选确认
- 整段删除确认
- 标题优化确认

### 通用弹窗外壳

用于逐步统一日志、代码查看、导入预览、确认和输入类弹窗。

结构：

- window 背景必须透明，由弹窗根布局绘制大圆角承载面。
- 根布局使用 `Ng.DialogRoot`，标题区使用 `Ng.DialogHeader` + `Ng.DialogTitle`。
- 内容区使用 `Ng.DialogBody`，需要分块时使用 `Ng.DialogSection`。
- 底部操作区使用 `Ng.DialogActionBar`，按钮使用 `Ng.DialogButton.Primary/Secondary`。
- DialogFragment 在 `onStart()` 中调用 `applyNgDialogWindow()` 统一宽度、dim 和透明背景；长内容弹窗可用 `ngDialogMaxHeight()` 限制高度。

推荐资源：

- Kotlin helper：`io.legado.app.ui.widget.dialog.NgDialog`
- Root：`@style/Ng.DialogRoot`
- Header：`@style/Ng.DialogHeader`
- Title：`@style/Ng.DialogTitle`
- Body：`@style/Ng.DialogBody`
- Section：`@style/Ng.DialogSection`
- Action bar：`@style/Ng.DialogActionBar`
- Buttons：`@style/Ng.DialogButton.Primary`、`@style/Ng.DialogButton.Secondary`

迁移顺序：

1. 日志和代码查看：`CodeDialog`、`AppLogDialog`、`NetworkLogDialog`、`CrashLogsDialog`。
2. 文本查看：`TextDialog` 及 HTML/崩溃日志/帮助类内容。
3. 导入预览和编辑类弹窗。
4. 普通选择/输入 `AlertDialog`。
5. 复杂业务弹窗，例如登录、TTS 编辑、规则编辑。

约束：

- 每批只迁移少量弹窗，迁移后由人工验收视觉和交互。
- 公共外壳只处理视觉框架，不改变弹窗的数据、保存、删除、复制等业务行为。
- 日志、代码、JSON 内容可以保持高信息密度，但不能贴边、不能使用旧 Toolbar 实色顶栏。
- 日志、代码、JSON 长内容弹窗保留纵向滚动条；日志类窗口右上角可提供 `导出内容` 文字按钮，按钮颜色跟随主题强调色，导出应保留完整文本。

## 通用组件

第一版 XML 资源已经落盘，后续新增或改造页面优先使用这些 `ng_*` 资源，不再新增功能私有的 `bg_ai_*`、`bg_xxx_card` 等重复资源，除非确有业务差异。

资源位置：

- Token：`app/src/main/res/values/ng_ui_tokens.xml`
- 夜间 Token：`app/src/main/res/values-night/ng_ui_tokens.xml`
- Style：`app/src/main/res/values/ng_ui_styles.xml`
- Drawable：`app/src/main/res/drawable/ng_bg_*.xml`

### SettingsGroup / SettingsItem

用于一级设置菜单。

结构：

- 分组标题。
- 一个低透明圆角分组容器。
- 每行左侧图标，中间标题 + 摘要，右侧箭头、Switch 或当前值。
- 每行使用现有半透明 18dp 圆角设置项，组内以 6dp 间距分开；分组顶部和底部各保留 8dp。

推荐资源：

- 分组标题：`@style/Ng.SettingsSectionLabel`
- 分组容器：`@style/Ng.SettingsGroup`
- View 设置项：`io.legado.app.ui.design.components.view.NgSettingsItemView`
- 旧布局兼容样式：`@style/Ng.SettingsItem`
- 图标：`@style/Ng.SettingsIconImage`
- 标题：`@style/Ng.SettingsTitle`
- 摘要：`@style/Ng.SettingsSummary`

当前真实规格：

- 设置项最小高度 64dp，内边距为 start 16dp、top/bottom 10dp、end 14dp。
- 图标容器 36dp、图标内边距 7dp、圆角 14dp；图标使用当前主题原始强调色。
- 标题 16sp，摘要 13sp，间距 4dp，均单行省略。
- 尾部只使用 `Chevron`、`Switch`、`Value`、`Custom` 具名类型；业务页面不再手写标题／摘要／尾部结构。

约束：

- 一级设置菜单不要使用 `EntryCard` 堆叠。
- 设置项高度应稳定，右侧控件对齐，避免像内容卡片一样漂浮。
- 分组和设置项共同形成现有透明层级，业务页面不能再散写透明度、圆角或间距。
- Switch 行支持整行切换和直接点击 Switch，两条路径必须只产生一次状态变更。
- AI 设置首页与朗读设置菜单均已通过 View 试点：已覆盖 Chevron、Switch、静态／动态摘要、整行点击和导航。后续同类 View 页面优先使用 `NgSettingsItemView`。
- AI 设置是第一张完整 Compose 迁移页：页面 XML 只承载一个透明 `ComposeView`，业务状态仍由 Fragment 提供。Compose `NgSettingsItem` 必须使用与 View 相同的颜色资源和几何；首轮 Switch 可在组件内部桥接 `SwitchCompat`，业务页面不得自行混用两套 Switch。
- AI 设置与朗读设置 Compose 页均已人工验收，成为首批 Settings Pattern 基线。页面状态由 Fragment 汇总成不可变 ScreenState，Compose 只负责渲染和事件回传，避免把数据库、偏好或导航逻辑塞入组件。

### GlassPanel

用于带主题背景图的设置页和工具页承载块。

推荐资源：

- View：`io.legado.app.ui.widget.NgGlassLayout`
- 样式：`@style/Ng.SettingsGroup`

视觉：

- 背景从页面根背景采样，降采样后模糊，形成毛玻璃底。
- 上层叠加半透明 tint，保证文字可读。
- 边缘使用半透明描边，不按具体背景图写死暖色、绿色或其它单一配色。

约束：

- 优先用于一级设置页 grouped list。
- 后续设置页迁移时复用 `NgGlassLayout`，不要再为每个页面单独写固定色卡片。
- 当前组件适合背景图或纯色背景；如果背后是复杂动态列表，需要先评估缓存刷新策略。

### EntryCard

用于少量强调入口或二级列表候选，不再作为一级设置菜单默认形态。

结构：

- 左侧圆形图标。
- 中间标题 + 一行摘要。
- 右侧箭头或 Switch。

推荐资源：

- 卡片：`@style/Ng.EntryCard`
- 圆形图标：`@style/Ng.IconCircle`
- 标题：`@style/Ng.CardTitle`
- 摘要：`@style/Ng.Summary`

约束：

- 标题不超过一行。
- 摘要用于状态，不放长说明。

### ListCard

用于二级列表项。

结构：

- 左侧图标。
- 中间标题、摘要、可选 tag 行。
- 右侧更多、拖拽、状态或箭头。

推荐资源：

- 卡片：`@style/Ng.ListCard`
- 标题：`@style/Ng.CardTitle`
- 摘要：`@style/Ng.Summary`
- 状态：`@style/Ng.Tag.*`

约束：

- 禁用项可以弱化，但不要使用大面积红色背景，除非是错误状态。
- 当前使用项可用红色或 info tag 标识。

### OutlinedField

用于配置输入。

结构：

- 标签。
- 圆角边框输入框。
- 可选辅助说明或错误说明。

推荐资源：

- 标签：`@style/Ng.Label`
- 输入框：`@style/Ng.OutlinedField`

约束：

- 边框使用低对比暖灰。
- 聚焦色使用 `ng_primary`。
- API Key 等敏感字段必须有显示/隐藏能力或保持密码输入。

### Tag

用于状态。

类型：

- 成功：启用、成功。
- 警告：禁用、待确认。
- 信息：模型数量、当前使用、耗时。
- 错误：失败、不可用。

推荐资源：

- `@style/Ng.Tag.Info`
- `@style/Ng.Tag.Success`
- `@style/Ng.Tag.Warning`
- `@style/Ng.Tag.Error`
- `@style/Ng.Tag.Neutral`

约束：

- tag 是辅助信息，不应成为主视觉。
- 同一卡片内 tag 不宜超过 3 个。

### Button

按钮只表达操作层级，不允许业务页面按背景临时决定一套新颜色。

已确认 Variant：

- Primary：主题强调色实底，前景使用可读的 `onPrimary`；一个操作组最多一个主按钮。
- Secondary：高不透明白底、1dp 主题强调色描边、主题强调色文字；View 使用 `NgSecondaryButtonView`。
- Danger：只用于明确的删除或不可逆操作；底部操作栏沿用 Secondary 承载面并改用错误色描边／图文，不能把普通取消、禁用做成危险按钮。
- Disabled：保留原轮廓和尺寸，统一降低表面、描边和文字强度。

尺寸基线：

- 普通小按钮 36dp 高、14sp、最小宽度 76dp、12dp 圆角。
- 弹窗按钮使用既有 `Ng.DialogButton` 的 42／48dp 规格，不与页面小按钮混用。
- 底部操作栏使用 View `BookInfoActionButton`／Compose `NgActionBarButton` 的 42dp 规格：20dp 图标、8dp 图文间距、14sp、12dp 圆角。
- 同一排按钮等宽或按内容稳定分配，不能因文案长度产生明显高低和边界跳变。

约束：

- 背景图页面上的非主操作不能只画强调色文字；必须使用可读的白色／高不透明承载面。
- 不增加 `useWhiteBackground`、`darkText` 一类调用方视觉布尔值，页面只选择具名 Variant。

### ActionBar

用于详情页底部或弹窗底部。

结构：

- 次要操作在左或靠前。
- 主操作在右。
- 危险操作要明确文案。

推荐资源：

- 操作区：`@style/Ng.ActionBar`
- 文本按钮：`@style/Ng.ActionText`
- 小按钮：`@style/Ng.SmallButton.Primary` / `@style/Ng.SmallButton.Secondary`
- Compose 图文操作：`NgActionBarButton`

约束：

- 不要把保存、恢复默认、测试连接随意分散。
- 同一类页面操作位置保持一致。

### SearchBar

用于页面顶部搜索和过滤入口。

推荐资源：

- View：`io.legado.app.ui.design.components.view.NgSearchBar`
- 容器资源：`@style/Ng.SearchPill`、`@drawable/ng_bg_search_pill`

冻结规格：

- 高度 44dp，搜索图标 22dp，输入文字 15sp。
- 左侧 16dp 留白，图标与文字间距 10dp；右侧清除按钮使用 38dp 点击区。
- 输入后显示清除按钮；点击清除后保留焦点；IME 使用搜索动作。

约束：

- 只承载搜索图标、输入文本和必要清除按钮。
- 不要在搜索框内放复杂操作。
- 页面和 BottomSheet 使用同一组件，只传 hint 和查询监听，不自行复制搜索布局。

### ActionPopup

用于标题栏新增类型、更多操作和短选择菜单。

推荐实现：

- Menu 绑定：`NgMenuPopup`
- Popup：`NgActionPopup`

冻结规格：

- 18dp 圆角、44dp 最小行高、8dp elevation。
- 宽度按最长文案自适应，限制在 152dp 到 280dp，并保留屏幕边缘 8dp 安全距离。
- 菜单项可包含图标、标题、选中状态和分组分隔线；选中状态统一使用右侧实心圆环，不使用对勾。

约束：

- 不再使用系统方形 `PopupMenu` 表现 NG 标题栏菜单。
- 多种新增方式或新增与查看选项并存时，统一收进标题栏更多菜单；搜索框旁不重复放新增按钮。
- 菜单只放短操作，长表单、复杂说明或可滚动选择使用 Dialog／BottomSheet。

### FloatingTabBar / Segmented

`NgFloatingTabBar` 用于详情页底部切换两个或三个同级分区；`Segmented` 只用于内容区内部的小范围互斥过滤。

推荐资源：

- View：`io.legado.app.ui.design.components.view.NgFloatingTabBar`
- 容器：`@style/Ng.SegmentedContainer`
- 普通项：`@style/Ng.SegmentedItem`
- 选中项：`@style/Ng.SegmentedItem.Selected`

Provider 试点冻结规格：

- 悬浮栏高度 48dp、内边距 3dp；每项等宽占满可用空间。
- 纯图标项使用独立 24dp `ImageView`，由父容器 `Gravity.CENTER` 真正居中。
- 选中项使用主题 selected container，图标使用主题原始强调色；未选中项使用主正文色。
- 可见标签可省略，但必须保留 `contentDescription`，不能把空 TextView 当图标定位容器。

约束：

- `FloatingTabBar` 只用于 2 到 3 个页面内同级分区，不替代 App 级导航。
- `Segmented` 只用于 2 到 4 个内容过滤项，不与底部悬浮栏叠加表达同一状态。
- 业务页面只提供 item、图标、语义和选中索引，不能增加页面私有偏移修正。

### LongListBottomSheet

用于模型、发音人、引擎等需要搜索的长列表选择。

推荐实现：`io.legado.app.ui.widget.dialog.NgLongListBottomSheet`。

冻结外壳：

- 与发音人抽屉使用同一背景策略：主题背景图叠加浅色可读遮罩；无背景图时使用 `ng_surface_soft`。
- 顶部圆角 28dp，默认展开到屏幕高度约 88%，由 BottomSheet 处理返回、拖拽和安全区。
- 搜索复用 44dp `NgSearchBar`；紧凑标题区为 44dp、17sp，普通标题区为 54dp、20sp。
- 只有搜索、没有其它过滤条件的简单长列表，可以在 compact 标题栏使用 40dp 搜索图标，点击后展开搜索框。
- 简单长列表展开搜索时也使用透明筛选承载层和无描边白色搜索卡片；推荐由 `NgLongListBottomSheet.useCompactFilterSearchPanel()` 统一处理容器显隐，页面不得另画一套描边搜索框。
- 模型、发音人等同时存在业务过滤条件的长列表，使用一个筛选图标控制折叠面板；面板内先放统一搜索框，再放厂商、语言、性别等过滤项，不能把搜索和过滤拆成两个入口。
- 筛选面板默认收起，不让大搜索框长期顶在抽屉首屏；展开后仍使用统一 40／44dp NG 搜索外观。
- 过滤项使用可换行的 NG Chip。单一厂商或单一选项不显示对应过滤行；多个厂商允许多选，未选择表示全部，长名称省略且不能把所有选项压进一行。
- 过滤分组名称确有区分作用时位于独立一行，禁止用固定宽度左侧标签持续挤压每一行 Chip。模型筛选只有厂商一个过滤维度时省略重复的“提供商”标题，使用一行三项的等宽 Tag；Logo 统一为 16dp，长名称单行省略。展开过滤面板或改变过滤条件后，长列表回到顶部，不能截断首个分组或首张卡片。
- 发音人文本搜索只匹配用户可见名称。语言、性别使用专用过滤项，风格与标签只负责展示和选音语义；引擎名、内部 ID、语言、性别、风格和标签均不参与名称搜索。
- 页面内的发音人管理列表也复用同一 `NgSearchBar` 和名称查询规则；无匹配与首次获取只显示短状态，不为搜索、过滤、刷新和批量操作分别堆叠说明文字。
- 筛选面板遵循朗读模式同源的两层承载：外层使用低透明 `ng_settings_group`，内层搜索框和可点击 Tag 使用高不透明 `ng_surface_card`，均不绘制描边。选中项依靠 `selectedContainer` 与强调色文字表达，禁止外层、搜索、Tag 同时叠加框线。
- 发音人语言／性别过滤项的未选中态同样使用无描边 `ng_surface_card` 白色承载面；选中态才使用对应语义色，灰色底不得被误用成普通可选项背景。
- 该层级已经由 AI 模型选择真机验收，发音人选择和 TTS 引擎选择使用同一基线；发音人保留语言／性别过滤，引擎只有搜索时不额外制造过滤项。

约束：

- 统一的是抽屉背景、圆角、标题、搜索和行为，不强迫模型卡、发音人卡使用同一种内容结构。
- 抽屉背景与内容卡必须形成可见层级，禁止透明／白色抽屉上再放无法分辨边界的白色卡片。
- 列表内容由业务提供，外壳不接管选择数据、试听或模型能力标签。
- 引擎、发音人等需要解析配置或查询数据库的长列表，必须先显示抽屉外壳和加载状态，再在后台生成一次展示快照；搜索、筛选和列表重绘只能复用该快照，禁止在主线程或每次查询变化时重复读取 Store。
- 管理页下拉刷新期间，序列化、完整 Store 解析、网络请求和数据库替换必须在 IO；主线程只更新加载状态与最终可见列表，不能为了同步隐藏页面而重载全量数据。
- 发音人试听状态必须在真实播放结束后复位。自定义播放链除完成事件外还要有媒体时间线末尾兜底，停止、切换和页面销毁必须同步取消动画与检查任务。

## AI 页面约定

AI 相关页面必须遵守：

- 一级菜单是 `AI 设置`。
- 二级能力包括 `提供商`、`提示词`，后续可扩展 `技能`、`默认模型`。
- Provider、Prompt、Skill 都使用二级列表 + 详情页模式。
- AI 功能 UI 参考 MD3 结构，但颜色使用 Reading NG token。
- 不要让单个功能把 AI 设置页变成孤立表单。
- Provider／模型等长列表统一使用 `NgSearchBar`；新增 Provider 的不同兼容类型统一放在标题栏圆角菜单。
- Provider 详情的配置／模型使用 48dp `NgFloatingTabBar`，不再使用过高的文字 Tab。
- 模型选择统一使用 `NgLongListBottomSheet` 紧凑外壳；点击标题栏筛选图标后同时显示搜索框和厂商过滤 Chip。单厂商时隐藏厂商行，多厂商使用 Flexbox 换行；模型卡片保留 Provider、能力标签等业务语义。
- 用户不需要了解 `use_skill`、内部目录暴露方式或协议实现等信息；这类实现细节不作为页面说明。

后续新增 AI 功能时，应先判断它属于：

- Provider：模型接入和连接能力。
- Prompt：任务说明和模型行为偏好。
- Skill：端到端功能编排。
- Runtime：阅读页中的即时操作入口。

## 调试页面约定

调试类页面应保持：

- 搜索条或过滤条在顶部。
- 主信息使用行式列表或阶段卡片。
- 状态信息使用 tag。
- 详细日志、响应体、JSON、HTML 使用 CodeView 或等宽展示。
- 大响应内容必须截断或提供导出，不直接撑爆弹窗。

## 滑动轨道

- Compose 统一使用 `NgSlider`，不直接暴露 Material 3 `Slider` 的默认造型。
- 连续型以听书播放进度条为视觉基线：6dp 圆角轨道、淡强调色未选区、强调色已选区、带表面色外圈的圆形滑块。
- 离散型使用 10dp 圆角轨道，保留同款圆形滑块，并增加圆点刻度和档位吸附；不改成块状轨道或竖直把手。
- 首尾刻度的圆心仍是可选范围端点，但可见轨道必须在两端各多延伸一个圆角半径，确保端点圆点完整收在胶囊轨道内；延伸不得改变触控映射、档位吸附或滑块中心位置。
- 背景虚化 0～25 展示全部整数圆点刻度并按整数吸附；字体大小等少量固定档位沿用同一离散变体。
- 拖动中的预览不得反复写偏好或重建 Activity；抽屉／编辑页先维护草稿，明确保存后再提交业务状态。

## 不推荐做法

避免：

- 每个功能单独定义一套卡片、颜色、按钮。
- 大面积使用 MD3 紫色或动态色覆盖阅读 NG 主题。
- 在同一页混用 PreferenceScreen 风格、RikkaHub 风格和自绘风格。
- 为了“现代感”增加不必要阴影、渐变、装饰图形。
- 卡片内塞过多解释文字。
- 弹窗使用宽松大字号导致正文候选错位或遮挡按钮。

## 验收标准

新增 UI 功能至少检查：

- 是否使用本文档已有页面模式。
- 是否复用现有 Reading NG 色彩和承载面风格。
- 是否与书架首页、搜索页、调试页冲突。
- 手机竖屏下文字是否溢出或挤压。
- 弹窗底部按钮是否遮挡内容。
- 状态信息是否一眼可懂。
- 如果是 AI 功能，是否区分 Provider、Prompt、Skill、Runtime。
- 页面标题、小节、主次按钮和选中态是否使用当前主题原始强调色，而非固定色或另行派生暗色。
- 图标是否在统一点击区和容器内视觉居中，且没有用重复图标表达不同语义。
- 搜索、标题栏菜单、底部切换和长列表抽屉是否复用已验收公共组件。
- 页面是否删除了用户无需理解的内部实现说明。

## 后续建议

短期：

- 为已验收的管理列表卡片、状态 Tag、搜索栏和详情 Dock 补齐 Compose 对等实现；只迁移实现技术，不改变当前视觉。
- 单独抽取列表空／加载／错误状态，避免与卡片视觉同时扩张。
- 按页面孤岛并行迁移 AI Provider、TTS 引擎和默认发音人，公共组件和巨型 Fragment 宿主由主集成者串行维护。
- 随后从 Provider／TTS 详情抽取表单字段、Switch 行和内容流操作区；继续以真实页面验收，不从 Catalog 外观直接冻结规则。

中期：

- 在真实页面通过后继续抽出 View/XML 版通用组件：`GlassPanel`、`ManagementListCard`、`OutlinedField`、`StatusTag`、`FormActions` 和统一状态页。
- 把 AI、MCP、网络日志、书源调试等开发功能统一到同一工具型视觉语言。
- 组件 API 在多张真实页面稳定后再提取到 `:modules:design-system`，避免模块化固化尚未验收的接口。

长期：

- 继续使用同一套 token 和页面模式渐进迁移 Compose，不把视觉方向绑定到当前 XML 或 Compose 实现。
- Theme V2、背景 Host 和个性化主题包排在组件与页面模式稳定之后，不能与当前组件抽取并行扩张。
