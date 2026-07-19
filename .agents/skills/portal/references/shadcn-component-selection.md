# web/portal shadcn/ui 组件选择指南

本文件面向在 `web/portal` 中设计或实现界面的 Agent。它用于回答“这个交互应该复用哪个组件”，不是 shadcn/ui 全量组件目录。

## 1. 事实来源与优先级

按以下顺序判断，不要依赖记忆中的组件数量、发布日期或旧版 API：

1. `web/portal/src/components/ui/`：当前已安装组件，是本地事实。
2. `web/portal/components.json`：安装配置与别名，是生成代码的事实。
3. `.agents/skills/portal/SKILL.md`：项目约束与禁用项。
4. shadcn/ui 官方文档：仅在选择或新增组件时查询最新用法。

当前项目使用 `new-york` 风格、Radix UI、Lucide 图标、Tailwind CSS v4。不要把 Base UI 示例或其他项目生成的组件直接复制进来。

## 2. 选择流程

1. 先查 `src/components/<resource>/`、`src/components/layout/` 和 `src/components/form/` 是否已有业务组件。
2. 再查 `src/components/ui/` 是否已有合适的 shadcn 原语。
3. 只有现有组件不能表达交互时，才查官方 registry 并新增组件。
4. 优先组合已有原语，不为一次性视觉效果引入新依赖。
5. 安装前核对 Radix 兼容性；安装用 `pnpm dlx shadcn@latest add <name>`，升级用 `--diff`，禁止 `--overwrite`。
6. 安装后阅读生成的 `.tsx`，核对 `@/` 别名、依赖、语义色、键盘交互和无障碍属性。

## 3. 常见需求映射

| 需求               | 优先组件                     | 选择提示                                             |
| ------------------ | ---------------------------- | ---------------------------------------------------- |
| 普通操作           | `Button`                     | 破坏性操作用 `destructive`；图标按钮必须有可访问名称 |
| 必须确认的危险操作 | `AlertDialog`                | 删除、撤销、退出等不可轻易恢复的动作                 |
| 表单或详情弹层     | `Dialog`                     | 必须有 Title 和 Description                          |
| 桌面侧边面板       | `Sheet`                      | 筛选、设置、辅助详情                                 |
| 移动端底部面板     | `Drawer`                     | 仅在移动交互确实需要时新增                           |
| 少量可交互浮层     | `Popover`                    | 点击打开，可承载小表单或操作                         |
| 悬停预览           | `HoverCard`                  | 仅预览补充信息，不承载关键操作                       |
| 简短解释           | `Tooltip`                    | 不放关键内容，必须支持键盘聚焦                       |
| 基础数据展示       | `Table`                      | 仅静态或简单表格                                     |
| 排序、筛选、分页   | 项目 Data Table 组合         | 复用 `useTableUrlState` 与现有表格布局，不自行拼装   |
| 固定选项下拉       | `Select`                     | 选项较少且无需搜索                                   |
| 原生移动体验       | `NativeSelect`               | 只有浏览器原生行为更合适时使用                       |
| 大量可搜索选项     | `Combobox`                   | 通常由 Command 与 Popover 组合，先查现有实现         |
| 单字段输入         | `Input` / `Textarea`         | 使用项目 `FormFieldShell` 或现有表单封装             |
| 日期输入           | `Calendar` 组合              | 先查现有日期组件，不重复组装                         |
| 页面级提示         | `Alert`                      | 信息持续可见，错误态用 destructive 语义              |
| 短暂操作反馈       | `Sonner`                     | 统一 `toast`；字段校验错误不要用 Toast               |
| 加载占位           | `Skeleton`                   | 已知布局的加载态；不要自制闪烁块                     |
| 空数据             | 项目 `AsyncState` 或 `Empty` | 先复用现有异步状态组件                               |
| 状态标签           | `Badge`                      | 状态到 variant 的映射集中在资源 `lib` 中             |
| 内容分区           | `Tabs`                       | 只用于同层内容切换，不替代路由导航                   |
| 折叠多个区块       | `Accordion`                  | 多项可展开内容                                       |
| 折叠单一区块       | `Collapsible`                | 单个受控区域                                         |
| 后台导航           | `Sidebar`                    | 复用现有 `AppSidebar`，不另建导航体系                |

## 4. 容易混淆的选择

- `Table` 与 Data Table：前者负责基础展示；后者还承担 URL 同步的筛选、排序、分页和列控制。
- `Dialog` 与 `AlertDialog`：普通内容或编辑用 Dialog；必须明确确认的高风险操作用 AlertDialog。
- `Popover`、`HoverCard` 与 `Tooltip`：分别对应点击交互、悬停预览、简短说明。
- `Sheet` 与 `Drawer`：Portal 桌面端优先 Sheet；Drawer 只服务明确的移动端底部交互。
- `Select`、`NativeSelect` 与 `Combobox`：分别适合固定选项、原生行为、可搜索的大选项集。
- `Alert` 与 `Sonner`：持续状态放页面内，瞬时反馈才用 Toast。

## 5. AI 对话组件边界

shadcn/ui 官方可能提供 `Attachment`、`Bubble`、`Message`、`Message Scroller`、`Marker` 等对话原语，但它们不自动进入本项目的可用组件集。

`web/portal` 当前是运营后台壳，不直接搭建 AI Chat。遇到 Incident Agent 工作台或流式诊断界面任务时，先遵守 `aegisops` 的阶段与前端边界，确认目标应用和设计文档，再决定是否引入这些组件；不要仅因官方存在就安装。

## 6. 交付检查

- 使用语义色 token，兼容暗色模式。
- Dialog、Sheet、Drawer 具备 Title 和 Description。
- 交互可由键盘完成，图标按钮具有 `aria-label` 或可见文本。
- 没有直接修改已有 `src/components/ui/` 文件。
- 新增组件后运行 portal 的 lint、typecheck、test、knip 和 build。
