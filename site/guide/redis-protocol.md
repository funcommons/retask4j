---
title: REST API & Redis Protocol
---


retask4j uses Redis as its only state store. This document describes the exact Redis key
format, value format, and Lua scripts that any client (Python, Go, Node.js, Ruby, etc.)
must implement to interoperate with the system.

> Most users should use the bundled HTTP gateway (`/api/submit`, `/api/pull`, etc.)
> and only fall back to direct Redis access for custom integration scenarios.

## Overview

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

## Key Format

All keys are prefixed by topic. Replace `{topic}` with the configured topic name.

| Key | Type | Purpose |
|---|---|---|
| `fu-task-{topic}-blocking` | list (Redis LIST) | Main work queue (LPUSH on submit, RPOP on poll) |
| `fu-task-{topic}-timing` | sorted set (ZSET, score = execute-at-ms) | Scheduled / delayed tasks |
| `fu-task-{topic}-pending` | sorted set (ZSET, score = lease-expires-at-ms) | In-flight tasks (lease = reclaim on crash) |
| `fu-task-{topic}-retry` | sorted set (ZSET, score = retry-at-ms) | Tasks scheduled for retry |
| `fu-task-{topic}-message:{id}` | hash (HSET) | Task message body (input/output/status/...) |
| `fu-task-{topic}-return:{callerId}` | list (Redis LIST) | FUNCTION-mode return queue (per caller) |
| `fu-task-{topic}-callback` | list (Redis LIST) | CALLBACK-mode notification queue |
| `fu-task-{topic}-callback-pending` | sorted set (ZSET, score = lease-expires-at-ms) | Callbacks in flight |

Key safety:
- `topic` must match `^[A-Za-z0-9_.-]{1,64}$`
- `id` must match `^[A-Za-z0-9_.-]{1,256}$`
- No `:`, `{`, `}`, control chars allowed (would break Redis key namespace or hash tag parsing)

## Message Body (`fu-task-{topic}-message:{id}`)

Stored as a Redis hash. Field names match the Java `FuTaskMessage` fields.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Task ID (matches the key suffix) |
| `topic` | string | yes | Topic name |
| `mode` | string | no | `NORMAL` (default), `FUNCTION`, or `CALLBACK` |
| `status` | string | yes | `WAITING` → `PENDING` → `SUCCESS` / `FAIL` |
| `input` | string (JSON) | no | Task payload (any JSON object, serialized as string) |
| `output` | string (JSON) | no | Worker result (set on SUCCESS) |
| `error` | string | no | Worker error message (set on FAIL) |
| `callerId` | string | no | Required for FUNCTION mode (routes result to return queue) |
| `strategy` | string | no | Custom strategy tag for routing/filtering |
| `tag` | string | no | Custom tag |
| `retryPlan` | string (JSON array) | no | `e.g. [60,120,300,600,3600]` — delays in seconds |
| `delayTime` | string (int) | no | Initial delay (seconds) before first execution |
| `executeExpire` | string (int) | yes | Max execution time in seconds (default 3600) |
| `resultExpire` | string (int) | no | Result TTL in seconds (FUNCTION mode); 0 = don't cache |
| `ttlBuffer` | string (int) | yes | Cache TTL buffer = sum of (retryPlan[i] + executeExpire) |
| `scheduleTime` | string (long) | no | When the task was scheduled (ms epoch) |
| `createTime` | string (long) | yes | When the task was created (ms epoch) |
| `executeTime` | string (long) | no | When the worker started processing (ms epoch) |
| `completeTime` | string (long) | no | When the task reached terminal state (ms epoch) |
| `retryTimes` | string (int) | yes | Number of retries so far (default 0) |
| `retryDelay` | string (int) | no | Delay for the current retry (seconds) |
| `callbackStatus` | string | no | `WAITING` → `SUCCESS` / `FAIL` for CALLBACK mode |
| `callbackError` | string | no | Callback error message |
| `callbackRetryTimes` | string (int) | no | Number of callback retries |

TTL: 24 hours, refreshed on each update.

## Lua Scripts

