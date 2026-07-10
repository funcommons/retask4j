---
title: Three-Way Diff Table (Documentation / Code / Tests)
---


This document performs an item-by-item comparison across the documentation, code, and tests of the retask4j project, identifying coverage gaps, documentation errors, and code defects.

## Diff Marker Legend

| Marker | Meaning |
|------|------|
| ✅ | Documentation, code, and tests are in agreement |
| ⚠️ Missing Documentation | Code has implementation, but documentation does not record it |
| ⚠️ Missing Tests | Code has implementation, but no test coverage |
| ❌ Documentation Error | Documentation description is inconsistent with the code implementation |
| 🐛 Code Defect | Code has a Bug |

---

## I. Key Findings Overview

### Code Bugs (all fixed)

| # | Location | Description | Fix | Test Verification |
|---|------|------|----------|----------|
| 🐛1✅ | `FuHttpTaskCallerController:103` | `retask4j-task-timing` delay calculation inverted | `(currentTime - timing)` → `(timing - currentTime)` | ✅ BugVerificationTest.TimingDelayFix |
| 🐛2✅ | `FuTaskWorker:166-173` | Null pointer risk: field accessed before null check | Move `id = taskMessage.getId()` after the null check | ✅ BugVerificationTest.WorkerNPEFix |
| 🐛3✅ | `FuTaskMonitor.WorkerMonitor` | Field name typo `fillyFail` | Renamed to `finallyFail` and updated all references | ✅ BugVerificationTest.FillyFailTypoFix |
| 🐛4✅ | `FuHttpTaskCallerController:110` | timing/delay has no mutual exclusion logic | Change delay's `if` to `else if` | ✅ BugVerificationTest.TimingDelayMutualExclusionFix |
| 🐛5✅ | `FuTaskWorkStrategy` | Callback and assertion were dead code as String | Added Functional interface fields; Worker callback method actually invokes them | ✅ BugVerificationTest.StrategyDeadCodeFix |
| 🐛6✅ | `HttpMessageUtils:190-191` | `remove(CONTENT_ENCODING)` called twice | Removed redundant second remove | ✅ FuTaskBatchManagerTest.contentEncodingDuplicateRemovalFixed |
| 🐛7✅ | `HttpMessageUtils:71` | Accept-Encoding not trimmed | `accepts.add(s)` → `accepts.add(s.trim())` | ✅ HttpMessageUtilsTest.AcceptEncodingFilter |
| 🐛8✅ | `FuHttpTaskWorkerAutoConfiguration:44` | Log message had wrong service name | `FuHttpTaskCallerService` → `FuHttpTaskWorkerService` | ✅ FuHttpTaskWorkerAutoConfigurationTest.logMessageFixed |

### Documentation / Code Inconsistencies

| # | Location | Description | Status |
|---|------|------|------|
| ❌1 | `best-practices.md` | BatchManager flush interval documented as 50ms, actual code default 20ms | Documentation pending fix |
| ❌2 | `configuration.md` | `FuHttpTaskCallerConfig.resultExpire` documentation table default `0`, actual code default `3600` | Documentation pending fix |
| ~~❌3~~ | ~~`http-proxy.md`~~ | ~~timing/delay mutual exclusion~~ | ✅ Fixed via else if |
| ~~❌4~~ | ~~`concepts.md`~~ | ~~Strategy callbacks were dead code~~ | ✅ Added Functional interface implementation |

---

## II. Module-Level Diff Details

### 2.1 retask4j-core — FuTaskCaller

