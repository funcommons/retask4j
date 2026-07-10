package fun.commons.retask4j.http.worker;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.http.message.HttpMessageUtils;
import fun.commons.retask4j.http.message.HttpRequestData;
import fun.commons.retask4j.http.message.HttpResponseData;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.http.HttpServlet;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Objects;

public class FuHttpLocalInvoker {

    public static final String DISPATCHER_SERVLET_BEAN_NAME = "dispatcherServlet";

    public static HttpResponseData localHttpInvoke(WebApplicationContext wac, HttpRequestData requestTemp) throws Exception {
        HttpServlet dispatcherServlet = wac.getBean(DISPATCHER_SERVLET_BEAN_NAME, HttpServlet.class);
        MockHttpServletRequest servletRequest = createServletRequest(requestTemp);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        dispatcherServlet.service(servletRequest, servletResponse);
        return convertMockResponseData(servletResponse);
    }

    public static HttpResponseData convertMockResponseData(MockHttpServletResponse response) throws IOException {
        HttpResponseData responseData = new HttpResponseData();
        responseData.setStatus(response.getStatus());
        HttpHeaders headers = responseData.getHeaders();
        response.getHeaderNames().forEach(k -> {
            response.getHeaders(k).forEach(v -> {
                headers.add(k, v);
            });
        });
        responseData.setBody(response.getContentAsByteArray());
        return responseData;
    }

    public static MockHttpServletRequest createServletRequest(HttpRequestData httpRequestData) throws URISyntaxException {
        HttpMethod httpMethod = HttpMethod.valueOf(httpRequestData.getMethod());
        URI uri = new URI(httpRequestData.getUrl());
        HttpHeaders headers = new HttpHeaders(httpRequestData.getHeaders());
        MediaType contentType = headers.getContentType();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(httpMethod.name());
        request.setRequestURI(uri.getPath());
        request.setPathInfo(uri.getPath());
        if (uri.getQuery() != null) {
            request.setQueryString(uri.getQuery());
        }

        headers.forEach((name, values) -> {
            for (String value : values) {
                request.addHeader(name, value);
            }
        });

        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
            JSONObject formBody = JSONObject.from(httpRequestData.getBody());

            JSONObject parameters = formBody.getJSONObject("parameters");
            if (parameters != null) {
                parameters.forEach((paramName, paramValues) -> {
                    if (paramValues instanceof JSONArray) {
                        ((JSONArray) paramValues).forEach(paramValue -> {
                            request.addParameter(paramName, String.valueOf(paramValue));
                        });
                    } else {
                        request.addParameter(paramName, String.valueOf(paramValues));
                    }
                });
            }

            JSONObject formFiles = formBody.getJSONObject("files");
            if (formFiles != null) {
                formFiles.forEach((paramName, paramValues) -> {
                    if (paramValues instanceof JSONArray) {
                        ((JSONArray) paramValues).forEach(paramValue -> {
                            JSONObject fileItem = (JSONObject) paramValue;
                            String filename = fileItem.getString("filename");
                            String fileType = fileItem.getString("contentType");
                            String value = fileItem.getString("value");
                            if (value == null || value.isBlank()) {
                                throw new IllegalArgumentException("Multipart file '" + filename + "' has missing or empty base64 value");
                            }
                            byte[] fileBytes = Base64.getDecoder().decode(value);
                            request.addPart(new jakarta.servlet.http.Part() {
                                @Override public String getName() { return paramName; }
                                @Override public String getSubmittedFileName() { return filename; }
                                @Override public String getContentType() { return fileType; }
                                @Override public long getSize() { return fileBytes.length; }
                                @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(fileBytes); }
                                @Override public void write(String fileName) { }
                                @Override public void delete() { }
                                @Override public String getHeader(String name) { return null; }
                                @Override public java.util.Collection<String> getHeaders(String name) { return java.util.List.of(); }
                                @Override public java.util.Collection<String> getHeaderNames() { return java.util.List.of(); }
                            });
                        });
                    }
                });
            }
        } else {
            byte[] body = httpRequestData.bodyBytes();
            if (Objects.nonNull(body) && body.length > 0) {
                request.setContent(body);
            }
        }

        return request;
    }

}
