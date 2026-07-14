---
title: 更新日志
---


retask4j 的所有重要变更都记录在本文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
本项目遵循 [语义化版本](https://semver.org/spec/v2.0.0.html)。

## [1.0.0] - 2026-07-11

### 新增

- 在单一 Redis 队列中支持三种任务模式：`NORMAL`（即发即弃）、`FUNCTION`（通过 `CompletableFuture<R>` 进行请求-响应）和 `CALLBACK`（异步 HTTP POST 通知）。
- HTTP 代理模式：将任意 HTTP 请求序列化为任务，通过 Redis 分发，在远程或本地 Worker 上执行，返回 HTTP 响应。
- 通过自定义请求头按请求粒度控制：`retask4j-retry-plan`、`retask4j-task-timing`、`retask4j-task-delay`、`retask4j-callback-url`、`retask4j-assert-response`。
- 7 个用于队列操作的原子 Lua 脚本：`push_task_message_deque_batch`、`complete_batch`、`retry_batch`、`remove_store_set_to_list`、`get_task_messages_for_work`、`get_task_messages_by_id`、`set_callback_batch`。
- 4 个管理 Lua 脚本：`delete_task`、`replay_task`、`force_retry_task`、`force_complete_task`。
- `FuTaskAdminService` 提供 `peek`/`replay`/`forceRetry`/`forceComplete`/`delete` 操作。
- 通过 `retask4j-http-starter` 实现 Spring Boot 自动配置（条件依赖于 `redisson` classpath、redis 属性配置）。
- Web 仪表板：基于 Vue 3 + Element Plus 的 SPA，包含：
  - 队列概览与指标卡片
  - Topic 列表，点击查看任务
  - 任务详情抽屉，提供 JSON 格式化的 input/output/error 以及 5 种操作（replay、force-retry、force-complete-success、force-complete-fail、delete）
  - 任务事件的实时 SSE 推送
  - 指标视图，含成功率的进度条
  - 告警视图，含活跃横幅与历史记录
  - 通过 Redis 心跳实现多实例发现
  - 端到端任务提交的快速测试器
  - 国际化（zh-CN / en-US），主题切换（light/dark/orange-black），品牌（mchuan）
  - 通过每次请求的 `Accept-Language` 与 `X-Dashboard-Token` 鉴权实现本地化
- 安全：
  - 带 DNS rebinding 防护的 SSRF 防护（4 层防御）
  - 文档服务的路径穿越防护
  - Header CRLF 注入防护
  - Redis 键名安全校验（不允许 `:/{/}`、控制字符，长度限制）
- 带规则评估和 webhook 投递的管理告警。
- `documents/` 目录下的 10 个 Markdown 文档：overview、quickstart、concepts、http-proxy、configuration、api-reference、best-practices、diff-table、readme、index。
- 236 个单元测试 + 4 个端到端集成测试（需要 Redis）。
- Apache 2.0 许可证、CONTRIBUTING.md、带快速入门的 README、GitHub 友好的 .gitignore。

### 模块

- `retask4j-core` — 纯 Redisson 的队列引擎（不依赖 Spring）
- `retask4j-http` — HTTP 代理层（依赖 core + spring-boot）
- `retask4j-http-starter` — Spring Boot 自动配置
- `retask4j-http-server` — 可运行的 Spring Boot 应用，内嵌仪表板
- `retask4j-demo-taskcaller` / `retask4j-demo-taskworker` — 独立的 demo 应用

[1.0.0]: https://github.com/funcommons/retask4j/releases/tag/v1.0.0