| Feature / Method | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| Constructor (RedissonClient, FuTaskCallConfig) | ✅ | ✅ | ⚠️ | |
| Constructor (with Consumer callback) | ✅ | ✅ | ⚠️ | |
| `newTaskMessage(JSONObject)` auto ID | ✅ | ✅ | ⚠️ | |
| `newTaskMessage(String, JSONObject)` specified ID | ✅ | ✅ | ⚠️ | |
| `sendTaskMessage(FuTaskMessage)` single | ✅ | ✅ | ⚠️ | |
| `sendTaskMessage(List)` batch | ✅ | ✅ | ⚠️ | |
| `sendTaskMessageBatch(FuTaskMessage)` BatchManager | ✅ | ✅ | ⚠️ | |
| `sendFuncMessage` / `sendFuncMessageBatch` | ✅ | ✅ | ⚠️ | |
| `sendCallbackMessage` / `sendCallbackMessageBatch` | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage)` → CompletableFuture | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage, BiConsumer)` | ✅ | ✅ | ⚠️ | |
| `funcAsyncBatch(FuTaskMessage)` | ✅ | ✅ | ⚠️ | |
| `funcAsyncBatch(FuTaskMessage, BiConsumer)` | ✅ | ✅ | ⚠️ | |
| `funcAsync(List<Map.Entry>)` multi-task batch | ✅ | ✅ | ⚠️ | |
| `funcAsync(FuTaskMessage, CompletableFuture)` | ⚠️ | ✅ | ⚠️ | Missing docs: overload taking externally supplied Future |
| `funcAsync(Map.Entry)` single | ⚠️ | ✅ | ⚠️ | Missing docs |
| `funcAsyncComplete(List<Entry>)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `sendMessageBatch(FuTaskMessage)` internal batch | ⚠️ | ✅ | ⚠️ | Missing docs |
| `completeFuncFutureById(String)` | ⚠️ | ✅ | ⚠️ | Missing docs: public method |
| `completeFuncFutureById(List<String>)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `completeFuncFuture(List<FuTaskMessage>)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `completeCallbackById(String)` | ⚠️ | ✅ | ⚠️ | Missing docs: public method |
| `completeCallback(List<FuTaskMessage>)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| Caller ID auto generation (8 random letters) | ✅ | ✅ | ⚠️ | |
| Return Map cache (Guava Cache) | ✅ | ✅ | ⚠️ | |
| Callback retry logic | ✅ | ✅ | ⚠️ | |

### 2.2 retask4j-core — FuTaskWorker

| Feature / Method | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| Constructor | ✅ | ✅ | ⚠️ | |
| `start()` | ✅ | ✅ | ⚠️ | |
| `getMonitor()` | ✅ | ✅ | ⚠️ | |
| `consume()` consumption logic | ✅ | ✅ | ⚠️ | |
| Timeout / expiration check | ✅ | ✅ | ⚠️ | |
| PENDING state timeout handling | ✅ | ✅ | ⚠️ | |
| Retry exhaustion judgment | ✅ | ✅ | ⚠️ | |
| `onSuccess` / `onFail` / `onFinallyFail` / `onComplete` | ✅ | ✅ | ⚠️ | Only increment counters, no custom logic |
| `assertSuccess` | ✅ | ✅ | ⚠️ | Always returns true |
| runResetPending | ✅ | ✅ | ⚠️ | |
| runResetTiming | ✅ | ✅ | ⚠️ | |
| runResetRetry | ✅ | ✅ | ⚠️ | |

### 2.3 retask4j-core — FuTaskMessage

| Feature / Method | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| All 25 fields | ✅ | ✅ | ⚠️ | Only temp/test1.java manual verification |
| `toRequestMap()` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `toRetryMap()` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `toCompleteMap()` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `toCallbackMap()` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `fromStringMap(Map)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `@FuTag` annotation system | ⚠️ | ✅ | ⚠️ | Missing docs: core mechanism for marking fields by lifecycle phase |
| requestFields / retryFields / completeFields / callbackFields / allFields | ⚠️ | ✅ | ⚠️ | Missing docs: static field lists |

### 2.4 retask4j-core — FuTaskBase (7 Lua Scripts)

| Feature / Method | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| Lua: removeStoreSetToListScript | ✅ | ✅ | ⚠️ | timing/pending/retry → working |
| Lua: getTaskMessageForWorkScript | ✅ | ✅ | ⚠️ | |
| Lua: getTaskMessagesByIdScript | ✅ | ✅ | ⚠️ | |
| Lua: pushTaskMessageDequeBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: retryBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: competeBatchScript | ✅ | ✅ | ⚠️ | |
| Lua: setCallbackBatchScript | ✅ | ✅ | ⚠️ | |
| `send(List<FuTaskMessage>)` | ✅ | ✅ | ⚠️ | |
| `retry(FuTaskMessage, int)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `retry(List)` / `retry(varargs)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `update(String, Map)` / `update(String, Map, int)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `complete(List)` / `complete(varargs)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `pollReturnMessageIds(String, int)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `getMessagesForWork(int, int)` | ⚠️ | ✅ | ⚠️ | Missing docs: protected method |
| `getMessagesForCallback(int, int)` | ⚠️ | ✅ | ⚠️ | Missing docs |
| `getTaskCountInfo()` | ✅ | ✅ | ⚠️ | Used by Debug endpoints |
| 24-hour TTL auto expiration | ✅ | ✅ | ⚠️ | |

