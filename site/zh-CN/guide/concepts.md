---
title: 核心概念
---


## 任务模式

retask4j 支持三种任务通信模式，覆盖从简单异步到复杂回调的各种场景。

### NORMAL — 普通推送

```
Caller → Redis 队列 → Worker 执行
```

- 即发即弃
- Caller 提交任务后立即返回 taskId
- 不关心执行结果；适用于日志写入、消息推送等场景

### FUNCTION — 同步调用

```
Caller → Redis 队列 → Worker 执行 → 结果写入返回队列 → Caller 拉取结果
```

- 请求-响应模式
- Caller 提交任务后收到一个 `CompletableFuture<R>`，阻塞等待结果
- 结果通过按 callerId 隔离的返回队列路由回 Caller
- 适用于需要同步获取结果的分布式调用场景

### CALLBACK — 回调通知

```
Caller → Redis 队列 → Worker 执行 → 回调队列 → Caller 的回调线程 POST 结果到回调 URL
```

- 异步通知模式
- Caller 提交任务后立即返回 taskId
- Worker 完成后，Caller 侧的回调线程主动 POST 结果到回调地址
- 支持回调重试；回调失败不影响任务自身状态
- 适用于耗时操作完成后的结果通知场景

### 模式对比

| 特性 | NORMAL | FUNCTION | CALLBACK |
|------|--------|----------|----------|
| 返回方式 | 立即返回 taskId | 阻塞等待结果 | 异步回调通知 |
| 结果获取 | 不获取 | CompletableFuture | HTTP POST |
| Caller 阻塞 | 非阻塞 | 阻塞 | 非阻塞 |
| 回调重试 | 不适用 | 不适用 | 支持 |
| 典型场景 | 日志、推送 | RPC 调用 | 通知、结果回调 |

## 任务生命周期

一个任务从创建到完成会经历以下状态转换：

```
┌──────────────────────────────────────────────────────────┐
│                      Caller 提交任务                       │
│                           │                              │
│                           ▼                              │
│                      [WAITING]                           │
│                    delayTime=0?                          │
│                   ╱            ╲                         │
│                  Yes            No                       │
│                  │               │                       │
│                  ▼               ▼                       │
│           workingDeque      timingSet                    │
│            (immediate)    (scheduled/delayed)            │
│                  │               │                       │
│                  │     定时时间到达                       │
│                  │               │                       │
│                  ◄───────────────╯                       │
│                  │                                       │
│                  ▼                                       │
│              Worker 拉取                                 │
│                  │                                       │
│                  ▼                                       │
│               [PENDING]                                  │
│           (加入 pendingSet)                              │
│                  │                                       │
│                  ▼                                       │
│             执行任务                                     │
│             ╱          ╲                                 │
│          成功            失败                            │
│           │               │                              │
│           ▼               ▼                              │
│       [SUCCESS]    剩余重试次数？                         │
│           │           ╱          ╲                       │
│           │          Yes            No                   │
│           │          │              │                    │
│           │          ▼              ▼                    │
│           │     [WAITING]       [FAIL]                   │
│           │   (retrySet)    (重试次数耗尽)                │
│           │       │              │                       │
│           │  重试时间到达       │                         │
│           │       │              │                       │
│           │       ◄──────────────╯                       │
│           │       │                                      │
│           ▼       ▼                                      │
│          完成                                            │
│           │                                              │
│           ▼                                              │
│   ┌──────────────────────┐                               │
│   │ mode=FUNCTION        │ → 结果推入返回队列              │
│   │ mode=CALLBACK        │ → 结果推入回调队列              │
│   │ mode=NORMAL          │ → 直接结束                     │
│   └──────────────────────┘                               │
└──────────────────────────────────────────────────────────┘
```

### 状态说明

| 状态 | 描述 |
|------|------|
| WAITING | 等待消费，任务位于 workingDeque、timingSet 或 retrySet |
| PENDING | 执行中，任务已从队列中取出并在 pendingSet 中等待确认 |
| SUCCESS | 执行成功 |
| FAIL | 执行失败（重试次数耗尽或超时） |

### 超时与过期

- **执行过期时间（executeExpire）**：从任务创建时间开始计算；在此时间内未完成的任务视为过期。默认为 86400 秒（1 天）。
- **请求超时（requestTimeout）**：在 FUNCTION 模式下，Caller 等待 Worker 返回结果的超时时间。默认为 90 秒。
- **结果过期时间（resultExpire）**：任务完成后结果保留多长时间。默认为 0（不缓存）。FUNCTION/CALLBACK 模式下最小为 60 秒。

### 重试机制

重试计划是一个以秒为单位的整数数组。例如 `[5, 20, 60]` 表示：

- 第 1 次重试：失败后 5 秒
- 第 2 次重试：失败后 20 秒
- 第 3 次重试：失败后 60 秒
- 3 次重试全部失败后，任务被标记为 FAIL

一旦到达重试时间，任务会从 retrySet 移回 workingDeque 等待重新消费。

## Redis 数据模型

每个 topic 在 Redis 中使用以下数据结构：

| Key | 类型 | 描述 |
|-----|------|------|
| `fu-task-{topic}-blocking` | List（RBlockingDeque） | 主工作队列，存储等待消费的任务 ID |
| `fu-task-{topic}-timing` | Sorted Set（RScoredSortedSet） | 定时/延时队列，score 为调度时间戳 |
| `fu-task-{topic}-pending` | Sorted Set（RScoredSortedSet） | 执行中队列，score 为超时时间戳 |
| `fu-task-{topic}-retry` | Sorted Set（RScoredSortedSet） | 重试队列，score 为重试时间戳 |
| `fu-task-{topic}-message:{id}` | Hash（RMap） | 任务消息详情，按生命周期阶段存储字段 |
| `fu-task-{topic}-return:{callerId}` | List（RBlockingDeque） | FUNCTION 模式返回队列，按 callerId 隔离 |
| `fu-task-{topic}-callback` | List（RBlockingDeque） | CALLBACK 模式回调队列 |
| `fu-task-{topic}-callback-pending` | Sorted Set（RScoredSortedSet） | 回调执行中队列，score 为超时时间戳 |

所有队列都会在 24 小时后自动过期，避免无限增长。

## 批处理机制

框架内置 `FuTaskBatchManager` 实现批量操作：

- 达到批量大小阈值（默认 1000）时立即刷新
- 达到时间间隔（默认 50ms）时强制刷新
- 用于批量发送、批量完成确认和批量重试，以减少 Redis 交互

## 策略（Strategy）

在 Worker 端，任务可以通过策略进行分组处理，每个策略可配置：

- **断言规则（assertResult）**：自定义成功判定逻辑
- **事件回调**：onSuccess、onFail、onFinallyFail、onComplete

默认使用 `default` 策略（无断言，始终视为成功）。