All scripts are loaded by Java workers and cached by SHA. Non-Java clients should
issue equivalent Redis commands atomically (using MULTI/EXEC, transactions, or their
language's equivalent).

### 1. `push_task_message_deque_batch.lua` — submit one or more tasks

**KEYS**: `[message-prefix, working-key, timing-key]`
**ARGV**: `["&lt;json1&gt;", "&lt;json2&gt;", ...]` (each JSON is the message hash as a JSON object)

For each item:
- if `delayTime > 0`: ZADD `timing-key` score=executeTime+delayTime*1000
- else: RPUSH `working-key` &lt;id&gt;; HSET `message-prefix&lt;id&gt;` &lt;all fields from json&gt;

### 2. `get_task_messages_for_work.lua` — worker poll (claim next task)

**KEYS**: `[working-key, pending-key, message-prefix]`
**ARGV**: `[maxCount, pendingTimeoutMs, field1, field2, ...]`

- Atomically: LPOP from `working-key` up to `maxCount` IDs
- For each ID: ZADD `pending-key` score=now+pendingTimeoutMs
- HGETALL `message-prefix&lt;id&gt;` and return as array of hashes
- Refresh hash TTL to 24h

### 3. `complete_batch.lua` — worker report completion

**KEYS**: `[pending-key, message-prefix, return-key, callback-key, callback-pending-key]`
**ARGV**: `["&lt;json1&gt;", ...]`

- For each message:
  - ZREM `pending-key` &lt;id&gt;
  - HSET `message-prefix&lt;id&gt;` status/output/error/completeTime from json
  - Refresh hash TTL
- If `output` non-empty AND `callerId` set: RPUSH `return-key` &lt;id&gt;
- If mode=CALLBACK: RPUSH `callback-key` &lt;id&gt;

### 4. `retry_batch.lua` — worker schedule retry

**KEYS**: `[pending-key, message-prefix, retry-key, working-key]`
**ARGV**: `["&lt;json1&gt;", ...]`

- For each message:
  - ZREM `pending-key` &lt;id&gt;
  - HSET `message-prefix&lt;id&gt;` retryTimes/retryDelay
  - ZADD `retry-key` score=now+retryDelay*1000 &lt;id&gt;
  - If retry exhausted: RPUSH `working-key` &lt;id&gt; (back to start of queue)

### 5. `remove_store_set_to_list.lua` — internal: pull from timing/pending/retry back to working

**KEYS**: `[sorted-set-key, working-key]`
**ARGV**: `[maxCount]`

ZRANGEBYSCORE the sorted set (score &lt;= now) and RPUSH the IDs back to working-key.
Used by the `runResetTiming/Pending/Retry` background threads.

### 6. `get_task_messages_by_id.lua` — peek (no mutation)

**KEYS**: `[message-prefix]`
**ARGV**: `[id1, id2, ...]`

HGETALL for each ID. Return all as array.

### 7. `set_callback_batch.lua` — mark callback as completed

**KEYS**: `[pending-key, message-prefix, callback-pending-key, retry-key]`
**ARGV**: `["&lt;json1&gt;", ...]`

ZREM pending-key; HSET callbackStatus; ZADD retry-key with lease; refresh TTL.

### Admin scripts (used by FuTaskAdminService and HTTP gateway)

### 8. `delete_task.lua` — atomic delete from all queues

**KEYS**: `[message-key, working-key, timing-key, pending-key, retry-key, callback-key, callback-pending-key]`
**ARGV**: `[id]`

LREM/ZREM from all queues; DEL the message hash. Returns total removals.

### 9. `replay_task.lua` — re-enqueue a task

**KEYS**: `[message-key, working-key]`
**ARGV**: `[id, now]`

HSET retryTimes=0, status=WAITING, error="", completeTime=0; RPUSH to working-key.

### 10. `force_retry_task.lua` — bypass retryDelay, push to working now

**KEYS**: `[message-key, working-key, pending-key]`
**ARGV**: `[id]`

ZREM pending-key; RPUSH to working-key.

### 11. `force_complete_task.lua` — manually mark SUCCESS/FAIL

**KEYS**: `[message-key, working-key, pending-key, retry-key]`
**ARGV**: `[id, status, outputJson, errorMsg, now, callerId, returnDequeKey, callbackDequeKey]`

LREM/ZREM from queues; HSET status/output/error/completeTime; route to FUNCTION/CALLBACK queue if applicable.

## Task Lifecycle

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

## HTTP Gateway (Recommended Path)

For most non-Java use cases, prefer the bundled HTTP gateway over raw Redis access.
The gateway hides all the protocol details behind a stable REST surface.

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/topics` | GET | X-Api-Token | List active topics |
| `/api/queues/{topic}` | GET | X-Api-Token | Queue depths (working/pending/timing/retry/callback) |
| `/api/submit` | POST | X-Api-Token | Submit a task (no target URL needed) |
| `/api/tasks/{topic}/{id}` | GET | X-Api-Token | Peek task state/input/output/error |
| `/api/pull/{topic}` | GET | X-Api-Token | Pull-mode worker: claim next task with lease |
| `/api/complete/{topic}/{id}` | POST | X-Api-Token | Pull-mode worker: report SUCCESS/FAIL with output/error |

Auth: header `X-Api-Token: &lt;token&gt;` or query `?token=&lt;token&gt;`. Configure via
`retask4j.api.token` and enable with `retask4j.api.enabled=true`.

### Submit example

```bash
curl -X POST http://retask4j-host:9400/api/submit \
  -H "X-Api-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "send-email",
    "id": "job-001",
    "mode": "NORMAL",
    "input": {"to": "[email protected]", "subject": "Hi", "body": "Hello"},
    "retryPlan": [60, 300, 1800],
    "executeExpire": 600
  }'

# Response 200:
# {"id":"job-001","topic":"send-email","mode":"NORMAL","status":"WAITING","createTime":1720000000000}
```

### Pull + complete example (Python-style pseudo-code)

```python
import requests
TOKEN = "..."

# Claim next task
r = requests.get("http://retask4j-host:9400/api/pull/send-email?wait=10",
                 headers={"X-Api-Token": TOKEN})
if r.status_code == 204:
    continue  # empty queue
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

## Notes for Direct-Redis Implementations

1. **Atomicity is critical**: never read+write a task across multiple Redis commands without
   using a Lua script, MULTI/EXEC, or a comparable transaction. Race conditions cause
   double-processing, lost tasks, or pending-leak.

2. **Pending lease recovery**: the Java worker runs a background thread that moves expired
   pending entries back to working. If you write your own worker in another language,
   you must implement an equivalent reclaim loop, or stuck-pending tasks will accumulate.

3. **Key TTLs are not auto-set**: the message hash gets a 24h TTL only via the Lua scripts.
   If you write a message hash directly, set `EXPIRE` yourself.

4. **Hash field values must be strings**: Redis hashes store strings, even for "numbers".
   Read with `HGET` (returns string), convert to int as needed.

5. **Sorted set scores are doubles**: use milliseconds for timestamps to avoid float
   precision loss past 2^53 ms (~year 285,427).

6. **Custom codecs**: `push_task_message_deque_batch.lua` accepts a JSON string per
   message. Field values in the JSON must be strings (not nested objects) for direct
   HSET compatibility. Use fastjson2 / ujson / json.dumps() to serialize.

7. **Topic name regex**: enforced by the Java side. Validate on the client too to avoid
   silent task loss when the Java side rejects the submit.
