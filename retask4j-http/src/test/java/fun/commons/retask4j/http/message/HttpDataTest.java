package fun.commons.retask4j.http.message;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class HttpDataTest {

    @Nested
    @DisplayName("HttpData body 处理")
    class BodyHandling {

        @Test
        @DisplayName("JSON body 读写")
        void jsonBody() {
            HttpData data = new HttpData();
            data.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            JSONObject json = new JSONObject().fluentPut("key", "value");
            data.setBody(json);

            Object parsed = data.getBody();
            assertInstanceOf(JSONObject.class, parsed);
            assertEquals("value", ((JSONObject) parsed).getString("key"));
        }

        @Test
        @DisplayName("text body 读写")
        void textBody() {
            HttpData data = new HttpData();
            data.getHeaders().setContentType(MediaType.TEXT_PLAIN);

            data.setBody("hello world");
            assertEquals("hello world", data.bodyText());
        }

        @Test
        @DisplayName("base64 body 回退")
        void base64BodyFallback() {
            HttpData data = new HttpData();
            // 无 Content-Type 时，getBody 返回 base64
            byte[] rawBytes = "test data".getBytes(StandardCharsets.UTF_8);
            data.setBody(rawBytes);

            Object result = data.getBody();
            assertInstanceOf(String.class, result);
            assertTrue(((String) result).startsWith("base64:retask4j:"));
        }

        @Test
        @DisplayName("base64 前缀字符串解码")
        void base64StringDecode() {
            HttpData data = new HttpData();
            byte[] original = "hello".getBytes(StandardCharsets.UTF_8);
            String base64Str = "base64:retask4j:" + Base64.getEncoder().encodeToString(original);

            data.setBody(base64Str);
            assertArrayEquals(original, data.bodyBytes());
        }

        @Test
        @DisplayName("null body 处理")
        void nullBody() {
            HttpData data = new HttpData();
            data.setBody(null);
            assertEquals(0, data.bodyBytes().length);
        }

        @Test
        @DisplayName("空 body")
        void emptyBody() {
            HttpData data = new HttpData();
            assertEquals(0, data.bodyBytes().length);
            assertEquals("", data.bodyText());
        }

        @Test
        @DisplayName("bodyText 带 charset")
        void bodyTextWithCharset() {
            HttpData data = new HttpData();
            data.getHeaders().setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            data.setBody("{\"name\":\"测试\"}".getBytes(StandardCharsets.UTF_8));

            String text = data.bodyText();
            assertTrue(text.contains("测试"));
        }
    }

    @Nested
    @DisplayName("HttpRequestData")
    class RequestData {

        @Test
        @DisplayName("构造与默认值")
        void defaults() {
            HttpRequestData data = new HttpRequestData();

            assertNull(data.getUrl());
            assertEquals("GET", data.getMethod());
            assertNotNull(data.getHeaders());
            assertEquals(0, data.bodyBytes().length);
        }

        @Test
        @DisplayName("clone 独立性")
        void cloneIndependence() {
            HttpRequestData original = new HttpRequestData();
            original.setUrl("http://example.com/api");
            original.setMethod("POST");
            original.setBody("data".getBytes(StandardCharsets.UTF_8));

            HttpRequestData cloned = original.clone();

            assertEquals(original.getUrl(), cloned.getUrl());
            assertEquals(original.getMethod(), cloned.getMethod());
            assertArrayEquals(original.bodyBytes(), cloned.bodyBytes());

            // 修改克隆不影响原始
            cloned.setUrl("http://other.com");
            assertEquals("http://example.com/api", original.getUrl());
        }
    }

    @Nested
    @DisplayName("HttpResponseData")
    class ResponseData {

        @Test
        @DisplayName("error 工厂方法")
        void errorFactory() {
            HttpResponseData error = HttpResponseData.error(403, "Forbidden", "http://example.com");

            assertEquals(403, error.getStatus());
            assertEquals("Forbidden", error.getReason());
            assertNotNull(error.bodyBytes());
            assertTrue(error.bodyBytes().length > 0);
        }

        @Test
        @DisplayName("json 工厂方法")
        void jsonFactory() {
            JSONObject body = new JSONObject().fluentPut("status", 0).fluentPut("msg", "ok");
            HttpResponseData response = HttpResponseData.json(body);

            assertEquals(200, response.getStatus());
            assertNotNull(response.bodyBytes());
            assertTrue(response.bodyBytes().length > 0);
        }
    }
}
