package fun.commons.retask4j.http.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SsrfValidatorTest {

    @Nested
    @DisplayName("validateUri 合法 URL")
    class ValidUri {

        @Test
        @DisplayName("http URL 允许")
        void httpUrl() {
            assertDoesNotThrow(() -> SsrfValidator.validateUri("http://example.com/path", "test"));
        }

        @Test
        @DisplayName("https URL 允许")
        void httpsUrl() {
            assertDoesNotThrow(() -> SsrfValidator.validateUri("https://example.com/path", "test"));
        }

        @Test
        @DisplayName("HTTP 大写 scheme 允许")
        void uppercaseScheme() {
            assertDoesNotThrow(() -> SsrfValidator.validateUri("HTTP://example.com/path", "test"));
        }
    }

    @Nested
    @DisplayName("validateUri 非法 URL")
    class InvalidUri {

        @Test
        @DisplayName("ftp scheme 拒绝")
        void ftpScheme() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("ftp://example.com/file", "test"));
            assertTrue(e.getMessage().contains("http or https"));
        }

        @Test
        @DisplayName("无 scheme 拒绝")
        void noScheme() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("example.com/path", "test"));
        }

        @Test
        @DisplayName("无 host 拒绝")
        void noHost() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http:///path", "test"));
            assertTrue(e.getMessage().contains("valid host"));
        }

        @Test
        @DisplayName("非法 URI 语法拒绝")
        void invalidSyntax() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://[invalid-ipv6", "test"));
        }

        @Test
        @DisplayName("localhost 拒绝（回环地址）")
        void localhost() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://localhost/path", "test"));
        }

        @Test
        @DisplayName("127.0.0.1 拒绝（回环 IP）")
        void loopbackIp() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://127.0.0.1/path", "test"));
        }

        @Test
        @DisplayName("10.0.0.1 拒绝（私有网络）")
        void privateIp10() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://10.0.0.1/path", "test"));
        }

        @Test
        @DisplayName("172.16.0.1 拒绝（私有网络）")
        void privateIp172() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://172.16.0.1/path", "test"));
        }

        @Test
        @DisplayName("192.168.1.1 拒绝（私有网络）")
        void privateIp192() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://192.168.1.1/path", "test"));
        }

        @Test
        @DisplayName("0.0.0.0 拒绝（anyLocal 地址）")
        void anyLocalIp() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateUri("http://0.0.0.0/path", "test"));
        }
    }

    @Nested
    @DisplayName("validateHost")
    class ValidateHost {

        @Test
        @DisplayName("合法公网主机名允许")
        void validPublicHost() {
            assertDoesNotThrow(() -> SsrfValidator.validateHost("example.com", "test"));
        }

        @Test
        @DisplayName("localhost 拒绝")
        void localhostRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.validateHost("localhost", "test"));
        }
    }

    @Nested
    @DisplayName("resolveAndValidate")
    class ResolveAndValidate {

        @Test
        @DisplayName("返回解析的 IP 地址字符串")
        void returnsIpAddress() {
            String ip = SsrfValidator.resolveAndValidate("example.com", "test");
            assertNotNull(ip);
            assertFalse(ip.isEmpty());
            // Should be a valid IP address format
            assertTrue(ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || ip.contains(":"),
                "Returned value should be an IP address: " + ip);
        }

        @Test
        @DisplayName("不可解析主机名拒绝")
        void unresolvableHost() {
            assertThrows(IllegalArgumentException.class,
                () -> SsrfValidator.resolveAndValidate("this-host-definitely-does-not-exist-xyz123.invalid", "test"));
        }
    }
}
