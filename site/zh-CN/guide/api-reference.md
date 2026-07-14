---
title: API 参考
---


## 核心 API

### FuTaskCaller

任务调用者，负责将任务提交到 Redis 队列。

#### 创建 Caller

```java
FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("topic", JSONObject.class);
config.setRetryPlan(List.of(5, 20, 60));
config.setExecuteExpire(86400);
config.setResultExpire(3600);
config.setRequestTimeout(90);

FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);
```

CALLBACK 模式需要传入回调处理器：

```java
Consumer<FuTaskMessage> callback = (msg) -> {
    // 处理回调，例如 HTTP POST 到业务系统
};
FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config, callback);
```

#### 创建任务消息

```java
// 自动生成 32 位随机 ID
FuTaskMessage message = caller.newTaskMessage(new JSONObject().fluentPut("key", "value"));

// 指定 ID
FuTaskMessage message = caller.newTaskMessage("my-task-id", new JSONObject().fluentPut("key", "value"));
```

#### NORMAL 模式

```java
// 单条发送
int count = caller.sendTaskMessage(message);

// 批量发送
int count = caller.sendTaskMessage(List.of(msg1, msg2, msg3));

// 批量发送（通过 BatchManager，自动批量化）
int count = caller.sendTaskMessageBatch(message);
```

#### FUNCTION 模式

```java
// 异步调用，返回 CompletableFuture
CompletableFuture<R> future = caller.funcAsync(message);
R result = future.get(30, TimeUnit.SECONDS);

// 异步调用并附带回调
caller.funcAsync(message, (result, throwable) -> {
    if (throwable != null) {
        // 处理异常
    } else {
        // 处理结果
    }
});

// 批量发送（通过 BatchManager）
CompletableFuture<R> future = caller.funcAsyncBatch(message);

// 多任务批量提交
List<Map.Entry<FuTaskMessage, CompletableFuture<R>>> entries = List.of(
    Map.entry(msg1, new CompletableFuture<>()),
    Map.entry(msg2, new CompletableFuture<>())
);
caller.funcAsync(entries);
```

#### CALLBACK 模式

```java
// 单条发送
int count = caller.sendCallbackMessage(message);

// 批量发送
int count = caller.sendCallbackMessage(List.of(msg1, msg2));

// 批量发送（通过 BatchManager）
int count = caller.sendCallbackMessageBatch(message);
```

### FuTaskWorker

任务工作者，负责从 Redis 队列中消费任务并执行。

#### 创建 Worker

```java
FuTaskWorkConfig config = new FuTaskWorkConfig("topic");
config.setMaxConsumeThreads(64);

FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
    (input, extInfo) -> {
        // 业务处理逻辑
        return new JSONObject().fluentPut("result", "done");
    },
    JSONObject.class
);

FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);
worker.start();
```

#### 自定义策略

```java
FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("myStrategy");
config.addStrategy("myStrategy", strategy);
```

#### 监控指标

```java
FuTaskMonitor.WorkerMonitor monitor = worker.getMonitor();
long consumed = monitor.consume.get();       // 总消费数
long success = monitor.success.get();        // 成功数
long fail = monitor.fail.get();              // 失败数
long complete = monitor.complete.get();      // 完成数
long finallyFail = monitor.fillyFail.get();  // 最终失败数
```

### FuTaskCallConfig

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `topic` | String | 必填 | Topic 名称 |
| `returnCls` | Class\<R\> | 必填 | 返回值类型 |
| `retryPlan` | List\<Integer\> | `[]` | 重试计划（秒） |
| `executeExpire` | int | `3600` | 执行过期时间（秒） |
| `resultExpire` | int | `0` | 结果缓存时间（秒） |
| `strategy` | String | `null` | 策略名称 |
| `requestTimeout` | int | `90` | 请求超时时间（秒） |
| `callbackMaxThreads` | int | `64` | 最大回调线程数 |
| `callbackRetryTimes` | int | `3` | 回调重试次数 |
| `callbackRetryInterval` | int | `60` | 回调重试间隔（秒） |

### FuTaskWorkConfig

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `topic` | String | `default` | Topic 名称 |
| `maxConsumeThreads` | int | `64` | 最大消费线程数 |
| `strategyMap` | Map\<String, FuTaskWorkStrategy\> | `{default: ...}` | 策略映射 |

### FuTaskMessage

| 字段 | 类型 | 描述 |
|------|------|------|
| `id` | String | 任务 ID |
| `topic` | String | Topic |
| `tag` | String | 标签 |
| `strategy` | String | 策略名称 |
| `createTime` | long | 创建时间（毫秒时间戳） |
| `delayTime` | int | 延时时间（秒）；0 表示立即消费 |
| `scheduleTime` | long | 计划执行时间（秒级时间戳） |
| `retryPlan` | List\<Integer\> | 重试计划（秒） |
| `retryTimes` | int | 已重试次数 |
| `retryDelay` | int | 当前重试延时（秒） |
| `executeExpire` | int | 执行过期时间（秒） |
| `resultExpire` | int | 结果缓存时间（秒） |
| `status` | String | 状态：WAITING / PENDING / SUCCESS / FAIL |
| `mode` | String | 模式：NORMAL / FUNCTION / CALLBACK |
| `input` | JSONObject | 输入数据 |
| `output` | JSONObject | 输出数据 |
| `extInfo` | JSONObject | 扩展信息 |
| `callerId` | String | Caller ID |
| `error` | String | 错误信息 |
| `executeTime` | long | 实际执行时间 |
| `completeTime` | long | 实际完成时间 |
| `callbackStatus` | String | 回调状态 |
| `callbackRetryTimes` | int | 已执行的回调重试次数 |
| `callbackError` | String | 回调错误信息 |

## 调试端点

HTTP 代理服务器内置了调试端点，用于运维排查。

### 队列状态

```
GET /debug/{topic}/taskCount.do
```

返回指定 topic 各队列的大小：

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "working": 0,
    "pending": 0,
    "timing": 0,
    "retry": 0,
    "callback-working": 0,
    "callback-pending": 0
  }
}
```

| 字段 | 描述 |
|------|------|
| `working` | 主工作队列中等待消费的任务数 |
| `pending` | 进行中队列中的任务数 |
| `timing` | 计划/延时队列中的任务数 |
| `retry` | 重试队列中的任务数 |
| `callback-working` | 待回调任务数 |
| `callback-pending` | 进行中回调任务数 |

### 任务详情

```
GET /debug/{topic}/getTask.do?taskId={taskId}
```

返回任务消息的完整信息。任务必须在有效期内才能被查询：

- 进行中：在 `executeExpire` 时间内（默认 1 天）
- 已完成：在 `resultExpire` 时间内（默认 1 小时，CALLBACK 模式下也默认为 1 小时）

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "id": "xhxrmKzqpTnomFWpsBbKFTcMyqvZaxLK",
    "topic": "proxy",
    "status": "SUCCESS",
    "mode": "CALLBACK",
    "createTime": 1736911800453,
    "executeTime": 1736911800761,
    "completeTime": 1736911839436,
    "input": { ... },
    "output": { ... },
    "retryPlan": [60, 120, 300, 600, 3600],
    "retryTimes": 0,
    "callbackStatus": "SUCCESS"
  }
}
```

如果 taskId 不存在或已过期：

```json
{
  "status": 404,
  "msg": "taskId not found"
}
```

### 请求回显

```
GET /debug/request.info
```

回显请求信息，可用于测试回调是否能正常工作。
