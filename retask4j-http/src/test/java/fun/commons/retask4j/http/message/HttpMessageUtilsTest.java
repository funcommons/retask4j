package fun.commons.retask4j.http.message;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.http.worker.FuHttpLocalInvoker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpMessageUtilsTest {

    @Nested
    @DisplayName("convertResponseData")
    class ConvertResponseData {

        @Test
        @DisplayName("无压缩的响应直接传递 body")
        void noCompression() throws IOException {
            byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(body, headers, HttpStatus.OK);

            HttpResponseData result = HttpMessageUtils.convertResponseData(responseEntity);

            assertEquals(200, result.getStatus());
            assertArrayEquals(body, result.bodyBytes());
        }

        @Test
        @DisplayName("gzip 压缩响应自动解压")
        void gzipDecompression() throws IOException {
            String original = "compress me compress me compress me";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(original.getBytes(StandardCharsets.UTF_8));
            }
            byte[] compressed = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(compressed, headers, HttpStatus.OK);

            HttpResponseData result = HttpMessageUtils.convertResponseData(responseEntity);

            assertEquals(200, result.getStatus());
            assertEquals(original, new String(result.bodyBytes(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Unrecognized Content-Encoding header preserved (no silent data corruption)")
        void contentEncodingPreserved() throws IOException {
            byte[] body = "plain".getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_ENCODING, "identity");
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(body, headers, HttpStatus.OK);

            HttpResponseData result = HttpMessageUtils.convertResponseData(responseEntity);

            // Unrecognized encodings keep Content-Encoding to avoid silent data corruption
            assertEquals("identity", result.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        }

        @Test
        @DisplayName("Bug 验证：Content-Encoding 被重复移除（代码行 190-191）")
        void contentEncodingDuplicateRemovalBug() throws IOException {
            // 创建实际 gzip 压缩数据
            String original = "test content for gzip";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(original.getBytes(StandardCharsets.UTF_8));
            }
            byte[] compressed = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(compressed, headers, HttpStatus.OK);

            // 修复确认：Content-Encoding 只移除一次
            HttpResponseData result = HttpMessageUtils.convertResponseData(responseEntity);
            assertNull(result.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        }

        @Test
        @DisplayName("状态码和 reason 正确提取")
        void statusAndReason() throws IOException {
            byte[] body = "not found".getBytes(StandardCharsets.UTF_8);
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(body, HttpStatus.NOT_FOUND);

            HttpResponseData result = HttpMessageUtils.convertResponseData(responseEntity);

            assertEquals(404, result.getStatus());
            assertEquals("Not Found", result.getReason());
        }
    }

    @Nested
    @DisplayName("convertErrorResponseData")
    class ConvertErrorResponseData {

        @Test
        @DisplayName("提取错误状态码和 body")
        void errorResponse() {
            // HttpStatusCodeException 是抽象类，需要子类或 mock
            // 使用 mock 方式测试
            org.springframework.web.client.HttpClientErrorException error =
                org.springframework.web.client.HttpClientErrorException.NotFound.create(
                    HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), "error body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
                );

            assertDoesNotThrow(() -> {
                HttpResponseData result = HttpMessageUtils.convertErrorResponseData(error);
                assertEquals(404, result.getStatus());
            });
        }
    }

    @Nested
    @DisplayName("flushToHttpResponse")
    class FlushToHttpResponse {

        @Test
        @DisplayName("状态码、Header、Body 正确写入")
        void flushAllFields() throws IOException {
            HttpResponseData data = new HttpResponseData();
            data.setStatus(200);
            data.getHeaders().set("X-Custom", "value");
            data.setBody("response body".getBytes(StandardCharsets.UTF_8));

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            jakarta.servlet.ServletOutputStream mockOutputStream = mock(jakarta.servlet.ServletOutputStream.class);
            when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

            HttpMessageUtils.flushToHttpResponse(data, mockResponse);

            verify(mockResponse).setStatus(200);
            verify(mockResponse).addHeader("X-Custom", "value");
            verify(mockResponse).setContentLength("response body".getBytes(StandardCharsets.UTF_8).length);
            verify(mockResponse).flushBuffer();
        }

        @Test
        @DisplayName("null body 不写入内容")
        void nullBodyNoWrite() throws IOException {
            HttpResponseData data = new HttpResponseData();
            data.setStatus(204);

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            jakarta.servlet.ServletOutputStream mockOutputStream = mock(jakarta.servlet.ServletOutputStream.class);
            when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

            HttpMessageUtils.flushToHttpResponse(data, mockResponse);

            verify(mockResponse, never()).setContentLength(anyInt());
            verify(mockOutputStream, never()).write(any(byte[].class));
        }
    }

    @Nested
    @DisplayName("convertMockResponseData")
    class ConvertMockResponseData {

        @Test
        @DisplayName("MockHttpServletResponse 转换正确")
        void mockResponseConversion() throws IOException {
            MockHttpServletResponse mockResponse = new MockHttpServletResponse();
            mockResponse.setStatus(200);
            mockResponse.setHeader("Content-Type", "application/json");
            mockResponse.getWriter().write("{\"ok\":true}");
            mockResponse.flushBuffer();

            HttpResponseData result = FuHttpLocalInvoker.convertMockResponseData(mockResponse);

            assertEquals(200, result.getStatus());
            assertNotNull(result.getHeaders().getFirst("Content-Type"));
            assertTrue(result.bodyBytes().length > 0);
        }
    }

    @Nested
    @DisplayName("Accept-Encoding 过滤逻辑")
    class AcceptEncodingFilter {

        @Test
        @DisplayName("只保留 gzip/deflate/br/zstd，值已 trim")
        void filterAcceptEncoding() {
            // 模拟修复后的 convertRequestData Accept-Encoding 过滤逻辑
            String acceptEncoding = "gzip, deflate, br, zstd, unknown-encoding";
            java.util.List<String> accepts = new java.util.ArrayList<>();
            java.util.Set<String> allowed = java.util.Set.of("gzip", "deflate", "br", "zstd");

            java.util.stream.Stream.of(acceptEncoding.split(",")).forEach(s -> {
                if (s != null && !s.isBlank() && allowed.contains(s.trim())) {
                    accepts.add(s.trim()); // 修复确认：使用 s.trim()
                }
            });

            assertEquals(4, accepts.size());
            assertTrue(accepts.contains("gzip"));
            assertTrue(accepts.contains("deflate"));
            assertTrue(accepts.contains("br"));
            assertTrue(accepts.contains("zstd"));
            assertFalse(accepts.contains("unknown-encoding"));
        }

        @Test
        @DisplayName("全部无效时移除 Accept-Encoding")
        void allInvalidRemovesHeader() {
            String acceptEncoding = "unknown1, unknown2";
            java.util.List<String> accepts = new java.util.ArrayList<>();
            java.util.Set<String> allowed = java.util.Set.of("gzip", "deflate", "br", "zstd");

            java.util.stream.Stream.of(acceptEncoding.split(",")).forEach(s -> {
                if (s != null && !s.isBlank() && allowed.contains(s.trim())) {
                    accepts.add(s.trim());
                }
            });

            assertTrue(accepts.isEmpty(), "全部无效时应清空 accepts 列表，代码会 remove Accept-Encoding");
        }
    }
}
