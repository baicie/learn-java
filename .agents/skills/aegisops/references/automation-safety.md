# 自动化安全

## 风险等级

### 低风险

若策略允许可免审批执行, 但必须始终生成审计日志。

```txt
- 查日志
- 查指标
- 运行巡检脚本
- 读取进程列表
- 读取磁盘使用
- 读取服务状态
- 创建工单
- 发送通知
- 健康检查
- 读取配置 (非敏感)
```

### 中风险

默认需审批, 审计日志必需。

```txt
- 重启无状态服务
- 清理临时文件
- 刷新缓存
- 调整副本数 (扩 / 缩)
- 重启网关 / 代理
- 重新加载配置 (不重启)
- 排空流量
```

### 高风险

必须审批 + 回滚预案 + 审计, 不得自动执行。

```txt
- 回滚发布
- 修改配置文件
- 流量切换 / 故障转移
- 重启数据库
- 重启核心中间件
- 删除文件或目录
- 终止进程
- 执行原始 Shell 命令
- 在生产 namespace 中运行 kubectl
```

### 默认禁止

未经用户明确需求与完整安全设计前, 永远不要实现、不可自动执行、Runbook 也不允许。

```txt
- `rm -rf` 或对生产路径的任何递归删除
- `drop database`
- `truncate table`
- 删除 Kubernetes namespace
- 停止核心中间件 (Kafka、Redis、Database)
- 修改防火墙规则
- flush Redis (FLUSHDB / FLUSHALL)
- 格式化磁盘
- 关闭生产主机
```

## 执行流

所有自动化必须遵循该链路:

```txt
AI 建议
  ↓
策略检查 (风险等级 + 是否需要审批)
  ↓
AutomationJob 创建 (status: pending)
  ↓
按需审批 (status: waiting_approval)
  ↓
Runner 接收审批通过的任务 (status: approved -> running)
  ↓
将日志流回 server
  ↓
执行后健康检查
  ↓
更新任务状态 (success / failed / timeout)
  ↓
生成审计日志
  ↓
在 Incident 时间线追加 AutomationEvent
```

## 策略引擎

在创建 AutomationJob 前, 策略引擎依次校验:

```txt
1. 该动作是否在禁止列表? -> 直接拒绝
2. 风险等级是什么? -> 决定是否需要审批
3. 是否匹配 Runbook? -> 关联到任务
4. 该 Runbook 是否启用? -> 禁用时拒绝
5. 用户是否拥有 automation:execute 权限? -> 无权限拒绝
6. 若需要审批, 用户是否有 automation:approve? -> 标记为待审批
```

## 健康检查

中、高风险任务执行完后:

```txt
1. 等待可配置时长 (默认 30 秒)
2. 查询目标资产健康端点或指标
3. 若失败则生成告警并通知责任人
4. 把健康检查结果写入任务输出
```

## 审计日志要求

每次自动化执行都必须记录:

```txt
- actor_user_id         谁触发的
- incident_id           属于哪个 Incident (若有)
- runbook_id            使用哪个 Runbook
- risk_level            low / medium / high
- approval_user_id      谁审批的 (若有)
- command_executed      执行了什么 (脱敏后)
- duration_ms           耗时
- exit_code             成功 / 失败
- output_summary        输出的前/后 N 行
- health_check_passed   通过 / 未通过 / 跳过
- created_at            执行时间
```

## Runbook 安全要求

每个 Runbook 必须声明:

```txt
- risk_level           low / medium / high
- approval_required    true / false
- forbidden_actions    该 Runbook 显式禁止的命令清单
- health_check         执行后检查什么
```

Runbook 步骤必须使用参数化输入, 不得使用字符串拼接。

Runner 必须拒绝任何匹配禁止模式列表的步骤, 即便 Runbook 本身通过校验。
