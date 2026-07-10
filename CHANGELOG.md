# Changelog

All notable changes to retask4j are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-11

### Added

- Three task modes in a single Redis-backed queue: `NORMAL` (fire-and-forget), `FUNCTION` (request-response via `CompletableFuture<R>`), and `CALLBACK` (async HTTP POST notification).
- HTTP proxy mode: serialize any HTTP request as a task, dispatch via Redis, execute on a remote or local worker, return the HTTP response.
- Per-request control via custom headers: `retask4j-retry-plan`, `retask4j-task-timing`, `retask4j-task-delay`, `retask4j-callback-url`, `retask4j-assert-response`.
- 7 atomic Lua scripts for queue operations: `push_task_message_deque_batch`, `complete_batch`, `retry_batch`, `remove_store_set_to_list`, `get_task_messages_for_work`, `get_task_messages_by_id`, `set_callback_batch`.
- 4 admin Lua scripts: `delete_task`, `replay_task`, `force_retry_task`, `force_complete_task`.
- `FuTaskAdminService` exposing `peek`/`replay`/`forceRetry`/`forceComplete`/`delete` operations.
- Spring Boot auto-configuration via `retask4j-http-starter` (conditional on `redisson` classpath, redis property config).
- Web dashboard: Vue 3 + Element Plus SPA with:
  - Queue overview with metric cards
  - Topics list with click-to-view tasks
  - Task detail drawer with JSON-formatted input/output/error and 5 actions (replay, force-retry, force-complete-success, force-complete-fail, delete)
  - Real-time SSE push for task events
  - Metrics view with success-rate progress bars
  - Alerts view with active banner + history
  - Multi-instance discovery via Redis heartbeats
  - Quick Tester for end-to-end task submission
  - i18n (zh-CN / en-US), theme switching (light/dark/orange-black), brand (mchuan)
  - Localized via per-request `Accept-Language` + `X-Dashboard-Token` auth
- Safety:
  - SSRF protection with DNS-rebinding prevention (4-layer defense)
  - Path traversal protection on document serving
  - Header CRLF injection prevention
  - Redis key safety validation (no `:/{/}`, control chars, length limits)
- Admin alerts with rule evaluation and webhook delivery.
- 10 markdown documentation files in `documents/`: overview, quickstart, concepts, http-proxy, configuration, api-reference, best-practices, diff-table, readme, index.
- 236 unit tests + 4 end-to-end integration tests (require Redis).
- Apache 2.0 license, CONTRIBUTING.md, README with quick-start, GitHub-ready .gitignore.

### Modules

- `retask4j-core` — pure Redisson-based queue engine (no Spring)
- `retask4j-http` — HTTP proxy layer (depends on core + spring-boot)
- `retask4j-http-starter` — Spring Boot auto-configuration
- `retask4j-http-server` — runnable Spring Boot app with embedded dashboard
- `retask4j-demo-taskcaller` / `retask4j-demo-taskworker` — standalone demo applications

[1.0.0]: https://github.com/funcommons/retask4j/releases/tag/v1.0.0
