# AI-Ops Portal 基础重构：基线与实施总览

## 1. 源码基线

本实施包基于仓库：

```text
https://github.com/baicie/ai-ops/tree/feat/record-doc-portal
```

拉取时间：2026-07-12。

当前分支相对 `43d1631` 又前进 3 个提交，已经包含 Phase 19 后续修复、`web/console` 删除以及 Phase 20 设计文档，但 Portal 基础问题仍存在：

```text
web/portal/package.json
  包名仍是 shadcn-admin
  仍依赖 @clerk/react
  仍依赖 @faker-js/faker
  已安装 @formily/core/json-schema/react/validator
  尚未安装 dnd-kit

web/portal/src/features/users/index.tsx
  直接 import { users } from './data/users'

web/portal/src/features/users/data/users.ts
  faker.seed(67890)
  Array.from({ length: 500 })
  角色仍是 superadmin/admin/cashier/manager
  状态仍是 active/inactive/invited/suspended

web/portal/src/features/roles/index.tsx
  permissions 为前端写死数组
  页面明确提示“后续接入真实角色 API”

web/portal/src/features/work-records/designer/form-canvas.tsx
  中心区域只是 fields.map()
  主要排序方式还是上下箭头
  没有真实输入控件与 Drop Zone

web/portal/src/features/work-records/designer/form-preview.tsx
  按 fieldType 手写 if
  使用原生 input/select/textarea
  没有 Formily

web/portal/src/features/work-records/designer/types.ts
  DesignerField 是扁平字段数组
  没有 Section/Grid/Divider 等布局节点
```

## 2. 总目标

完成四项基础重构：

```text
1. Portal 模板残留清理
2. 真实用户管理
3. 真实 RBAC 与数据范围
4. Formily + Shadcn 统一运行时与设计器 v2
```

最终结构：

```text
真实用户/角色 API
        ↓
TanStack Query 管理页面

DesignerDocument
        ↓ compile
Formily ISchema
        ↓
Formily + Shadcn Renderer
        ↓
画布 / 预览 / 新建 / 编辑 / 详情
```

## 3. 强制架构约束

### 3.1 用户与角色

- 前端不得维护 Faker 用户。
- 权限定义和权限依赖由服务端提供。
- 用户、角色、数据范围全部带 tenantId 过滤。
- 页面隐藏不是安全边界，后端必须使用 Method Security。
- 所有写操作使用 rowVersion 乐观锁。
- 系统角色不可删除。
- 不能禁用当前操作者自己。
- 不能移除租户最后一个 system_admin。
- 危险权限变更需要确认原因。
- 变更必须进入现有 audit_log。

### 3.2 表单

- Formily 是唯一动态表单运行内核。
- Shadcn 是 Formily 组件实现层。
- 禁止继续增加 `switch(fieldType)` 运行时渲染器。
- DesignerDocument 与 Formily Schema 分离。
- 发布版本不可变。
- 旧版本通过兼容器读取，不原地改写。
- 表达式采用白名单 DSL，不允许任意 JavaScript/eval。

## 4. 模块边界

```text
modules/aiops-platform
  platform user
  platform role
  permission definition
  role permission
  data scope
  user-role binding

modules/aiops-security
  AuthorizationService
  AuthorizationRepository
  UserPrincipal
  PermissionCodes

modules/aiops-work-record
  DesignerDocument parser
  Designer compiler
  template draft/version validation
  schema compatibility

web/portal
  platform-users
  platform-roles
  work-records/formily
  work-records/designer-v2
```

## 5. 实施顺序

```text
UI-0 模板残留清理
UI-1 IAM 数据库与后端 API
UI-2 用户管理页面
UI-3 角色权限页面
UI-4 Formily + Shadcn 统一运行时
UI-5 DesignerDocument + 设计器 v2
UI-6 旧 Schema/Designer 兼容
UI-7 测试、E2E、删除旧实现
```

禁止将 Phase 20 的评论、附件、AI、审批等业务能力混入本重构 PR。

## 6. 目录结果

```text
web/portal/src/
├── features/platform-users/
├── features/platform-roles/
├── features/work-records/formily/
├── features/work-records/designer-v2/
└── routes/_authenticated/platform/

modules/aiops-platform/src/main/java/io/aegisops/platform/iam/
├── api/
├── application/
├── domain/
└── infrastructure/

modules/aiops-work-record/src/main/java/io/aegisops/workrecord/designer/
├── application/
├── domain/
└── infrastructure/
```

## 7. 提交拆分

```text
commit 1  chore(portal): remove demo users, clerk and sample navigation
commit 2  feat(iam): add platform user and role management APIs
commit 3  feat(portal): add real user management page
commit 4  feat(portal): add role permission and data-scope editor
commit 5  feat(form): add Formily Shadcn runtime adapters
commit 6  refactor(work-record): use unified Formily renderer
commit 7  feat(designer): add DesignerDocument compiler and dnd canvas
commit 8  test(portal): add IAM, designer and enterprise acceptance tests
commit 9  chore(portal): remove legacy canvas, preview and mock code
```

## 8. Definition of Done

```text
1. package.json 不再包含 @clerk/react 和 @faker-js/faker。
2. 用户页面没有任何本地演示数据。
3. 角色页面可真实读取和保存权限。
4. ALL/SELF 数据范围真实生效。
5. 权限变更有 before/after 审计。
6. 新建、编辑、详情全部使用 Formily Renderer。
7. 中心画布展示真实 Shadcn 表单组件。
8. 字段可拖入、排序、分组和两列布局。
9. 字段编码发布后锁定。
10. 历史字段删除采用禁用。
11. v1 历史记录按 v1 Schema 展示。
12. 所有 JUnit、PostgreSQL IT、Vitest Browser、Playwright E2E 通过。
```
