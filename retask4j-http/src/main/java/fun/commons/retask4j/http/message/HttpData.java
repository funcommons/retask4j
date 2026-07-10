package fun.commons.retask4j.http.message;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;


@EqualsAndHashCode
public class HttpData implements Cloneable {

    /**
     * Internal marker prefix used by {@link #getBody()} and {@link #setBody(Object)}
     * to round-trip binary body data through JSON serialization.
     * When a body is binary (non-text, non-JSON), {@link #getBody()} returns a String
     * prefixed with this marker; {@link #setBody(Object)} detects the prefix and
     * Base64-decodes the remainder. This is an internal protocol — callers should
     * not construct strings with this prefix manually.
     */
    static final String BODY_BASE64_PREFIX = "base64:retask4j:";

    @Getter
    HttpHeaders headers = new HttpHeaders();

    public void setHeaders(HttpHeaders headers) {
        this.headers = headers != null ? headers : new HttpHeaders();
    }

    @com.alibaba.fastjson2.annotation.JSONField(deserialize = true, name = "headers")
    public void setHeadersFromMap(java.util.Map<String, ?> map) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (map != null) {
            map.forEach((key, value) -> {
                if (value instanceof java.util.List<?> list) {
                    list.forEach(item -> httpHeaders.add(key, item != null ? item.toString() : ""));
                } else if (value != null) {
                    httpHeaders.add(key, value.toString());
                }
            });
        }
        this.headers = httpHeaders;
    }
    @Getter
    byte[] body = new byte[0];

    public void setBody(byte[] body) {
        this.body = body != null ? body.clone() : new byte[0];
    }

    @com.alibaba.fastjson2.annotation.JSONField(deserialize = true, name = "body")
    public void setBodyFromJson(Object body) {
        setBody(body);
    }

    public byte[] bodyBytes(){
        return body.clone();
    }

    public Object getBody(){

        MediaType contentType = headers.getContentType();
        if (Objects.nonNull(contentType)) {

            if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)){
                return body.length > 0 ? JSON.parse(body) : new JSONObject();
            }
            if (contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)){
                return body.length > 0 ? JSON.parse(body) : new JSONObject();
            }
            if ("text".equals(contentType.getType())){
                return bodyText();
            }
        }

        return BODY_BASE64_PREFIX + Base64.getEncoder().encodeToString(body);
    }

    public void setBody(Object body){

        if (Objects.isNull(body)){
            this.body = new byte[0];
            return;
        }

        MediaType contentType = headers.getContentType();
        Charset charset = StandardCharsets.UTF_8;
        if (Objects.nonNull(contentType)) {
            if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)){
                if (contentType.getCharset() != null){
                    charset = contentType.getCharset();
                }
            }
        }

        if (body instanceof byte[]){
            this.body = ((byte[]) body).clone();
        }
        else if (body instanceof String){
            String str = (String) body;
            if (str.startsWith(BODY_BASE64_PREFIX)){
                this.body = Base64.getDecoder().decode(str.substring(BODY_BASE64_PREFIX.length()));
            }else{
                this.body = ((String) body).getBytes(charset);
            }
        }
        else if (body instanceof JSONObject || body instanceof JSONArray){

            this.body = JSON.toJSONBytes(body);

        }else{
            // Fallback: serialize via JSON for unsupported types
            this.body = JSON.toJSONBytes(body);
        }

    }


    public String bodyText() {

        MediaType contentType = headers.getContentType();
        Charset charset = StandardCharsets.UTF_8;
        if (Objects.nonNull(contentType)) {
            if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)){
                if (contentType.getCharset() != null){
                    charset = contentType.getCharset();
                }
            }
        }

        String result;
        if (body.length == 0) {
            result = "";
        } else {
            result = new String(body,charset);
        }
        return result;
    }

    @Override
    public HttpData clone() {
        try {
            HttpData copy = (HttpData) super.clone();
            copy.headers = new HttpHeaders(this.headers);
            copy.body = this.body.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloneable implemented, clone should not fail", e);
        }
    }

}
