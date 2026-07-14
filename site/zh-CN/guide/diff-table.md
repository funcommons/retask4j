---
title: 三方对照表（文档 / 代码 / 测试）
---


本文档对 retask4j 项目的文档、代码和测试进行逐项比对，找出覆盖差异、文档错误和代码缺陷。

## 对照标识说明

| 标识 | 含义 |
|------|------|
| ✅ | 文档、代码、测试三方一致 |
| ⚠️ 缺失文档 | 代码有实现，但文档未记录 |
| ⚠️ 缺少测试 | 代码有实现，但无测试覆盖 |
| ❌ 文档错误 | 文档描述与代码实现不一致 |
| 🐛 代码缺陷 | 代码存在 Bug |

---

## I. 关键发现概览

### 代码 Bug（已全部修复）

| # | 位置 | 描述 | 修复 | 测试验证 |
|---|------|------|----------|----------|
| 🐛1✅ | `FuHttpTaskCallerController:103` | `retask4j-task-timing` 延时计算方向反了 | `(currentTime - timing)` → `(timing - currentTime)` | ✅ BugVerificationTest.TimingDelayFix |
| 🐛2✅ | `FuTaskWorker:166-173` | 空指针风险：字段在 null 检查前就被访问 | 将 `id = taskMessage.getId()` 移到 null 检查之后 | ✅ BugVerificationTest.WorkerNPEFix |
| 🐛3✅ | `FuTaskMonitor.WorkerMonitor` | 字段名拼写错误 `fillyFail` | 重命名为 `finallyFail` 并更新所有引用 | ✅ BugVerificationTest.FillyFailTypoFix |
| 🐛4✅ | `FuHttpTaskCallerController:110` | timing/delay 没有互斥逻辑 | 将 delay 的 `if` 改为 `else if` | ✅ BugVerificationTest.TimingDelayMutualExclusionFix |
| 🐛5✅ | `FuTaskWorkStrategy` | 回调和断言是 String 类型的死代码 | 新增函数式接口字段；Worker 回调方法实际调用它们 | ✅ BugVerificationTest.StrategyDeadCodeFix |
| 🐛6✅ | `HttpMessageUtils:190-191` | `remove(CONTENT_ENCODING)` 被调用了两次 | 移除多余的第二次 remove | ✅ FuTaskBatchManagerTest.contentEncodingDuplicateRemovalFixed |
| 🐛7✅ | `HttpMessageUtils:71` | Accept-Encoding 未做 trim | `accepts.add(s)` → `accepts.add(s.trim())` | ✅ HttpMessageUtilsTest.AcceptEncodingFilter |
| 🐛8✅ | `FuHttpTaskWorkerAutoConfiguration:44` | 日志中服务名写错 | `FuHttpTaskCallerService` → `FuHttpTaskWorkerService` | ✅ FuHttpTaskWorkerAutoConfigurationTest.logMessageFixed |

### 文档 / 代码不一致

| # | 位置 | 描述 | 状态 |
|---|------|------|------|
| ❌1 | `best-practices.md` | BatchManager flush 间隔文档写为 50ms，实际代码默认为 20ms | 文档待修复 |
| ❌2 | `configuration.md` | `FuHttpTaskCallerConfig.resultExpire` 文档表格默认值 `0`，实际代码默认为 `3600` | 文档待修复 |
| ~~❌3~~ | ~~`http-proxy.md`~~ | ~~timing/delay 互斥~~ | ✅ 已通过 else if 修复 |
| ~~❌4~~ | ~~`concepts.md`~~ | ~~策略回调是死代码~~ | ✅ 已新增函数式接口实现 |

---

## II. 模块级 Diff 详情

### 2.1 retask4j-core — FuTaskCaller

