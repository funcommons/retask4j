# API Reference

## Core API

### FuTaskCaller

The task caller, responsible for submitting tasks to the Redis queue.

#### Creating a Caller

```java
FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("topic", JSONObject.class);
config.setRetryPlan(List.of(5, 20, 60));
config.setExecuteExpire(86400);
config.setResultExpire(3600);
config.setRequestTimeout(90);

FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);
```

CALLBACK mode requires passing in a callback handler:

```java
Consumer<FuTaskMessage> callback = (msg) -> {
    // Handle the callback, e.g. HTTP POST to a business system
};
FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config, callback);
```

#### Creating a Task Message

```java
// Auto-generate a 32-digit random ID
FuTaskMessage message = caller.newTaskMessage(new JSONObject().fluentPut("key", "value"));

// Specify an ID
FuTaskMessage message = caller.newTaskMessage("my-task-id", new JSONObject().fluentPut("key", "value"));
```

#### NORMAL Mode

```java
// Single send
int count = caller.sendTaskMessage(message);

// Batch send
int count = caller.sendTaskMessage(List.of(msg1, msg2, msg3));

// Batch send (via BatchManager, automatic batching)
int count = caller.sendTaskMessageBatch(message);
```

#### FUNCTION Mode

```java
// Async call, returns CompletableFuture
CompletableFuture<R> future = caller.funcAsync(message);
R result = future.get(30, TimeUnit.SECONDS);

// Async call with callback
caller.funcAsync(message, (result, throwable) -> {
    if (throwable != null) {
        // Handle exception
    } else {
        // Handle result
    }
});

// Batch send (via BatchManager)
CompletableFuture<R> future = caller.funcAsyncBatch(message);

// Multiple task batch submission
List<Map.Entry<FuTaskMessage, CompletableFuture<R>>> entries = List.of(
    Map.entry(msg1, new CompletableFuture<>()),
    Map.entry(msg2, new CompletableFuture<>())
);
caller.funcAsync(entries);
```

#### CALLBACK Mode

```java
// Single send
int count = caller.sendCallbackMessage(message);

// Batch send
int count = caller.sendCallbackMessage(List.of(msg1, msg2));

// Batch send (via BatchManager)
int count = caller.sendCallbackMessageBatch(message);
```

### FuTaskWorker

The task worker, responsible for consuming tasks from the Redis queue and executing them.

#### Creating a Worker

```java
FuTaskWorkConfig config = new FuTaskWorkConfig("topic");
config.setMaxConsumeThreads(64);

FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
    (input, extInfo) -> {
        // Business processing logic
        return new JSONObject().fluentPut("result", "done");
    },
    JSONObject.class
);

FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);
worker.start();
```

#### Custom Strategy

```java
FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("myStrategy");
config.addStrategy("myStrategy", strategy);
```

#### Monitoring Metrics

```java
FuTaskMonitor.WorkerMonitor monitor = worker.getMonitor();
long consumed = monitor.consume.get();       // Total consumed count
long success = monitor.success.get();        // Success count
long fail = monitor.fail.get();              // Failure count
long complete = monitor.complete.get();      // Completion count
long finallyFail = monitor.fillyFail.get();  // Final failure count
```

### FuTaskCallConfig

| Field | Type | Default | Description |
|------|------|--------|------|
| `topic` | String | Required | Topic name |
| `returnCls` | Class\<R\> | Required | Return value type |
| `retryPlan` | List\<Integer\> | `[]` | Retry plan (seconds) |
| `executeExpire` | int | `3600` | Execution expiration time (seconds) |
| `resultExpire` | int | `0` | Result cache time (seconds) |
| `strategy` | String | `null` | Strategy name |
| `requestTimeout` | int | `90` | Request timeout (seconds) |
| `callbackMaxThreads` | int | `64` | Maximum callback threads |
| `callbackRetryTimes` | int | `3` | Callback retry count |
| `callbackRetryInterval` | int | `60` | Callback retry interval (seconds) |

### FuTaskWorkConfig

| Field | Type | Default | Description |
|------|------|--------|------|
| `topic` | String | `default` | Topic name |
| `maxConsumeThreads` | int | `64` | Maximum consumer threads |
| `strategyMap` | Map\<String, FuTaskWorkStrategy\> | `{default: ...}` | Strategy map |

### FuTaskMessage

| Field | Type | Description |
|------|------|------|
| `id` | String | Task ID |
| `topic` | String | Topic |
| `tag` | String | Tag |
| `strategy` | String | Strategy name |
| `createTime` | long | Creation time (millisecond timestamp) |
| `delayTime` | int | Delay time (seconds); 0 means consume immediately |
| `scheduleTime` | long | Schedule time (second timestamp) |
| `retryPlan` | List\<Integer\> | Retry plan (seconds) |
| `retryTimes` | int | Number of retries already performed |
| `retryDelay` | int | Current retry delay (seconds) |
| `executeExpire` | int | Execution expiration time (seconds) |
| `resultExpire` | int | Result cache time (seconds) |
| `status` | String | Status: WAITING / PENDING / SUCCESS / FAIL |
| `mode` | String | Mode: NORMAL / FUNCTION / CALLBACK |
| `input` | JSONObject | Input data |
| `output` | JSONObject | Output data |
| `extInfo` | JSONObject | Extended info |
| `callerId` | String | Caller ID |
| `error` | String | Error info |
| `executeTime` | long | Actual execution time |
| `completeTime` | long | Actual completion time |
| `callbackStatus` | String | Callback status |
| `callbackRetryTimes` | int | Callback retries already performed |
| `callbackError` | String | Callback error info |

## Debug Endpoints

The HTTP proxy server has built-in Debug endpoints for operations troubleshooting.

### Queue Status

```
GET /debug/{topic}/taskCount.do
```

Returns the size of each queue for the specified topic:

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

| Field | Description |
|------|------|
| `working` | Count of tasks awaiting consumption in the main work queue |
| `pending` | Count in the in-progress queue |
| `timing` | Count in the scheduled/delayed queue |
| `retry` | Count in the retry queue |
| `callback-working` | Pending callbacks count |
| `callback-pending` | In-progress callbacks count |

### Task Details

```
GET /debug/{topic}/getTask.do?taskId={taskId}
```

Returns the complete information for the task message. The task must be within its expiration time to be queried:

- In progress: Within `executeExpire` time (default 1 day)
- Completed: Within `resultExpire` time (default 1 hour, default 1 hour in CALLBACK mode)

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

If taskId does not exist or has expired:

```json
{
  "status": 404,
  "msg": "taskId not found"
}
```

### Request Echo

```
GET /debug/request.info
```

Echoes the request information, which can be used to test whether the callback is working correctly.
