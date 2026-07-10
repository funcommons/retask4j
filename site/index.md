---
layout: home
title: retask4j
hero:
  name: retask4j
  text: Distributed async task queue
  tagline: Redis-backed, three-mode, HTTP-proxying. Turn any HTTP call into a reliable, retriable, schedulable async task.
  image:
    src: /logo.svg
    alt: retask4j
  actions:
    - theme: brand
      text: Get Started
      link: /guide/quickstart
    - theme: alt
      text: View on GitHub
      link: https://github.com/funcommons/retask4j
    - theme: alt
      text: REST API
      link: /guide/redis-protocol
features:
  - icon: 🚀
    title: HTTP Proxy Mode
    details: Turn any HTTP request into an async task. Worker re-dispatches to a remote URL or executes locally via Spring's DispatcherServlet. No extra hop.
  - icon: 🔁
    title: Three Modes, One Queue
    details: NORMAL (fire-and-forget), FUNCTION (request-response via CompletableFuture), and CALLBACK (async HTTP notification on completion) — unified in a single Redis-backed queue.
  - icon: 🎯
    title: Per-Request Control
    details: Override retry plan, schedule, callback URL, and response assertions per HTTP call via retask4j-* headers. No code change required.
  - icon: ⚡
    title: 7 Atomic Lua Scripts
    details: Every queue operation runs inside a Lua script. No race conditions, no double-processing, no lost retries.
  - icon: 🛡️
    title: Security by Default
    details: 4-layer SSRF prevention with DNS-rebinding protection. Path traversal, CRLF injection, and unsafe Redis key names all rejected at the boundary.
  - icon: 📊
    title: Web Dashboard
    details: Vue 3 + Element Plus SPA. Topic overview, task detail with replay/force-retry/force-complete/delete, live metrics, alerts, multi-instance discovery, real-time SSE.
  - icon: 🐍
    title: Polyglot via REST
    details: Built-in HTTP gateway lets Python, Go, Node.js, or any language submit, peek, and complete tasks without writing Java.
  - icon: 🪶
    title: Zero Magic
    details: Pure Redisson + Spring Boot. No bespoke protocol, no opaque server. Lua scripts and Redis key format are documented and reproducible.
---
