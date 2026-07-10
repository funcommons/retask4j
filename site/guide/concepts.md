---
title: Core Concepts
---


## Task Modes

retask4j supports three task communication modes, covering scenarios from simple asynchronous to complex callbacks.

### NORMAL — Plain Push

```
Caller → Redis Queue → Worker Execution
```

- Fire-and-forget
- The Caller returns the taskId immediately after submitting the task
- Does not care about the execution result; suitable for log writes, message pushes, etc.

### FUNCTION — Synchronous Call

```
Caller → Redis Queue → Worker Execution → Result written to Return Queue → Caller pulls result
```

- Request-response mode
- The Caller receives a `CompletableFuture<R>` after submitting a task, blocking while waiting for the result
- Results are routed back to the Caller through a Return Queue isolated by callerId
- Suitable for distributed call scenarios that need to synchronously obtain results

### CALLBACK — Callback Notification

```
Caller → Redis Queue → Worker Execution → Callback Queue → Caller's callback thread POSTs result to callback URL
```

- Asynchronous notification mode
- The Caller returns the taskId immediately after submitting the task
- Once the Worker is done, the callback thread on the Caller side actively POSTs the result to the callback address
- Supports callback retries; callback failures do not affect the task's own status
- Suitable for result notification scenarios after time-consuming operations complete

### Mode Comparison

| Feature | NORMAL | FUNCTION | CALLBACK |
|------|--------|----------|----------|
| Return Method | Immediately return taskId | Block waiting for result | Async callback notification |
| Result Retrieval | Not retrieved | CompletableFuture | HTTP POST |
| Caller Blocking | Non-blocking | Blocking | Non-blocking |
| Callback Retry | Not applicable | Not applicable | Supported |
| Typical Scenario | Logging, push | RPC call | Notification, result callback |

## Task Lifecycle

A task goes through the following state transitions from creation to completion:

```
┌──────────────────────────────────────────────────────────┐
│                      Caller submits task                  │
│                           │                              │
│                           ▼                              │
│                      [WAITING]                           │
│                    delayTime=0?                           │
│                   ╱            ╲                          │
│                  Yes            No                        │
│                  │               │                        │
│                  ▼               ▼                        │
│           workingDeque      timingSet                    │
│            (immediate)    (scheduled/delayed)             │
│                  │               │                        │
│                  │     Schedule time arrives              │
│                  │               │                        │
│                  ◄───────────────╯                        │
│                  │                                        │
│                  ▼                                        │
│              Worker pulls                                  │
│                  │                                        │
│                  ▼                                        │
│               [PENDING]                                   │
│           (added to pendingSet)                            │
│                  │                                        │
│                  ▼                                        │
│             Execute task                                   │
│             ╱          ╲                                  │
│          Success         Fail                             │
│           │               │                              │
│           ▼               ▼                              │
│       [SUCCESS]    Retries remaining?                     │
│           │           ╱          ╲                        │
│           │          Yes            No                    │
│           │          │              │                     │
│           │          ▼              ▼                     │
│           │     [WAITING]       [FAIL]                    │
│           │   (retrySet)    (retries exhausted)            │
│           │       │              │                        │
│           │  Retry time arrives  │                        │
│           │       │              │                        │
│           │       ◄──────────────╯                        │
│           │       │                                       │
│           ▼       ▼                                       │
│          Complete                                         │
│           │                                              │
│           ▼                                              │
│   ┌──────────────────────┐                               │
│   │ mode=FUNCTION        │ → Result pushed to Return Queue│
│   │ mode=CALLBACK        │ → Result pushed to Callback Queue│
│   │ mode=NORMAL          │ → Finish directly              │
│   └──────────────────────┘                               │
└──────────────────────────────────────────────────────────┘
```

### Status Description

| Status | Description |
|------|------|
| WAITING | Waiting for consumption, the task is in workingDeque, timingSet, or retrySet |
| PENDING | Executing, the task has been taken from the queue and waits in pendingSet for acknowledgment |
| SUCCESS | Executed successfully |
| FAIL | Execution failed (retries exhausted or timed out) |

### Timeout and Expiration

- **Execution Expire (executeExpire)**: Counted from task creation time; tasks not completed within this time are considered expired. Default is 86400 seconds (1 day).
- **Request Timeout (requestTimeout)**: In FUNCTION mode, the timeout for the Caller waiting for the Worker's return result. Default is 90 seconds.
- **Result Expire (resultExpire)**: How long the result is retained after task completion. Default is 0 (no caching). Minimum 60 seconds in FUNCTION/CALLBACK mode.

### Retry Mechanism

A retry plan is an integer array in seconds. For example, `[5, 20, 60]` means:

- 1st retry: 5 seconds after failure
- 2nd retry: 20 seconds after failure
- 3rd retry: 60 seconds after failure
- After all 3 retries fail, the task is marked as FAIL

Once the retry time arrives, the task is moved from retrySet to workingDeque for re-consumption.

## Redis Data Model

Each topic uses the following data structures in Redis:

| Key | Type | Description |
|-----|------|------|
| `fu-task-{topic}-blocking` | List (RBlockingDeque) | Main work queue, storing task IDs awaiting consumption |
| `fu-task-{topic}-timing` | Sorted Set (RScoredSortedSet) | Scheduled/delayed queue, score is the dispatch timestamp |
| `fu-task-{topic}-pending` | Sorted Set (RScoredSortedSet) | In-progress queue, score is the timeout timestamp |
| `fu-task-{topic}-retry` | Sorted Set (RScoredSortedSet) | Retry queue, score is the retry timestamp |
| `fu-task-{topic}-message:{id}` | Hash (RMap) | Task message details, fields stored by lifecycle phase |
| `fu-task-{topic}-return:{callerId}` | List (RBlockingDeque) | FUNCTION mode return queue, isolated by callerId |
| `fu-task-{topic}-callback` | List (RBlockingDeque) | CALLBACK mode callback queue |
| `fu-task-{topic}-callback-pending` | Sorted Set (RScoredSortedSet) | Callback in-progress queue, score is the timeout timestamp |

All queues automatically expire after 24 hours to avoid infinite growth.

## Batch Processing Mechanism

The framework has a built-in `FuTaskBatchManager` to implement batch operations:

- Immediately flush when the batch size threshold (default 1000) is reached
- Force flush when the time interval (default 50ms) is reached
- Used for batch sending, batch completion acknowledgment, and batch retries to reduce Redis interactions

## Strategy

On the Worker side, tasks can be grouped for processing by strategy, with each strategy configurable for:

- **Assertion Rule (assertResult)**: Custom success determination logic
- **Event Callbacks**: onSuccess, onFail, onFinallyFail, onComplete

The `default` strategy is used by default (no assertion, always considered successful).