| 功能 / 方法 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| 构造函数 (RedissonClient, FuTaskCallConfig) | ✅ | ✅ | ⚠️ | |
| 构造函数（带 Consumer 回调） | ✅ | ✅ | ⚠️ | |
| `newTaskMessage(JSONObject)` 自动 ID | ✅ | ✅ | ⚠️ | |
| `newTaskMessage(String, JSONObject)` 指定 ID | ✅ | ✅ | ⚠️ | |
| `sendTaskMessage(FuTaskMessage)` 单条 | ✅ | ✅ | ⚠️ | |
| `sendTaskMessage(List)` 批量 | ✅ | ✅ | ⚠️ | |
| `sendTaskMessageBatch(FuTaskMessage)` BatchManager | ✅ | ✅ | ⚠️ | |
| `sendFuncMessage` / `sendFuncMessageBatch` | ✅ | ✅ | ⚠️ | |
| `sendCallbackMessage` / `sendCallbackMessageBatch` | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage)` → CompletableFuture | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage, BiConsumer)` | ✅ | ✅ | ⚠️ | |
| `funcAsyncBatch(FuTaskMessage)` | ✅ | ✅ | ⚠️ | |
| `funcAsyncBatch(FuTaskMessage, BiConsumer)` | ✅ | ✅ | ⚠️ | |
| `funcAsync(List<Map.Entry>)` 多任务批量 | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage, CompletableFuture)` | ⚠️ | ✅ | ⚠️ | 缺少文档：接收外部 Future 的重载 |
| `funcAsync(Map.Entry)` 单条 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `funcAsyncComplete(List<Entry>)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `sendMessageBatch(FuTaskMessage)` 内部批量 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `completeFuncFutureById(String)` | ⚠️ | ✅ | ⚠️ | 缺少文档：public 方法 |
| `completeFuncFutureById(List<String>)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `completeFuncFuture(List<FuTaskMessage>)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `completeCallbackById(String)` | ⚠️ | ✅ | ⚠️ | 缺少文档：public 方法 |
| `completeCallback(List<FuTaskMessage>)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| Caller ID 自动生成（8 位随机字母） | ✅ | ✅ | ⚠️ | |
| Return Map 缓存（Guava Cache） | ✅ | ✅ | ⚠️ | |
| Callback 重试逻辑 | ✅ | ✅ | ⚠️ | |

### 2.2 retask4j-core — FuTaskWorker

| 功能 / 方法 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| 构造函数 | ✅ | ✅ | ⚠️ | |
| `start()` | ✅ | ✅ | ⚠️ | |
| `getMonitor()` | ✅ | ✅ | ⚠️ | |
| `consume()` 消费逻辑 | ✅ | ✅ | ⚠️ | |
| 超时 / 过期检查 | ✅ | ✅ | ⚠️ | |
| PENDING 状态超时处理 | ✅ | ✅ | ⚠️ | |
| 重试耗尽判断 | ✅ | ✅ | ⚠️ | |
| `onSuccess` / `onFail` / `onFinallyFail` / `onComplete` | ✅ | ✅ | ⚠️ | 仅递增计数器，无自定义逻辑 |
| `assertSuccess` | ✅ | ✅ | ⚠️ | 始终返回 true |
| runResetPending | ✅ | ✅ | ⚠️ | |
| runResetTiming | ✅ | ✅ | ⚠️ | |
| runResetRetry | ✅ | ✅ | ⚠️ | |

### 2.3 retask4j-core — FuTaskMessage

| 功能 / 方法 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| 全部 25 个字段 | ✅ | ✅ | ⚠️ | 仅通过 temp/test1.java 手工验证 |
| `toRequestMap()` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `toRetryMap()` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `toCompleteMap()` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `toCallbackMap()` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `fromStringMap(Map)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `@FuTag` 注解系统 | ⚠️ | ✅ | ⚠️ | 缺少文档：按生命周期阶段标记字段的核心机制 |
| requestFields / retryFields / completeFields / callbackFields / allFields | ⚠️ | ✅ | ⚠️ | 缺少文档：静态字段列表 |

### 2.4 retask4j-core — FuTaskBase（7 个 Lua 脚本）

