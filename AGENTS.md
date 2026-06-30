# AGENTS.md

本文件是 AegisOps / FaultLens 项目的**工程纪律与 Agent 行为规范**配套文件。

```text
- 产品定义、架构原则、技术栈、Phase 路线图、领域模型 → 以 .agents/skills/aegisops/SKILL.md 为准
- 本文件不重复产品定义，仅承担 SKILL.md 未覆盖的「工程执行纪律」
- SKILL.md 章节引用统一写作「SKILL.md §X」；AGENTS.md 自身的章节写作「§X」
```

---

## 0. 文档源头与覆盖关系

本仓库的实施与评审约定以项目 Skill 为单一真相源，按以下优先级生效：

```text
1. .agents/skills/aegisops/SKILL.md
2. .agents/skills/aegisops/references/architecture-boundaries.md
3. .agents/skills/aegisops/references/automation-safety.md
4. .agents/skills/aegisops/references/doc-governance.md
5. .agents/skills/aegisops/references/phase-checklist.md
6. 本文件（AGENTS.md）—— 工程执行纪律
```

文件治理规则（YAML frontmatter、目录、命名）以 SKILL.md §25 + references/doc-governance.md 为准；其中「`AGENTS.md` 在根目录保留」已为该规则明确豁免。

冲突仲裁：

```text
内容冲突时，以 SKILL.md + references/*.md 为准；本文件不主张修改或重写 SKILL.md 已定义内容。
本文件新增章节不得与 SKILL.md 任何 §X 重复。
SKILL.md 后续新增/修订内容时，Owner 应同步从本文件删除重复章节。
```

进入 V0.2 阶段后，由 Owner 评审并合并两套约定的覆盖关系章节。

---

## 1. Agent 工作方式

AI 编码 Agent 在执行任务时必须：

````txt
1. 先阅读 .agents/skills/aegisops/SKILL.md
2. 再阅读本文件
3. 明确当前属于哪个 Phase（见 SKILL.md §14、§21）
4. 不越级实现后续阶段功能
5. 优先补全领域模型和测试
6. 修改外部接口时同步更新 OpenAPI 与 references/api/*
7. 修改数据库结构时新增 Flyway migration（按 SKILL.md §17 命名）
8. 修改安全相关代码时补充审计和权限判断
9. 不引入不必要的大型依赖
10. 不把 MVP 复杂化
11. 不绕过 aiops-runner 执行自动化动作
12. 与用户交互、撰写文档、提交说明、代码注释、PR 描述默认使用中文；除非用户明确要求其他语言或上下文必须使用英文（例如公开协议、外部 SDK API、国际化文案）
13. 写前端 UI 前先 `pnpm dlx shadcn@latest add` 拉取组件，禁止自封 div + 颜色 class 拼 UI（详见 §3.3、§3.4）
14. 改完代码必须跑对应工程的 `lint` / `format` / `test` / `build` 四件套并自检通过，提交前不得有未处理告警
15. 提交前必须评估 PR 等级，按等级走对应合并流程；等级在 commit footer 必须显式标注 `PR-Tier: L1 / L2 / L3`

    分级阈值：
    ```txt
    L1 低风险（满足任一即可）：
      a) 纯文档 / typo / 注释 类改动，单批次 ≤ 2000 行 diff，多文件允许
         （覆盖 AGENTS.md / SKILL.md / docs/**/*.md 重写与新增文档目录）
      b) 单文件代码改动 ≤ 100 行，不涉及 schema / 安全 / 权限 / 公开 API
      c) CI / 构建 / 基础设施脚本 typo 修正
      处理：fast-forward 直推，不强制 PR 审阅
      提交前要求：
        - §3.1 五件套相关项若涉及代码必须跑通
        - 纯文档类改动只需 grep 自检 + 人工 review
        - commit footer 必须含 PR-Tier: L1

    L2 中风险（默认）：
      - 单模块重构、多文件改动
      - schema 无关的业务逻辑变更
      - 文档治理、ADR、跨模块设计调整
      处理：走 PR，需 1 名 Owner 审阅通过
      提交前要求：五件套全绿 + PR 描述五段式（背景 / 主要变更 / 验证方式 / 风险与回滚 / 关联 issue）

    L3 高风险：
      - 跨 app 改动（server/worker/runner 任两个以上）
      - Flyway schema 迁移（V 编号新增）
      - 安全 / 权限 / 审计 / 凭据
      - 公开 API 破坏性变更
      - 自动化执行路径（aiops-runner）
      处理：走 PR，需 1 名 Owner + 1 名模块责任人审阅通过
      提交前要求：五件套全绿 + PR 描述五段式 + 影响面显式列出
    ```

    主分支（main / mvp）严禁 force push；L1 fast-forward 仍需 `git push`，不允许 `git push --force`
````

---

## 2. Git 提交与变更规范

所有 Commit Message、PR 标题、PR 描述、变更日志必须遵循以下规范。**违反规范的提交会被打回重写。**

### 2.1 格式

```txt
<type>(<scope>): <中文主题>

