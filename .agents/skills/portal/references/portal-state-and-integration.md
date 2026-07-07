---
title: web/portal 状态管理与 AegisOps 集成约定
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - .agents/skills/portal/SKILL.md
  - .agents/skills/portal/references/portal-frontend-conventions.md
  - .agents/skills/aegisops/SKILL.md
---

# web/portal 状态管理与 AegisOps 集成约定

## 1. 顶层 Provider 嵌套顺序

`main.tsx` 是唯一允许的 Provider 组装入口:

```tsx
<StrictMode>
  <QueryClientProvider client={queryClient}>
    <ThemeProvider>
      <FontProvider>
        <DirectionProvider>
          <RouterProvider router={router} />
        </DirectionProvider>
      </FontProvider>
    </ThemeProvider>
  </QueryClientProvider>
</StrictMode>
```

业务侧 (`AuthenticatedLayout`) 再叠两层:

```tsx
<SearchProvider>
  <LayoutProvider>
    <SidebarProvider defaultOpen={defaultOpen}>
      <AppSidebar />
      <SidebarInset>...</SidebarInset>
    </SidebarProvider>
  </LayoutProvider>
</SearchProvider>
```

规则:

```text
- 顶层 Provider 不要在路由文件里再包, 已装配好
- Theme/Font/Direction 永远在最外层, 路由切换不能重置
- Search/Layout/Sidebar 只在 _authenticated 内重置, 这是有意的
- 想新增全局 Provider 必须先写 ADR, 不在路由内私自添加
```

## 2. QueryClient 默认值

`main.tsx` 已统一:

```text
queries.retry: 401/403 不重试
queries.refetchOnWindowFocus: PROD 启用
queries.staleTime: 10s
mutations.onError: 走 handleServerError + sonner
queryCache.onError:
  401 -> toast + auth.reset + 跳 sign-in
  500 -> toast + PROD 跳 /500
  403 -> 占位
```

新 feature 直接用 `useQuery` / `useMutation`, 不要重写这些默认值。

新增特例:

```text
- 长连接 (EventSource) 不要走 Query, 用 zustand 或单独 hook
- 大文件上传用 useMutation, onUploadProgress 给到组件
- 流式响应 (SSE) 拿到 token 后, 在 src/lib/sse.ts 集中封装
```

## 3. Zustand Store

现有 `stores/auth-store.ts` 是模板参考实现, 接 AegisOps 后端时要替换:

```text
- accessToken 改由 AegisOps 后端发放, 不要再用 Clerk JWT
- setAccessToken 必须同步 setCookie(ACCESS_TOKEN, ...) (已在)
- reset 必须在 QueryCache.onError(401) 里调用
- 新增 store 必须配套 .test.ts (init / set / reset)
```

严禁的反模式:

```text
- 把服务端数据塞进 zustand
- 在组件里直接调用 useAuthStore.setState((s) => ({ user: ... })), 走 action
- 在 SSR 阶段调用 store
- 持久化敏感字段 (password / ssoToken / 多租户 key)
```

## 4. Cookie 与持久化

```text
- 已存在的 cookie: sidebar_state / layout_collapsible / layout_variant / vite-ui-theme / ACCESS_TOKEN
- 新增 cookie 必须:
    * 走 src/lib/cookies.ts 的 getCookie / setCookie / removeCookie
    * 在 cookie 名上标注业务前缀, 例如 aegis_<key>
    * 在 settings/feature 里暴露"清除 portal 偏好"按钮
- 禁止存 token / api key 到 cookie, 走 httpOnly + 后端签名
```

## 5. 表单

统一 react-hook-form + zodResolver, 不引入 Formik:

```tsx
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

const schema = z.object({
  email: z.string().email(),
});

const form = useForm<z.infer<typeof schema>>({
  resolver: zodResolver(schema),
  defaultValues: { email: "" },
});
```

Submit 提交走 `useMutation`, 不要 `onSubmit={form.handleSubmit(async v => axios.post(...))}`, 因为这样错误不会被 main.tsx 捕到。

## 6. Dialog / Sheet / Drawer

```text
- 用 shadcn/ui 的 Dialog (modal) / Sheet (侧滑) / AlertDialog (确认)
- 必须带 Title + Description (a11y)
- 同步显示 useDialogState hook, 放在 feature-provider 内
- 提交后用 setOpen(null) 关闭, 不要 rely on onOpenChange 写逻辑
- AlertDialog 用于破坏性操作, 用 main.tsx 里的 confirm-dialog 模式
```

## 7. 与 AegisOps 后端的边界

portal 是"前端壳", 真正的领域在 `modules/*` 的 Spring Boot, portal **不应**:

```text
- 直接连 MySQL / Redis / Kafka
- 自己实现 OAuth / SAML
- 重复实现 Zabbix / Prometheus 协议
- 处理审计 / 审批 / runner (一律走后端)
```

portal 应:

```text
- 调用 AegisOps REST API (VITE_API_BASE_URL)
- 调用 AegisOps SSE / WebSocket 做实时告警 / 运行日志
- 复用后端 schema 生成 zod schema (通过 openapi-typescript 或手动同步)
- 任何写操作经 React Query mutation, 不在 effect / event 里裸 axios
```

## 8. 与 web/console 的边界

```text
- portal 与 console 是两个独立 Vite 工程, 不共享代码
- 共享类型: 待 packages/shared/ 创建后, 在两个工程都引用
- 共享主题: 不共享, portal 走 Tailwind v4, console 走自己的 theme.css
- 共享组件: 不共享, 各工程自维护 shadcn/ui
- 文档: docs/portal/ 放 portal 独有内容, 不混到 docs/architecture/
```

## 9. ESLint 规则强约束

`eslint.config.js` 已开启:

```text
no-console: error                                 // 不要 console.log, 用 main.tsx 的 logging
consistent-type-imports: prefer type-imports      // import { type X } from '...'
no-duplicate-imports: error                       // 一个模块只能 import 一次
react-refresh/only-export-components: warn        // 仅组件在模块顶层导出, 其它加 eslint-disable
@typescript-eslint/no-unused-vars: argsIgnorePattern '^_'
```

新增规则必须先 ADR。

## 10. 参考

```text
- .agents/skills/portal/SKILL.md
- web/portal/src/main.tsx
- web/portal/src/components/layout/authenticated-layout.tsx
- web/portal/src/stores/auth-store.ts
- web/portal/src/context/*.tsx
- web/portal/src/lib/handle-server-error.ts
- .agents/skills/aegisops/SKILL.md            // 后端事实
```
