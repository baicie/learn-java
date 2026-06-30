---
title: PR2 - RuntimePhase + health phase 标签
type: fix
status: accepted
phase: phase-5
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - docs/fixes/phase-5/2026-06-29-second-review-remediation.md
  - .agents/skills/aegisops/SKILL.md
---

# PR2 — RuntimePhase + health phase 标签

## 背景

依据 `2026-06-29-second-review-remediation.md` §4，aiops-server / aiops-worker / aiops-runner
三个 app 的 internal status 端点全部 hardcode `phase=phase0`，导致监控、PR 评审、Owner 验收
无法辨别应用真实运行时 phase。本修复落地 RuntimePhase 机制。

## 事实

| app          | 入口                                           | 修复前                   | 修复后                                                                  |
| ------------ | ---------------------------------------------- | ------------------------ | ----------------------------------------------------------------------- |
| aiops-server | `aiops-server` Spring 主类 + `application.yml` | 完全缺失 phase 概念      | `aiops.runtime.phase=phase5` 由 `@ConfigurationPropertiesScan` 自动装载 |
| aiops-worker | `/internal/worker/status` Controller           | `phase: phase0` hardcode | 注入 `RuntimeProperties`，返回真实 phase                                |
| aiops-runner | `/internal/runner/status` Controller           | `phase: phase0` hardcode | 同上                                                                    |

## 设计决策

### RuntimePhase 放在 aiops-common

aiops-common 是三 app 唯一共同依赖的层（参考 `apps/aiops-server/pom.xml`、
`apps/aiops-worker/pom.xml`、`apps/aiops-runner/pom.xml`），RuntimePhase + RuntimeProperties
属于运行时公共契约，必须住在 aiops-common。代价：aiops-common 加 `spring-boot` 依赖。

### wire label 不含下划线

`RuntimePhase.propertyName()` 返回 `phase0..phase6` 而非 `phase_0..phase_6`。
两个理由：

1. Prometheus 标签语义偏好紧凑小写（参考 aiops-runner 现有 `application.yml` 中
   `metrics.tags.application` 风格）。
2. application.yml 已有 `${AIOPS_RUNTIME_PHASE:phase5}` 默认值形式，必须保证
   `fromPropertyName("phase5") == PHASE_5` 闭环。

### 注册策略不统一，但兼容现有约定

| app          | 已有的 properties 加载模式                                                                         | RuntimeProperties 注册方式                                         |
| ------------ | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| aiops-server | `@ConfigurationPropertiesScan("io.aegisops")`                                                      | 自动扫到，无须改动                                                 |
| aiops-runner | `@EnableConfigurationProperties({RunnerProperties, ExecutionProperties, AnsibleRunnerProperties})` | 加入清单                                                           |
| aiops-worker | 完全没有 properties 加载                                                                           | 首次引入 `@EnableConfigurationProperties(RuntimeProperties.class)` |

刻意保留三 app 各自现行的注册方式，避免本 PR 越界重写所有 `@EnableConfigurationProperties`。

## 变更清单

### 新增

```text
modules/aiops-common/src/main/java/io/aegisops/common/runtime/RuntimePhase.java
modules/aiops-common/src/main/java/io/aegisops/common/runtime/RuntimeProperties.java
modules/aiops-common/src/test/java/io/aegisops/common/runtime/RuntimePhaseTest.java
modules/aiops-common/src/test/java/io/aegisops/common/runtime/RuntimePropertiesTest.java
docs/fixes/phase-5/2026-06-30-runtime-phase.md   ← 本文件
```

### 修改

```text
modules/aiops-common/pom.xml                                            (新增 spring-boot + test 依赖)
apps/aiops-server/src/main/resources/application.yml                    (新增 aiops.runtime.phase)
apps/aiops-worker/src/main/java/io/aegisops/worker/AiOpsWorkerApplication.java  (新增 @EnableConfigurationProperties)
apps/aiops-worker/src/main/java/io/aegisops/worker/WorkerController.java         (注入 RuntimeProperties)
apps/aiops-worker/src/main/resources/application.yml                    (新增 aiops.runtime.phase)
apps/aiops-runner/src/main/java/io/aegisops/runner/AiOpsRunnerApplication.java  (RuntimeProperties 加入 EnableConfigurationProperties)
apps/aiops-runner/src/main/java/io/aegisops/runner/RunnerController.java         (注入 RuntimeProperties)
apps/aiops-runner/src/main/resources/application.yml                    (新增 aiops.runtime.phase)
```

## 验证

```text
$ mvn -pl modules/aiops-common -q test
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0   ← RuntimePhaseTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0   ← RuntimePropertiesTest

$ mvn -pl modules/aiops-common,modules/aiops-observability,...,modules/aiops-zabbix-adapter -q test
全部成功（除 aiops-integration pre-existing 编译错误，与本 PR 无关）

$ mvn -DskipTests -pl apps/aiops-server,apps/aiops-worker,apps/aiops-runner -am package
[INFO] BUILD SUCCESS
aiops-server / aiops-worker / aiops-runner 全部 SUCCESS
```

## 状态

- **PR2 已落地**：本地 + 远端（mvp 分支）
- 与 PR3（Flyway 拆分 V0001..V0005）正交，无依赖
- 与 PR5（worker 骨架）正交，本 PR 提供了 worker 接收 RuntimeProperties 的入口

## 后续动作（不在本 PR 范围）

1. `@ConditionalOnProperty(prefix = "aiops.runtime", name = "phase")` 门控接入——
   随 Phase 6 落地，本 PR 仅约定 phase 来源。
2. 三 app 的 controller 单元测试——本 PR 不引入 test 依赖扩张，需要更克制地选择何时引入
   `spring-boot-starter-test` 到 aiops-worker / aiops-runner。
3. 2026-06-30 09:21 复核：上一版本文件误判 aiops-integration 模块存在
   ZabbixWebhook 测试编译错误，实际原因为 aiops-common 改动后 stale build
   artifact 导致 surefire 跑旧字节码。`mvn clean test` 后 14/14 PASS，
   全仓库 26 模块 BUILD SUCCESS。后续遇到类似错误先 `mvn clean` 验证。
