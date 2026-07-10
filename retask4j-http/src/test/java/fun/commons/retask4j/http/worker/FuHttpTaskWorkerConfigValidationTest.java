package fun.commons.retask4j.http.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskWorkerConfigValidationTest {

    @Nested
    @DisplayName("topic 验证 (TopicValidator)")
    class TopicValidation {

        @Test
        @DisplayName("null 拒绝")
        void nullTopic() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic(null));
        }

        @Test
        @DisplayName("空字符串拒绝")
        void blankTopic() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic(""));
        }

        @Test
        @DisplayName("129 字符拒绝")
        void topicExceedsMaxLength() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("t".repeat(129)));
        }

        @Test
        @DisplayName("包含冒号拒绝")
        void topicWithColon() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topic:name"));
        }

        @Test
        @DisplayName("包含控制字符拒绝")
        void topicWithControlChar() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topicname"));
        }
    }

    @Nested
    @DisplayName("maxConsumeThreads 验证")
    class MaxConsumeThreadsValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void maxConsumeThreadsAtMin() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            config.setMaxConsumeThreads(1);
            assertEquals(1, config.getMaxConsumeThreads());
        }

        @Test
        @DisplayName("0 拒绝")
        void maxConsumeThreadsZero() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxConsumeThreads(0));
        }
    }

    @Nested
    @DisplayName("pendingTimeout 验证")
    class PendingTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void pendingTimeoutAtMin() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            config.setPendingTimeout(1);
            assertEquals(1, config.getPendingTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void pendingTimeoutZero() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setPendingTimeout(0));
        }
    }

    @Nested
    @DisplayName("connectTimeout 验证")
    class ConnectTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void connectTimeoutAtMin() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            config.setConnectTimeout(1);
            assertEquals(1, config.getConnectTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void connectTimeoutZero() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setConnectTimeout(0));
        }
    }

    @Nested
    @DisplayName("readTimeout 验证")
    class ReadTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void readTimeoutAtMin() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            config.setReadTimeout(1);
            assertEquals(1, config.getReadTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void readTimeoutZero() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setReadTimeout(0));
        }
    }

    @Nested
    @DisplayName("routes 验证")
    class RoutesValidation {

        @Test
        @DisplayName("null 规范化为空列表")
        void nullRoutesNormalized() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            config.setRoutes(null);
            assertNotNull(config.getRoutes());
            assertTrue(config.getRoutes().isEmpty());
        }

        @Test
        @DisplayName("返回不可修改列表")
        void routesUnmodifiable() {
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            assertThrows(UnsupportedOperationException.class,
                () -> config.getRoutes().add(new FuHttpTaskWorkerConfig.RouteConfig()));
        }
    }

    @Nested
    @DisplayName("RouteConfig.redirect 验证")
    class RedirectValidation {

        @Test
        @DisplayName("null 允许")
        void nullRedirect() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertDoesNotThrow(() -> route.setRedirect(null));
        }

        @Test
        @DisplayName("2048 字符允许（边界值）")
        void redirectAtMaxLength() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            String redirect = "a".repeat(2048);
            assertDoesNotThrow(() -> route.setRedirect(redirect));
            assertEquals(2048, route.getRedirect().length());
        }

        @Test
        @DisplayName("2049 字符拒绝")
        void redirectExceedsMaxLength() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertThrows(IllegalArgumentException.class,
                () -> route.setRedirect("a".repeat(2049)));
        }
    }

    @Nested
    @DisplayName("RouteConfig.path 验证")
    class PathValidation {

        @Test
        @DisplayName("'*' 不编译正则（compiledPath 为 null）")
        void defaultPathNoCompilation() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertEquals("*", route.getPath());
            assertNull(route.getCompiledPath());
        }

        @Test
        @DisplayName("正则 path 编译成功")
        void regexPathCompiles() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            route.setPath("http://www\\.example\\.com/(.+)");
            assertNotNull(route.getCompiledPath());
        }

        @Test
        @DisplayName("无效正则 path 拒绝")
        void invalidRegexPathRejected() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertThrows(java.util.regex.PatternSyntaxException.class,
                () -> route.setPath("[invalid"));
        }

        @Test
        @DisplayName("ReDoS pattern path 拒绝")
        void redosPatternPathRejected() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertThrows(IllegalArgumentException.class,
                () -> route.setPath("(a+)+"));
        }
    }

    @Nested
    @DisplayName("RouteConfig headers 防御性拷贝")
    class RouteConfigHeadersDefensiveCopy {

        @Test
        @DisplayName("rewriteRequestHeaders null 规范化为空 map")
        void nullRequestHeaders() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            route.setRewriteRequestHeaders(null);
            assertNotNull(route.getRewriteRequestHeaders());
            assertTrue(route.getRewriteRequestHeaders().isEmpty());
        }

        @Test
        @DisplayName("rewriteResponseHeaders null 规范化为空 map")
        void nullResponseHeaders() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            route.setRewriteResponseHeaders(null);
            assertNotNull(route.getRewriteResponseHeaders());
            assertTrue(route.getRewriteResponseHeaders().isEmpty());
        }

        @Test
        @DisplayName("rewriteRequestHeaders 返回不可修改 map")
        void requestHeadersUnmodifiable() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertThrows(UnsupportedOperationException.class,
                () -> route.getRewriteRequestHeaders().put("X-Hack", "val"));
        }

        @Test
        @DisplayName("rewriteResponseHeaders 返回不可修改 map")
        void responseHeadersUnmodifiable() {
            FuHttpTaskWorkerConfig.RouteConfig route = new FuHttpTaskWorkerConfig.RouteConfig();
            assertThrows(UnsupportedOperationException.class,
                () -> route.getRewriteResponseHeaders().put("X-Hack", "val"));
        }
    }

    @Nested
    @DisplayName("AssertsConfig.compileSafePattern 验证")
    class CompileSafePatternValidation {

        @Test
        @DisplayName("null 拒绝（NullPointerException）")
        void nullPattern() {
            assertThrows(NullPointerException.class,
                () -> FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern(null));
        }

        @Test
        @DisplayName("256 字符允许（边界值）")
        void patternAtMaxLength() {
            String regex = "a".repeat(256);
            assertNotNull(FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern(regex));
        }

        @Test
        @DisplayName("257 字符拒绝")
        void patternExceedsMaxLength() {
            String regex = "a".repeat(257);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern(regex));
            assertTrue(e.getMessage().contains("256"));
        }

        @Test
        @DisplayName("嵌套量词 (a+)+ 拒绝")
        void nestedQuantifiersRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern("(a+)+"));
        }

        @Test
        @DisplayName("嵌套量词 (a*)* 拒绝")
        void nestedStarQuantifiersRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern("(a*)*"));
        }

        @Test
        @DisplayName("量词交替 (a|a)+ 拒绝")
        void alternationQuantifiersRejected() {
            assertThrows(IllegalArgumentException.class,
                () -> FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern("(a|a)+"));
        }

        @Test
        @DisplayName("安全正则允许")
        void safePatternAllowed() {
            assertNotNull(FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern("success|ok|true"));
            assertNotNull(FuHttpTaskWorkerConfig.AssertsConfig.compileSafePattern("application/json(;\\s?charset=.+)?"));
        }
    }

    @Nested
    @DisplayName("AssertsConfig 集合验证")
    class AssertsConfigCollections {

        @Test
        @DisplayName("statusIn null 规范化为空列表")
        void nullStatusIn() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            asserts.setStatusIn(null);
            assertNotNull(asserts.getStatusIn());
            assertTrue(asserts.getStatusIn().isEmpty());
        }

        @Test
        @DisplayName("statusIn 返回不可修改列表")
        void statusInUnmodifiable() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            assertThrows(UnsupportedOperationException.class,
                () -> asserts.getStatusIn().add(200));
        }

        @Test
        @DisplayName("headerMatch null 规范化为空 map 并编译")
        void nullHeaderMatch() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            asserts.setHeaderMatch(null);
            assertNotNull(asserts.getHeaderMatch());
            assertTrue(asserts.getHeaderMatch().isEmpty());
        }

        @Test
        @DisplayName("headerMatch 正则编译失败拒绝")
        void headerMatchInvalidRegex() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            Map<String, String> hm = new HashMap<>();
            hm.put("Content-Type", "(a+)+");
            assertThrows(IllegalArgumentException.class,
                () -> asserts.setHeaderMatch(hm));
        }

        @Test
        @DisplayName("jsonPathMatch null 规范化为空 map")
        void nullJsonPathMatch() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            asserts.setJsonPathMatch(null);
            assertNotNull(asserts.getJsonPathMatch());
            assertTrue(asserts.getJsonPathMatch().isEmpty());
        }

        @Test
        @DisplayName("textBodyMatch 空白字符串不编译")
        void blankTextBodyMatch() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            asserts.setTextBodyMatch("");
            assertEquals("", asserts.getTextBodyMatch());
        }

        @Test
        @DisplayName("textBodyMatch ReDoS pattern 拒绝")
        void textBodyMatchRedos() {
            FuHttpTaskWorkerConfig.AssertsConfig asserts = new FuHttpTaskWorkerConfig.AssertsConfig();
            assertThrows(IllegalArgumentException.class,
                () -> asserts.setTextBodyMatch("(a+)+"));
        }
    }

    @Nested
    @DisplayName("RouteConfig.copy 验证")
    class RouteConfigCopy {

        @Test
        @DisplayName("copy 产生独立副本")
        void copyIsIndependent() {
            FuHttpTaskWorkerConfig.RouteConfig original = new FuHttpTaskWorkerConfig.RouteConfig();
            original.setPath("http://www\\.example\\.com/(.+)");
            original.setRedirect("https://example.com/&1");

            Map<String, String> reqHeaders = new HashMap<>();
            reqHeaders.put("X-Auth", "token");
            original.setRewriteRequestHeaders(reqHeaders);

            FuHttpTaskWorkerConfig.RouteConfig copy = original.copy();
            copy.setRedirect("https://other.com/&1");

            assertEquals("https://example.com/&1", original.getRedirect());
            assertEquals("https://other.com/&1", copy.getRedirect());
        }
    }

    @Nested
    @DisplayName("AssertsConfig.copy 验证")
    class AssertsConfigCopy {

        @Test
        @DisplayName("copy 产生独立副本")
        void copyIsIndependent() {
            FuHttpTaskWorkerConfig.AssertsConfig original = new FuHttpTaskWorkerConfig.AssertsConfig();
            original.setStatusIn(List.of(200, 301));

            FuHttpTaskWorkerConfig.AssertsConfig copy = original.copy();
            copy.setStatusIn(List.of(404));

            assertEquals(2, original.getStatusIn().size());
            assertEquals(1, copy.getStatusIn().size());
        }
    }

    @Nested
    @DisplayName("deepCopy 验证")
    class DeepCopyValidation {

        @Test
        @DisplayName("deepCopy 产生独立副本")
        void deepCopyIsIndependent() {
            FuHttpTaskWorkerConfig original = new FuHttpTaskWorkerConfig();
            original.setMaxConsumeThreads(32);

            FuHttpTaskWorkerConfig copy = original.deepCopy();
            copy.setMaxConsumeThreads(16);

            assertEquals(32, original.getMaxConsumeThreads());
            assertEquals(16, copy.getMaxConsumeThreads());
        }
    }
}
