---
title: REST API 与 Redis 协议
---


retask4j 使用 Redis 作为其唯一的状态存储。本文档描述任何客户端（Python、Go、Node.js、Ruby 等）必须实现的精确 Redis 键格式、值格式和 Lua 脚本，以便与系统互操作。

> 大多数用户应使用内置的 HTTP 网关（`/api/submit`、`/api/pull` 等），仅在自定义集成场景下才回退到直接访问 Redis。

## 概述

```
┌──────────────────────────────────────────────────────────────────────┐
│  Client (any language)                                                │
│      │                                                                 │
│      ├── HTTP gateway (/api/submit) ──► FuTaskSubmitter ──► Redis      │
│      ├── HTTP gateway (/api/pull)   ──► Redisson poll   ──► Redis      │
│      └── direct Redis access        ─────────────────────► Redis      │
│                                                                       │
│  Worker (Java only for now, but protocol-compatible with any client)   │
│      └── FuTaskWorker ──► Redis (polls, processes, completes)         │
└──────────────────────────────────────────────────────────────────────┘
```

## 键格式

所有键都以 topic 为前缀。请将 `{topic}` 替换为配置的 topic 名称。

| 键 | 类型 | 用途 |
|---|---|---|
| `fu-task-{topic}-blocking` | list (Redis LIST) | 主工作队列（提交时 LPUSH，拉取时 RPOP） |
| `fu-task-{topic}-timing` | sorted set (ZSET, score = execute-at-ms) | 定时/延时任务 |
| `fu-task-{topic}-pending` | sorted set (ZSET, score = lease-expires-at-ms) | 进行中任务（租约 = 崩溃时回收） |
| `fu-task-{topic}-retry` | sorted set (ZSET, score = retry-at-ms) | 计划重试的任务 |
| `fu-task-{topic}-message:{id}` | hash (HSET) | 任务消息体（输入/输出/状态/...） |
| `fu-task-{topic}-return:{callerId}` | list (Redis LIST) | FUNCTION 模式返回队列（按 caller 隔离） |
| `fu-task-{topic}-callback` | list (Redis LIST) | CALLBACK 模式通知队列 |
| `fu-task-{topic}-callback-pending` | sorted set (ZSET, score = lease-expires-at-ms) | 进行中的回调 |

键名安全要求：
- `topic` 必须匹配 `^[A-Za-z0-9_.-]{1,64}$`
- `id` 必须匹配 `^[A-Za-z0-9_.-]{1,256}$`
- 不允许出现 `:`、`{`、`}`、控制字符（会破坏 Redis 键命名空间或 hash tag 解析）

## 消息体（`fu-task-{topic}-message:{id}`）

存储为 Redis hash。字段名称与 Java 的 `FuTaskMessage` 字段对应。

| 字段 | 类型 | 必填 | 描述 |
|---|---|---|---|
| `id` | string | 是 | 任务 ID（与键后缀一致） |
| `topic` | string | 是 | Topic 名称 |
| `mode` | string | 否 | `NORMAL`（默认）、`FUNCTION` 或 `CALLBACK` |
| `status` | string | 是 | `WAITING` → `PENDING` → `SUCCESS` / `FAIL` |
| `input` | string (JSON) | 否 | 任务负载（任意 JSON 对象，序列化为字符串） |
| `output` | string (JSON) | 否 | Worker 结果（在 SUCCESS 时设置） |
| `error` | string | 否 | Worker 错误信息（在 FAIL 时设置） |
| `callerId` | string | 否 | FUNCTION 模式必填（将结果路由到返回队列） |
| `strategy` | string | 否 | 自定义策略标签，用于路由/过滤 |
| `tag` | string | 否 | 自定义标签 |
| `retryPlan` | string (JSON array) | 否 | 例如 `[60,120,300,600,3600]` — 延时秒数 |
| `delayTime` | string (int) | 否 | 首次执行前的初始延时（秒） |
| `executeExpire` | string (int) | 是 | 最大执行时间（秒，默认 3600） |
| `resultExpire` | string (int) | 否 | 结果 TTL（秒，FUNCTION 模式）；0 = 不缓存 |
| `ttlBuffer` | string (int) | 是 | 缓存 TTL 缓冲 = 所有 (retryPlan[i] + executeExpire) 之和 |
| `scheduleTime` | string (long) | 否 | 任务调度时间（毫秒 epoch） |
| `createTime` | string (long) | 是 | 任务创建时间（毫秒 epoch） |
| `executeTime` | string (long) | 否 | Worker 开始处理时间（毫秒 epoch） |
| `completeTime` | string (long) | 否 | 任务达到终态的时间（毫秒 epoch） |
| `retryTimes` | string (int) | 是 | 截至目前的重试次数（默认 0） |
| `retryDelay` | string (int) | 否 | 当前重试的延时（秒） |
| `callbackStatus` | string | 否 | CALLBACK 模式的 `WAITING` → `SUCCESS` / `FAIL` |
| `callbackError` | string | 否 | 回调错误信息 |
| `callbackRetryTimes` | string (int) | 否 | 回调重试次数 |

