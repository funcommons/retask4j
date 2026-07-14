---
title: 最佳实践
---


## 生产部署

### Redis 配置建议

- **独立部署**：Redis 应独立部署，不建议与应用混部。Redis 的性能直接影响任务吞吐量。
- **内存规划**：任务消息存储在 Redis Hash 中，每条消息大约 1~5 KB。请根据峰值积压量预估所需内存。
- **持久化**：建议开启 AOF 持久化，避免 Redis 重启导致任务丢失。
- **连接池**：根据 Caller/Worker 实例的数量调整 `connectionPoolSize`，建议每个实例 16~32。

### 多实例部署

Caller 和 Worker 可以在不同实例上独立部署：

```
┌─────────────────┐
│   Caller 实例   │  接收 HTTP 请求，推送到 Redis
│   （可多实例）  │
└────────┬────────┘
         │ Redis
┌────────▼────────┐
│   Worker 实例   │  消费任务，执行 HTTP 调用
│   （可多实例）  │
└─────────────────┘
```

- Caller 是无状态的，可以水平扩展
- Worker 可以通过增加实例数和 `maxConsumeThreads` 来提升消费能力
- 同一 topic 的多个 Worker 实例自动负载均衡（Redis 队列天然支持多消费者）

### 线程数配置

| 参数 | 推荐值 | 描述 |
|------|--------|------|
| `maxConsumeThreads` | CPU 核心数 × 4 ~ 8 | Worker 消费线程数；IO 密集型场景可适当上调 |
| `callbackMaxThreads` | 32 ~ 64 | CALLBACK 模式的回调线程数 |
| Tomcat `max-threads` | 500 ~ 2000 | HTTP 服务线程，根据 QPS 调优 |

### 配置外置

在生产环境中，务必将 `config.yml` 外置，并通过 ConfigMap 或 Volume 挂载：

```bash
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

避免将 Redis 密码等敏感信息打包到镜像中。

## 性能调优

### 批量发送

Caller 默认启用批量发送（`batch: true`），通过 `FuTaskBatchManager` 进行批量聚合并一次性写入 Redis：

- 批量大小阈值：1000 条消息
- Flush 间隔：50ms
- 并发 Flush 线程数：4

推荐在高 QPS 场景下保持开启，在低 QPS 场景下关闭以减少延迟。

### Worker 消费能力

Worker 的消费能力取决于：

1. **`maxConsumeThreads`**：并发执行的任务数
2. **目标服务响应时间**：Worker HTTP 调用所消耗的时间直接影响吞吐量
3. **Redis 连接池**：连接数不足会成为瓶颈

容量公式：

```
理论吞吐量 = maxConsumeThreads / 平均执行时间（秒）
```

例如，`maxConsumeThreads=512`，平均执行时间 200ms，理论吞吐量约为 2560 QPS。

### Redis 性能

- 所有队列操作都通过 Lua 脚本执行，以减少网络往返
- 队列自动获得 24 小时的 TTL，避免无限增长
- 建议监控 Redis 内存使用情况和慢查询

## 常见问题

### 任务卡在 WAITING 状态

**可能原因**：

1. Worker 未启动或未连接到同一个 Redis
2. Worker 的 `topic` 与 Caller 的 `topic` 不匹配
3. Worker 线程池已满，无法继续消费

**排查方法**：

```bash
# 查看队列状态
curl http://localhost:9093/debug/proxy/taskCount.do

# 如果 working > 0 且 pending = 0，说明 Worker 没有消费
# 如果 pending > 0 且持续增长，说明 Worker 处理慢或卡住
```

### FUNCTION 模式请求超时

**可能原因**：

1. Worker 的执行时间超过 `requestTimeout`
2. Worker 未启动
3. Redis 连接异常导致无法从 Return Queue 读取数据

**建议**：

- 根据目标服务的实际响应时间调整 `requestTimeout`
- FUNCTION 模式不适用于非常耗时的操作；可考虑改用 CALLBACK 模式

### CALLBACK 失败

**可能原因**：

1. 回调 URL 不可达
2. 回调服务返回非 200 状态码
3. 回调数据格式不被回调服务接受

**排查方法**：

```bash
# 查看任务详情，检查 callbackStatus 和 callbackError
curl http://localhost:9093/debug/proxy/getTask.do?taskId=xxx
```

在回调重试次数耗尽后，`callbackStatus` 将变为 `FAIL`。

### 任务执行失败但没有重试

**可能原因**：

1. 未配置 `retry-plan` 或为空数组
2. 重试次数已用尽
3. 任务已过期（超过 `executeExpire`）

**排查方法**：

```bash
# 查看任务详情
curl http://localhost:9093/debug/proxy/getTask.do?taskId=xxx

# 检查 retryTimes、retryPlan、executeExpire
```

### Redis 内存持续增长

**可能原因**：

1. `resultExpire` 设置过大，已完成任务的结果未及时清理
2. 任务积压过多
3. 文件上传场景中大文件被存入 Redis

**建议**：

- 根据业务需求合理设置 `resultExpire`
- 监控队列积压，及时扩容 Worker
- 文件上传场景控制文件大小

### 定时任务未准时执行

**可能原因**：

1. `retask4j-task-timing` 时间戳格式错误（10 位秒 vs 13 位毫秒）
2. 计划时间超过 24 小时（不支持）
3. Worker 的 resetTiming 线程异常

**说明**：

- 框架自动识别 10 位秒级和 13 位毫秒级时间戳
- 定时任务最长支持 24 小时

## 安全建议

- 务必为 Redis 设置密码，生产环境禁止无密码访问
- `callback-url` 应使用内网地址，避免暴露到公网
- 在不可信环境中应禁用 Worker 的 `enableLocal`，以防通过任务注入访问本地接口
- 使用 `rewrite-request-headers` 移除敏感的请求头信息（例如 Authorization）
- 文件上传场景限制文件大小，防止 Redis 内存溢出
