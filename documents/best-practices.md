# Best Practices

## Production Deployment

### Redis Configuration Recommendations

- **Standalone Deployment**: Redis should be deployed independently; co-location with the application is not recommended. Redis performance directly affects task throughput.
- **Memory Planning**: Task messages are stored in Redis Hashes, with each message around 1~5 KB. Estimate memory requirements based on peak backlog.
- **Persistence**: It is recommended to enable AOF persistence to avoid task loss caused by Redis restarts.
- **Connection Pool**: Adjust `connectionPoolSize` based on the number of Caller/Worker instances; 16~32 per instance is recommended.

### Multi-Instance Deployment

Callers and Workers can be deployed independently on different instances:

```
┌─────────────────┐
│   Caller Instance │  Receives HTTP requests, pushes to Redis
│   (Multi-instance OK) │
└────────┬────────┘
         │ Redis
┌────────▼────────┐
│   Worker Instance │  Consumes tasks, executes HTTP calls
│   (Multi-instance OK) │
└─────────────────┘
```

- Callers are stateless and can scale horizontally
- Workers can boost consumption by adding instances and increasing `maxConsumeThreads`
- Multiple Worker instances of the same topic automatically load-balance (Redis queues naturally support multiple consumers)

### Thread Count Configuration

| Parameter | Recommended Value | Description |
|------|--------|------|
| `maxConsumeThreads` | CPU cores × 4 ~ 8 | Worker consumer threads; IO-intensive scenarios can be increased appropriately |
| `callbackMaxThreads` | 32 ~ 64 | CALLBACK mode callback threads |
| Tomcat `max-threads` | 500 ~ 2000 | HTTP service threads, tuned based on QPS |

### Externalized Configuration

In production environments, be sure to externalize `config.yml` and mount it via ConfigMap or Volume:

```bash
SPRING_CONFIG_IMPORT=/workspace/config.yml java -jar retask4j-http-server.jar
```

Avoid packaging sensitive information such as Redis passwords into the image.

## Performance Tuning

### Batch Sending

Callers enable batch sending by default (`batch: true`), batching via `FuTaskBatchManager` and writing to Redis at once:

- Batch size threshold: 1000 messages
- Flush interval: 50ms
- Concurrent flush threads: 4

It is recommended to keep it enabled in high-QPS scenarios, and disable it in low-QPS scenarios to reduce latency.

### Worker Consumption Capability

The Worker's consumption capability depends on:

1. **`maxConsumeThreads`**: Number of tasks executed concurrently
2. **Target Service Response Time**: The time taken by Worker HTTP calls directly affects throughput
3. **Redis Connection Pool**: Insufficient connection count will become a bottleneck

Capacity formula:

```
Theoretical throughput = maxConsumeThreads / average execution time (seconds)
```

For example, `maxConsumeThreads=512`, with an average execution time of 200ms, yields a theoretical throughput of about 2560 QPS.

### Redis Performance

- All queue operations are executed via Lua scripts to reduce network round trips
- Queues automatically get a 24-hour TTL to prevent unbounded growth
- It is recommended to monitor Redis memory usage and slow queries

## FAQ

### Task Stuck in WAITING Status

**Possible causes**:

1. Worker has not started or has not connected to the same Redis
2. Worker's `topic` does not match Caller's `topic`
3. Worker thread pool is full, unable to continue consumption

**Troubleshooting**:

```bash
# View queue status
curl http://localhost:9093/debug/proxy/taskCount.do

# If working > 0 and pending = 0, the Worker is not consuming
# If pending > 0 and keeps growing, the Worker is slow or stuck
```

### FUNCTION Mode Request Timeout

**Possible causes**:

1. Worker's execution time exceeds `requestTimeout`
2. Worker has not started
3. Redis connection anomalies prevent reading data from the Return Queue

**Recommendations**:

- Adjust `requestTimeout` based on the target service's actual response time
- FUNCTION mode is not suitable for very long-running operations; consider using CALLBACK mode

### CALLBACK Failure

**Possible causes**:

1. Callback URL is unreachable
2. Callback service returns non-200 status code
3. The callback data format is not accepted by the callback service

**Troubleshooting**:

```bash
# View task details, check callbackStatus and callbackError
curl http://localhost:9093/debug/proxy/getTask.do?taskId=xxx
```

After callback retries are exhausted, `callbackStatus` becomes `FAIL`.

### Task Execution Failed Without Retry

**Possible causes**:

1. `retry-plan` is not configured or is an empty array
2. Retry count has been exhausted
3. Task has expired (exceeds `executeExpire`)

**Troubleshooting**:

```bash
# View task details
curl http://localhost:9093/debug/proxy/getTask.do?taskId=xxx

# Check retryTimes, retryPlan, executeExpire
```

### Redis Memory Keeps Growing

**Possible causes**:

1. `resultExpire` is set too high, completed task results are not cleaned up in time
2. Excessive task backlog
3. Large files are stored in Redis during file upload scenarios

**Recommendations**:

- Set `resultExpire` reasonably based on business needs
- Monitor queue backlog and expand Workers in time
- Control file sizes during file upload scenarios

### Scheduled Task Not Executed On Time

**Possible causes**:

1. `retask4j-task-timing` timestamp format is incorrect (10-digit seconds vs 13-digit milliseconds)
2. Scheduled time exceeds 24 hours (not supported)
3. Worker's resetTiming thread is abnormal

**Notes**:

- The framework automatically recognizes 10-digit second and 13-digit millisecond timestamps
- Scheduled tasks support up to 24 hours

## Security Recommendations

- Always set a password for Redis; passwordless access is prohibited in production
- `callback-url` should use an internal network address, avoid exposing it to the public network
- Worker's `enableLocal` should be disabled in untrusted environments to prevent access to local interfaces via task injection
- Use `rewrite-request-headers` to remove sensitive header info (such as Authorization)
- Restrict file sizes in file upload scenarios to prevent Redis memory overflow
