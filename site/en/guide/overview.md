---
title: Product Overview
---


## What is retask4j

retask4j is a Redis-based distributed asynchronous task queue framework, built with Java 17 + Spring Boot 3.4. It implements the producer-consumer pattern, supporting the submission of tasks from a Caller to a Redis queue, which are then asynchronously consumed and executed by Workers.

## Core Capabilities

- **Three Task Modes**: Normal Push (NORMAL), Synchronous Call (FUNCTION), Callback Notification (CALLBACK), covering the main asynchronous processing scenarios
- **Reliable Retry**: Supports custom retry plans, automatically retrying failed task executions according to strategy
- **Scheduled and Delayed**: Supports scheduled tasks and delayed messages with second-level precision
- **HTTP Proxy**: Built-in HTTP proxy mode; the Caller forwards HTTP requests to Workers for execution, supporting both remote and local invocation
- **Response Assertion**: Supports assertion validation of Worker execution results; assertion failures can trigger retries
- **Batch Processing**: Built-in batch send and batch acknowledge mechanism to reduce Redis interactions
- **Atomic Operations**: Core queue operations are implemented via Lua scripts, ensuring atomicity of Redis operations

## Use Cases

| Scenario | Description |
|------|------|
| HTTP Proxy Forwarding | Forward requests to remote or local services for execution, with retry and assertion support |
| Asynchronous Task Processing | Make time-consuming operations asynchronous, such as file processing, data synchronization, message pushing |
| Distributed RPC | Cross-service synchronous calls implementing request-response via FUNCTION mode |
| Event Callback Notification | Notify business systems via HTTP callback upon task completion |
| Scheduled/Delayed Tasks | Delayed message delivery, scheduled task scheduling |

## Technology Stack

| Component | Version | Description |
|------|------|------|
| Java | 17+ | Runtime environment |
| Spring Boot | 3.4.1 | Application framework |
| Redisson | 3.41.0 | Redis client providing Lua script and distributed data structure support |
| fastjson2 | 2.0.53 | JSON serialization |
| Guava | 33.4.0 | Local cache (FUNCTION mode return value cache) |

## Module Composition

```
retask4j-core          Core layer, task queue data structures and Redis interaction logic, no Spring dependency
retask4j-http          HTTP transport layer, implementing HTTP proxy Caller/Worker
retask4j-http-starter  Spring Boot Starter, auto-configuration module
retask4j-http-server   Runnable HTTP proxy server
retask4j-demo-taskcaller  Caller usage example
retask4j-demo-taskworker  Worker usage example
```

Module dependency relationship:

```
retask4j-core
    ↑
retask4j-http
    ↑
retask4j-http-starter
    ↑
retask4j-http-server
```
