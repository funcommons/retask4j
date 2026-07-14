---
title: 配置参考
---


## 配置结构

所有配置都在 `retask4j.http` 命名空间下，包含三个部分：

```yaml
retask4j:
  http:
    redis: ...       # Redis 连接配置
    callers: [...]   # Caller 配置列表
    workers: [...]   # Worker 配置列表
```

## Redis 配置

配置项对应 Redisson 的配置格式，支持单节点、哨兵和集群模式。

### 单节点模式

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

### 哨兵模式

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

### 集群模式

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

> 完整的 Redisson 配置项请参考 [Redisson Wiki](https://github.com/redisson/redisson/wiki/Configuration)。

## Caller 配置

每个 Caller 定义一个 HTTP 代理入口，绑定到一个 topic 和一个模式。

```yaml
callers:
  - topic: "proxy"                    # Topic，与 Worker 的 topic 匹配
    path: "/proxy/push"               # 请求路径前缀
    mode: "NORMAL"                    # 模式：NORMAL / FUNCTION / CALLBACK
    retry-plan: [5, 20, 60, 120, 300] # 重试计划（秒）
    execute-expire: 86400             # 执行过期时间（秒）
    result-expire: 60                 # 结果缓存时间（秒）
    request-timeout: 90              # 请求超时（秒）
    headers:                          # 自定义请求头注入
      "X-Custom-Header": "value"
    batch: true                       # 启用批量发送
    callback-url: "http://..."        # 回调 URL（仅 CALLBACK 模式）
    callback-retry-times: 3           # 回调重试次数
    callback-retry-interval: 60       # 回调重试间隔（秒）
    callback-max-threads: 64          # 最大回调线程数
```

### 配置项说明

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `topic` | String | 必填 | Topic 名称；Caller 和 Worker 通过 topic 匹配 |
| `path` | String | 必填 | HTTP 请求路径前缀，例如 `/proxy/push` |
| `mode` | String | `NORMAL` | 任务模式：`NORMAL`、`FUNCTION`、`CALLBACK` |
| `retry-plan` | List\<Integer\> | `[]` | 重试计划，单位秒。例如 `[5,20,60]` 表示失败后分别在 5 秒、20 秒、60 秒后重试一次 |
| `execute-expire` | Integer | `86400` | 执行过期时间（秒），从任务创建开始计算 |
| `result-expire` | Integer | `0` | 结果缓存时间（秒）；0 表示不缓存。FUNCTION/CALLBACK 模式下最小为 60 |
| `request-timeout` | Integer | `120` | FUNCTION 模式的请求超时时间（秒） |
| `headers` | Map\<String,String\> | `{}` | 注入/覆盖请求头。null 值表示删除请求头 |
| `batch` | Boolean | `true` | 启用批量发送模式 |
| `callback-url` | String | `null` | 默认回调 URL，仅在 CALLBACK 模式下生效 |
| `callback-retry-times` | Integer | `3` | 最大回调重试次数 |
| `callback-retry-interval` | Integer | `60` | 回调重试间隔（秒） |
| `callback-max-threads` | Integer | `64` | 回调处理线程池大小 |

### 多 Caller 配置示例

同一个服务可以配置多个 Caller，绑定到不同的路径和模式：

```yaml
callers:
  # 普通推送
  - topic: "proxy"
    path: "/proxy/push"
    mode: "NORMAL"
    retry-plan: [5, 20, 60, 120, 300]
    execute-expire: 86400
    result-expire: 60
    request-timeout: 90

  # 同步调用
  - topic: "proxy"
    path: "/proxy/call"
    mode: "FUNCTION"
    retry-plan: [2, 5]
    execute-expire: 86400
    result-expire: 60
    request-timeout: 90

  # 回调通知
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

## Worker 配置

每个 Worker 消费指定 topic 的任务，并通过路由配置控制执行行为。

```yaml
workers:
  - topic: "proxy"                    # Topic，与 Caller 的 topic 匹配
    maxConsumeThreads: 512            # 最大消费线程数
    enableRemote: true                # 允许远程 HTTP 调用
    enableLocal: true                 # 允许本地 MockMvc 调用
    routes:                           # 路由配置列表
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

      - path: "*"                     # 默认路由
        rewrite-response-headers:
          "Access-Control-Allow-Origin": "*"
          "Access-Control-Max-Age": "86400"
          "Timing-Allow-Origin": "*"
```

### 配置项说明

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `topic` | String | `default` | Topic 名称 |
| `maxConsumeThreads` | Integer | `64` | 最大消费线程数 |
| `enableRemote` | Boolean | `true` | 是否允许远程 HTTP 调用 |
| `enableLocal` | Boolean | `true` | 是否允许本地 MockMvc 调用 |
| `routes` | List\<RouteConfig\> | `[]` | 路由配置列表 |

### 路由配置（RouteConfig）

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `path` | String | `*` | URL 匹配正则，`*` 匹配所有 |
| `redirect` | String | `null` | URL 重写模板，`&1`~`&N` 引用正则捕获组 |
| `rewrite-request-headers` | Map\<String,String\> | `{}` | 请求头重写，值为 null 表示删除 |
| `rewrite-response-headers` | Map\<String,String\> | `{}` | 响应头重写，值为 null 表示删除 |
| `assert-response` | AssertsConfig | — | 响应断言配置 |

### 断言配置（AssertsConfig）

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `statusIn` | List\<Integer\> | `[]` | 允许的 HTTP 状态码；空表示不检查 |
| `headerMatch` | Map\<String,String\> | `{}` | 响应头正则匹配，Key 是 header 名，Value 是正则表达式 |
| `textBodyMatch` | String | `null` | 响应体文本正则匹配 |
| `jsonPathMatch` | Map\<String,String\> | `null` | JSON 响应体字段匹配，Key 是字段路径，Value 是正则表达式 |

## 配置生效方式

### 开发环境

可以写在 `application.yml` 中直接生效。

### 生产环境

1. 通过 K8s ConfigMap 或 Docker Volume 将 `config.yml` 挂载到容器中
2. 设置环境变量 `SPRING_CONFIG_IMPORT` 指向配置文件路径

```bash
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

K8s 示例：

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