<中文正文，列点说明动机与变更点>

<可选 Footer，英文关键字>
```

### 2.2 主题行（首行）

```txt
type    : 必填，小写，固定枚举
scope   : 必填，小写，固定枚举；多模块用逗号分隔
冒号    : 半角英文冒号 + 一个半角空格
主题    : 中文，祈使句，「动词 + 名词」结构，不加句号，不超过 50 个汉字
```

#### 2.2.1 type 枚举

```txt
feat     新功能、新接口、新页面
fix      缺陷修复
refactor 重构，不改变行为
perf     性能优化
test     补齐/调整测试
docs     文档、设计、ADR
build    构建脚本、依赖、版本
ci       CI / CD 流水线
infra    docker-compose、部署脚本、基础设施
db       数据库迁移、Schema 调整
chore    杂项（拼写、注释、目录调整）
revert   回滚
```

#### 2.2.2 scope 枚举

按本项目实际结构收敛，新增模块时再扩展：

```txt
# 后端应用
server, worker, runner

# 后端模块
common, web, security, tenant, user, datasource, asset, alert,
incident, rca, ai, runbook, automation, audit, notification,
zabbix-adapter, vm-adapter, clickhouse-adapter, otel-adapter, rum

# 前端
console

# 基础设施与跨切
infra, db, deps, config

# 元数据
docs, agent, governance
```

#### 2.2.3 主题行示例

```txt
feat(incident): 新增按时间桶聚合策略
fix(alert): 修复 fingerprint 为空时重复入库
refactor(server): 将 tenant 过滤下沉到 Repository
perf(datasource): 同步主机时使用批量 upsert
docs(mvp): 补充 Phase 2 设计与验证步骤
db(incident): 新增 V0006__init_alert_incident_extend.sql
infra(compose): 引入 VictoriaMetrics 与 MinIO
```

### 2.3 正文

```txt
- 必填，使用中文，列点说明
- 每条以「模块/动作」开头，例如「后端:」「前端:」「迁移:」
- 聚焦「为什么」与「影响」，不要复述 diff
- 涉及外部接口变更必须显式写「接口:」
- 涉及数据库变更必须显式写「迁移:」并指明 Flyway 版本号（按 SKILL.md §17 命名）
- 涉及安全、权限、审计必须显式写「安全:」
- 总长度建议控制在 30 行内
```

#### 2.3.1 正文示例

```txt
feat(incident): 新增按时间桶聚合策略

后端:
- IncidentService 引入 TimeBucketPolicy，key = asset_id + severity，
  默认 30 分钟；命中已开事故时只追加 incident_alert。
- 同桶内告警数超过阈值时按严重度升级事故（warning → critical）。
- 新增 IncidentAggregationPolicy 配置类，支持按租户覆盖。

迁移:
- V0006__init_incident_aggregation.sql：incidents、incident_alerts、
  incident_timeline 三张表，tenant_id 必填。

接口:
- POST /api/incidents/aggregate
- GET  /api/incidents/{id}/timeline
- POST /api/incidents/{id}/status

安全:
- 所有接口强制 tenant 过滤，复用 TenantContext

Refs: docs/mvp/design/phase2.md
```

### 2.4 Footer

```txt
可选项；用于自动化与关联追踪，关键字使用英文半角
Refs:       #关联 issue / 文档（不关闭）
Closes:     #关闭 issue
Breaks:     #破坏性变更，必须列出影响面
Refs-Tests: #测试覆盖说明
```

### 2.5 PR 规范

```txt
标题：与提交主题行同格式，例 feat(incident): 新增按时间桶聚合策略
描述：必须包含
  1. 背景（为什么）
  2. 主要变更（列点）
  3. 验证方式（curl / SQL / 截图 / 录屏）
  4. 风险与回滚
  5. 关联 issue / 文档
合并：仅允许 squash merge 或 rebase merge；merge commit 会污染主线历史
```

### 2.6 反例（禁止写法）

```txt
# 1. 没有 type 和 scope
add zabbix integration