TTL：24 小时，每次更新时刷新。

## Lua 脚本

所有脚本由 Java worker 加载并按 SHA 缓存。非 Java 客户端应以原子方式发出等效的 Redis 命令（使用 MULTI/EXEC、事务或其语言中的等效机制）。

### 1. `push_task_message_deque_batch.lua` — 提交一个或多个任务

**KEYS**：`[message-prefix, working-key, timing-key]`
**ARGV**：`["&lt;json1&gt;", "&lt;json2&gt;", ...]`（每个 JSON 是消息 hash 的 JSON 对象）

对于每个 item：
- 如果 `delayTime > 0`：ZADD `timing-key` score=executeTime+delayTime*1000
- 否则：RPUSH `working-key` &lt;id&gt;；HSET `message-prefix&lt;id&gt;` &lt;json 中的所有字段&gt;

### 2. `get_task_messages_for_work.lua` — worker 拉取（认领下一个任务）

**KEYS**：`[working-key, pending-key, message-prefix]`
**ARGV**：`[maxCount, pendingTimeoutMs, field1, field2, ...]`

- 原子地：从 `working-key` 最多 LPOP `maxCount` 个 ID
- 对每个 ID：ZADD `pending-key` score=now+pendingTimeoutMs
- HGETALL `message-prefix&lt;id&gt;` 并以 hash 数组形式返回
- 将 hash TTL 刷新为 24 小时

### 3. `complete_batch.lua` — worker 报告完成

**KEYS**：`[pending-key, message-prefix, return-key, callback-key, callback-pending-key]`
**ARGV**：`["&lt;json1&gt;", ...]`

- 对于每个消息：
  - ZREM `pending-key` &lt;id&gt;
  - HSET `message-prefix&lt;id&gt;` 从 json 写入 status/output/error/completeTime
  - 刷新 hash TTL
- 如果 `output` 非空 AND `callerId` 已设置：RPUSH `return-key` &lt;id&gt;
- 如果 mode=CALLBACK：RPUSH `callback-key` &lt;id&gt;

### 4. `retry_batch.lua` — worker 调度重试

**KEYS**：`[pending-key, message-prefix, retry-key, working-key]`
**ARGV**：`["&lt;json1&gt;", ...]`

- 对于每个消息：
  - ZREM `pending-key` &lt;id&gt;
  - HSET `message-prefix&lt;id&gt;` 写入 retryTimes/retryDelay
  - ZADD `retry-key` score=now+retryDelay*1000 &lt;id&gt;
  - 如果重试耗尽：RPUSH `working-key` &lt;id&gt;（回到队列头部）

### 5. `remove_store_set_to_list.lua` — 内部：从 timing/pending/retry 拉回 working

**KEYS**：`[sorted-set-key, working-key]`
**ARGV**：`[maxCount]`

ZRANGEBYSCORE 获取 sorted set 中 score <= now 的项，并将 ID RPUSH 回 working-key。
由 `runResetTiming/Pending/Retry` 后台线程调用。

### 6. `get_task_messages_by_id.lua` — 查看（无变更）

**KEYS**：`[message-prefix]`
**ARGV**：`[id1, id2, ...]`

对每个 ID 执行 HGETALL。以数组形式返回所有结果。

### 7. `set_callback_batch.lua` — 标记回调已完成

**KEYS**：`[pending-key, message-prefix, callback-pending-key, retry-key]`
**ARGV**：`["&lt;json1&gt;", ...]`

ZREM pending-key；HSET callbackStatus；ZADD retry-key 并设置租约；刷新 TTL。

### 管理脚本（由 FuTaskAdminService 和 HTTP 网关使用）

### 8. `delete_task.lua` — 从所有队列中原子删除

**KEYS**：`[message-key, working-key, timing-key, pending-key, retry-key, callback-key, callback-pending-key]`
**ARGV**：`[id]`

从所有队列执行 LREM/ZREM；DEL 消息 hash。返回总的移除数量。

### 9. `replay_task.lua` — 重新入队一个任务

**KEYS**：`[message-key, working-key]`
**ARGV**：`[id, now]`

HSET retryTimes=0、status=WAITING、error=""、completeTime=0；RPUSH 到 working-key。

### 10. `force_retry_task.lua` — 绕过 retryDelay，立即推入 working

**KEYS**：`[message-key, working-key, pending-key]`
**ARGV**：`[id]`

ZREM pending-key；RPUSH 到 working-key。

### 11. `force_complete_task.lua` — 手动标记 SUCCESS/FAIL

**KEYS**：`[message-key, working-key, pending-key, retry-key]`
**ARGV**：`[id, status, outputJson, errorMsg, now, callerId, returnDequeKey, callbackDequeKey]`

LREM/ZREM 队列；HSET status/output/error/completeTime；如适用则路由到 FUNCTION/CALLBACK 队列。

