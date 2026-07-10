package fun.commons.retask4j.http.worker;


import com.alibaba.fastjson2.annotation.JSONField;

import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import fun.commons.retask4j.http.config.HttpHeaderUtils;
import fun.commons.retask4j.http.config.RegexSafetyUtils;
import fun.commons.retask4j.http.config.SsrfValidator;
import fun.commons.retask4j.http.config.TopicValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.apache.commons.lang3.StringUtils;

import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Getter
public class FuHttpTaskWorkerConfig {

    @NotBlank
    @Setter(AccessLevel.NONE)
    private String topic = "default";

    public void setTopic(String topic) {
        TopicValidator.validate(topic);
        this.topic = topic;
    }

    @Setter
    private boolean enableRemote = true;
    @Setter
    private boolean enableLocal = true;

    // Route configuration
    private List<RouteConfig> routes = new ArrayList<>();

    public List<RouteConfig> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    public void setRoutes(List<RouteConfig> routes) {
        if (routes != null) {
            for (RouteConfig route : routes) {
                if (route == null) {
                    throw new IllegalArgumentException("routes must not contain null elements");
                }
            }
        }
        this.routes = routes != null ? new ArrayList<>(routes) : new ArrayList<>();
    }

    @Setter(AccessLevel.NONE)
    private int maxConsumeThreads = 64;

    public void setMaxConsumeThreads(int maxConsumeThreads) {
        if (maxConsumeThreads < 1) {
            throw new IllegalArgumentException("maxConsumeThreads must be at least 1: " + maxConsumeThreads);
        }
        if (maxConsumeThreads > 1024) {
            throw new IllegalArgumentException("maxConsumeThreads must not exceed 1024: " + maxConsumeThreads);
        }
        this.maxConsumeThreads = maxConsumeThreads;
    }

    @Setter(AccessLevel.NONE)
    private int pendingTimeout = 86400;

    public void setPendingTimeout(int pendingTimeout) {
        if (pendingTimeout < 1) {
            throw new IllegalArgumentException("pendingTimeout must be at least 1 second: " + pendingTimeout);
        }
        if (pendingTimeout > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("pendingTimeout must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + pendingTimeout);
        }
        this.pendingTimeout = pendingTimeout;
    }

    @Setter(AccessLevel.NONE)
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS;

    public void setConnectTimeout(int connectTimeout) {
        if (connectTimeout < 1) {
            throw new IllegalArgumentException("connectTimeout must be at least 1 ms: " + connectTimeout);
        }
        if (connectTimeout > 300_000) {
            throw new IllegalArgumentException("connectTimeout must not exceed 300000 ms: " + connectTimeout);
        }
        this.connectTimeout = connectTimeout;
    }

    @Setter(AccessLevel.NONE)
    private int readTimeout = DEFAULT_READ_TIMEOUT_MS;

    public void setReadTimeout(int readTimeout) {
        if (readTimeout < 1) {
            throw new IllegalArgumentException("readTimeout must be at least 1 ms: " + readTimeout);
        }
        if (readTimeout > 600_000) {
            throw new IllegalArgumentException("readTimeout must not exceed 600000 ms: " + readTimeout);
        }
        this.readTimeout = readTimeout;
    }

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 60_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 180_000;

    @Getter
    public static class RouteConfig{

        @Setter(AccessLevel.NONE)
        private String path = "*";
        @Setter(AccessLevel.NONE)
        private Pattern compiledPath;
        @Setter(AccessLevel.NONE)
        private String redirect;

        public void setRedirect(String redirect) {
            if (redirect != null && redirect.length() > 2048) {
                throw new IllegalArgumentException("redirect must not exceed 2048 characters");
            }
            // Validate scheme and host for redirects that are literal URLs (no regex group references).
            // Redirects with &N references are validated at execution time after substitution.
            if (redirect != null && redirect.contains("://") && !redirect.contains("&")) {
                SsrfValidator.validateUri(redirect, "redirect");
            }
            this.redirect = redirect;
        }

        // Request header rewrite
        @Setter(AccessLevel.NONE)
        private Map<String,String> rewriteRequestHeaders = new HashMap<>();

        public Map<String, String> getRewriteRequestHeaders() {
            return Collections.unmodifiableMap(rewriteRequestHeaders);
        }

        public void setRewriteRequestHeaders(Map<String, String> rewriteRequestHeaders) {
            Map<String, String> copy = rewriteRequestHeaders != null ? new HashMap<>(rewriteRequestHeaders) : new HashMap<>();
            copy.forEach(HttpHeaderUtils::validateNoCrlf);
            this.rewriteRequestHeaders = copy;
        }

        // Response header rewrite
        @Setter(AccessLevel.NONE)
        private Map<String,String> rewriteResponseHeaders = new HashMap<>();

        public Map<String, String> getRewriteResponseHeaders() {
            return Collections.unmodifiableMap(rewriteResponseHeaders);
        }

        public void setRewriteResponseHeaders(Map<String, String> rewriteResponseHeaders) {
            Map<String, String> copy = rewriteResponseHeaders != null ? new HashMap<>(rewriteResponseHeaders) : new HashMap<>();
            copy.forEach(HttpHeaderUtils::validateNoCrlf);
            this.rewriteResponseHeaders = copy;
        }

        // Request/response assertion
        @Setter(AccessLevel.NONE)
        private AssertsConfig assertResponse;

        public void setAssertResponse(AssertsConfig assertResponse) {
            this.assertResponse = assertResponse != null ? assertResponse.copy() : null;
        }

        public void setPath(String path) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must not be null or blank");
            }
            if (path.length() > 512) {
                throw new IllegalArgumentException("path must not exceed 512 characters: " + path.length());
            }
            this.path = path;
            if (!"*".equals(path)) {
                this.compiledPath = RegexSafetyUtils.compileSafePattern(path);
            } else {
                this.compiledPath = null;
            }
        }

