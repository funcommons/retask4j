package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuHttpTaskBaseControllerTest {

    private final FuHttpTaskBaseController controller = new FuHttpTaskBaseController();

    private StringWriter createMockResponse(HttpServletResponse mockResponse) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(mockResponse.getWriter()).thenReturn(pw);
        when(mockResponse.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) { sw.write(b); }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
        });
        return sw;
    }

    @Nested
    @DisplayName("writeApiResponse")
    class WriteApiResponse {

        @Test
        @DisplayName("null 输入返回 500 错误")
        void nullInputReturnsError() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            controller.writeApiResponse(mockResponse, null);

            verify(mockResponse).setStatus(500);
            assertTrue(sw.toString().contains("500"));
        }

        @Test
        @DisplayName("Exception 输入返回 500 错误（不含异常详情）")
        void exceptionInputReturnsError() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            controller.writeApiResponse(mockResponse, new RuntimeException("test error"));

            verify(mockResponse).setStatus(500);
            assertFalse(sw.toString().contains("test error"));
        }

        @Test
        @DisplayName("正常数据调用 writeApiSuccess")
        void normalDataCallsSuccess() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            controller.writeApiResponse(mockResponse, "hello");

            String json = sw.toString();
            assertTrue(json.contains("success"));
            assertTrue(json.contains("hello"));
        }
    }

    @Nested
    @DisplayName("writeApiError")
    class WriteApiError {

        @Test
        @DisplayName("设置错误状态码和消息")
        void setErrorStatus() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            controller.writeApiError(mockResponse, 400, "Bad Request");

            verify(mockResponse).setStatus(400);
            String json = sw.toString();
            assertTrue(json.contains("400"));
            assertTrue(json.contains("Bad Request"));
        }
    }

    @Nested
    @DisplayName("writeApiSuccess")
    class WriteApiSuccess {

        @Test
        @DisplayName("Map 含 status+msg 直接输出不包装")
        void mapWithStatusAndMsgNoWrap() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            JSONObject data = new JSONObject();
            data.put("status", 0);
            data.put("msg", "ok");
            data.put("extra", "value");

            controller.writeApiSuccess(mockResponse, data);

            String json = sw.toString();
            assertTrue(json.contains("ok"));
            assertTrue(json.contains("value"));
            // 不应有外层包装（不应有 "data" 键）
            JSONObject parsed = JSONObject.parseObject(json);
            assertFalse(parsed.containsKey("data"), "含 status+msg 的 Map 不应被包装");
        }

        @Test
        @DisplayName("Map 含 status+message 直接输出不包装")
        void mapWithStatusAndMessageNoWrap() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            JSONObject data = new JSONObject();
            data.put("status", 1);
            data.put("message", "info");

            controller.writeApiSuccess(mockResponse, data);

            String json = sw.toString();
            JSONObject parsed = JSONObject.parseObject(json);
            assertFalse(parsed.containsKey("data"), "含 status+message 的 Map 不应被包装");
        }

        @Test
        @DisplayName("Map 无 status+msg 被包装为 {status:0, msg:success, data:...}")
        void mapWithoutStatusWrapped() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            JSONObject data = new JSONObject();
            data.put("key", "val");

            controller.writeApiSuccess(mockResponse, data);

            String json = sw.toString();
            JSONObject parsed = JSONObject.parseObject(json);
            assertEquals(0, parsed.getIntValue("status"));
            assertEquals("success", parsed.getString("msg"));
            assertNotNull(parsed.get("data"));
        }

        @Test
        @DisplayName("非 Map 数据被包装")
        void nonMapDataWrapped() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            controller.writeApiSuccess(mockResponse, "plain text");

            String json = sw.toString();
            JSONObject parsed = JSONObject.parseObject(json);
            assertEquals(0, parsed.getIntValue("status"));
            assertEquals("success", parsed.getString("msg"));
            assertEquals("plain text", parsed.getString("data"));
        }
    }

    @Nested
    @DisplayName("writeResponse")
    class WriteResponse {

        @Test
        @DisplayName("设置 Content-Type 为 application/json; charset=utf-8")
        void setsContentType() throws Exception {
            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            StringWriter sw = createMockResponse(mockResponse);

            JSONObject data = new JSONObject();
            data.put("status", 0);
            data.put("msg", "test");

            controller.writeResponse(mockResponse, data);

            verify(mockResponse).setContentType("application/json; charset=utf-8");
        }
    }
}
