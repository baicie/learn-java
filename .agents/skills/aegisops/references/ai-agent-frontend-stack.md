---
title: 前端 AI 工作台组件栈 (shadcn/ui + AI Elements + prompt-kit)
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - .agents/skills/aegisops/SKILL.md §4.1
  - .agents/skills/aegisops/references/frontend-conventions.md
  - .agents/skill-library/vercel-composition-patterns/AGENTS.md
  - .agents/skill-library/shadcn/SKILL.md
---

# 前端 AI 工作台组件栈

AegisOps 不是一个通用 AI Chat 工具, 也不是通用大屏。它是一个 "Incident 工作台 +
Agent 诊断对话 + 证据链"。前端因此要分三层选组件:

```txt
1. 后台壳子:  shadcn/ui  (dashboard / sidebar / card / table / tabs / dialog / sheet)
2. AI Chat 基础: shadcn/ui (message / bubble / message-scroller / attachment / marker)
3. Agent 工作台: AI Elements 或 prompt-kit (agent / tool / confirmation / task / plan
                                     terminal / file-tree / stack-trace / test-results
                                     reasoning / chain-of-thought / source / steps)
```

## 1. 三层组件一览

### 1.1 shadcn/ui (基础)

官方注册表, 已稳定支持 AI Chat 基础组件:

```txt
Attachment        # chat composer / thread / upload list 的文件与图片附件 (idle/uploading/processing/error/done)
Bubble            # 单条消息气泡
Marker            # 标记状态 (如 "agent 正在调用工具")
Message           # 单条消息, 支持 reasoning steps / tool calls / assistant
Message Scroller  # 流式对话的滚动容器, 处理 stream / 跳转 / 滚动位置
```

命令 (与项目 packageManager 一致, 使用 pnpm dlx):

```bash
pnpm dlx shadcn@latest add message
pnpm dlx shadcn@latest add bubble
pnpm dlx shadcn@latest add message-scroller
pnpm dlx shadcn@latest add attachment
pnpm dlx shadcn@latest add marker
```

安装后必读官方文档, 确认 base 是 base-ui (本项目 `components.json` 的 `base` = base):

```bash
pnpm dlx shadcn@latest docs message
```

⚠️ 不要把 radix 示例代码 (例如 `asChild`) 直接抄到本项目, 必须改成 `render={<a/>}` 或 `render={<Button/>}`。

### 1.2 AI Elements (Vercel) — 优先推荐

基于 shadcn/ui 的 Agent 组件库, 直接对应本项目 "Agent 工作台" 需求:

```txt
Conversation / Message / Prompt Input
Reasoning / Chain of Thought / Sources
Tool / Confirmation / Agent / Artifact
Task / Plan / Queue
Terminal / File Tree / Stack Trace / Test Results
Web Preview / Workflow Canvas
```

最相关的组件:

| 组件                           | 用在 AegisOps 哪                                                                      |
| ------------------------------ | ------------------------------------------------------------------------------------- |
| `Agent`                        | 显示 AI Agent 配置 (model / instructions / tools / output schema)                     |
| `Tool`                         | 显示工具调用详情 (pending / running / completed / error / denied / awaiting approval) |
| `Confirmation`                 | Runbook 执行前的审批卡片, 关联 automation:approve 权限                                |
| `Task` / `Plan`                | Incident 处理步骤与建议动作                                                           |
| `Terminal`                     | 执行流式日志 (替代自造 ANSI viewer)                                                   |
| `File Tree`                    | Ansible Playbook 目录浏览                                                             |
| `Stack Trace` / `Test Results` | 错误诊断面板                                                                          |

安装:

```bash
# 注意: ai-elements 是第三方 registry, 必须先确认来源并写入 SKILL/AGENTS
npx ai-elements@latest add agent
npx ai-elements@latest add tool
npx ai-elements@latest add confirmation
npx ai-elements@latest add task
npx ai-elements@latest add plan
npx ai-elements@latest add terminal
npx ai-elements@latest add file-tree
npx ai-elements@latest add stack-trace
npx ai-elements@latest add test-results
```

合规要求:

```txt
1. 在 PR 描述里说明 registry 来源 (ai-sdk.dev/elements)
2. 安装后必须:
   - 读 .tsx 文件, 替换 import 路径为本项目 @/components/ui/@/lib/@/hooks 别名
   - 检查图标是否为 lucide-react (项目 iconLibrary = "lucide")
   - 检查 base-ui vs radix, 用本项目的写法替换
3. 不允许单独引入 @ai-sdk/* 与 AI Elements 不匹配的运行时
4. 不允许引入 prompt-ui / shadcn-chat-* 等其它 AI Chat 库与 AI Elements 并存
```

### 1.3 prompt-kit (备选 / 部分场景)

prompt-kit 是另一套 AI 组件库。当 AI Elements 没有合适原语, 或与 shadcn 组件冲突时使用:

```txt
Prompt Input / Prompt Suggestion
Chat Container
Reasoning / Chain of Thought / Thinking Bar
Steps / System Message
Source / Tool / Code Block / File Upload
Markdown
```

```bash
npx prompt-kit@latest add prompt-input
npx prompt-kit@latest add reasoning
npx prompt-kit@latest add steps
npx prompt-kit@latest add source
```

