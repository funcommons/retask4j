---
layout: home
title: retask4j
hero:
  name: retask4j
  text: 分布式异步任务队列
  tagline: 基于 Redis、三种模式、HTTP 代理。把任意 HTTP 调用变成可靠、可重试、可定时的异步任务。
  image:
    src: /logo.svg
    alt: retask4j
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/quickstart
    - theme: alt
      text: 在 GitHub 上查看
      link: https://github.com/funcommons/retask4j
    - theme: alt
      text: REST API
      link: /guide/redis-protocol
features:
  - icon: 🚀
    title: HTTP 代理模式
    details: 把任意 HTTP 请求变成异步任务。Worker 重新分发到远程 URL 或通过 Spring 的 DispatcherServlet 在本地执行。无额外跳数。
  - icon: 🔁
    title: 三种模式，一个队列
    details: NORMAL（即发即弃）、FUNCTION（通过 CompletableFuture 进行请求-响应）和 CALLBACK（完成时异步 HTTP 通知）——统一在单个 Redis 队列中。
  - icon: 🎯
    title: 每次请求可控
    details: 通过 retask4j-* 请求头按每次 HTTP 调用覆盖重试计划、定时、回调 URL 和响应断言。无需更改代码。
  - icon: ⚡
    title: 7 个原子 Lua 脚本
    details: 每个队列操作都在 Lua 脚本内运行。没有竞态条件、没有重复处理、没有丢失的重试。
  - icon: 🛡️
    title: 默认安全
    details: 4 层 SSRF 防护，附带 DNS rebinding 防护。路径穿越、CRLF 注入和不安全的 Redis 键名都在边界被拒绝。
  - icon: 📊
    title: Web 仪表板
    details: Vue 3 + Element Plus SPA。主题概览、任务详情支持重放/强制重试/强制完成/删除、实时指标、告警、多实例发现、实时 SSE。
  - icon: 🐍
    title: 通过 REST 实现多语言
    details: 内置 HTTP 网关，让 Python、Go、Node.js 或任何语言都能提交、查看和完成任务，无需编写 Java。
  - icon: 🪶
    title: 零魔法
    details: 纯 Redisson + Spring Boot。没有定制协议、没有不透明的服务。Lua 脚本和 Redis 键格式都有文档说明且可复现。
---