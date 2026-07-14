---
title: 产品概览
---


## 什么是 retask4j

retask4j 是一个基于 Redis 的分布式异步任务队列框架，基于 Java 17 + Spring Boot 3.4 构建。它实现了生产者-消费者模式，支持从 Caller 向 Redis 队列提交任务，然后由 Worker 异步消费并执行。

## 核心能力

- **三种任务模式**：普通推送（NORMAL）、同步调用（FUNCTION）、回调通知（CALLBACK），覆盖主要的异步处理场景
- **可靠重试**：支持自定义重试计划，按照策略自动重试失败的任务执行
- **定时与延时**：支持秒级精度的定时任务和延时消息
- **HTTP 代理**：内置 HTTP 代理模式；Caller 将 HTTP 请求转发给 Worker 执行，支持远程和本地调用
- **响应断言**：支持对 Worker 执行结果进行断言验证；断言失败可触发重试
- **批量处理**：内置批量发送和批量确认机制，减少 Redis 交互
- **原子操作**：核心队列操作通过 Lua 脚本实现，确保 Redis 操作的原子性

## 使用场景

| 场景 | 描述 |
|------|------|
| HTTP 代理转发 | 将请求转发到远程或本地服务执行，支持重试和断言 |
| 异步任务处理 | 将耗时操作异步化，例如文件处理、数据同步、消息推送 |
| 分布式 RPC | 通过 FUNCTION 模式实现跨服务同步调用，支持请求-响应 |
| 事件回调通知 | 任务完成后通过 HTTP 回调通知业务系统 |
| 定时/延时任务 | 延时消息投递、定时任务调度 |

## 技术栈

| 组件 | 版本 | 描述 |
|------|------|------|
| Java | 17+ | 运行时环境 |
| Spring Boot | 3.4.1 | 应用框架 |
| Redisson | 3.41.0 | Redis 客户端，提供 Lua 脚本和分布式数据结构支持 |
| fastjson2 | 2.0.53 | JSON 序列化 |
| Guava | 33.4.0 | 本地缓存（FUNCTION 模式返回值缓存） |

## 模块组成

```
retask4j-core          核心层，任务队列数据结构和 Redis 交互逻辑，无 Spring 依赖
retask4j-http          HTTP 传输层，实现 HTTP 代理 Caller/Worker
retask4j-http-starter  Spring Boot Starter，自动配置模块
retask4j-http-server   可运行的 HTTP 代理服务器
retask4j-demo-taskcaller  Caller 使用示例
retask4j-demo-taskworker  Worker 使用示例
```

模块依赖关系：

```
retask4j-core
    ↑
retask4j-http
    ↑
retask4j-http-starter
    ↑
retask4j-http-server
```