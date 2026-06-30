---
title: MVP 第四个后端应用 demo-order-service 的边界与可见性
type: adr
status: accepted
phase: phase-5
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/fixes/phase-5/2026-06-29-second-review-remediation.md
  - docs/adr/0001-use-java-spring-boot.md
---

# ADR 0002: MVP 第四个后端应用 demo-order-service 的边界与可见性

## 状态

- **已接受（2026-06-30）**
- 触发来源：第二轮回盘点出 P1（参见 `docs/fixes/phase-5/2026-06-29-second-review-remediation.md` §7）
- 落地 PR：PR6（ADR + profile 划分）
- 替代方案：[被否决的方案 B](#被否决的方案-b直接删除-demo-order-service)

## 背景

SKILL §3.2 与 AGENTS §2.3 明确 MVP 仅允许三个后端应用：`aiops-server`、`aiops-worker`、`aiops-runner`。但仓库 `apps/` 目录下还存在第四个应用 `demo-order-service`：

```txt
apps/
├── aiops-server/        # SKILL 声明的三 app 之一
├── aiops-worker/        # SKILL 声明的三 app 之一
├── aiops-runner/        # SKILL 声明的三 app 之一
└── demo-order-service/  # 第四个应用，无 ADR 说明
```

### demo-order-service 的实际功能

通过对源码的梳理（2026-06-30），该应用包含 12 个 Java 类：

| 组件                          | 用途                                                                   |
| ----------------------------- | ---------------------------------------------------------------------- |
| `OrderController`             | 模拟下单接口 `/api/order/create`，可被注入 latency/异常                |
| `ZabbixMetricsController`     | 模拟 6 个 Zabbix 抓取项（health / latency / cpu / mem / load / error） |
| `FaultInjectionController`    | 提供 `/demo/fault/*` 端点切换故障状态                                  |
| `FaultModeService`            | 内存态故障状态机（slow API / CPU 高 / 服务降级 / 错误日志）            |
| `DemoOrderServiceApplication` | 独立 Spring Boot 启动入口                                              |

它**没有任何数据库表**（无 Flyway、无 JPA）、**不持久化任何状态**，所有故障状态都在进程内存。

### 它的真实作用

该服务是 Phase Z9 demo 流水线的「业务侧」：

```txt
aiops-server (Zabbix webhook / incident aggregate / postmortem)
        ↑
        │ outbox + webhook
        ↓
demo-order-service (订单 + Zabbix 抓取项 + 故障注入)
        ↑
        │ Zabbix agent HTTP exporter
        ↓
   Zabbix server (sidecar)
```

没有这个应用，Phase Z9 的端到端演示（故障注入 → Zabbix 触发 → AegisOps 收敛 → 复盘草稿）就只能跑在 mock 数据上，**不能验证真实 HTTP 抓取、真实 webhook 入口、真实 outbox 派单链路**。

## 决策

**保留 `demo-order-service` 为 MVP demo 期间的辅助应用，但通过 Spring profile 严格限制其可见性**：

1. **应用类型**：定义为 `demo`（非生产）应用，不是 MVP 运行时拓扑的一部分
2. **默认 profile（prod）**：不启动 `demo-order-service` 进程；CI 在 `mvn verify` 时**不**包含此模块
3. **`demo` profile（dev/staging）**：通过 `--spring.profiles.active=demo` 显式启用；CI 通过 `-Pdemo` Maven profile 包含此模块
4. **架构位置**：与 `apps/aiops-agent`（Python LLM sidecar，PR9 落地）平级 — 都是「sidecar 工具」，不进 MVP 主拓扑
5. **文档归宿**：本 ADR 作为长期依据；后续任何新增 demo 应用必须先写 ADR 才能进入 `apps/`

### 为什么 profile 划分而不是拆分模块

理由：

- `demo-order-service` 是**独立进程**而非 `aiops-server` 内的模块 — 它有自己的故障状态机、独立的 HTTP 端口（`/api/order/*` 不应与 `/api/incidents/*` 混在同一端口）
- 进程隔离是它能稳定模拟「下游业务侧」的前提 — 如果内联到 server，server 重启会丢失故障注入状态
- Spring profile 是 Spring Boot 原生机制，零额外依赖、零额外构建复杂度

### profile 配置契约

| 环境       | 启用方式                        | 是否进入 mvn verify | 故障注入端点 |
| ---------- | ------------------------------- | ------------------- | ------------ |
| prod       | 默认不启用                      | 否                  | 不可达       |
| dev        | `--spring.profiles.active=demo` | 是（`-Pdemo`）      | 可达         |
| staging    | `--spring.profiles.active=demo` | 是（`-Pdemo`）      | 可达         |
| ci (默认)  | 默认不启用                      | 否                  | 不可达       |
| ci (smoke) | `-Pdemo`                        | 是                  | 可达         |

落地后由 ArchUnit 守门：demo 包内的 controller 在非 demo profile 下不参与 bean 扫描（详见落地层 §3）。

## 被否决的方案

### 被否决的方案 A:把 demo-order 内联到 integration test 的 docker-compose

- **优点**：demo 进程不进入生产构建图
- **缺点**：docker-compose 只能起 Zabbix / Postgres，**无法启动 JVM 进程模拟 HTTP 抓取项**；故障注入语义无法表达；CI 时间翻倍
- **否**：demo 的核心价值是「模拟下游业务侧的 HTTP 端点」，docker-compose 替代不了

### 被否决的方案 B:直接删除 demo-order-service

- **优点**：app 数量回到 3，SKILL 完全自洽
- **缺点**：Phase Z9 端到端测试变成 mock 数据；新进贡献者无法本地复现「故障注入 → 收敛 → 复盘」链路
- **否**：MVP 阶段 demo 是 onboarding 的关键资产

### 被否决的方案 C:合并到 aiops-server 的 `demo` 模块

- **优点**：少一个 jar
- **缺点**：demo 故障状态机污染 server 的 bean 上下文；server 重启会清空内存态；故障注入端点（`/demo/fault/*`）若暴露到 prod 会成安全漏洞
- **否**：与 §3.1「模块化单体优先」冲突；与 §3.6「写接口必须经权限校验」冲突

## 落地层

落地 PR6 由 4 个文件组成：

1. `docs/adr/0002-mvp-fourth-app-justification.md`（本文件，L1 纯文档）
2. `apps/demo-order-service/src/main/resources/application.yml` — 显式声明 `spring.profiles.active: demo`，prod 不启用（如果未来有人误启动）
3. `apps/demo-order-service/src/main/resources/application-demo.yml` — demo profile 下的端口、metrics、白名单
4. `pom.xml`（根）新增 `-Pdemo` Maven profile，让 `mvn verify -Pdemo` 包含 demo-order 模块
5. `apps/demo-order-service/src/main/java/.../DemoOrderServiceApplication.java` 改为 `@Profile("demo")` 守门 + 启动日志明确打印当前 profile

### ArchUnit 守门（可选后续）

如果 PR6 落地后 CI 经常误启 demo 到 prod，下一轮可以加 ArchUnit 守门：`apps.demo.order` 包的 controller 在非 demo profile 下不应被 `@RestController` 注入。MVP 阶段先不引入，等真有误用再说。

## 影响

- `apps/` 目录仍然有 4 个目录，但只有 3 个进入生产构建图 — 在 `README.md` 中需要明确标注「demo-order-service 是 dev/staging only」
- CI 默认 `mvn verify` 跑 3 app；加 `-Pdemo` 跑 4 app
- docker-compose prod 段不包含 demo-order；dev/staging 段可选包含
- `docs/INDEX.md` 自动列出本 ADR

## 参考

- SKILL §3.2（应用职责） / §4.1（技术栈）
- AGENTS §2.3（git scope 枚举） / §3.1（五件套） / §3.6（写接口权限校验）
- PR-mega-1（AGENTS.md 文档源头决断）
- PR3（Flyway clean slate）— 提供本 ADR 文档治理的范本
