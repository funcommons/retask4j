package fun.commons.retask4j.http.caller;

import fun.commons.retask4j.core.message.FuTaskMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskCallerConfigTest {

    @Nested
    @DisplayName("默认值验证")
    class Defaults {

        @Test
        @DisplayName("所有默认值与代码一致")
        void allDefaults() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();

            assertNull(config.getTopic());
            assertNull(config.getPath());
            assertEquals(FuTaskMode.NORMAL, config.getMode());
            assertEquals(Arrays.asList(60, 120, 300, 600, 3600), config.getRetryPlan());
            assertEquals(86400, config.getExecuteExpire());
            assertEquals(3600, config.getResultExpire()); // 文档错误：文档写默认 0，代码实际 3600
            assertEquals(120, config.getRequestTimeout());
            assertEquals(64, config.getCallbackMaxThreads());
            assertEquals(3, config.getCallbackRetryTimes());
            assertEquals(60, config.getCallbackRetryInterval());
            assertNotNull(config.getHeaders());
            assertTrue(config.getHeaders().isEmpty());
            assertNull(config.getCallbackUrl());
            assertTrue(config.isBatch());
        }

        @Test
        @DisplayName("Bug 验证：resultExpire 默认值文档写 0，代码实际 3600")
        void resultExpireDefaultDocBug() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            // 代码默认值
            assertEquals(3600, config.getResultExpire(),
                "Bug 确认：FuHttpTaskCallerConfig.resultExpire 默认值为 3600，文档写 0");
        }
    }

    @Nested
    @DisplayName("字段赋值")
    class FieldAssignment {

        @Test
        @DisplayName("所有字段可正确赋值")
        void setAllFields() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            config.setTopic("proxy");
            config.setPath("/proxy/push");
            config.setMode(FuTaskMode.CALLBACK);
            config.setRetryPlan(List.of(5, 20, 60));
            config.setExecuteExpire(7200);
            config.setResultExpire(1800);
            config.setRequestTimeout(60);
            config.setCallbackMaxThreads(32);
            config.setCallbackRetryTimes(5);
            config.setCallbackRetryInterval(30);
            config.setCallbackUrl("http://example.com/notify");
            config.setBatch(false);

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Token", "abc123");
            config.setHeaders(headers);

            assertEquals("proxy", config.getTopic());
            assertEquals("/proxy/push", config.getPath());
            assertEquals(FuTaskMode.CALLBACK, config.getMode());
            assertEquals(List.of(5, 20, 60), config.getRetryPlan());
            assertEquals(7200, config.getExecuteExpire());
            assertEquals(1800, config.getResultExpire());
            assertEquals(60, config.getRequestTimeout());
            assertEquals(32, config.getCallbackMaxThreads());
            assertEquals(5, config.getCallbackRetryTimes());
            assertEquals(30, config.getCallbackRetryInterval());
            assertEquals("http://example.com/notify", config.getCallbackUrl());
            assertFalse(config.isBatch());
            assertEquals("abc123", config.getHeaders().get("X-Token"));
        }
    }
}
