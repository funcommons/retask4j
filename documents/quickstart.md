# Quick Start

## Requirements

- Java 17 or above
- Redis 6.0 or above
- Maven 3.8 or above

## Deploying the HTTP Proxy Server

### 1. Build

```bash
git clone <repository-url>
cd retask4j
mvn clean package -DskipTests
```

The build output is located at `retask4j-http-server/target/retask4j-http-server.jar`.

### 2. Configure Redis

Create a `config.yml` file:

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

For the complete configuration, refer to [Configuration Reference](configuration.md).

### 3. Start the Service

```bash
# Method 1: Specify an external configuration file
SPRING_CONFIG_IMPORT=/path/to/config.yml java -jar retask4j-http-server.jar

# Method 2: Via environment variable
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

The default port is `9093`, and can be modified via `server.port`.

### 4. K8s / Docker Deployment

Mount `config.yml` into the container via a ConfigMap or Volume, and set the environment variable:

```yaml
env:
  - name: SPRING_CONFIG_IMPORT
    value: /workspace/config.yml
```

## 5-Minute Tutorial

### Normal Push (NORMAL)

Submit a task and immediately return a taskId; the Worker executes asynchronously:

```bash
curl http://localhost:9093/proxy/push/https://httpbin.org/get
```

Response:

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "taskId": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX"
  }
}
```

### Synchronous Call (FUNCTION)

Submit a task, block until the Worker execution result is returned synchronously:

```bash
curl http://localhost:9093/proxy/call/https://httpbin.org/get
```

The response is the raw return content of the target URL.

### Callback Notification (CALLBACK)

Submit a task and immediately return a taskId; after the Worker completes, it actively POSTs the result to the callback address:

```bash
curl -H "retask4j-callback-url: http://your-callback-server/callback" \
     http://localhost:9093/proxy/task/https://httpbin.org/post \
     -d '{"key":"value"}'
```

Callback data format:

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

If the callback service returns HTTP 200, it is considered successful; any non-200 status will trigger a retry.

## Using the Core API

If the HTTP proxy mode is not required, you can use the Core API directly.

### Add the Dependency

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>retask4j-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Caller Side

```java
// Create a Redisson client
RedissonClient redissonClient = Redisson.create(config);

// Create the Caller configuration
FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>("demo", JSONObject.class);
callConfig.setRetryPlan(List.of(5, 20, 60));

// Create the Caller
FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, callConfig);

// NORMAL mode: send task
FuTaskMessage message = caller.newTaskMessage(new JSONObject().fluentPut("key", "value"));
caller.sendTaskMessage(message);

// FUNCTION mode: synchronous call
CompletableFuture<JSONObject> future = caller.funcAsync(message);
JSONObject result = future.get(30, TimeUnit.SECONDS);
```

### Worker Side

```java
// Create the Worker configuration
FuTaskWorkConfig workConfig = new FuTaskWorkConfig("demo");
workConfig.setMaxConsumeThreads(64);

// Create the executor
FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
    (input, extInfo) -> {
        // Business processing logic
        return new JSONObject().fluentPut("result", "done");
    },
    JSONObject.class
);

// Create and start the Worker
FuTaskWorker worker = new FuTaskWorker(redissonClient, workConfig, executor);
worker.start();
```