### 1.4 不引入

```txt
- 自己手写 AI Chat 容器
- 引入 langchain-ui / assistant-ui (与 aiops-agent 不耦合)
- 引入 antd / element-plus / mantine (已确定 shadcn 路线)
- 引入完整 low-code 表单引擎 (aiops-work-record 第一版明确禁止)
```

## 2. Incident 详情页骨架 (强制)

SKILL §4.1 页面优先级第 7 项 "Incident 详情" 必须按下面三栏组织:

```txt
┌─────────────────────────────────────────────────────────────────────┐
│ PageHeader: <Incident 标题>  <StatusBadge>  <SeverityBadge>         │
│ 顶部 Tabs: 概览 / 时间线 / AI 诊断 / Runbook / 自动化日志 / 复盘  │
├──────────────────────┬───────────────────────────┬───────────────────┤
│ 左侧 (sticky)        │ 中间 (主区)                │ 右侧 (drawer)     │
│                      │                           │                   │
│ 事件信息              │ Agent 诊断对话             │ 证据链            │
│ 关联资产              │  ├ Conversation /         │  ├ 指标卡片        │
│ 关联告警              │  │   MessageScroller      │  ├ 日志片段        │
│ 状态机 Stepper        │  ├ Message (含 Reasoning) │  ├ 变更记录        │
│ 严重度 / 影响范围     │  ├ Tool                   │  └ RCA 报告        │
│                      │  └ Confirmation           │                   │
└──────────────────────┴───────────────────────────┴───────────────────┘
```

组件对应:

```txt
左侧:   shadcn Card + Field + StatusBadge + 自定义 IncidentSidebar
中间:   AI Elements Conversation + Message + MessageScroller + Tool + Confirmation
        (或 prompt-kit Chat Container + Message)
右侧:   shadcn Card + Tabs + 自定义 EvidenceDrawer
```

关键约束:

```txt
- Conversation 容器用 useEffectEvent / ref 接管 SSE 流, 避免每次流事件都重渲染整棵子树
- Confirmation 必须是显式卡片, 不能用普通 Dialog 替代 (automation:approve 是审计点)
- Tool 组件必须映射到 ExecutionJob 的 status:
    pending -> running -> success / failed / denied / awaiting_approval
- 证据链 Tab 切换用 deferred value, 避免 AI 诊断 SSE 影响切换体验
```

## 3. AI 诊断面板 (独立页面, IncidentDetail 内嵌也可)

组件组合:

```txt
- PageHeader: "AI 诊断" + 触发按钮 "Diagnose" (后端 POST /api/incidents/{id}/diagnose)
- AI Elements Conversation + Message
  - Prompt Input: 复跑 / 增加上下文
  - Reasoning: AI 思考步骤 (折叠默认收起)
  - Tool: 每次工具调用的入参与输出
  - Source: 引用的 Runbook / 历史 Incident
- Confirmation Card: 当 AI 推荐 Runbook 时, 显式 [批准执行] [修改参数] [拒绝]
- 底部: 最近一次 DiagnosisResult 的 JSON 预览 (Tabs: Raw / 表格 / 证据链)
```

流式实现:

```txt
- SSE (EventSource) 走 web/console/src/lib/sse.ts
- 不要把 SSE 直接写在组件里
- Mutation: useSWRMutation trigger diagnose, 成功后 invalidate diagnosis 列表
- 流式片段: useReducer 管理 message list, 仅追加, 不要每条都 setState 全列表
- 失败: 显示 <Alert variant="destructive">, 不允许 alert() 或自造红条
```

## 4. 安装与升级流程

```bash
# 1. 查已有组件
ls web/console/src/components/ui

# 2. 在 shadcn 注册表搜
pnpm dlx shadcn@latest search @shadcn -q "agent"
pnpm dlx shadcn@latest search @shadcn -q "message"

# 3. 装基础
pnpm dlx shadcn@latest add message bubble message-scroller attachment marker

# 4. 装 AI Elements (注意是第三方 registry, 需在 PR 声明)
npx ai-elements@latest add agent tool confirmation task terminal file-tree

# 5. 升级时优先 --diff
pnpm dlx shadcn@latest add message --diff web/console/src/components/ui/message.tsx

# 6. 校验
pnpm run lint
pnpm run typecheck
pnpm run build
```

## 5. 与现有规范的关系

```txt
- 主题 / 颜色 / 间距:  references/frontend-conventions.md (优先级最高)
- 组件组合 (避免 boolean prop):  .agents/skill-library/vercel-composition-patterns/AGENTS.md
- React 性能:  .agents/skill-library/vercel-react-best-practices/AGENTS.md
- shadcn 注册表使用流程:  .agents/skill-library/shadcn/SKILL.md
- 后端 AI 接口契约:  docs/ai-agent-design.md + SKILL.md §10
```

## 6. 决策记录

```txt
2026-07-07: 选择 AI Elements 为主, prompt-kit 为备选; 不混用其它 AI Chat 库
2026-07-07: shadcn 基础组件优先; AI Elements 安装必须 PR 声明来源
2026-07-07: Incident 详情页强制三栏布局, 不允许单页堆所有内容
```
