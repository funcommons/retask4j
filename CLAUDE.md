# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules (skip tests)
mvn clean package -DskipTests

# Build a single module
mvn clean package -pl retask4j-core -DskipTests

# Run tests (both core and HTTP modules have tests)
mvn test -pl retask4j-core
mvn test -pl retask4j-http

# Run a single test class
mvn test -pl retask4j-core -Dtest=FuTaskBatchManagerTest
mvn test -pl retask4j-http -Dtest=FuHttpTaskCallerConfigValidationTest

# Run the HTTP server
mvn spring-boot:run -pl retask4j-http-server

# Run demo caller (port 9090) / demo worker (port 9091)
mvn spring-boot:run -pl retask4j-demo-taskcaller
mvn spring-boot:run -pl retask4j-demo-taskworker
```

Requires Java 17+ and a running Redis instance.

## Architecture

retask4j is a distributed async task queue framework built on Redis (via Redisson) and Spring Boot 3.4. It implements a producer-consumer pattern where Callers submit tasks and Workers execute them.

### Module Dependency Graph

```
retask4j-core (no Spring dependency, pure Redisson)
    ↑
retask4j-http (core + spring-boot-starter-web)
    ↑
retask4j-http-starter (auto-configuration module, no Java source)
    ↑
retask4j-http-server (runnable Spring Boot app, port 9093)

retask4j-demo-taskcaller (standalone caller demo, depends on core only)
retask4j-demo-taskworker (standalone worker demo, depends on core only)
```

### Core Abstractions (retask4j-core)

Core classes are organized into subpackages by responsibility:

- **`internal`** — `FuTaskBase` (Redis data structures, Lua scripts), `FuTaskBatchManager` (batching utility)
- **`api`** — `FuTaskCaller<R>` (producer), `FuTaskWorker` (consumer), `FuTaskExecutor<T,R>` (execution wrapper)
- **`config`** — `FuTaskBaseConfig`, `FuTaskCallConfig<R>`, `FuTaskWorkConfig` (configuration POJOs)
- **`message`** — `FuTaskMessage`, `FuTaskMode`, `FuTaskStatus` (task message model and enums)
- **`strategy`** — `FuTaskWorkStrategy` (per-route worker strategy)
- **`monitor`** — `FuTaskMonitor` (metrics/counters)
- **`exception`** — `FuTaskExpiredException`, `FuTaskRetryExhaustedException`, `FuTaskAssertionException`

Key classes:
- **`FuTaskBase`** — Foundation class managing all Redis data structures and 7 Lua scripts for atomic queue operations. Redis key pattern: `fu-task-{topic}-{purpose}`.
- **`FuTaskCaller<R>`** — Producer side. Extends FuTaskBase. Manages a Guava Cache for FUNCTION-mode return futures. Background threads poll return/callback queues.
- **`FuTaskWorker`** — Consumer side. Extends FuTaskBase. Background threads poll the work deque, move expired pending items back, and transfer due timing/retry items. Delegates execution to a `FuTaskExecutor`.
- **`FuTaskMessage`** — Task message model with `@FuTag` annotations marking fields by lifecycle phase (request/retry/complete/callback). Provides phase-specific map projections.

### Three Task Modes

- **NORMAL** — Fire-and-forget. Caller gets taskId immediately.
- **FUNCTION** — Request-response via `CompletableFuture<R>`. Results routed to per-callerId return queue.
- **CALLBACK** — Async notification. Worker completion triggers a callback Consumer (HTTP module POSTs to a URL).

### Task Lifecycle

```
WAITING → PENDING → SUCCESS (complete)
                  → FAIL (retry if retries remain → WAITING)
                  → FAIL (retries exhausted → complete with error)
```

### Redis Data Model (per topic)

| Key Pattern | Type | Purpose |
|---|---|---|
| `fu-task-{topic}-blocking` | RBlockingDeque | Main work queue |
| `fu-task-{topic}-timing` | RScoredSortedSet | Delayed messages (score=timestamp) |
| `fu-task-{topic}-pending` | RScoredSortedSet | In-progress tasks (score=timeout) |
| `fu-task-{topic}-retry` | RScoredSortedSet | Scheduled retries (score=retry time) |
| `fu-task-{topic}-message:{id}` | RMap | Task message hash |
| `fu-task-{topic}-return:{callerId}` | RBlockingDeque | FUNCTION-mode return queue |
| `fu-task-{topic}-callback` | RBlockingDeque | Callback queue |
| `fu-task-{topic}-callback-pending` | RScoredSortedSet | Callback pending with timeout |

### HTTP Proxy Layer (retask4j-http)

Turns the task queue into an HTTP proxy. Callers accept HTTP requests and serialize them as task messages; Workers deserialize and execute them (remote via RestTemplate or local via MockMvc dispatch).

- **Caller side**: `FuHttpTaskCallerController` is dynamically registered per config using `RequestMappingHandlerMapping.registerMapping()`. Supports special headers: `retask4j-retry-plan`, `retask4j-task-timing`, `retask4j-task-delay`, `retask4j-assert-response`, `retask4j-callback-url`.
- **Worker side**: `FuHttpTaskWorkerService.doExecute()` matches request URL against route configs, rewrites headers/URL, executes remote or local HTTP call, and runs response assertions.

### Configuration

Spring Boot auto-configuration via `retask4j-http-starter`. Configure in YAML under `retask4j.http`:
- `redis` — Redisson connection config
- `callers` — List of caller configs (topic, path, mode, retry plan, etc.)
- `workers` — List of worker configs (topic, routes with redirect/header-rewrite/assertions)

### Key Libraries

Redisson 3.41.0, fastjson2 2.0.53, Guava 33.4.0, Lombok 1.18.36, Log4j2 (logback excluded), commons-compress (HTTP body decompression).
