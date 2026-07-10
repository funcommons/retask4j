---
title: Configuration Reference
---


## Configuration Structure

All configurations are under the `retask4j.http` namespace, and contain three parts:

```yaml
retask4j:
  http:
    redis: ...       # Redis connection configuration
    callers: [...]   # Caller configuration list
    workers: [...]   # Worker configuration list
```

## Redis Configuration

The configuration items correspond to Redisson's configuration format, supporting single-node, sentinel, and cluster modes.

### Single-Node Mode

```yaml
retask4j:
  http:
    redis:
      redisson:
        singleServerConfig:
          address: "redis://localhost:6379"
          database: 0
          password: null
          idleConnectionTimeout: 10000
          connectTimeout: 10000
          timeout: 60000
          retryAttempts: 3
          retryInterval: 1500
          subscriptionsPerConnection: 5
          subscriptionConnectionMinimumIdleSize: 1
          subscriptionConnectionPoolSize: 50
          connectionMinimumIdleSize: 8
          connectionPoolSize: 16
          dnsMonitoringInterval: 5000
        threads: 0
        nettyThreads: 0
```

### Sentinel Mode

```yaml
retask4j:
  http:
    redis:
      redisson:
        sentinelServersConfig:
          masterName: "mymaster"
          sentinelAddresses:
            - "redis://sentinel1:26379"
            - "redis://sentinel2:26379"
            - "redis://sentinel3:26379"
          database: 0
          password: null
```

### Cluster Mode

```yaml
retask4j:
  http:
    redis:
      redisson:
        clusterServersConfig:
          nodeAddresses:
            - "redis://node1:6379"
            - "redis://node2:6379"
            - "redis://node3:6379"
```