        public Pattern getCompiledPath() {
            return compiledPath;
        }

        public RouteConfig copy() {
            RouteConfig c = new RouteConfig();
            c.path = this.path;
            c.compiledPath = this.compiledPath;
            c.setRedirect(this.redirect);
            c.setRewriteRequestHeaders(this.rewriteRequestHeaders);
            c.setRewriteResponseHeaders(this.rewriteResponseHeaders);
            c.setAssertResponse(this.assertResponse != null ? this.assertResponse.copy() : null);
            return c;
        }

    }

    @Getter
    public static class AssertsConfig{

        private static final int MAX_REGEX_LENGTH = RegexSafetyUtils.MAX_REGEX_LENGTH;
        private static final int MAX_BODY_MATCH_CHARS = 10240;

        // Empty means no validation
        // Required status codes, common values [200,301,302,304,403,404]
        @Setter(AccessLevel.NONE)
        private List<Integer> statusIn = new ArrayList<>();

        public List<Integer> getStatusIn() {
            return Collections.unmodifiableList(statusIn);
        }

        public void setStatusIn(List<Integer> statusIn) {
            if (statusIn != null) {
                for (Integer code : statusIn) {
                    if (code == null) {
                        throw new IllegalArgumentException("statusIn must not contain null elements");
                    }
                    if (code < 100 || code > 599) {
                        throw new IllegalArgumentException("statusIn values must be valid HTTP status codes (100-599): " + code);
                    }
                }
            }
            this.statusIn = statusIn != null ? new ArrayList<>(statusIn) : new ArrayList<>();
        }

        // Response headers must match, key:regex
        @Setter(AccessLevel.NONE)
        private Map<String,String> headerMatch = new HashMap<>();

        public Map<String, String> getHeaderMatch() {
            return Collections.unmodifiableMap(headerMatch);
        }

        public void setHeaderMatch(Map<String, String> headerMatch) {
            this.headerMatch = headerMatch != null ? new HashMap<>(headerMatch) : new HashMap<>();
            this.compiledHeaderMatch = new HashMap<>();
            if (headerMatch != null) {
                for (Map.Entry<String, String> entry : headerMatch.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        throw new IllegalArgumentException("headerMatch key must not be null or blank");
                    }
                    this.compiledHeaderMatch.put(entry.getKey(), compileSafePattern(entry.getValue()));
                }
            }
        }
        // Response body (text) must match regex
        @Setter(AccessLevel.NONE)
        private String textBodyMatch;

        @JSONField(deserialize = false)
        private Pattern compiledTextBodyMatch;
        // Response body (json) key match, key:regex (flat key lookup via getString, not JSONPath)
        @Setter(AccessLevel.NONE)
        private Map<String,String> jsonPathMatch = new HashMap<>();

        public Map<String, String> getJsonPathMatch() {
            return Collections.unmodifiableMap(jsonPathMatch);
        }

        @JSONField(deserialize = false)
        private Map<String,Pattern> compiledJsonPathMatch = new HashMap<>();

        @JSONField(deserialize = false)
        public Map<String, Pattern> getCompiledJsonPathMatch() {
            return Collections.unmodifiableMap(compiledJsonPathMatch);
        }

        // Response header match (pre-compiled)
        @JSONField(deserialize = false)
        private Map<String,Pattern> compiledHeaderMatch = new HashMap<>();

        @JSONField(deserialize = false)
        public Map<String, Pattern> getCompiledHeaderMatch() {
            return Collections.unmodifiableMap(compiledHeaderMatch);
        }

        public static Pattern compileSafePattern(String regex) {
            return RegexSafetyUtils.compileSafePattern(regex);
        }

        public void setTextBodyMatch(String textBodyMatch) {
            this.textBodyMatch = textBodyMatch;
            this.compiledTextBodyMatch = StringUtils.isNotBlank(textBodyMatch) ? compileSafePattern(textBodyMatch) : null;
        }

        public void setJsonPathMatch(Map<String, String> jsonPathMatch) {
            this.jsonPathMatch = jsonPathMatch != null ? new HashMap<>(jsonPathMatch) : new HashMap<>();
            this.compiledJsonPathMatch = new HashMap<>();
            if (jsonPathMatch != null) {
                for (Map.Entry<String, String> entry : jsonPathMatch.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        throw new IllegalArgumentException("jsonPathMatch key must not be null or blank");
                    }
                    this.compiledJsonPathMatch.put(entry.getKey(), compileSafePattern(entry.getValue()));
                }
            }
        }

        public AssertsConfig copy() {
            AssertsConfig c = new AssertsConfig();
            c.setStatusIn(this.statusIn != null ? new ArrayList<>(this.statusIn) : null);
            c.setHeaderMatch(this.headerMatch != null ? new HashMap<>(this.headerMatch) : null);
            c.setTextBodyMatch(this.textBodyMatch);
            c.setJsonPathMatch(this.jsonPathMatch);
            return c;
        }

    }

    public FuTaskWorkConfig toWorkConfig() {
        FuTaskWorkConfig config = new FuTaskWorkConfig(topic);
        config.setMaxConsumeThreads(maxConsumeThreads);
        config.setPendingTimeout(pendingTimeout);
        return config;
    }

    public FuHttpTaskWorkerConfig deepCopy() {
        FuHttpTaskWorkerConfig c = new FuHttpTaskWorkerConfig();
        c.setTopic(this.topic);
        c.setEnableRemote(this.enableRemote);
        c.setEnableLocal(this.enableLocal);
        List<RouteConfig> copiedRoutes = new ArrayList<>();
        for (RouteConfig route : this.routes) {
            copiedRoutes.add(route.copy());
        }
        c.setRoutes(copiedRoutes);
        c.setMaxConsumeThreads(this.maxConsumeThreads);
        c.setPendingTimeout(this.pendingTimeout);
        c.setConnectTimeout(this.connectTimeout);
        c.setReadTimeout(this.readTimeout);
        return c;
    }

}
