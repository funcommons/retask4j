package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.exception.FuTaskCallbackException;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.http.config.SsrfValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.function.Consumer;

@Slf4j
public class FuHttpTaskCallback implements Consumer<FuTaskMessage> {


    private final RestTemplate restTemplate;
    private final FuHttpTaskCallerConfig callerConfig;

    public FuHttpTaskCallback(RestTemplate restTemplate, FuHttpTaskCallerConfig callerConfig) {
        this.restTemplate = restTemplate;
        this.callerConfig = callerConfig;
    }

    @Override
    public void accept(FuTaskMessage fuTaskMessage) {
        String url = fuTaskMessage.getExtInfo().getString("callback");
        if (StringUtils.isBlank(url)) {
            url = callerConfig.getCallbackUrl();
        }
        if (StringUtils.isBlank(url)) {
            throw new FuTaskCallbackException("Callback URL is missing in task message " + fuTaskMessage.getId(), false);
        }

        // Resolve and validate, then pin to validated IP to prevent DNS rebinding
        String pinnedUrl = url;
        String originalHost = null;
        int originalPort = -1;
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            originalHost = host;
            originalPort = uri.getPort();
            if (host != null && !host.isBlank()) {
                String validatedIp = SsrfValidator.resolveAndValidate(host, "Callback URL");
                // IPv6 addresses must be bracketed in URLs
                String hostForUrl = validatedIp.contains(":") ? "[" + validatedIp + "]" : validatedIp;
                // Reconstruct URI with validated IP instead of string replacement
                // to avoid issues with userinfo, multiple host occurrences, or regex metacharacters
                pinnedUrl = new java.net.URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        hostForUrl,
                        uri.getPort(),
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                ).toString();
            }
        } catch (IllegalArgumentException e) {
            throw new FuTaskCallbackException("Callback URL validation failed", false);
        } catch (java.net.URISyntaxException e) {
            throw new FuTaskCallbackException("Callback URL is not a valid URI", false);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (originalHost != null && !originalHost.isBlank()) {
            headers.set("Host", originalHost + (originalPort > 0 ? ":" + originalPort : ""));
        }

        JSONObject body = new JSONObject();
        body.put("id", fuTaskMessage.getId());
        body.put("response", fuTaskMessage.getOutput());
        body.put("status", fuTaskMessage.getStatus());
        body.put("completeTime", fuTaskMessage.getCompleteTime());
        body.put("executeTime", fuTaskMessage.getExecuteTime());

        HttpEntity<String> requestEntity = new HttpEntity<>(JSON.toJSONString(body), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(pinnedUrl, HttpMethod.POST, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                boolean retryable = !response.getStatusCode().is4xxClientError();
                throw new FuTaskCallbackException("HTTP request failed with status code: " + response.getStatusCode(), retryable);
            }
        } catch (RestClientException e) {
            // 4xx errors are non-retryable; connection/timeout/5xx errors are retryable
            boolean retryable = !(e instanceof org.springframework.web.client.HttpClientErrorException);
            throw new FuTaskCallbackException("Callback request failed: " + e.getMessage(), retryable);
        }
    }

}