### 2.5 retask4j-core — Other Classes

| Class | Feature | Docs | Code | Tests | Notes |
|---|---|:---:|:---:|:---:|---|
| FuTaskCallConfig | All 10 fields | ✅ | ✅ | ⚠️ | |
| FuTaskWorkConfig | All 3 fields + addStrategy | ✅ | ✅ | ⚠️ | |
| FuTaskWorkStrategy | 6 fields | ❌ | ✅ | ⚠️ | Docs describe as behavior callbacks, code is actually String dead code |
| FuTaskExecutor | Two constructor overloads | ✅ | ✅ | ⚠️ | |
| FuTaskExecutor.`execute(JSONObject,JSONObject)` | ⚠️ | ✅ | ⚠️ | Missing docs: actual execution method |
| FuTaskBatchManager | Construction + submit + threshold + time trigger | ✅ | ✅ | ✅ | Only class with formal tests |
| FuTaskBatchManager.`getTaskCount()` / `getWorkerCount()` | ⚠️ | ✅ | ✅ | Missing docs |
| FuTaskBatchManager exception handling | ⚠️ | ✅ | ✅ | Missing docs |
| FuTaskBatchManager whenComplete callback | ⚠️ | ✅ | ✅ | Missing docs |
| FuTaskStatus (4 constants) | ✅ | ✅ | ⚠️ | |
| FuTaskMode (3 constants) | ✅ | ✅ | ⚠️ | |
| FuTaskRedissonUtils (3 Lua utility methods) | ⚠️ | ✅ | ⚠️ | Missing docs |
| FuTaskMonitor.WorkerMonitor.consume/success/fail/complete | ✅ | ✅ | ⚠️ | |
| FuTaskMonitor.WorkerMonitor.fillyFail | ❌ | ✅ | ⚠️ | Docs say `finallyFail`, code field name `fillyFail`, inconsistent spelling |
| FuTaskMonitor.WorkerMonitor.timingPoll / pendingPoll / retryPoll | ⚠️ | ✅ | ⚠️ | Missing docs |
| FuTaskMonitor.WorkerMonitor.workerActiveCount | ⚠️ | ✅ | ⚠️ | Missing docs |

