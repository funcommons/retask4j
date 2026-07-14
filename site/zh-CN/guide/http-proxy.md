---
title: HTTP 代理模式
---


HTTP 代理模式是使用 retask4j 的主要方式。Caller 端接收 HTTP 请求，将请求序列化为任务消息并推送到 Redis 队列；Worker 端从队列中消费，反序列化并执行 HTTP 调用，然后返回结果。

## 架构

```
┌────────────┐     HTTP      ┌──────────────┐    Redis    ┌──────────────┐    HTTP     ┌──────────────┐
│   Client    │ ──────────► │    Caller     │ ────────► │    Worker     │ ────────► │  Target URL  │
│  (Caller)   │              │  (Proxy Entry) │            │  (Worker Node)│            │  (Target Svc) │
└────────────┘              └──────────────┘            └──────────────┘            └──────────────┘
                                  │                                                    │
                                  │◄─────────────── Redis 返回队列 ──────────────────────┘
                                  │              (FUNCTION 模式同步返回)
                                  │
                                  │◄─────────────── HTTP POST 回调 ──────────────────────┘
                                                 (CALLBACK 模式异步回调)
```

## URL 路由规则

Caller 的请求路径格式为：

```
{path}/{target-url}
```

其中 `{path}` 是配置中的 `path` 字段，`{target-url}` 是目标请求地址。

支持两种 URL 格式：

| 格式 | 示例 | 描述 |
|------|------|------|
| 带 `://` | `/proxy/call/https://httpbin.org/get` | 标准写法 |
| 用 `/` 代替 `://` | `/proxy/call/https/httpbin.org/get` | 简写格式 |

两种格式等价，框架会自动将 `https/` 转换为 `https://`。

当目标 URL 不以 `/` 或 `http://`/`https://` 开头时，会自动添加 `/` 前缀并视为本地路径。

## 三种模式接口

### NORMAL — 普通推送

```
POST/GET {path}/{target-url}
```

提交任务，立即返回 taskId：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "taskId": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX"
  }
}
```

适用于不需要返回结果的异步任务。

### FUNCTION — 同步调用

```
POST/GET {path}/{target-url}
```

提交任务后，HTTP 连接保持打开（Servlet 异步），等待 Worker 执行完成，同步返回目标 URL 的响应内容。

- 默认超时为 60 秒
- 返回内容为目标 URL 的原始响应（包括状态码、响应头、响应体）

适用于需要同步获取结果的场景。

### CALLBACK — 回调通知

```
POST/GET {path}/{target-url}
```

提交任务后立即返回 taskId；Worker 完成后主动将结果 POST 到回调地址。

回调数据（application/json）：

```json
{
  "id": "Task ID",
  "response": {
    "body": { "key": "value" },
    "headers": { "Content-Type": ["application/json; charset=utf-8"] },
    "status": 200
  },
  "status": "SUCCESS",
  "completeTime": 1736850478880,
  "executeTime": 1736850465689
}
```

回调地址配置方式（优先级从高到低）：

1. 请求头 `retask4j-callback-url`
2. 配置文件 `callback-url`

回调成功标准：回调目标返回 HTTP 200。非 200 触发回调重试。

## 请求头控制

Caller 支持通过请求头动态控制任务行为；请求头优先级高于配置文件。

### retask4j-retry-plan

自定义重试计划，覆盖配置文件中的 `retry-plan`。

```bash
curl -H "retask4j-retry-plan: [5,20,60,120]" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

值为 JSON 数组，单位为秒。

### retask4j-task-timing

指定定时执行时间；在指定时间之前任务不会被消费。

```bash
curl -H "retask4j-task-timing: 1737077674000" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

- 支持 13 位毫秒时间戳和 10 位秒时间戳，秒级精度
- 如果值小于当前时间，则立即执行
- 不支持调度超过 24 小时

### retask4j-task-delay

指定延时执行；在指定的秒数后任务才会被消费。

```bash
curl -H "retask4j-task-delay: 300" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

- 允许范围为 1 ~ 3600（1 秒 ~ 1 小时）
- 不能与 `retask4j-task-timing` 同时使用

### retask4j-assert-response

自定义响应断言，覆盖配置文件中的 `assert-response`。