# 2. 主题用英文
feat(incident): add time bucket policy

# 3. 主题过长，超过 50 字
feat(incident): 新增按时间桶聚合策略并支持严重度自动升级以及自定义租户配置

# 4. scope 自由发挥
feat(my-module): xxx

# 5. 多件事塞一个提交
feat(server, worker, runner): 重构、重写、修复若干问题
```

### 2.7 Agent 自检清单

每次准备 `git commit` 之前必须确认：

```txt
[ ] 首行符合 <type>(<scope>): 中文主题 格式
[ ] type 在固定枚举内
[ ] scope 在固定枚举内
[ ] 主题 ≤ 50 字，无句号
[ ] 正文列点，每点带「模块/动作」前缀
[ ] 涉及接口变更已写「接口:」
[ ] 涉及数据库变更已写「迁移:」并指明 Flyway 版本号
[ ] 涉及安全变更已写「安全:」
[ ] Footer 关键字使用英文
[ ] 提交前已跑过 mvn verify 或对应模块的测试
```

---

## 3. 工程纪律与代码质量

本节是横切规则，优先级高于个人风格偏好。**违反本节的代码必须打回。**

### 3.1 Lint / Format / Typecheck / Test / Build 五件套

所有工程必须配置并跑通以下五件套，提交前不得有任何一项失败。

| 工程                         | 类型检查                     | 静态检查                             | 格式化   | 测试          | 构建                         |
| ---------------------------- | ---------------------------- | ------------------------------------ | -------- | ------------- | ---------------------------- |
| `apps/*` `modules/*`（Java） | `mvn -q -DskipTests compile` | Spotless + Checkstyle（见 3.2）      | Spotless | `mvn -q test` | `mvn -q -DskipTests package` |
| `web/console`（TS/React）    | `pnpm exec tsc -b`           | ESLint + `eslint-plugin-tailwindcss` | Prettier | `pnpm test`   | `pnpm build`                 |
| `infra/`                     | —                            | `docker compose config`              | —        | —             | `docker compose build`       |

四件套脚本统一收敛在根 `package.json` 的 `scripts`：

```jsonc
{
  "scripts": {
    "lint": "pnpm -r --parallel run lint",
    "format": "pnpm -r --parallel run format",
    "typecheck": "pnpm -r --parallel run typecheck",
    "test": "pnpm -r --parallel run test",
    "build": "pnpm -r --parallel run build",
  },
}
```

CI 流水线（`.github/workflows/ci.yml`）必须串行执行 `lint → typecheck → test → build`，任一失败即阻断合并。

### 3.2 后端代码质量硬要求

```txt
- Spotless 强制格式：2 空格缩进、UTF-8、LF 行尾、去除尾部空白；import 按字母序
- Checkstyle 规则：方法 ≤ 80 行、类 ≤ 500 行、参数列表 ≤ 5 个、嵌套深度 ≤ 4
- 强制开启的 Spotbugs 规则：EI_EXPOSE_REP、SQL_INJECTION、REC_CATCH_EXCEPTION
- 公共 API 类必须有 Javadoc；领域 Service 方法描述业务意图而非实现
- 异常必须继承 AiopsException 子类，禁止裸 throw new RuntimeException
- 日志格式：MDC 必须含 traceId / tenantId / userId / requestPath，缺失即告警
- 业务包禁止依赖 org.springframework.web；org.springframework.web 只能出现在 controller / filter / config 层
- Repository / Mapper 不允许返回 Map<String,Object>，必须用 Entity / DTO
- 任何跨模块调用必须经过 Application Service，禁止 Module A 直接注入 Module B 的 Repository
- 任何外部系统（Zabbix、VM、ClickHouse、LLM、Ansible）调用必须经过 aiops-*-adapter 抽象，禁止 Service 直接 HttpClient
```

### 3.3 前端代码质量硬要求

```txt
- ESLint 必须开启的规则集：
  - @typescript-eslint/no-explicit-any            error
  - @typescript-eslint/no-unused-vars             error (忽略 _ 前缀)
  - @typescript-eslint/consistent-type-imports    error
  - react-hooks/rules-of-hooks                    error
  - react-hooks/exhaustive-deps                   error
  - tailwindcss/classnames-order                  warn
  - tailwindcss/no-custom-classname               error  // 配合 3.4 强制语义化
  - import/order                                 warn
- Prettier：单引号、printWidth 100、trailingComma all、semi false（与 Vite 模板一致）
- 任何 src/**/*.tsx 不允许出现以下自封模式：
  - 散落的 className="rounded-2xl bg-white p-6 shadow-sm" 等手搓卡片
  - 散落的 className="rounded-full border bg-xxx text-xxx" 手搓徽章
  - className="space-y-*" / "space-x-*"  // 一律改为 flex + gap-*
  - 自定义 <Field>、<StatusChip>、<EmptyState> 等与 shadcn 等价的私有组件