| 功能 / 方法 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| Lua: removeStoreSetToListScript | ✅ | ✅ | ⚠️ | timing/pending/retry → working |
| Lua: getTaskMessageForWorkScript | ✅ | ✅ | ⚠️ | |
| Lua: getTaskMessagesByIdScript | ✅ | ✅ | ⚠️ | |
| Lua: pushTaskMessageDequeBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: retryBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: competeBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: setCallbackBatchScript | ✅ | ✅ | ⚠️ | |
| `send(List<FuTaskMessage>)` | ✅ | ✅ | ⚠️ | |
| `retry(FuTaskMessage, int)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `retry(List)` / `retry(varargs)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `update(String, Map)` / `update(String, Map, int)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `complete(List)` / `complete(varargs)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `pollReturnMessageIds(String, int)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `getMessagesForWork(int, int)` | ⚠️ | ✅ | ⚠️ | 缺少文档：protected 方法 |
| `getMessagesForCallback(int, int)` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `getTaskCountInfo()` | ✅ | ✅ | ⚠️ | 被 Debug 端点使用 |
| 24 小时 TTL 自动过期 | ✅ | ✅ | ⚠️ | |

### 2.5 retask4j-core — 其他类

| 类 | 功能 | 文档 | 代码 | 测试 | 备注 |
|---|---|:---:|:---:|:---:|---|
| FuTaskCallConfig | 全部 10 个字段 | ✅ | ✅ | ⚠️ | |
| FuTaskWorkConfig | 全部 3 个字段 + addStrategy | ✅ | ✅ | ⚠️ | |
| FuTaskWorkStrategy | 6 个字段 | ❌ | ✅ | ⚠️ | 文档描述为行为回调，代码实际是 String 死代码 |
| FuTaskExecutor | 两种构造重载 | ✅ | ✅ | ⚠️ | |
| FuTaskExecutor.`execute(JSONObject,JSONObject)` | ⚠️ | ✅ | ⚠️ | 缺少文档：实际执行方法 |
| FuTaskBatchManager | 构造 + submit + 阈值 + 时间触发 | ✅ | ✅ | ✅ | 唯一有正式测试的类 |
| FuTaskBatchManager.`getTaskCount()` / `getWorkerCount()` | ⚠️ | ✅ | ✅ | 缺少文档 |
| FuTaskBatchManager 异常处理 | ⚠️ | ✅ | ✅ | 缺少文档 |
| FuTaskBatchManager whenComplete 回调 | ⚠️ | ✅ | ✅ | 缺少文档 |
| FuTaskStatus（4 个常量） | ✅ | ✅ | ⚠️ | |
| FuTaskMode（3 个常量） | ✅ | ✅ | ⚠️ | |
| FuTaskRedissonUtils（3 个 Lua 工具方法） | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| FuTaskMonitor.WorkerMonitor.consume/success/fail/complete | ✅ | ✅ | ⚠️ | |
| FuTaskMonitor.WorkerMonitor.fillyFail | ❌ | ✅ | ⚠️ | 文档写 `finallyFail`，代码字段名为 `fillyFail`，拼写不一致 |
| FuTaskMonitor.WorkerMonitor.timingPoll / pendingPoll / retryPoll | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| FuTaskMonitor.WorkerMonitor.workerActiveCount | ⚠️ | ✅ | ⚠️ | 缺少文档 |

### 2.6 retask4j-http — Caller 侧

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| `FuHttpTaskCallerController.request()` | ✅ | ✅ | ⚠️ | |
| URL 路由：`{path}/{target-url}` | ✅ | ✅ | ⚠️ | |
| URL 格式转换：`https/` → `https://` | ✅ | ✅ | ⚠️ | |
| NORMAL 模式推送 | ✅ | ✅ | ⚠️ | |
| FUNCTION 模式异步 Servlet | ✅ | ✅ | ⚠️ | |
| CALLBACK 模式回调 | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-retry-plan` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-task-timing` | ✅ | 🐛 | ⚠️ | **Bug：延时计算方向反了** |
| Header: `retask4j-task-delay` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-assert-response` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-callback-url` | ✅ | ✅ | ⚠️ | |
| timing 与 delay 互斥 | ❌ | ✅ | ⚠️ | 文档写不可同时使用，代码无互斥逻辑 |
| timing 自动检测 10/13 位时间戳 | ✅ | ✅ | ⚠️ | |
| timing 最大 24 小时限制 | ✅ | ✅ | ⚠️ | |
| delay 范围 1~3600 校验 | ✅ | ✅ | ⚠️ | |
| batch 配置开关 | ✅ | ✅ | ⚠️ | |
| Config headers 注入/覆盖 | ✅ | ✅ | ⚠️ | |
| 远程调用 Host header 重写 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `FuHttpTaskCallerService` 动态 RequestMapping 注册 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `FuHttpTaskCallerService.destroy()` 注销 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `FuHttpTaskCallerAsyncListener` 异步监听 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `FuHttpTaskCallerAutoConfiguration` 条件装配 | ⚠️ | ✅ | ⚠️ | 缺少文档：@ConditionalOnMissingBean |
| `FuHttpTaskCallback` POST 回调数据格式 | ✅ | ✅ | ⚠️ | |

