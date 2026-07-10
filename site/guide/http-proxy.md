---
title: HTTP Proxy Mode
---


The HTTP proxy mode is the primary way to use retask4j. The Caller side receives HTTP requests, serializes the requests as task messages and pushes them to the Redis queue; the Worker side consumes from the queue, deserializes and executes the HTTP call, and returns the result.

## Architecture

```
┌────────────┐     HTTP      ┌──────────────┐    Redis    ┌──────────────┐    HTTP     ┌──────────────┐
│   Client    │ ──────────► │    Caller     │ ────────► │    Worker     │ ────────► │  Target URL  │
│  (Caller)   │              │  (Proxy Entry) │            │  (Worker Node)│            │  (Target Svc) │
└────────────┘              └──────────────┘            └──────────────┘            └──────────────┘
                                  │                                                    │
                                  │◄─────────────── Redis Return Queue ────────────────┘
                                  │              (FUNCTION mode synchronous return)
                                  │
                                  │◄─────────────── HTTP POST Callback ────────────────┘
                                                 (CALLBACK mode async callback)
```

## URL Routing Rules

The Caller's request path format is:

```
{path}/{target-url}
```

Where `{path}` is the `path` field in the configuration and `{target-url}` is the target request address.

Two URL formats are supported:

| Format | Example | Description |
|------|------|------|
| With `://` | `/proxy/call/https://httpbin.org/get` | Standard notation |
| Use `/` instead of `://` | `/proxy/call/https/httpbin.org/get` | Shorthand format |

The two formats are equivalent; the framework automatically converts `https/` to `https://`.

When the target URL does not start with `/` or `http://`/`https://`, a `/` prefix is automatically added and treated as a local path.

## Three Mode Interfaces

### NORMAL — Plain Push

```
POST/GET {path}/{target-url}
```

Submit a task, immediately return a taskId:

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "taskId": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX"
  }
}
```

Suitable for asynchronous tasks that do not need a return result.

### FUNCTION — Synchronous Call

```
POST/GET {path}/{target-url}
```

After submitting the task, the HTTP connection is kept open (Servlet Async), waiting for the Worker's execution to complete and synchronously returning the target URL's response content.

- Default timeout is 60 seconds
- The return content is the target URL's raw response (including status code, response headers, response body)

Suitable for scenarios that require synchronously obtaining results.

### CALLBACK — Callback Notification

```
POST/GET {path}/{target-url}
```

After submitting the task, the taskId is returned immediately; after the Worker completes, it actively POSTs the result to the callback address.

Callback data (application/json):

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

Callback address configuration methods (priority from highest to lowest):

1. Request header `retask4j-callback-url`
2. Configuration file `callback-url`

Callback success criteria: The callback target returns HTTP 200. Non-200 triggers callback retry.

## Request Header Control

The Caller supports dynamically controlling task behavior via request headers; request headers take priority over the configuration file.

### retask4j-retry-plan

Custom retry plan, overriding the `retry-plan` in the configuration file.

```bash
curl -H "retask4j-retry-plan: [5,20,60,120]" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

The value is a JSON array, in seconds.

### retask4j-task-timing

Specify the scheduled execution time; the task will not be consumed until the specified time.

```bash
curl -H "retask4j-task-timing: 1737077674000" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

- Supports 13-digit millisecond timestamps and 10-digit second timestamps, with second precision
- If the value is less than the current time, execute immediately
- Does not support scheduling beyond 24 hours

### retask4j-task-delay

Specify delayed execution; the task will be consumed after the specified number of seconds.

```bash
curl -H "retask4j-task-delay: 300" \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

- Allowed range is 1 ~ 3600 (1 second ~ 1 hour)
- Cannot be used together with `retask4j-task-timing`

### retask4j-assert-response

Custom response assertion, overriding the `assert-response` in the configuration file.

```bash
curl -H 'retask4j-assert-response: {"statusIn":[200],"jsonPathMatch":{"$.code":"0"}}' \
     http://localhost:9093/proxy/push/https://httpbin.org/post
```

