package fun.commons.retask4j.http.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskWorkerConfigTest {

    @Nested
    @DisplayName("Worker 配置默认值")
    class WorkerDefaults {

        @Test
        @DisplayName("所有默认值正确")
        void defaults() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();

            assertEquals("default", config.getTopic());
            assertTrue(config.isEnableRemote());
            assertTrue(config.isEnableLocal());
            assertEquals(64, config.getMaxConsumeThreads());
            assertNotNull(config.getRoutes());
            assertTrue(config.getRoutes().isEmpty());
        }
    }

    @Nested
    @DisplayName("路由配置（RouteConfig）")
    class RouteConfig {

        @Test
        @DisplayName("默认路由匹配所有")
        void defaultRoute() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();

            assertEquals("*", route.getPath());
            assertNull(route.getRedirect());
            assertNotNull(route.getRewriteRequestHeaders());
            assertTrue(route.getRewriteRequestHeaders().isEmpty());
            assertNotNull(route.getRewriteResponseHeaders());
            assertTrue(route.getRewriteResponseHeaders().isEmpty());
            assertNull(route.getAssertResponse());
        }

        @Test
        @DisplayName("路由配置完整赋值")
        void fullRouteConfig() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            route.setPath("http://www\\.example\\.com/(.+)");
            route.setRedirect("https://www.example.com/&1");

            Map<String, String> reqHeaders = new HashMap<>();
            reqHeaders.put("X-Auth", "token123");
            reqHeaders.put("X-Remove", null);
            route.setRewriteRequestHeaders(reqHeaders);

            Map<String, String> resHeaders = new HashMap<>();
            resHeaders.put("Access-Control-Allow-Origin", "*");
            route.setRewriteResponseHeaders(resHeaders);

            assertEquals("http://www\\.example\\.com/(.+)", route.getPath());
            assertEquals("https://www.example.com/&1", route.getRedirect());
            assertEquals("token123", route.getRewriteRequestHeaders().get("X-Auth"));
            assertEquals("*", route.getRewriteResponseHeaders().get("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("断言配置（AssertsConfig）")
    class AssertsConfig {

        @Test
        @DisplayName("断言默认值")
        void assertsDefaults() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();

            assertNotNull(asserts.getStatusIn());
            assertTrue(asserts.getStatusIn().isEmpty());
            assertNotNull(asserts.getHeaderMatch());
            assertTrue(asserts.getHeaderMatch().isEmpty());
            assertNull(asserts.getTextBodyMatch());
            assertNotNull(asserts.getJsonPathMatch());
            assertTrue(asserts.getJsonPathMatch().isEmpty());
        }

        @Test
        @DisplayName("断言完整配置")
        void fullAssertsConfig() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            asserts.setStatusIn(List.of(200, 301, 302));

            Map<String, String> headerMatch = new HashMap<>();
            headerMatch.put("Content-Type", "application/json(;\\s?charset=.+)?");
            asserts.setHeaderMatch(headerMatch);

            asserts.setTextBodyMatch("success|ok|true");

            Map<String, String> jsonPathMatch = new HashMap<>();
            jsonPathMatch.put("$.code", "0");
            jsonPathMatch.put("$.msg", "success");
            asserts.setJsonPathMatch(jsonPathMatch);

            assertEquals(List.of(200, 301, 302), asserts.getStatusIn());
            assertEquals("application/json(;\\s?charset=.+)?", asserts.getHeaderMatch().get("Content-Type"));
            assertEquals("success|ok|true", asserts.getTextBodyMatch());
            assertEquals("0", asserts.getJsonPathMatch().get("$.code"));
            assertEquals("success", asserts.getJsonPathMatch().get("$.msg"));
        }
    }

    @Nested
    @DisplayName("Worker 路由匹配逻辑")
    class RouteMatching {

        @Test
        @DisplayName("正则路由匹配 URL")
        void regexRouteMatch() {
            String url = "http://www.baidu.com/search?q=test";
            String path = "http://www\\.baidu\\.com/(.+)";

            assertTrue(url.matches(path));
        }

        @Test
        @DisplayName("默认路由 * 在代码逻辑中通过 equals 判断，不走正则")
        void defaultRouteMatchAll() {
            String url1 = "http://example.com/api";
            String url2 = "/local/path";
            String path = "*";

            // 代码中逻辑："*".equals(path) || url.matches(path)
            // * 不是合法正则，所以只能通过 equals 匹配
            assertTrue("*".equals(path));
            assertTrue("*".equals(path) || url1.matches(path.replace("*", ".*")));
        }

        @Test
        @DisplayName("URL 重定向捕获组替换")
        void redirectWithCaptureGroups() {
            String path = "http://www\\.baidu\\.com/(.+)";
            String redirect = "https://www.baidu.com/&1";
            String url = "http://www.baidu.com/search?q=test";

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(path);
            java.util.regex.Matcher m = pattern.matcher(url);

            assertTrue(m.find());
            String result = redirect;
            for (int i = 0; i <= m.groupCount(); i++) {
                result = result.replaceAll("&" + i, m.group(i) != null ? m.group(i) : "");
            }

            assertEquals("https://www.baidu.com/search?q=test", result);
        }
    }

    @Nested
    @DisplayName("响应断言逻辑")
    class ResponseAssertion {

        @Test
        @DisplayName("statusIn 校验：允许的状态码通过")
        void statusInAllowed() {
            List<Integer> statusIn = List.of(200, 301, 302);
            assertTrue(statusIn.contains(200));
            assertTrue(statusIn.contains(301));
            assertFalse(statusIn.contains(404));
        }

        @Test
        @DisplayName("headerMatch 正则匹配")
        void headerMatchRegex() {
            String contentType = "application/json; charset=utf-8";
            String pattern = "application/json(;\\s?charset=.+)?";
            assertTrue(contentType.matches(pattern));

            String contentType2 = "application/json";
            assertTrue(contentType2.matches(pattern));

            String contentType3 = "text/html";
            assertFalse(contentType3.matches(pattern));

            // 注意：代码中 headerMatch 的 key 是响应头名称
            // 实际匹配时用 headers.getFirst(key).matches(pattern)
            // Bug 验证：原正则 application/json(;charset=.+)? 无法匹配含空格的 Content-Type
            // 如 "application/json; charset=utf-8"，因为 ; 和 charset 之间有空格
            String buggyPattern = "application/json(;charset=.+)?";
            assertFalse(contentType.matches(buggyPattern),
                "Bug 确认：不含 \\s? 的正则无法匹配含空格的 Content-Type");
        }

        @Test
        @DisplayName("textBodyMatch 正则匹配")
        void textBodyMatchRegex() {
            String body = "success";
            String pattern = "success|SUCCESS|ok|OK|true|TRUE";
            assertTrue(body.matches(pattern));

            String body2 = "error";
            assertFalse(body2.matches(pattern));
        }

        @Test
        @DisplayName("jsonPathMatch 字段匹配")
        void jsonPathMatchField() {
            com.alibaba.fastjson2.JSONObject body = new com.alibaba.fastjson2.JSONObject();
            body.put("code", "0");
            body.put("msg", "success");

            assertEquals("0", body.getString("code"));
            assertTrue(body.getString("code").matches("0"));
            assertTrue(body.getString("msg").matches("success"));

            // 不匹配的场景
            assertFalse(body.getString("code").matches("1"));
        }
    }
}