### 2.7 retask4j-http — Worker 侧

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| `FuHttpTaskWorkerService.doExecute()` 远程调用 | ✅ | ✅ | ⚠️ | |
| `doExecute()` 本地 MockMvc 调用 | ✅ | ✅ | ⚠️ | |
| enableRemote / enableLocal 开关 | ✅ | ✅ | ⚠️ | |
| 路由正则匹配 | ✅ | ✅ | ⚠️ | |
| URL 重定向（redirect + 捕获组） | ✅ | ✅ | ⚠️ | |
| 请求头重写 | ✅ | ✅ | ⚠️ | |
| 响应头重写 | ✅ | ✅ | ⚠️ | |
| 断言：statusIn | ✅ | ✅ | ⚠️ | |
| 断言：headerMatch | ✅ | ✅ | ⚠️ | |
| 断言：textBodyMatch | ✅ | ✅ | ⚠️ | |
| 断言：jsonPathMatch | ✅ | ✅ | ⚠️ | |
| 通过 extInfo header 覆盖 assert-response | ✅ | ✅ | ⚠️ | |
| `FuHttpTaskWorkerService.destroy()` | ⚠️ | ✅ | ⚠️ | 缺少文档（目前仅记录日志） |
| `FuHttpTaskWorkerAutoConfiguration` 条件装配 | ⚠️ | ✅ | ⚠️ | 缺少文档 |

### 2.8 retask4j-http — 消息模型与工具

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| HttpData 请求体处理（JSON / text / base64） | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| HttpRequestData（url, method, headers, body） | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| HttpResponseData（status, reason, headers, body） | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| HttpResponseData.`error()` / `json()` 工厂方法 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| HttpMessageUtils 7 个 public 方法 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| 压缩/解压：gzip / deflate / br / zstd | ✅ | ✅ | ⚠️ | |
| Multipart form-data 处理 | ✅ | ✅ | ⚠️ | |
| Accept-Encoding 过滤 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| `FuHttpTaskBaseController` 响应写入工具 | ⚠️ | ✅ | ⚠️ | 缺少文档 |

### 2.9 retask4j-http — 配置类

| 配置项 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| FuHttpTaskCallerConfig.topic | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.path | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.mode | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.retryPlan | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.executeExpire | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.resultExpire | ❌ | ✅ | ⚠️ | 文档默认 `0`，代码默认为 `3600` |
| FuHttpTaskCallerConfig.requestTimeout | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.callbackUrl | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.callbackRetryTimes | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.callbackRetryInterval | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.callbackMaxThreads | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.headers | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.batch | ✅ | ✅ | ⚠️ | |
| FuHttpTaskWorkerConfig.topic | ✅ | ✅ | ⚠️ | |
| FuHttpTaskWorkerConfig.maxConsumeThreads | ✅ | ✅ | ⚠️ | |
| FuHttpTaskWorkerConfig.enableRemote / enableLocal | ✅ | ✅ | ⚠️ | |
| FuHttpTaskWorkerConfig.routes | ✅ | ✅ | ⚠️ | |
| RouteConfig 全部 5 个字段 | ✅ | ✅ | ⚠️ | |
| AssertsConfig 全部 4 个字段 | ✅ | ✅ | ⚠️ | |

