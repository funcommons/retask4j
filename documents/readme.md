# Asynchronous HTTP Proxy Service

## Quick Start

### APIs

#### /proxy/call/**
Execute the proxy on the distributed server and synchronously return the recognition result.

* Try it

* json
<a href="/proxy/call/https://news.baidu.com/widget?id=LocalNews&ajax=json" target="_blank">/proxy/call/https://news.baidu.com/widget?id=LocalNews&ajax=json</a>

* Image
<a href="/proxy/call/https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png" target="_blank">/proxy/call/https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png</a>

* http 400
<a href="/proxy/call/https://t7.baidu.com/it/font/iconfont.woff2" target="_blank">/proxy/call/https://t7.baidu.com/it/font/iconfont.woff2</a>

#### /proxy/push/**
Execute the proxy push on the distributed server, with failure retry support.

#### /proxy/task/**
Execute an HTTP call on the distributed server and asynchronously return the call result. When the result is returned asynchronously, a callback address needs to be configured.

* Method 1: Configure the default callback-url in the config.yml configuration file.
* Method 2: Configure retask4j-callback-url in the request header to specify the callback address.

#### HTTP Support
1. Supports all HTTP methods. E.g. GET, POST, PUT, DELETE, etc.
2. Supports HTTP and HTTPS protocols.
2. Supports all content-type formats. E.g. text/html, application/json, form-data, application/x-www-form-urlencoded, etc.
3. Supports content-encoding compression: gzip, deflate, br, zstd
4. Supports cross-domain requests.
5. Supports custom HTTP request headers and custom HTTP response headers.
6. Supports custom HTTP response assertions.
7. Supports file transfer. (Note: file contents are also stored in Redis; Redis memory must be considered.)

#### Scheduled Tasks

1. Configure retask4j-task-timing in the request header to specify the time.
* Timestamp, e.g. 1737077674000; supports both 13-digit millisecond timestamps and 10-digit second timestamps
* Precision is to the second
* If the value is less than the current time, execute immediately.
* Greater than 24 hours is not supported

2. Configure retask4j-task-delay in the request header to specify the delay.
* retask4j-task-delay is the delay message in seconds. Value range is 1~3600
* Greater than 24 hours is not supported
* If both retask4j-task-delay and retask4j-task-timing exist, retask4j-task-delay takes precedence

#### Retry Plan
1. Configure retry-plan in the config file.
* Value, e.g. "[1,3,5]" means retry after 1 second, retry after 3 seconds, retry after 5 seconds, for a total of 3 retries

2. Configure retask4j-retry-plan in the request header.
* Same as above

#### Response Assertion

1. Configure assert-response in the config file.
* Refer to the configuration file
2. Configure retask4j-assert-response in the request header.
* Value is JSON format, e.g. {"statusIn":[200],"jsonPathMatch":{"$.code":"0"}}
* Refer to the configuration file

#### Request Header Priority
Configuration in request headers takes precedence over configuration in config.xml

##### Return Values
For synchronous return of recognition results:
The return value of the target url

For asynchronous return of recognition results:
```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "taskId": "HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX"
  }
}
```


When the recognition result is returned asynchronously, the JSON-format data is POSTed to the callback address.
Returning HTTP status 200 means the callback is successful.
If the callback is not successful, the system will retry; the retry plan is configurable.

```json
{
    "id":"HDCAgsLOCCUkEYVnbezxrWwfKwUXCznX",
    "response":{
        "body":{
            "status":0,
            "msg":"success",
            "data": {
                "text":"hello"
            }
        },
        "headers":{
            "Content-Type":[
                "application/json; charset=utf-8"
            ]
        },
        "status":200
    },
    "status":"SUCCESS",
    "completeTime":1736850478880,
    "executeTime":1736850465689
}
```


## Configuration

#### Configuration file: config.yml