### 2.6 retask4j-http — Caller Side

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| `FuHttpTaskCallerController.request()` | ✅ | ✅ | ⚠️ | |
| URL routing: `{path}/{target-url}` | ✅ | ✅ | ⚠️ | |
| URL format conversion: `https/` → `https://` | ✅ | ✅ | ⚠️ | |
| NORMAL mode push | ✅ | ✅ | ⚠️ | |
| FUNCTION mode async Servlet | ✅ | ✅ | ⚠️ | |
| CALLBACK mode callback | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-retry-plan` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-task-timing` | ✅ | 🐛 | ⚠️ | **Bug: delay calculation inverted** |
| Header: `retask4j-task-delay` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-assert-response` | ✅ | ✅ | ⚠️ | |
| Header: `retask4j-callback-url` | ✅ | ✅ | ⚠️ | |
| timing and delay mutual exclusion | ❌ | ✅ | ⚠️ | Docs say they cannot be used together, code has no mutual exclusion logic |
| timing auto-detects 10/13 digit timestamps | ✅ | ✅ | ⚠️ | |
| timing maximum 24-hour limit | ✅ | ✅ | ⚠️ | |
| delay range 1~3600 validation | ✅ | ✅ | ⚠️ | |
| batch config toggle | ✅ | ✅ | ⚠️ | |
| Config headers injection/override | ✅ | ✅ | ⚠️ | |
| Remote call Host header rewrite | ⚠️ | ✅ | ⚠️ | Missing docs |
| `FuHttpTaskCallerService` dynamic RequestMapping registration | ⚠️ | ✅ | ⚠️ | Missing docs |
| `FuHttpTaskCallerService.destroy()` deregistration | ⚠️ | ✅ | ⚠️ | Missing docs |
| `FuHttpTaskCallerAsyncListener` async listener | ⚠️ | ✅ | ⚠️ | Missing docs |
| `FuHttpTaskCallerAutoConfiguration` conditional wiring | ⚠️ | ✅ | ⚠️ | Missing docs: @ConditionalOnMissingBean |
| `FuHttpTaskCallback` POST callback data format | ✅ | ✅ | ⚠️ | |

### 2.7 retask4j-http — Worker Side

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| `FuHttpTaskWorkerService.doExecute()` remote call | ✅ | ✅ | ⚠️ | |
| `doExecute()` local MockMvc call | ✅ | ✅ | ⚠️ | |
| enableRemote / enableLocal switches | ✅ | ✅ | ⚠️ | |
| Route regex matching | ✅ | ✅ | ⚠️ | |
| URL redirect (redirect + capture groups) | ✅ | ✅ | ⚠️ | |
| Request header rewrite | ✅ | ✅ | ⚠️ | |
| Response header rewrite | ✅ | ✅ | ⚠️ | |
| Assertion: statusIn | ✅ | ✅ | ⚠️ | |
| Assertion: headerMatch | ✅ | ✅ | ⚠️ | |
| Assertion: textBodyMatch | ✅ | ✅ | ⚠️ | |
| Assertion: jsonPathMatch | ✅ | ✅ | ⚠️ | |
| assert-response overridden via extInfo header | ✅ | ✅ | ⚠️ | |
| `FuHttpTaskWorkerService.destroy()` | ⚠️ | ✅ | ⚠️ | Missing docs (currently just logs) |
| `FuHttpTaskWorkerAutoConfiguration` conditional wiring | ⚠️ | ✅ | ⚠️ | Missing docs |

### 2.8 retask4j-http — Message Model and Utilities

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| HttpData body handling (JSON / text / base64) | ⚠️ | ✅ | ⚠️ | Missing docs |
| HttpRequestData (url, method, headers, body) | ⚠️ | ✅ | ⚠️ | Missing docs |
| HttpResponseData (status, reason, headers, body) | ⚠️ | ✅ | ⚠️ | Missing docs |
| HttpResponseData.`error()` / `json()` factory methods | ⚠️ | ✅ | ⚠️ | Missing docs |
| HttpMessageUtils 7 public methods | ⚠️ | ✅ | ⚠️ | Missing docs |
| Compression/decompression: gzip / deflate / br / zstd | ✅ | ✅ | ⚠️ | |
| Multipart form-data handling | ✅ | ✅ | ⚠️ | |
| Accept-Encoding filter | ⚠️ | ✅ | ⚠️ | Missing docs |
| `FuHttpTaskBaseController` response writing utility | ⚠️ | ✅ | ⚠️ | Missing docs |

### 2.9 retask4j-http — Configuration Classes

| Config Item | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| FuHttpTaskCallerConfig.topic | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.path | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.mode | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.retryPlan | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.executeExpire | ✅ | ✅ | ⚠️ | |
| FuHttpTaskCallerConfig.resultExpire | ❌ | ✅ | ⚠️ | Docs default `0`, code default `3600` |
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
| RouteConfig all 5 fields | ✅ | ✅ | ⚠️ | |
| AssertsConfig all 4 fields | ✅ | ✅ | ⚠️ | |

### 2.10 retask4j-http-server

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| MainApplication Spring Boot entry | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/{topic}/taskCount.do` | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/{topic}/getTask.do` | ✅ | ✅ | ⚠️ | |
| DebugController `/debug/request.info` | ✅ | ✅ | ⚠️ | |
| BaseController response utility | ⚠️ | ✅ | ⚠️ | Missing docs |
| DocumentsController Markdown rendering | ⚠️ | ✅ | ⚠️ | Missing docs |
| index.html → /documents/readme.md redirect | ⚠️ | ✅ | ⚠️ | Missing docs |

### 2.11 retask4j-http-starter

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| spring.factories auto-config registration | ✅ | ✅ | ⚠️ | |
| AutoConfiguration.imports (Spring Boot 3.x) | ⚠️ | ✅ | ⚠️ | Missing docs |

### 2.12 Demo Modules

| Feature | Docs | Code | Tests | Notes |
|---|:---:|:---:|:---:|---|
| demo-taskcaller `/demo-push/send.do` | ⚠️ | ✅ | ⚠️ | Missing docs |
| demo-taskcaller `/demo-push/batch.do` | ⚠️ | ✅ | ⚠️ | Missing docs |
| demo-taskworker WorkService | ⚠️ | ✅ | ⚠️ | Missing docs |

---

## III. Test Coverage Statistics

### By Module

| Module | Java Source Files | Formal Test Classes | Test Methods | Coverage Assessment |
|---|---|---|---|---|
| retask4j-core | 14 | 7 | 88 | ~70% (core logic covered; Redis Lua requires integration tests) |
| retask4j-http | 16 | 11 | 18+ | ~60% (HTTP messages, routing, assertions, configuration covered; Spring wiring requires integration tests) |
| retask4j-http-server | 4 | 0 | 0 | 0% (Spring Boot integration tests needed) |
| retask4j-http-starter | 0 | 0 | 0 | N/A |
| retask4j-demo-taskcaller | 3 | 0 | 0 | 0% (Demo module) |
| retask4j-demo-taskworker | 2 | 0 | 0 | 0% (Demo module) |

**Total: 106 test methods, 0 failures, 0 errors**

### Key Untested Scenarios

The following scenarios have documentation and code implementation but need integration tests (Spring Boot context + Redis):

1. NORMAL mode end-to-end flow
2. FUNCTION mode synchronous return flow
3. CALLBACK mode callback and retry flow
4. Scheduled message end-to-end (retask4j-task-timing, bug already verified)
5. Lua script atomic operation correctness
6. Local MockMvc calls
7. Multipart file upload end-to-end
8. Callback retry and callback timeout end-to-end
9. Batch send batching mechanism end-to-end

The following scenarios have unit test coverage:

1. ✅ Retry plan parsing
2. ✅ Scheduled/delayed message timestamp parsing and range validation
3. ✅ Retry exhaustion judgment
4. ✅ Task expiration (executeExpire) logic
5. ✅ Response assertion (statusIn / textBodyMatch / jsonPathMatch)
6. ✅ headerMatch regex matching (with space bug verification)
7. ✅ URL redirect and capture group substitution
8. ✅ Request header / response header rewrite
9. ✅ Compressed content decompression (gzip)
10. ✅ Accept-Encoding filter
11. ✅ Config headers injection/override
12. ✅ URL routing format conversion
13. ✅ FuTaskMessage serialization/deserialization round-trip
14. ✅ FuTag annotation system field grouping
15. ✅ Caller/Worker configuration class defaults
16. ✅ BatchManager submit/threshold/time trigger/exception handling

---

## IV. Fix Priority Recommendations

### P0 — Must Fix Immediately

| # | Type | Description |
|---|------|------|
| 🐛1 | Code Bug | `retask4j-task-timing` delay calculation formula inverted; scheduled messaging functionality unavailable |

### P1 — High Priority

| # | Type | Description |
|---|------|------|
| ❌4 | Documentation Error | FuTaskWorkStrategy event callbacks and assertion rules are dead code; docs mislead users |
| ❌1 | Documentation Error | BatchManager flush interval 50ms → actual 20ms |
| ❌2 | Documentation Error | resultExpire default 0 → actual 3600 |
| 🐛2 | Code Bug | FuTaskWorker null pointer risk |

### P2 — Medium Priority

| # | Type | Description |
|---|------|------|
| ❌3 | Documentation Error | Timing and delay mutual exclusion description inconsistent with code |
| 🐛3 | Code Defect | WorkerMonitor.fillyFail typo |
| ⚠️ | Missing Documentation | 6 FuTaskCaller public methods undocumented |
| ⚠️ | Missing Documentation | FuTaskBase retry/update/complete etc. methods undocumented |
| ⚠️ | Missing Documentation | FuTaskMessage serialization methods and FuTag system undocumented |

### P3 — Low Priority

| # | Type | Description |
|---|------|------|
| ⚠️ | Missing Documentation | Internal utility classes such as HttpMessageUtils / HttpData undocumented |
| ⚠️ | Missing Documentation | AutoConfiguration conditional wiring logic undocumented |
| ⚠️ | Missing Documentation | DocumentsController / Demo modules undocumented |
| ⚠️ | Missing Tests | All 16 key scenarios have no test coverage |