> For the complete Redisson configuration items, refer to [Redisson Wiki](https://github.com/redisson/redisson/wiki/Configuration).

## Caller Configuration

Each Caller defines an HTTP proxy entry, bound to a topic and a mode.

```yaml
callers:
  - topic: "proxy"                    # Topic, matches the Worker's topic
    path: "/proxy/push"               # Request path prefix
    mode: "NORMAL"                    # Mode: NORMAL / FUNCTION / CALLBACK
    retry-plan: [5, 20, 60, 120, 300] # Retry plan (seconds)
    execute-expire: 86400             # Execution expiration time (seconds)
    result-expire: 60                 # Result cache time (seconds)
    request-timeout: 90              # Request timeout (seconds)
    headers:                          # Custom request header injection
      "X-Custom-Header": "value"
    batch: true                       # Enable batch sending
    callback-url: "http://..."        # Callback URL (CALLBACK mode only)
    callback-retry-times: 3           # Callback retry count
    callback-retry-interval: 60       # Callback retry interval (seconds)
    callback-max-threads: 64          # Maximum callback threads
```

### Configuration Item Description

| Field | Type | Default | Description |
|------|------|--------|------|
| `topic` | String | Required | Topic name; Caller and Worker match via topic |
| `path` | String | Required | HTTP request path prefix, e.g. `/proxy/push` |
| `mode` | String | `NORMAL` | Task mode: `NORMAL`, `FUNCTION`, `CALLBACK` |
| `retry-plan` | List\<Integer\> | `[]` | Retry plan, in seconds. E.g. `[5,20,60]` means retry once after 5s, 20s, and 60s on failure |
| `execute-expire` | Integer | `86400` | Execution expiration time (seconds), counted from task creation |
| `result-expire` | Integer | `0` | Result cache time (seconds); 0 means no caching. Minimum 60 in FUNCTION/CALLBACK mode |
| `request-timeout` | Integer | `120` | FUNCTION mode request timeout (seconds) |
| `headers` | Map\<String,String\> | `{}` | Inject/overwrite request headers. A null value deletes the request header |
| `batch` | Boolean | `true` | Enable batch sending mode |
| `callback-url` | String | `null` | Default callback URL, only valid in CALLBACK mode |
| `callback-retry-times` | Integer | `3` | Maximum callback retry count |
| `callback-retry-interval` | Integer | `60` | Callback retry interval (seconds) |
| `callback-max-threads` | Integer | `64` | Callback processing thread pool size |

### Multiple Caller Configuration Example

The same service can be configured with multiple Callers, bound to different paths and modes:

```yaml
callers:
  # Normal push
  - topic: "proxy"
    path: "/proxy/push"
    mode: "NORMAL"
    retry-plan: [5, 20, 60, 120, 300]
    execute-expire: 86400
    result-expire: 60
    request-timeout: 90

  # Synchronous call
  - topic: "proxy"
    path: "/proxy/call"
    mode: "FUNCTION"
    retry-plan: [2, 5]
    execute-expire: 86400
    result-expire: 60
    request-timeout: 90

  # Callback notification
  - topic: "proxy"
    path: "/proxy/task"
    mode: "CALLBACK"
    retry-plan: [60, 120, 300, 600, 3600]
    execute-expire: 86400
    result-expire: 3600
    request-timeout: 120
    callback-url: "http://your-callback-server/callback"
    callback-retry-times: 3
    callback-retry-interval: 60
```

## Worker Configuration

Each Worker consumes tasks of a specified topic and controls execution behavior via route configuration.

```yaml
workers:
  - topic: "proxy"                    # Topic, matches the Caller's topic
    maxConsumeThreads: 512            # Maximum consumer threads
    enableRemote: true                # Allow remote HTTP calls
    enableLocal: true                 # Allow local MockMvc calls
    routes:                           # Route configuration list
      - path: "http://www\\.baidu\\.com/(.+)"
        redirect: "https://www.baidu.com/&1"
        rewrite-request-headers:
          "my-token1": null
          "my-token2": "123"
        assert-response:
          statusIn: [200]
          headerMatch:
            "Content-Type": "application/json(;charset=.+)?"
          textBodyMatch: null
          jsonPathMatch:
            "$.code": "0"
            "$.msg": "success"
        rewrite-response-headers:
          "Access-Control-Allow-Origin": "*"
          "Timing-Allow-Origin": "*"

      - path: "*"                     # Default route
        rewrite-response-headers:
          "Access-Control-Allow-Origin": "*"
          "Access-Control-Max-Age": "86400"
          "Timing-Allow-Origin": "*"
```

### Configuration Item Description

| Field | Type | Default | Description |
|------|------|--------|------|
| `topic` | String | `default` | Topic name |
| `maxConsumeThreads` | Integer | `64` | Maximum consumer threads |
| `enableRemote` | Boolean | `true` | Whether to allow remote HTTP calls |
| `enableLocal` | Boolean | `true` | Whether to allow local MockMvc calls |
| `routes` | List\<RouteConfig\> | `[]` | Route configuration list |

### Route Configuration (RouteConfig)

| Field | Type | Default | Description |
|------|------|--------|------|
| `path` | String | `*` | URL matching regex, `*` matches everything |
| `redirect` | String | `null` | URL rewrite template, `&1`~`&N` reference regex capture groups |
| `rewrite-request-headers` | Map\<String,String\> | `{}` | Request header rewrite, value null means delete |
| `rewrite-response-headers` | Map\<String,String\> | `{}` | Response header rewrite, value null means delete |
| `assert-response` | AssertsConfig | — | Response assertion configuration |

### Assertion Configuration (AssertsConfig)

| Field | Type | Default | Description |
|------|------|--------|------|
| `statusIn` | List\<Integer\> | `[]` | Allowed HTTP status codes; empty means no check |
| `headerMatch` | Map\<String,String\> | `{}` | Response header regex match, Key is header name, Value is regex |
| `textBodyMatch` | String | `null` | Response body text regex match |
| `jsonPathMatch` | Map\<String,String\> | `null` | JSON response body field match, Key is field path, Value is regex |

## How Configuration Takes Effect

### Development Environment

Configuration can be written in `application.yml` to take effect directly.

### Production Environment

1. Mount `config.yml` into the container via K8s ConfigMap or Docker Volume
2. Set the environment variable `SPRING_CONFIG_IMPORT` to point to the configuration file path

```bash
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

K8s example:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: retask4j-config
data:
  config.yml: |
    retask4j:
      http:
        redis:
          redisson:
            singleServerConfig:
              address: "redis://redis-service:6379"
        callers:
          - topic: "proxy"
            path: "/proxy/push"
            mode: "NORMAL"
        workers:
          - topic: "proxy"
            maxConsumeThreads: 512
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: retask4j
          env:
            - name: SPRING_CONFIG_IMPORT
              value: /workspace/config.yml
          volumeMounts:
            - name: config
              mountPath: /workspace
      volumes:
        - name: config
          configMap:
            name: retask4j-config
```
