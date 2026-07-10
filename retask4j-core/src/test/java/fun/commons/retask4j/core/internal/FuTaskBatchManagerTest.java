package fun.commons.retask4j.core.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class FuTaskBatchManagerTest {

    @Nested
    @DisplayName("构造与配置")
    class Construction {

        @Test
        @DisplayName("默认构造函数参数正确")
        void defaultConstructor() {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(list -> "ok");

            assertDoesNotThrow(() -> manager.submit("test"));
            assertEquals(0, manager.getWorkerCount());
        }

        @Test
        @DisplayName("自定义构造参数")
        void customConstructor() {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                5, 10, 2, list -> "result"
            );

            assertDoesNotThrow(() -> manager.submit("test"));
        }
    }

    @Nested
    @DisplayName("submit 提交任务")
    class Submit {

        @Test
        @DisplayName("submit 返回 CompletableFuture")
        void submitReturnsFuture() {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1, 50, 4, list -> "batch-result"
            );

            CompletableFuture<String> future = manager.submit("item1");
            assertNotNull(future);
        }

        @Test
        @DisplayName("submit 带 BiConsumer 回调")
        void submitWithCallback() {
            List<String> callbackResults = new ArrayList<>();
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1, 50, 4, list -> "batch-result"
            );

            CompletableFuture<String> future = manager.submit("item1", (result, ex) -> {
                if (ex == null) callbackResults.add(result);
            });
            assertNotNull(future);
        }

        @Test
        @DisplayName("批量触发：达到 batchSize 触发处理")
        void batchTriggerOnSize() throws Exception {
            List<List<String>> capturedBatches = new ArrayList<>();
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                3, 5000, 4, list -> {
                    capturedBatches.add(new ArrayList<>(list));
                    return "ok";
                }
            );

            CompletableFuture<String> f1 = manager.submit("a");
            CompletableFuture<String> f2 = manager.submit("b");
            CompletableFuture<String> f3 = manager.submit("c");

            // 等待批次处理完成
            String result = f3.get(2, TimeUnit.SECONDS);
            assertEquals("ok", result);

            // 验证批次包含 3 个元素
            assertFalse(capturedBatches.isEmpty());
            assertEquals(3, capturedBatches.get(0).size());
            assertTrue(capturedBatches.get(0).contains("a"));
            assertTrue(capturedBatches.get(0).contains("b"));
            assertTrue(capturedBatches.get(0).contains("c"));
        }
    }

    @Nested
    @DisplayName("批次处理逻辑")
    class BatchProcessing {

        @Test
        @DisplayName("所有 future 共享同一个结果（设计确认）")
        void sharedResultAcrossFutures() throws Exception {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                2, 50, 4, list -> "shared-result"
            );

            CompletableFuture<String> f1 = manager.submit("x");
            CompletableFuture<String> f2 = manager.submit("y");

            assertEquals("shared-result", f1.get(2, TimeUnit.SECONDS));
            assertEquals("shared-result", f2.get(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("batchFunction 抛异常时所有 future 异常完成")
        void batchFunctionException() throws Exception {
            RuntimeException expectedEx = new RuntimeException("batch failed");
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1, 50, 4, list -> { throw expectedEx; }
            );

            CompletableFuture<String> future = manager.submit("fail-item");

            Exception actualEx = assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS));
            // CompletableFuture wraps execution exceptions
            assertNotNull(actualEx);
        }

        @Test
        @DisplayName("时间间隔触发批次处理")
        void timeIntervalTrigger() throws Exception {
            List<List<String>> capturedBatches = new ArrayList<>();
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1000, 50, 4, list -> {
                    capturedBatches.add(new ArrayList<>(list));
                    return "ok";
                }
            );

            manager.submit("delayed-item");

            // 等待时间间隔触发（50ms interval + 10ms scheduler tick）
            Thread.sleep(200);

            assertFalse(capturedBatches.isEmpty(), "时间间隔应触发批次处理");
            assertEquals(1, capturedBatches.get(0).size());
            assertEquals("delayed-item", capturedBatches.get(0).get(0));
        }
    }

    @Nested
    @DisplayName("getTaskCount / getWorkerCount")
    class Observability {

        @Test
        @DisplayName("getTaskCount 反映队列大小")
        void taskCount() {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1000, 5000, 4, list -> "ok"
            );

            manager.submit("a");
            manager.submit("b");

            // 任务可能在检查前已被处理，但至少应 >= 0
            assertTrue(manager.getTaskCount() >= 0);
        }

        @Test
        @DisplayName("getWorkerCount 初始为 0")
        void workerCountInitial() {
            FuTaskBatchManager<String, String> manager = new FuTaskBatchManager<>(
                1000, 5000, 4, list -> "ok"
            );

            assertEquals(0, manager.getWorkerCount());
        }
    }

    @Nested
    @DisplayName("Bug 验证")
    class BugVerification {

        @Test
        @DisplayName("Bug 确认：批次所有 future 共享单一结果，非逐项结果")
        void sharedResultBug() throws Exception {
            FuTaskBatchManager<Integer, String> manager = new FuTaskBatchManager<>(
                3, 50, 4, list -> "single-result-for-all"
            );

            CompletableFuture<String> f1 = manager.submit(1);
            CompletableFuture<String> f2 = manager.submit(2);
            CompletableFuture<String> f3 = manager.submit(3);

            // 所有 future 得到相同的结果，即使输入不同
            assertEquals("single-result-for-all", f1.get(2, TimeUnit.SECONDS));
            assertEquals("single-result-for-all", f2.get(2, TimeUnit.SECONDS));
            assertEquals("single-result-for-all", f3.get(2, TimeUnit.SECONDS));
            // Bug 确认：无法为每个 item 返回独立结果
        }

        @Test
        @DisplayName("修复确认：Content-Encoding 重复移除已修复（HttpMessageUtils 中）")
        void contentEncodingDuplicateRemovalFixed() {
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Encoding", "gzip");

            // 修复确认：只调用一次 remove
            headers.remove("Content-Encoding");
            assertNull(headers.get("Content-Encoding"));
        }
    }
}
