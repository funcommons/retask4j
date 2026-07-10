# retask4j

A distributed async task queue framework built on Redis (via Redisson) and Spring Boot 3. Turn any synchronous HTTP call into a reliable, retriable, schedulable async task.

## Why retask4j?

Most task queues give you one mode (fire-and-forget, RPC, or callback). retask4j unifies all three on a single Redis-backed queue and adds a unique **HTTP proxy mode**: any HTTP request can be transparently dispatched as an async task, executed on a remote or local worker, and returned as a response — with per-request retry plans, scheduled execution, and response assertions.

## Features

- **Three task modes in one framework**:
  - `NORMAL` — fire-and-forget; caller gets a task ID immediately
  - `FUNCTION` — request-response via `CompletableFuture<R>`
  - `CALLBACK` — async notification when the worker completes
- **HTTP proxy mode** — serialize a full HTTP request as a task, dispatch via Redis, execute on a remote or local worker, return the HTTP response
- **Per-request control** via custom headers:
  - `retask4j-retry-plan` — override the retry schedule
  - `retask4j-task-timing` / `retask4j-task-delay` — scheduled / delayed execution
  - `retask4j-callback-url` — override the callback target
  - `retask4j-assert-response` — assert on the response body or headers
- **Atomic operations** via 7 Lua scripts — no race conditions in the queue
- **Built-in safety**:
  - SSRF prevention with DNS-rebinding protection (4-layer defense)
  - Path traversal protection on document serving
  - Header CRLF injection prevention
  - Redis key safety validation
- **Spring Boot auto-configuration** — drop in the starter, configure in YAML
- **Production-ready observability** — `FuTaskMonitor` exposes caller/worker counters (sends, completes, timeouts, evictions, retries)

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- A running Redis instance (default: `localhost:6379`)

### Build

```bash
mvn clean package -DskipTests
```

### Run the included HTTP server

```bash
mvn spring-boot:run -pl retask4j-http-server
```

The server listens on `http://localhost:9400` and exposes the proxy endpoints configured in `retask4j-http-server/src/main/resources/config.yml`.

### Run the demo caller / worker (in separate terminals)

```bash
# Caller on port 9090
mvn spring-boot:run -pl retask4j-demo-taskcaller

# Worker on port 9091
mvn spring-boot:run -pl retask4j-demo-taskworker
```

### Submit a task

```bash
curl -X POST http://localhost:9090/demo-push/send.do
# => "ok"
```

See [`documents/quickstart.md`](documents/quickstart.md) for a 5-minute walkthrough.

## Module Layout

```
retask4j-core          pure Redisson, no Spring dependency
retask4j-http          HTTP proxy layer (depends on core + spring-boot)
retask4j-http-starter  auto-configuration module (no Java source)
retask4j-http-server   runnable Spring Boot app
retask4j-demo-taskcaller  standalone caller demo
retask4j-demo-taskworker  standalone worker demo
```

## Documentation

| Document | Description |
|---|---|
| [Overview](documents/overview.md) | Positioning, capabilities, use cases |
| [Quickstart](documents/quickstart.md) | 5-minute getting-started |
| [Core Concepts](documents/concepts.md) | Task modes, lifecycle, data model |
| [HTTP Proxy Mode](documents/http-proxy.md) | Endpoint spec, request headers, response assertions |
| [REST API & Redis Protocol](documents/redis-protocol.md) | HTTP gateway for non-Java clients + Redis protocol reference |
| [Configuration](documents/configuration.md) | Full config reference with examples |
| [API Reference](documents/api-reference.md) | Core API and debug interfaces |
| [Best Practices](documents/best-practices.md) | Production deployment, tuning, FAQ |
| [Competitor Comparison](documents/diff-table.md) | Comparison with Celery/Hangfire/BullMQ/Quartz/Redisson |

## Testing

```bash
# Unit tests (no Redis required)
mvn test -pl retask4j-core,retask4j-http

# End-to-end integration tests (requires Redis)
mvn test -pl retask4j-core -Dtest=EndToEnd* -Dredis.host=localhost
```

## License

Apache License 2.0. See [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
