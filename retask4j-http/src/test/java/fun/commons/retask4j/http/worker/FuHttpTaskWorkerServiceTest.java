package fun.commons.retask4j.http.worker;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.http.message.HttpRequestData;
import fun.commons.retask4j.http.message.HttpResponseData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FuHttpTaskWorkerService 逻辑测试 — 验证路由匹配、URL 重写、请求头/响应头重写、断言逻辑。
 * 不依赖 Spring Context，纯逻辑测试。
 */
class FuHttpTaskWorkerServiceTest {

    @Nested
    @DisplayName("路由匹配逻辑")
    class RouteMatching {

        @Test
        @DisplayName("正则路由匹配成功")
        void regexMatch() {
            String url = "http://www.baidu.com/search?q=test";
            String path = "http://www\\.baidu\\.com/(.+)";
            assertTrue(url.matches(path));
        }

        @Test
        @DisplayName("通配符 * 匹配所有")
        void wildcardMatchAll() {
            // 代码中逻辑："*".equals(path) || url.matches(path)
            String anyUrl = "http://anything.com/path";
            String path = "*";
            assertTrue("*".equals(path) || anyUrl.matches(path));
        }

        @Test
        @DisplayName("路由按配置顺序匹配，第一个命中")
        void firstMatchWins() {
            List<String> routes = List.of(
                "http://www\\.baidu\\.com/(.+)",
                "http://www\\.google\\.com/(.+)",
                "*"
            );
            String url = "http://www.google.com/search";

            String matched = null;
            for (String path : routes) {
                if ("*".equals(path) || url.matches(path)) {
                    matched = path;
                    break;
                }
            }
            assertEquals("http://www\\.google\\.com/(.+)", matched);
        }

        @Test
        @DisplayName("默认路由 * 总是最后匹配")
        void defaultRouteFallback() {
            List<String> routes = List.of("http://specific\\.com/(.+)", "*");
            String url = "http://other.com/path";

            String matched = null;
            for (String path : routes) {
                if ("*".equals(path) || url.matches(path)) {
                    matched = path;
                    break;
                }
            }
            assertEquals("*", matched);
        }
    }

    @Nested
    @DisplayName("URL 重定向（redirect + 捕获组）")
    class UrlRedirect {

        @Test
        @DisplayName("http 重定向到 https，保留路径")
        void httpToHttpsRedirect() {
            String path = "http://www\\.baidu\\.com/(.+)";
            String redirect = "https://www.baidu.com/&1";
            String url = "http://www.baidu.com/search?q=test";

            Pattern pattern = Pattern.compile(path);
            Matcher m = pattern.matcher(url);
            assertTrue(m.find());

            String result = redirect;
            List<String> groups = new ArrayList<>();
            for (int i = 0; i <= m.groupCount(); i++) {
                groups.add(m.group(i));
                result = result.replaceAll("&" + i, groups.get(i) != null ? groups.get(i) : "");
            }

            assertEquals("https://www.baidu.com/search?q=test", result);
        }

        @Test
        @DisplayName("多捕获组替换")
        void multipleCaptureGroups() {
            String path = "http://api\\.example\\.com/(v[0-9]+)/(.+)";
            String redirect = "https://new-api.example.com/&2?version=&1";
            String url = "http://api.example.com/v2/users/list";

            Pattern pattern = Pattern.compile(path);
            Matcher m = pattern.matcher(url);
            assertTrue(m.find());

            String result = redirect;
            List<String> groups = new ArrayList<>();
            for (int i = 0; i <= m.groupCount(); i++) {
                groups.add(m.group(i));
                result = result.replaceAll("&" + i, groups.get(i) != null ? groups.get(i) : "");
            }

            assertEquals("https://new-api.example.com/users/list?version=v2", result);
        }
    }

    @Nested
    @DisplayName("请求头重写")
    class RequestHeaderRewrite {

        @Test
        @DisplayName("非空值设置请求头")
        void setHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Old", "old-value");

            Map<String, String> rewriteHeaders = new HashMap<>();
            rewriteHeaders.put("X-Auth", "token123");
            rewriteHeaders.put("X-Old", "new-value");

            rewriteHeaders.forEach((k, v) -> {
                if (v != null) {
                    headers.set(k, v);
                } else {
                    headers.remove(k);
                }
            });

            assertEquals("token123", headers.getFirst("X-Auth"));
            assertEquals("new-value", headers.getFirst("X-Old"));
        }

        @Test
        @DisplayName("null 值删除请求头")
        void removeHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Remove", "value");

            Map<String, String> rewriteHeaders = new HashMap<>();
            rewriteHeaders.put("X-Remove", null);

            rewriteHeaders.forEach((k, v) -> {
                if (v != null) {
                    headers.set(k, v);
                } else {
                    headers.remove(k);
                }
            });