The value is a JSON object, see [Response Assertion](#response-assertion) for the format.

### retask4j-callback-url

Specify the callback address, overriding `callback-url` in the configuration file. Only valid in CALLBACK mode.

```bash
curl -H "retask4j-callback-url: http://your-server/callback" \
     http://localhost:9093/proxy/task/https://httpbin.org/post
```

## Response Assertion

After the Worker finishes the HTTP call, it can perform assertion checks on the response. An assertion failure throws an exception, and the task enters the retry flow.

### statusIn

Limit the allowed HTTP status codes:

```yaml
assert-response:
  statusIn: [200, 301, 302]
```

If empty or not configured, no status code check is performed.

### headerMatch

Regular expressions that response headers must match; multiple conditions must all be satisfied:

```yaml
assert-response:
  headerMatch:
    "Content-Type": "application/json(;charset=.+)?"
```

The Key is the response header name (case-insensitive), the Value is the regular expression.

### textBodyMatch

Regular expression that the response body (text) must match:

```yaml
assert-response:
  textBodyMatch: "success|SUCCESS|ok|OK|true"
```

### jsonPathMatch

Extract values from the response body (JSON) via JSONPath and match against regular expressions; multiple conditions must all be satisfied:

```yaml
assert-response:
  jsonPathMatch:
    "$.code": "0"
    "$.msg": "success"
```

> Note: The current JSONPath uses fastjson2's field path parsing, which only supports a simple `$.field.subField` format and does not support the full JSONPath syntax.

### Assertion Priority

1. Request header `retask4j-assert-response` (highest)
2. Worker route configuration `assert-response`

## Worker Routing

The Worker uses route configuration to control how requests are executed. Routes are matched in configuration order, and the first matching route takes effect. `path: "*"` is the default route and matches all requests.

### Remote Call

When the target URL starts with `http://` or `https://`, the Worker initiates a remote HTTP call via RestTemplate.

### Local Call

When the target URL does not start with `http://` or `https://`, the Worker executes the request within the local Spring context via MockMvc.

Switches can be controlled via `enableRemote` and `enableLocal`:

```yaml
workers:
  - topic: "proxy"
    enableRemote: true
    enableLocal: true
```

### URL Rewrite (redirect)

Implement URL rewriting using regex matching + capture group replacement:

```yaml
routes:
  - path: "http://www\\.baidu\\.com/(.+)"
    redirect: "https://www.baidu.com/&1"
```

- `path`: Regular expression
- `redirect`: Replacement template, `&1`, `&2` correspond to regex capture groups

### Request Header Rewrite

```yaml
routes:
  - path: "*"
    rewrite-request-headers:
      "my-token1": null     # Delete the request header (value is null)
      "my-token2": "123"    # Set/overwrite the request header
```

### Response Header Rewrite

```yaml
routes:
  - path: "*"
    rewrite-response-headers:
      "Access-Control-Allow-Origin": "*"
      "Timing-Allow-Origin": "*"
      "etag": null           # Delete the response header
```

## Supported HTTP Features

| Feature | Description |
|------|------|
| HTTP Methods | All methods including GET, POST, PUT, DELETE, PATCH, etc. |
| Protocols | HTTP, HTTPS |
| Content-Type | text/html, application/json, form-data, x-www-form-urlencoded, etc. |
| Compression | gzip, deflate, br (Brotli), zstd |
| File Upload | Supports multipart/form-data (note: file contents are stored in Redis, consider memory) |
| CORS | Configure CORS via the Worker's response header rewriting |

## Request Header Priority Overview

The configuration in request headers always takes precedence over the configuration file:

| Request Header | Corresponding Configuration |
|--------|-----------|
| `retask4j-retry-plan` | `callers[].retry-plan` |
| `retask4j-task-timing` | — |
| `retask4j-task-delay` | — |
| `retask4j-assert-response` | `workers[].routes[].assert-response` |
| `retask4j-callback-url` | `callers[].callback-url` |