## 任务生命周期

```
                          ┌──── ZREM timing (expired) ───┐
                          ▼                              │
   submit ──► ZADD timing ──────────► runResetTiming ──► RPUSH working
                                                            │
                                                            ▼
                                            worker poll (LPOP + ZADD pending)
                                                            │
                          ┌──── on success ─────────────────┤
                          │                                 │
                          ▼                                 ▼
                  ZREM pending                       ZREM pending
                  HSET status=SUCCESS                 HSET status=FAIL
                  HSET output=...                    HSET error=...
                  HSET completeTime=...              (retryPlan empty?)
                  RPUSH return-key (FUNCTION)             │
                  RPUSH callback-key (CALLBACK)         yes ──► RPUSH working (re-enqueue)
                                                            no ──► (drop, FAIL is final)
```

## HTTP 网关（推荐路径）

对于大多数非 Java 用例，推荐使用内置 HTTP 网关而非直接访问 Redis。网关将所有协议细节隐藏在一个稳定的 REST 接口背后。

| 接口 | 方法 | 鉴权 | 用途 |
|---|---|---|---|
| `/api/topics` | GET | X-Api-Token | 列出活跃 topics |
| `/api/queues/{topic}` | GET | X-Api-Token | 队列深度（working/pending/timing/retry/callback） |
| `/api/submit` | POST | X-Api-Token | 提交任务（不需要目标 URL） |
| `/api/tasks/{topic}/{id}` | GET | X-Api-Token | 查看任务状态/输入/输出/错误 |
| `/api/pull/{topic}` | GET | X-Api-Token | Pull 模式 worker：以租约方式认领下一个任务 |
| `/api/complete/{topic}/{id}` | POST | X-Api-Token | Pull 模式 worker：上报 SUCCESS/FAIL 及 output/error |

鉴权：通过请求头 `X-Api-Token: &lt;token&gt;` 或查询参数 `?token=&lt;token&gt;`。通过 `retask4j.api.token` 配置，并通过 `retask4j.api.enabled=true` 启用。

### 提交示例

```bash
curl -X POST http://retask4j-host:9400/api/submit \
  -H "X-Api-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "send-email",
    "id": "job-001",
    "mode": "NORMAL",
    "input": {"to": "user@example.com", "subject": "Hi", "body": "Hello"},
    "retryPlan": [60, 300, 1800],
    "executeExpire": 600
  }'

# 响应 200：
# {"id":"job-001","topic":"send-email","mode":"NORMAL","status":"WAITING","createTime":1720000000000}
```

### Pull + complete 示例（Python 风格伪代码）

```python
import requests
TOKEN = "..."

# 认领下一个任务
r = requests.get("http://retask4j-host:9400/api/pull/send-email?wait=10",
                 headers={"X-Api-Token": TOKEN})
if r.status_code == 204:
    continue  # 队列为空
task = r.json()
task_id = task["id"]

try:
    result = send_email(task["input"]["to"], task["input"]["subject"])
    requests.post(f"http://retask4j-host:9400/api/complete/send-email/{task_id}",
                  headers={"X-Api-Token": TOKEN, "Content-Type": "application/json"},
                  json={"status": "SUCCESS", "output": {"sent_at": "..."}})
except Exception as e:
    requests.post(f"http://retask4j-host:9400/api/complete/send-email/{task_id}",
                  headers={"X-Api-Token": TOKEN, "Content-Type": "application/json"},
                  json={"status": "FAIL", "error": str(e)})
```

## 直接访问 Redis 实现的注意事项

1. **原子性至关重要**：在没有使用 Lua 脚本、MULTI/EXEC 或等效事务的情况下，绝不要跨多个 Redis 命令读+写任务。竞态条件会导致重复处理、丢失任务或 pending 泄漏。

2. **Pending 租约恢复**：Java worker 运行一个后台线程，将过期的 pending 项移回 working。如果你在另一种语言中编写自己的 worker，必须实现等效的回收循环，否则卡住的 pending 任务会不断累积。

3. **键 TTL 不会自动设置**：消息 hash 仅通过 Lua 脚本获得 24 小时 TTL。如果直接写入消息 hash，请自行设置 `EXPIRE`。

4. **Hash 字段值必须是字符串**：Redis hash 存储的是字符串，即使是"数字"。使用 `HGET` 读取（返回字符串），按需转换为 int。

5. **Sorted set 的 score 是 double**：使用毫秒时间戳以避免 2^53 ms（约公元 285,427 年）之后的浮点精度丢失。

6. **自定义编解码器**：`push_task_message_deque_batch.lua` 接受每条消息一个 JSON 字符串。JSON 中的字段值必须是字符串（而不是嵌套对象），以兼容直接 HSET。使用 fastjson2 / ujson / json.dumps() 进行序列化。

7. **Topic 名称正则**：由 Java 端强制校验。请在客户端也进行验证，以避免 Java 端拒绝提交时任务静默丢失。