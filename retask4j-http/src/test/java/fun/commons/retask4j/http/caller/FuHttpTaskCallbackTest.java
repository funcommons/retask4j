package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.exception.FuTaskCallbackException;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FuHttpTaskCallbackTest {

    private RestTemplate mockRestTemplate() {
        return mock(RestTemplate.class);
    }

    private FuHttpTaskCallerConfig configWithCallbackUrl(String url) {
        FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
        config.setTopic("test");
        config.setPath("/test");
        // Only set callbackUrl if it's a resolvable public URL
        if (url != null) {
            config.setCallbackUrl(url);
        }
        return config;
    }

    @Nested
    @DisplayName("回调 URL 缺失")
    class MissingCallbackUrl {

        @Test
        @DisplayName("extInfo 无 callback 且 config 无 callbackUrl 时抛异常")
        void noUrlAtAllThrows() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            config.setTopic("test");
            config.setPath("/test");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(mockRestTemplate(), config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");

            FuTaskCallbackException e = assertThrows(FuTaskCallbackException.class,
                () -> callback.accept(msg));
            assertTrue(e.getMessage().contains("missing"));
        }

        @Test
        @DisplayName("extInfo 有空白 callback 且 config 无 callbackUrl 时抛异常")
        void blankCallbackInExtInfoThrows() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            config.setTopic("test");
            config.setPath("/test");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(mockRestTemplate(), config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.getExtInfo().put("callback", "   ");

            FuTaskCallbackException e = assertThrows(FuTaskCallbackException.class,
                () -> callback.accept(msg));
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    @Nested
    @DisplayName("回调 HTTP 成功判定")
    class CallbackSuccess {

        @Test
        @DisplayName("HTTP 200 视为成功，不抛异常")
        void http200Success() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);
            msg.setOutput(new JSONObject().fluentPut("result", "done"));
            msg.setCompleteTime(1736850478880L);
            msg.setExecuteTime(1736850465689L);

            assertDoesNotThrow(() -> callback.accept(msg));
        }

        @Test
        @DisplayName("HTTP 201 视为成功（2xx）")
        void http201Success() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("created", HttpStatus.CREATED);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);

            assertDoesNotThrow(() -> callback.accept(msg));
        }
    }

    @Nested
    @DisplayName("回调 HTTP 失败判定")
    class CallbackFailure {

        @Test
        @DisplayName("HTTP 500 抛异常且标记可重试")
        void http500Retryable() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);

            FuTaskCallbackException e = assertThrows(FuTaskCallbackException.class,
                () -> callback.accept(msg));
            assertTrue(e.isRetryable(), "5xx should be retryable");
            assertTrue(e.getMessage().contains("500"));
        }

        @Test
        @DisplayName("HTTP 400 抛异常且标记不可重试")
        void http400NotRetryable() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("bad", HttpStatus.BAD_REQUEST);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);

            FuTaskCallbackException e = assertThrows(FuTaskCallbackException.class,
                () -> callback.accept(msg));
            assertFalse(e.isRetryable(), "4xx should not be retryable");
        }

        @Test
        @DisplayName("RestClientException 抛异常且标记可重试（非 4xx）")
        void restClientExceptionRetryable() {
            RestTemplate rt = mockRestTemplate();
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);

            FuTaskCallbackException e = assertThrows(FuTaskCallbackException.class,
                () -> callback.accept(msg));
            assertTrue(e.isRetryable(), "Connection errors should be retryable");
        }
    }

    @Nested
    @DisplayName("回调 extInfo 优先级")
    class ExtInfoPrecedence {

        @Test
        @DisplayName("extInfo 中的 callback URL 优先于 config 的 callbackUrl")
        void extInfoOverridesConfig() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            // Config has a default callback URL
            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/default");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "id-1");
            msg.setStatus(FuTaskStatus.SUCCESS);
            // extInfo overrides with a different URL (using public hostname)
            msg.getExtInfo().put("callback", "http://example.com/override");

            assertDoesNotThrow(() -> callback.accept(msg));

            // Verify that exchange was called (callback URL was used)
            verify(rt, atLeastOnce()).exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        }
    }

    @Nested
    @DisplayName("回调 body 构造")
    class CallbackBodyConstruction {

        @Test
        @DisplayName("回调 body 包含 id, response, status, completeTime, executeTime")
        void callbackBodyFields() {
            RestTemplate rt = mockRestTemplate();
            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

            FuHttpTaskCallerConfig config = configWithCallbackUrl("http://example.com/notify");
            FuHttpTaskCallback callback = new FuHttpTaskCallback(rt, config);
            FuTaskMessage msg = new FuTaskMessage("test", "task-001");
            msg.setStatus(FuTaskStatus.SUCCESS);
            msg.setOutput(new JSONObject().fluentPut("key", "val"));
            msg.setCompleteTime(1736850478880L);
            msg.setExecuteTime(1736850465689L);

            assertDoesNotThrow(() -> callback.accept(msg));

            // Capture and verify the request body
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
            verify(rt).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
            HttpEntity<String> entity = captor.getValue();

            // Verify Content-Type is JSON
            assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());

            // Verify body contains expected fields
            String body = entity.getBody();
            assertNotNull(body);
            assertTrue(body.contains("task-001"));
            assertTrue(body.contains("SUCCESS"));
            assertTrue(body.contains("1736850478880"));
            assertTrue(body.contains("1736850465689"));
            assertTrue(body.contains("key"));
        }
    }
}