```yaml
retask4j:
  http:
    redis:
      # Redis address; for distributed deployment, modify to the Redis address
      redisson:
        singleServerConfig:
          address: "redis://localhost:6379"
          database: 0
          #clientName:
          #password:
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

    callers:
      # Topic
      - topic: "proxy"
        # Request path
        path: "/proxy/push"
        # Mode NORMAL/FUNCTION/CALLBACK
        mode: "NORMAL"
        # Retry strategy (in seconds), e.g. [1,3]; retry after 1s and 3s; no retries by default
        retry-plan: [5,20,60,120,300]
        # Consumption expiration in seconds, default 1 day
        execute-expire: 86400
        # Result cache time in seconds
        result-expire: 60
        # Request timeout in seconds
        request-timeout: 90

        # Topic
      - topic: "proxy"
        # Request path
        path: "/proxy/call"
        # Mode NORMAL/FUNCTION/CALLBACK
        mode: "FUNCTION"
        retry-plan: [2,5]
        # Consumption expiration in seconds, default 1 day
        execute-expire: 86400
        # Result cache time in seconds
        result-expire: 60
        # Request timeout in seconds
        request-timeout: 90

        # Topic
      - topic: "proxy"
        # Request path
        path: "/proxy/task"
        # Mode NORMAL/FUNCTION/CALLBACK
        mode: "CALLBACK"
        # Retry strategy (in seconds), e.g. [1,3]; retry after 1s and 3s; no retries by default
        retry-plan: [60,120,300,600,3600]
        # Consumption expiration in seconds, default 1 day
        execute-expire: 86400
        # Result cache time in seconds, 1 hour
        result-expire: 3600
        # Request timeout, default 120s
        request-timeout: 120
        # Default callback URL
        callback-url: "http://localhost:9400/debug/request.info"
        # Callback retry count, default 3
        callback-retry-times: 3
        # Callback retry interval, default 60s
        callback-retry-interval: 60

    workers:
      - topic: "proxy"
        maxConsumeThreads: 512
        routes:
          # Route regex match
          - path: "http://www\\.baidu\\.com/(.+)"
            # Rewrite URL; leave empty to skip
            redirect: "https://www.baidu.com/&1"
            # Rewrite request headers
            rewrite-request-headers:
              "my-token1": Null
              "my-token2": "123"
            # Assert response; if not matched, the HTTP assertion fails and retry can be triggered.
            assert-response:
              # Empty means unlimited, e.g.: [200,301,302,304,403,404]
              statusIn: []
              # The response header must match "{key}": "{regex}", multiple entries must all match
              headerMatch:
                "Content-Type": "application/json(;charset=.+)?"
              # Response body (text) must match the regex, e.g. "success|SUCCESS|Success|Ok|ok|OK|true|TRUE|True"
              textBodyMatch:
              # Response body (json) must match "{jsonpath}": "{regex}", multiple entries must all match
              jsonPathMatch:
                "$.code": "0"
                "$.msg": "success"

            # Rewrite response headers
            rewrite-response-headers:
              "Access-Control-Allow-Origin": "*"
              "Timing-Allow-Origin": "*"
              # "my-token": "123"
              # "etag": null

          # default route config
          - path: "*"
            # Rewrite response headers
            rewrite-response-headers:
              "Access-Control-Allow-Origin": "*"
              "Access-Control-Max-Age": "86400"
              "Timing-Allow-Origin": "*"

```
#### Configuration Activation
1. Mount the config.yml file into the container via K8s ConfigMap or docker -v (e.g.: /workspace/config.yml)
2. Set the environment variable SPRING_CONFIG_IMPORT=/workspace/config.yml


### DEBUG

#### View Task Queue Count

/debug/proxy/taskCount.do

<a href="/debug/proxy/taskCount.do" target="_blank">Try it</a>

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

#### View Task Details

Tasks can be queried through the following API before expiration.
Before task ends: default 1-day expiration.
After task ends: default 1-hour expiration.

Configurable
```yaml
# Consumption expiration in seconds, default 1 day
execute-expire: 86400
# Result cache time in seconds, 1 hour
result-expire: 3600
```

<a href="/debug/proxy/getTask.do?taskId=xhxrmKzqpTnomFWpsBbKFTcMyqvZaxLK" target="_blank">Try it</a>

```json
{
  "status": 0,
  "msg": "success",
  "data": {
    "callbackRetryTimes": 0,
    "callbackStatus": "SUCCESS",
    "callerId": "RwtRBjTD",
    "completeTime": 1736911839436,
    "createTime": 1736911800453,
    "delayTime": 0,
    "executeExpire": 86400,
    "executeTime": 1736911800761,
    "extInfo": {
      "callback-url": "http://localhost:9400/debug/request.info"
    },
    "id": "xhxrmKzqpTnomFWpsBbKFTcMyqvZaxLK",
    "input": {
      "body": "base64:",
      "headers": {
        "host": [
          "funasr-offline-server.t-bps.ldx11.com"
        ],
        "remoteip": [
          "183.6.105.234"
        ],
        "user-agent": [
          "PostmanRuntime/7.43.0"
        ],
        "accept": [
          "*/*"
        ],
        "accept-encoding": [
          "gzip, deflate, br"
        ]
      },
      "method": "GET",
      "url": "https://news.baidu.com/widget?id=LocalNews&ajax=json"
    },
    "mode": "CALLBACK",
    "output": {
      "body": {
        "status": 0,
        "msg": "success",
        "data": {
          "text": "hello"
        }
      },
      "headers": {
        "Content-Type": [
          "application/json; charset=utf-8"
        ]
      },
      "status": 200
    },
    "resultExpire": 3600,
    "retryDelay": 0,
    "retryPlan": [60, 120, 300, 600, 3600],
    "retryTimes": 0,
    "scheduleTime": 1736911800,
    "status": "SUCCESS",
    "topic": "proxy"
  }
}
```

##### Invalid or Expired taskId
```yaml
  {
    "status": 404,
    "msg": "taskId not found"
  }
```
