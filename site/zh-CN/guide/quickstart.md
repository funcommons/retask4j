---
title: 快速开始
---


## 环境要求

- Java 17 或更高版本
- Redis 6.0 或更高版本
- Maven 3.8 或更高版本

## 部署 HTTP 代理服务

### 1. 构建

```bash
git clone <repository-url>
cd retask4j
mvn clean package -DskipTests
```

构建产物位于 `retask4j-http-server/target/retask4j-http-server.jar`。

### 2. 配置 Redis

创建 `config.yml` 文件：

```yaml
retask4j:
  http:
    redis:
      redisson:
        singleServerConfig:
          address: "redis://your-redis-host:6379"
          database: 0
          password: "your-password"
```

完整配置请参考 [配置参考](configuration.md)。

### 3. 启动服务

```bash
# 方式一：指定外部配置文件
SPRING_CONFIG_IMPORT=/path/to/config.yml java -jar retask4j-http-server.jar

# 方式二：通过环境变量
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

默认端口为 `9093`，可通过 `server.port` 修改。

### 4. K8s / Docker 部署

通过 ConfigMap 或 Volume 将 `config.yml` 挂载到容器内，并设置环境变量：

```yaml
env:
  - name: SPRING_CONFIG_IMPORT
    value: /workspace/config.yml
```

## 5 分钟教程

### 普通推送（NORMAL）

提交任务并立即返回 taskId；Worker 异步执行：

```bash
curl http://localhost:9093/proxy/push/https://httpbin.org/get
```

响应：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "taskId": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX"
  }
}
```

### 同步调用（FUNCTION）

提交任务，阻塞等待 Worker 同步返回执行结果：

```bash
curl http://localhost:9093/proxy/call/https://httpbin.org/get
```

响应为目标 URL 的原始返回内容。

### 回调通知（CALLBACK）

提交任务并立即返回 taskId；Worker 完成后主动将结果 POST 到回调地址：

```bash
curl -H "retask4j-callback-url: http://your-callback-server/callback" \
     http://localhost:9093/proxy/task/https://httpbin.org/post \
     -d '{"key":"value"}'
```

回调数据格式：

```json
{
  "id": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX",
  "response": {
    "body": { "status": 0, "msg": "success" },
    "headers": { "Content-Type": ["application/json; charset=utf-8"] },
    "status": 200
  },
  "status": "SUCCESS",
  "completeTime": 1736850478880,
  "executeTime": 1736850465689
}
```

回调服务返回 HTTP 200 即视为成功；任何非 200 状态码都会触发回调重试。

## 使用 Core API

如果不需要 HTTP 代理模式，可以直接使用 Core API。

### 添加依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>retask4j-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Caller 端

```java
// 创建 Redisson 客户端
RedissonClient redissonClient = Redisson.create(config);

// 创建 Caller 配置
FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>("demo", JSONObject.class);
callConfig.setRetryPlan(List.of(5, 20, 60));

// 创建 Caller
FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, callConfig);

// NORMAL 模式：发送任务
FuTaskMessage message = caller.newTaskMessage(new JSONObject().fluentPut("key", "value"));
caller.sendTaskMessage(message);

// FUNCTION 模式：同步调用
CompletableFuture<JSONObject> future = caller.funcAsync(message);
JSONObject result = future.get(30, TimeUnit.SECONDS);
```

### Worker 端

```java
// 创建 Worker 配置
FuTaskWorkConfig workConfig = new FuTaskWorkConfig("demo");
workConfig.setMaxConsumeThreads(64);

// 创建执行器
FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
    (input, extInfo) -> {
        // 业务处理逻辑
        return new JSONObject().fluentPut("result", "done");
    },
    JSONObject.class
);

// 创建并启动 Worker
FuTaskWorker worker = new FuTaskWorker(redissonClient, workConfig, executor);
worker.start();
```