- 新页面必须先 `pnpm dlx shadcn@latest add` 再写代码；如确认 shadcn 暂无对应组件，必须在 §3.5「本项目 shadcn 缺口」清单追加
- API 客户端类型必须从 `src/api/client.ts` 集中维护或由 OpenAPI 生成；禁止页面里散落手写 DTO interface
- Hook 命名以 use 开头；超过 80 行或包含多步副作用的 Hook 必须拆为 useXxx + useXxxMutation 配对
```

### 3.4 Tailwind / shadcn 强制规则

```txt
- 颜色：必须用 bg-primary / text-muted-foreground / ring 等语义 token；禁止 bg-blue-500、text-slate-600 这类 raw 颜色
- 间距：使用 gap-*；禁止 space-y-* / space-x-*
- 等宽高：使用 size-*；禁止 w-10 h-10 同时出现
- 截断：使用 truncate；禁止手写 overflow-hidden text-ellipsis whitespace-nowrap
- 暗色：禁止手动 dark: 覆盖颜色；通过 .dark 父级 + 语义 token 自动生效
- 条件类：必须用 cn() 工具函数；禁止手写三元 template literal
- z-index：shadcn 已内置的 Dialog/Sheet/Popover/Tooltip 禁止再覆盖 z-index
- 按钮：使用 Button 组件 + variant；禁止 <button className="rounded-lg bg-indigo-600 ..."> 自封
- 徽章：使用 Badge 组件；禁止 <span className="rounded-full bg-rose-100 ..."> 自封
- 卡片：使用 Card + CardHeader + CardTitle + CardDescription + CardContent + CardFooter 完整组合；禁止 <div className="rounded-2xl bg-white shadow-sm"> 简化版
- 表单：使用 FieldGroup + Field + FieldLabel + FieldDescription + FieldError；禁止 <label className="block"><span>Label</span><input /></label> 自封
- 校验：Field 写 data-invalid，控件写 aria-invalid；FieldSet + FieldLegend 用于分组
- 图标：使用 lucide-react；Button 内的 icon 必须用 data-icon="inline-start" / data-icon="inline-end"，不允许手写 size-4
- 空态：使用 Empty + EmptyMedia + EmptyTitle + EmptyDescription；禁止 <div className="text-center text-slate-500">No data</div>
- 加载占位：使用 Skeleton；禁止 <div className="animate-pulse bg-slate-200" />
- 反馈：Toast 统一走 sonner 的 toast()；禁止 <div className="absolute top-2 right-2 bg-emerald-500">Saved</div>
```

### 3.5 本项目 shadcn 缺口

记录经评审确认 shadcn 暂无等价、必须自封的组件。**新增条目需 Owner 同意。**

```txt
（暂无）
```

### 3.6 安全与多租户

```txt
- 任何 HTTP 出口必须设置 connectTimeout 与 readTimeout，缺省值 ≤ 10s
- 任何写接口必须经 TenantGuard / PermissionGuard 双层校验；缺一即不合规
- 写操作必须经 AuditLogger 落库 audit_log；缺失即视为绕过审计
- 自动化执行类动作必须走 aiops-runner；禁止 aiops-server / aiops-worker 直接 SSH / Ansible
- 凭据类字段（password、apiToken、secret）禁止写入普通日志；Logback Filter 必须 mask
- 导出文件 / 上传文件必须经过 mime + size + name 校验，禁止前端单点校验
```

### 3.7 依赖与版本

```txt
- 新增依赖前必须经 Owner 评审；同一类需求已有依赖时不得引入竞品
- 禁止引入：lodash（全量）、moment（请用 dayjs / date-fns）、@ant-design/*、element-plus（与 shadcn 冲突）、nivo、bizcharts（统一 ECharts）
- 锁文件必须提交；不得出现 package-lock.json + pnpm-lock.yaml + yarn.lock 并存
- 升级主版本（major）必须单列 PR，PR 描述需写明 Breaking 影响面
- 内部模块之间禁止循环依赖；arch-unit 或自定义脚本必须每 CI 跑一次
```

### 3.8 测试与覆盖率

```txt
- 单元测试覆盖率门槛：domain/service 层 ≥ 80%，controller 层 ≥ 60%
- 新增 Service 公共方法必须含至少 1 个单测：覆盖正常路径 + 至少 1 个异常路径
- Adapter 必须含集成测试，允许 mock 外部 HTTP；测试用例至少覆盖：成功、超时、4xx、5xx
- 前端关键页面必须含 1 个 smoke test：组件挂载 + 核心交互至少 1 次
- Bug 修复必须先写复现单测再修复；修复后单测必须先红后绿
```

### 3.9 MVP 阶段无需向下兼容

```txt
- 目标读者：MVP 阶段内（参见 SKILL.md §14 Phase 0 ~ Phase 6）所有参与设计、开发、评审的 Agent
- 适用范围：仓库内全部后端 / 前端 / 基础设施 / 文档 / Schema / API 契约
- 核心原则：MVP 阶段禁止引入任何「兼容旧版本」「兼容旧数据」「兼容旧接口」的代码与配置
  - 不写 @Deprecated / 兼容垫片 / 双写 / 灰度回退开关
  - 不留 v1 / legacy / old / previous 命名的包、目录、Controller、Service、字段、API 路径
  - 不为「未来可能的迁移」预留抽象层、Strategy 枚举、Feature Flag、配置开关
  - 数据库迁移（按 SKILL.md §17 命名）一旦合并即为唯一事实源；不得新增「把旧字段含义映射到新字段」之类的转换层
  - API 破坏性变更直接改接口、字段与文档；不要用「同时支持新旧两种入参」的方式过渡
  - 删除代码 / 文件 / 模块时直接删除，必要时连同相关测试、文档、Migration、配置项一起清理
- 允许的例外（必须写明原因并经 Owner 评审通过）
  - 法规、安全、第三方协议强制要求的兼容（例如 OAuth/JWT 协议字段、审计日志保留期）
  - 已经在生产环境运行的数据迁移期过渡，由独立 PR + 显式「过渡窗口」注释承接，超期后清理
- 自检
  - 提交前 grep `@Deprecated`、`_legacy`、`legacy_`、`_old`、`old_`、`_previous`、`previous_`、`v1_`、`_v1` 等关键字
  - Flyway 迁移版本号遵循 SKILL.md §17 命名 `V\d{4}__init_[a-z_]+\.sql`，不应被当作 v1 误报
  - 命中上述任一关键字必须给出删除或例外说明
  - PR 描述如出现「兼容 / 兼容旧 / 兼容老 / 向下兼容 / 灰度 / 双写」等措辞，默认打回，除非命中上述例外
- 适用阶段切换：进入 V0.2 及以后版本时，由 Owner 评审后单独修订本节
```

### 3.10 AI Agent 自检清单（提交前必走）

```txt
[ ] §1 Agent 工作方式 14 条全部满足
[ ] §2 Git 提交与变更规范自检 10 条全部勾选
[ ] PR-Tier 已评估并在 commit footer 显式标注（L1 / L2 / L3）
[ ] L2 及以上：§3.1 五件套全部通过，CI 全绿
[ ] L2 及以上：PR 描述五段式齐全（背景 / 主要变更 / 验证方式 / 风险与回滚 / 关联 issue）
[ ] L3 额外：影响面在 PR 描述中显式列出
[ ] L3 额外：1 名 Owner + 1 名模块责任人审阅通过
[ ] L1 纯文档：grep @Deprecated / `_legacy` / `legacy_` / `_old` / `old_` / `_previous` / `previous_` / `v1_` / `_v1` 0 命中
[ ] L1 纯文档：被引用的 SKILL.md §X 章节仍存在
```

---

## 4. 最高优先级提醒

本节与 SKILL.md §24 同源，但作为 Agent 提交前必读以保留可执行焦点。

永远优先保证：

```txt
事件归一
Incident 模型
证据链
自动化安全边界
审计
MVP 闭环
```

不要优先追求：

```txt
酷炫大屏
复杂 AI Agent
过早微服务
复杂拓扑图
完全自动修复
全量采集平台
```

---

本文件与 SKILL.md + references/\*.md 共同构成项目的 Agent 规范。任何对本文件的修改需经 Owner 评审，且必须遵守 §0 文档源头关系——不得与 SKILL.md 任一章节内容重复或冲突。

```
本项目真正有价值的不是"接入了多少数据源"，而是：

> 能否把一次线上故障从发现、定位、处置到复盘真正串起来。
```