### 2.10 retask4j-http-server

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| MainApplication Spring Boot 入口 | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/{topic}/taskCount.do` | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/{topic}/getTask.do` | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/request.info` | ✅ | ✅ | ⚠️ | |
| BaseController 响应工具 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| DocumentsController Markdown 渲染 | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| index.html → /documents/readme.md 重定向 | ⚠️ | ✅ | ⚠️ | 缺少文档 |

### 2.11 retask4j-http-starter

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| spring.factories 自动配置注册 | ✅ | ✅ | ⚠️ | |
| AutoConfiguration.imports（Spring Boot 3.x） | ⚠️ | ✅ | ⚠️ | 缺少文档 |

### 2.12 Demo 模块

| 功能 | 文档 | 代码 | 测试 | 备注 |
|---|:---:|:---:|:---:|---|
| demo-taskcaller `/demo-push/send.do` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| demo-taskcaller `/demo-push/batch.do` | ⚠️ | ✅ | ⚠️ | 缺少文档 |
| demo-taskworker WorkService | ⚠️ | ✅ | ⚠️ | 缺少文档 |

---

## III. 测试覆盖率统计

### 按模块

| 模块 | Java 源文件 | 正式测试类 | 测试方法数 | 覆盖率评估 |
|---|---|---|---|---|
| retask4j-core | 14 | 7 | 88 | ~70%（核心逻辑已覆盖；Redis Lua 需要集成测试） |
| retask4j-http | 16 | 11 | 18+ | ~60%（HTTP 消息、路由、断言、配置已覆盖；Spring 装配需要集成测试） |
| retask4j-http-server | 4 | 0 | 0 | 0%（需要 Spring Boot 集成测试） |
| retask4j-http-starter | 0 | 0 | 0 | N/A |
| retask4j-demo-taskcaller | 3 | 0 | 0 | 0%（Demo 模块） |
| retask4j-demo-taskworker | 2 | 0 | 0 | 0%（Demo 模块） |

**总计：106 个测试方法，0 个失败，0 个错误**

### 关键未测试场景

以下场景已有文档和代码实现，但需要集成测试（Spring Boot 上下文 + Redis）：

1. NORMAL 模式端到端流程
2. FUNCTION 模式同步返回流程
3. CALLBACK 模式回调与重试流程
4. 定时消息端到端（retask4j-task-timing，bug 已验证）
5. Lua 脚本原子操作正确性
6. 本地 MockMvc 调用
7. Multipart 文件上传端到端
8. 回调重试与回调超时端到端
9. 批量发送的批量化机制端到端

以下场景已有单元测试覆盖：

1. ✅ 重试计划解析
2. ✅ 定时/延时消息时间戳解析与范围校验
3. ✅ 重试耗尽判断
4. ✅ 任务过期（executeExpire）逻辑
5. ✅ 响应断言（statusIn / textBodyMatch / jsonPathMatch）
6. ✅ headerMatch 正则匹配（含空格 bug 验证）
7. ✅ URL 重定向与捕获组替换
8. ✅ 请求头 / 响应头重写
9. ✅ 压缩内容解压（gzip）
10. ✅ Accept-Encoding 过滤
11. ✅ Config headers 注入/覆盖
12. ✅ URL 路由格式转换
13. ✅ FuTaskMessage 序列化/反序列化往返
14. ✅ FuTag 注解系统字段分组
15. ✅ Caller/Worker 配置类默认值
16. ✅ BatchManager submit / 阈值 / 时间触发 / 异常处理

---

## IV. 修复优先级建议

### P0 — 必须立即修复

| # | 类型 | 描述 |
|---|------|------|
| 🐛1 | 代码 Bug | `retask4j-task-timing` 延时计算公式方向反了；定时消息功能不可用 |

### P1 — 高优先级

| # | 类型 | 描述 |
|---|------|------|
| ❌4 | 文档错误 | FuTaskWorkStrategy 事件回调与断言规则是死代码；文档误导用户 |
| ❌1 | 文档错误 | BatchManager flush 间隔 50ms → 实际 20ms |
| ❌2 | 文档错误 | resultExpire 默认 0 → 实际 3600 |
| 🐛2 | 代码 Bug | FuTaskWorker 空指针风险 |

### P2 — 中优先级

| # | 类型 | 描述 |
|---|------|------|
| ❌3 | 文档错误 | timing 与 delay 互斥的描述与代码不一致 |
| 🐛3 | 代码缺陷 | WorkerMonitor.fillyFail 拼写错误 |
| ⚠️ | 缺少文档 | 6 个 FuTaskCaller 公共方法未文档化 |
| ⚠️ | 缺少文档 | FuTaskBase 的 retry / update / complete 等方法未文档化 |
| ⚠️ | 缺少文档 | FuTaskMessage 序列化方法与 FuTag 系统未文档化 |

### P3 — 低优先级

| # | 类型 | 描述 |
|---|------|------|
| ⚠️ | 缺少文档 | HttpMessageUtils / HttpData 等内部工具类未文档化 |
| ⚠️ | 缺少文档 | AutoConfiguration 条件装配逻辑未文档化 |
| ⚠️ | 缺少文档 | DocumentsController / Demo 模块未文档化 |
| ⚠️ | 缺少测试 | 全部 16 个关键场景无测试覆盖 |
