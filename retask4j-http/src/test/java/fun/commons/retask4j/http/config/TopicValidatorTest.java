package fun.commons.retask4j.http.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicValidatorTest {

    @Nested
    @DisplayName("合法 topic")
    class ValidTopics {

        @Test
        @DisplayName("简单字母数字")
        void simpleAlphanumeric() {
            assertDoesNotThrow(() -> TopicValidator.validate("order"));
        }

        @Test
        @DisplayName("包含连字符")
        void withHyphen() {
            assertDoesNotThrow(() -> TopicValidator.validate("my-topic"));
        }

        @Test
        @DisplayName("包含下划线")
        void withUnderscore() {
            assertDoesNotThrow(() -> TopicValidator.validate("my_topic"));
        }

        @Test
        @DisplayName("128 字符（边界值）")
        void atMaxLength() {
            assertDoesNotThrow(() -> TopicValidator.validate("t".repeat(128)));
        }
    }

    @Nested
    @DisplayName("非法 topic")
    class InvalidTopics {

        @Test
        @DisplayName("null 拒绝")
        void nullTopic() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate(null));
        }

        @Test
        @DisplayName("空字符串拒绝")
        void emptyTopic() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate(""));
        }

        @Test
        @DisplayName("纯空格拒绝")
        void blankTopic() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("   "));
        }

        @Test
        @DisplayName("129 字符拒绝")
        void exceedsMaxLength() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("t".repeat(129)));
        }

        @Test
        @DisplayName("包含冒号拒绝")
        void containsColon() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topic:name"));
        }

        @Test
        @DisplayName("包含 { 拒绝")
        void containsOpenBrace() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topic{name"));
        }

        @Test
        @DisplayName("包含 } 拒绝")
        void containsCloseBrace() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topic}name"));
        }

        @Test
        @DisplayName("包含换行符拒绝")
        void containsNewline() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topic\nname"));
        }

        @Test
        @DisplayName("包含 tab 拒绝")
        void containsTab() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topic\tname"));
        }

        @Test
        @DisplayName("包含 DEL (0x7F) 拒绝")
        void containsDel() {
            assertThrows(IllegalArgumentException.class,
                () -> TopicValidator.validate("topicname"));
        }
    }
}