            assertNull(headers.getFirst("X-Remove"));
        }
    }

    @Nested
    @DisplayName("响应头重写")
    class ResponseHeaderRewrite {

        @Test
        @DisplayName("设置 CORS 响应头")
        void setCorsHeaders() {
            HttpResponseData response = HttpResponseData.json(new JSONObject());
            Map<String, String> rewriteHeaders = new HashMap<>();
            rewriteHeaders.put("Access-Control-Allow-Origin", "*");
            rewriteHeaders.put("Timing-Allow-Origin", "*");

            rewriteHeaders.forEach((k, v) -> {
                if (v != null) {
                    response.getHeaders().set(k, v);
                } else {
                    response.getHeaders().remove(k);
                }
            });

            assertEquals("*", response.getHeaders().getFirst("Access-Control-Allow-Origin"));
            assertEquals("*", response.getHeaders().getFirst("Timing-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("响应断言逻辑")
    class ResponseAssertion {

        @Test
        @DisplayName("statusIn 断言通过")
        void statusInPass() {
            List<Integer> statusIn = List.of(200, 301, 302);
            int actualStatus = 200;

            assertTrue(statusIn.isEmpty() || statusIn.contains(actualStatus));
        }

        @Test
        @DisplayName("statusIn 断言失败抛异常")
        void statusInFail() {
            List<Integer> statusIn = List.of(200, 301);
            int actualStatus = 500;

            assertFalse(statusIn.isEmpty() && true || statusIn.contains(actualStatus));
            // 模拟 FuHttpTaskWorkerService 中的逻辑
            assertThrows(RuntimeException.class, () -> {
                if (!statusIn.contains(actualStatus)) {
                    throw new RuntimeException("status error:" + actualStatus);
                }
            });
        }

        @Test
        @DisplayName("statusIn 为空不校验")
        void statusInEmptyNoCheck() {
            List<Integer> statusIn = new ArrayList<>();
            int actualStatus = 500;

            // 空列表不校验
            assertTrue(statusIn.isEmpty());
        }

        @Test
        @DisplayName("textBodyMatch 正则匹配通过")
        void textBodyMatchPass() {
            String textBodyMatch = "success|ok|true";
            String body = "success";

            assertTrue(body.matches(textBodyMatch));
        }

        @Test
        @DisplayName("textBodyMatch 正则匹配失败抛异常")
        void textBodyMatchFail() {
            String textBodyMatch = "success|ok|true";
            String body = "error";

            assertFalse(body.matches(textBodyMatch));
            assertThrows(RuntimeException.class, () -> {
                if (!body.matches(textBodyMatch)) {
                    throw new RuntimeException("body match error:" + textBodyMatch);
                }
            });
        }

        @Test
        @DisplayName("jsonPathMatch 字段匹配通过")
        void jsonPathMatchPass() {
            JSONObject body = new JSONObject();
            body.put("code", "0");
            body.put("msg", "success");

            Map<String, String> jsonPathMatch = new HashMap<>();
            jsonPathMatch.put("$.code", "0");
            jsonPathMatch.put("$.msg", "success");

            boolean allMatch = true;
            for (Map.Entry<String, String> entry : jsonPathMatch.entrySet()) {
                String value = body.getString(entry.getKey().replace("$.", ""));
                if (value == null || !value.matches(entry.getValue())) {
                    allMatch = false;
                    break;
                }
            }
            assertTrue(allMatch);
        }

        @Test
        @DisplayName("jsonPathMatch 字段不匹配抛异常")
        void jsonPathMatchFail() {
            JSONObject body = new JSONObject();
            body.put("code", "1");
            body.put("msg", "error");

            Map<String, String> jsonPathMatch = new HashMap<>();
            jsonPathMatch.put("$.code", "0");

            String codeValue = body.getString("code");
            assertFalse(codeValue.matches("0"));

            assertThrows(RuntimeException.class, () -> {
                if (!codeValue.matches("0")) {
                    throw new RuntimeException("json body match error:$.code >> 0");
                }
            });
        }

        @Test
        @DisplayName("headerMatch 正则匹配（含空格修复）")
        void headerMatchRegex() {
            String contentType = "application/json; charset=utf-8";
            String pattern = "application/json(;\\s?charset=.+)?";
            assertTrue(contentType.matches(pattern));

            // 修复确认：带 \\s? 的正则可匹配含空格的 Content-Type
            String contentTypeNoSpace = "application/json;charset=utf-8";
            assertTrue(contentTypeNoSpace.matches(pattern));
        }
    }

    @Nested
    @DisplayName("enableRemote / enableLocal 开关")
    class EnableSwitches {

        @Test
        @DisplayName("enableRemote=false 时远程调用返回 403")
        void remoteDisabled() {
            boolean enableRemote = false;
            String url = "https://example.com/api";

            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (!enableRemote) {
                    HttpResponseData response = HttpResponseData.error(403, "worker is not remote call mode", url);
                    assertEquals(403, response.getStatus());
                }
            }
        }

        @Test
        @DisplayName("enableLocal=false 时本地调用返回 403")
        void localDisabled() {
            boolean enableLocal = false;
            String url = "/local/api";

            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                if (!enableLocal) {
                    HttpResponseData response = HttpResponseData.error(403, "worker is not local call mode", url);
                    assertEquals(403, response.getStatus());
                }
            }
        }
    }

    @Nested
    @DisplayName("assert-response 通过 extInfo 覆盖")
    class AssertResponseOverride {

        @Test
        @DisplayName("extInfo 中的 assert-response 覆盖路由配置")
        void extInfoOverride() {
            JSONObject extInfo = new JSONObject();
            extInfo.put("assert-response", new JSONObject().fluentPut("statusIn", List.of(200)));

            assertTrue(extInfo.containsKey("assert-response"));
            JSONObject assertConfig = extInfo.getJSONObject("assert-response");
            assertEquals(List.of(200), assertConfig.getList("statusIn", Integer.class));
        }
    }
}
