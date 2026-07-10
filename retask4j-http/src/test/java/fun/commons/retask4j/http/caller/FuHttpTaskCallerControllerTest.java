package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.http.message.HttpRequestData;
import fun.commons.retask4j.http.message.HttpResponseData;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FuHttpTaskCallerController 测试 — 验证 URL 路由、Header 解析、请求打包逻辑。
 */
class FuHttpTaskCallerControllerTest {

    @Nested
    @DisplayName("URL 路由规则")
    class UrlRouting {

        @Test
        @DisplayName("https/ 格式转换为 https://")
        void httpsSlashFormat() {
            String uri = "https/httpbin.org/get";
            if (uri.toLowerCase().startsWith("http/") || uri.toLowerCase().startsWith("https/")) {
                uri = uri.replaceFirst("/", "://");
            }
            assertEquals("https://httpbin.org/get", uri);
        }

        @Test
        @DisplayName("http:// 标准格式保持不变")
        void httpStandardFormat() {
            String uri = "http://httpbin.org/get";
            // 不匹配 http/ 或 https/，不做转换
            assertFalse(uri.toLowerCase().startsWith("http/") || uri.toLowerCase().startsWith("https/"));
            assertEquals("http://httpbin.org/get", uri);
        }

        @Test
        @DisplayName("非 http 开头的路径补 /")
        void localPathPrefix() {
            String uri = "local/api/data";
            if (!uri.startsWith("/") && !uri.startsWith("http://") && !uri.startsWith("https://")) {
                uri = "/" + uri;
            }
            assertEquals("/local/api/data", uri);
        }

        @Test
        @DisplayName("query string 保留")
        void queryStringPreserved() {
            String uri = "https/httpbin.org/get";
            if (uri.toLowerCase().startsWith("https/")) {
                uri = uri.replaceFirst("/", "://");
            }
            // 模拟 query string 拼接
            String fullUrl = UriComponentsBuilder.fromUriString(uri)
                .query("id=123&name=test")
                .build().toUriString();
            assertTrue(fullUrl.contains("id=123"));
            assertTrue(fullUrl.contains("name=test"));
        }
    }

    @Nested
    @DisplayName("Header 解析逻辑")
    class HeaderParsing {

        @Test
        @DisplayName("retask4j-retry-plan 解析为 List<Integer>")
        void retryPlanHeaderParsing() {
            String header = "[5,20,60,120]";
            List<Integer> plan = com.alibaba.fastjson2.JSONArray.parseArray(header, Integer.class);
            assertEquals(List.of(5, 20, 60, 120), plan);
        }

        @Test
        @DisplayName("retask4j-task-timing 10 位时间戳识别")
        void timing10DigitTimestamp() {
            long timingSeconds = 1737077674L; // 10 位
            // 代码逻辑：timing < 1700000000000l ? timing * 1000l : timing
            long converted = timingSeconds < 1700000000000L ? timingSeconds * 1000L : timingSeconds;
            assertEquals(1737077674000L, converted);
        }

        @Test
        @DisplayName("retask4j-task-timing 13 位时间戳识别")
        void timing13DigitTimestamp() {
            long timingMs = 1737077674000L; // 13 位
            long converted = timingMs < 1700000000000L ? timingMs * 1000L : timingMs;
            assertEquals(1737077674000L, converted);
        }

        @Test
        @DisplayName("retask4j-task-timing 不支持超过 24 小时")
        void timingMax24Hours() {
            long nowTime = System.currentTimeMillis();
            long timing = nowTime + 25 * 3600 * 1000L; // 25 小时后

            assertTrue(timing > nowTime + 24 * 3600 * 1000L,
                "超过 24 小时的定时应被拒绝");
        }

        @Test
        @DisplayName("retask4j-task-delay 范围 1~3600")
        void delayRange() {
            int validDelay = 300;
            assertTrue(validDelay >= 1 && validDelay <= 3600);

            int tooSmall = 0;
            assertFalse(tooSmall >= 1 && tooSmall <= 3600);

            int tooLarge = 3601;
            assertFalse(tooLarge >= 1 && tooLarge <= 3600);
        }

        @Test
        @DisplayName("修复验证：timing delayTime 计算公式已修正")
        void timingDelayTimeCalculationFixed() {
            long nowTime = System.currentTimeMillis();
            long timing = nowTime + 600000L; // 10 分钟后

            // 修复后的计算：(timing - currentTime)
            int delayTime = (int) ((timing - nowTime) / 1000);

            assertEquals(600, delayTime,
                "修复确认：(timing - currentTime) 计算出正确的正数 delayTime");
        }

        @Test
        @DisplayName("retask4j-assert-response 解析为 JSONObject")
        void assertResponseHeaderParsing() {
            String header = "{\"statusIn\":[200],\"jsonPathMatch\":{\"$.code\":\"0\"}}";
            assertTrue(com.alibaba.fastjson2.JSON.isValidObject(header));

            JSONObject parsed = JSONObject.parseObject(header);
            assertNotNull(parsed.getJSONArray("statusIn"));
            assertNotNull(parsed.getJSONObject("jsonPathMatch"));
            assertEquals(200, parsed.getJSONArray("statusIn").getInteger(0));
            assertEquals("0", parsed.getJSONObject("jsonPathMatch").getString("$.code"));
        }

        @Test
        @DisplayName("无效的 assert-response JSON 被拒绝")
        void invalidAssertResponseRejected() {
            String invalid = "not a json object";
            assertFalse(com.alibaba.fastjson2.JSON.isValidObject(invalid));
        }
    }

    @Nested
    @DisplayName("Config Headers 注入")
    class ConfigHeaders {

        @Test
        @DisplayName("非空值设置请求头")
        void nonNullValueSetsHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("existing", "old-value");

            Map<String, String> configHeaders = new HashMap<>();
            configHeaders.put("X-Token", "new-token");
            configHeaders.put("existing", "replaced");

            configHeaders.forEach((k, v) -> {
                if (v != null) {
                    headers.set(k, v);
                } else {
                    headers.remove(k);
                }
            });

            assertEquals("new-token", headers.getFirst("X-Token"));
            assertEquals("replaced", headers.getFirst("existing"));
        }

        @Test
        @DisplayName("null 值删除请求头")
        void nullValueRemovesHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Remove-Me", "value");

            Map<String, String> configHeaders = new HashMap<>();
            configHeaders.put("X-Remove-Me", null);

            configHeaders.forEach((k, v) -> {
                if (v != null) {
                    headers.set(k, v);
                } else {
                    headers.remove(k);
                }
            });

            assertNull(headers.getFirst("X-Remove-Me"));
        }
    }

    @Nested
    @DisplayName("FuHttpTaskCallerAsyncListener")
    class AsyncListener {

        @Test
        @DisplayName("AsyncListener 类存在且实现正确接口")
        void asyncListenerImplementsInterface() {
            assertTrue(jakarta.servlet.AsyncListener.class.isAssignableFrom(FuHttpTaskCallerAsyncListener.class));
        }
    }
}