```bash
curl -H 'retask4j-assert-response: {"statusIn":[200],"jsonPathMatch":{"$.code":"0"}}' \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

值为 JSON 对象，格式参见 [响应断言](#响应断言)。

### retask4j-callback-url

指定回调地址，覆盖配置文件中的 `callback-url`。仅在 CALLBACK 模式下生效。

```bash
curl -H "retask4j-callback-url: http://your-server/callback" \
     http://localhost:9093/proxy/task/https://httpbin.org/post
```

## 响应断言

Worker 完成 HTTP 调用后，可以对响应进行断言检查。断言失败会抛出异常，任务进入重试流程。

### statusIn

限制允许的 HTTP 状态码：

```yaml
assert-response:
  statusIn: [200, 301, 302]
```

为空或未配置时，不进行状态码检查。

### headerMatch

响应头必须匹配的正则表达式；多个条件必须同时满足：

```yaml
assert-response:
  headerMatch:
    "Content-Type": "application/json(;charset=.+)?"
```

Key 为响应头名称（不区分大小写），Value 为正则表达式。

### textBodyMatch

响应体（文本）必须匹配的正则表达式：

```yaml
assert-response:
  textBodyMatch: "success|SUCCESS|ok|OK|true"
```

### jsonPathMatch

通过 JSONPath 从响应体（JSON）中提取值并与正则表达式匹配；多个条件必须同时满足：

```yaml
assert-response:
  jsonPathMatch:
    "$.code": "0"
    "$.msg": "success"
```

> 注意：当前 JSONPath 使用 fastjson2 的字段路径解析，仅支持简单的 `$.field.subField` 格式，不支持完整的 JSONPath 语法。

### 断言优先级

1. 请求头 `retask4j-assert-response`（最高）
2. Worker 路由配置 `assert-response`

## Worker 路由

Worker 使用路由配置来控制请求的执行方式。路由按配置顺序匹配，第一个匹配的路由生效。`path: "*"` 是默认路由，匹配所有请求。

### 远程调用

当目标 URL 以 `http://` 或 `https://` 开头时，Worker 通过 RestTemplate 发起远程 HTTP 调用。

### 本地调用

当目标 URL 不以 `http://` 或 `https://` 开头时，Worker 通过 MockMvc 在本地 Spring 上下文内执行请求。

可以通过 `enableRemote` 和 `enableLocal` 控制开关：

```yaml
workers:
  - topic: "proxy"
    enableRemote: true
    enableLocal: true
```

### URL 重写（redirect）

使用正则匹配 + 捕获组替换实现 URL 重写：

```yaml
routes:
  - path: "http://www\\.baidu\\.com/(.+)"
    redirect: "https://www.baidu.com/&1"
```

- `path`：正则表达式
- `redirect`：替换模板，`&1`、`&2` 对应正则的捕获组

### 请求头重写

```yaml
routes:
  - path: "*"
    rewrite-request-headers:
      "my-token1": null     # 删除请求头（值为 null）
      "my-token2": "123"    # 设置/覆盖请求头
```

### 响应头重写

```yaml
routes:
  - path: "*"
    rewrite-response-headers:
      "Access-Control-Allow-Origin": "*"
      "Timing-Allow-Origin": "*"
      "etag": null           # 删除响应头
```

## 支持的 HTTP 特性

| 特性 | 描述 |
|------|------|
| HTTP 方法 | 包括 GET、POST、PUT、DELETE、PATCH 等所有方法 |
| 协议 | HTTP、HTTPS |
| Content-Type | text/html、application/json、form-data、x-www-form-urlencoded 等 |
| 压缩 | gzip、deflate、br（Brotli）、zstd |
| 文件上传 | 支持 multipart/form-data（注意：文件内容会存入 Redis，请考虑内存） |
| CORS | 通过 Worker 的响应头重写配置 CORS |

## 请求头优先级概览

请求头中的配置始终优先于配置文件：

| 请求头 | 对应配置 |
|--------|-----------|
| `retask4j-retry-plan` | `callers[].retry-plan` |
| `retask4j-task-timing` | — |
| `retask4j-task-delay` | — |
| `retask4j-assert-response` | `workers[].routes[].assert-response` |
| `retask4j-callback-url` | `callers[].callback-url` |