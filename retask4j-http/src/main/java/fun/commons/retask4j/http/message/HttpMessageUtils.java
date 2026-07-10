package fun.commons.retask4j.http.message;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class HttpMessageUtils {

    private static final int MAX_REQUEST_BODY_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_MULTIPART_FILE_SIZE = 10 * 1024 * 1024; // 10 MB per file

    private static final StandardServletMultipartResolver MULTIPART_RESOLVER = new StandardServletMultipartResolver();

    public static HttpRequestData convertRequestData(HttpServletRequest request) throws IOException {

        HttpRequestData requestData = new HttpRequestData();
        requestData.setMethod(request.getMethod());

        String queryString = request.getQueryString();
        UriComponents targetUrl = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .query(queryString != null ? queryString : "").build();
        String url = targetUrl.toUriString();
        requestData.setUrl(url);

        HttpHeaders headers = requestData.getHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
            }
        }

        String acceptEncoding = headers.getFirst(HttpHeaders.ACCEPT_ENCODING);

        if (StringUtils.isNotBlank(acceptEncoding)) {
            List<String> accepts = new ArrayList<>();
            Stream.of(acceptEncoding.split(",")).forEach(s -> {
                String trimmed = s.trim();
                // Strip quality value (e.g., "gzip;q=1.0" → "gzip")
                String encoding = trimmed.split(";", 2)[0].trim();
                if (StringUtils.isNotBlank(encoding) && Set.of("gzip","deflate","br","zstd").contains(encoding)) {
                    accepts.add(encoding);
                }
            });
            if (!accepts.isEmpty()) {
                headers.set(HttpHeaders.ACCEPT_ENCODING, String.join(",", accepts));
            }else{
                headers.remove(HttpHeaders.ACCEPT_ENCODING);
            }
        }

        MediaType contentType = headers.getContentType();

        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {

            JSONObject formBody = new JSONObject();
            if (MULTIPART_RESOLVER.isMultipart(request)) {

                MultipartHttpServletRequest multipartRequest = MULTIPART_RESOLVER.resolveMultipart(request);

                try {

                // Add form parameters
                JSONObject formParameters = JSONObject.from(multipartRequest.getParameterMap());

                // Remove form parameters from query params
                targetUrl.getQueryParams().forEach((paramName, paramValues) -> {
                    for (String paramValue : paramValues) {
                        if (formParameters.containsKey(paramName)) {
                            JSONArray vs = formParameters.getJSONArray(paramName);
                            if (vs != null && vs.contains(paramValue)) {
                                vs.remove(paramValue);
                            }
                        }
                    }
                });

                formBody.put("parameters", formParameters);
                // Add files

                JSONObject formFiles = new JSONObject();
                for (Iterator<String> it = multipartRequest.getFileNames(); it.hasNext(); ) {
                    String name = it.next();
                    List<MultipartFile> files = multipartRequest.getFiles(name);
                    if (files.isEmpty()) {
                        continue;
                    }

                    JSONArray fileArray = new JSONArray();
                    for (MultipartFile file : files) {
                        if (file.getSize() > MAX_MULTIPART_FILE_SIZE) {
                            throw new IOException("Multipart file '" + file.getOriginalFilename() + "' exceeds maximum size of " + MAX_MULTIPART_FILE_SIZE + " bytes");
                        }
                        JSONObject fileItem = JSONObject.of(
                                "name", name,
                                "value", Base64.getEncoder().encodeToString(file.getBytes()),
                                "filename", file.getOriginalFilename(),
                                "contentType", file.getContentType()
                        );
                        fileArray.add(fileItem);
                    }
                    formFiles.put(name, fileArray);
                }

                formBody.put("files", formFiles);

                requestData.setBody(JSON.toJSONBytes(formBody));

                } finally {
                    MULTIPART_RESOLVER.cleanupMultipart(multipartRequest);
                }

            } else {
                // Content-Type claims multipart but resolver rejected it (malformed boundary, etc.)
                // Fall back to reading raw body to avoid silently discarding the request data
                requestData.setBody(readBounded(request.getInputStream(), MAX_REQUEST_BODY_SIZE));
            }

        } else {
            requestData.setBody(readBounded(request.getInputStream(), MAX_REQUEST_BODY_SIZE));
        }


        return requestData;
    }

    public static HttpResponseData convertResponseData(ResponseEntity<byte[]> responseEntity) throws IOException {
        byte[] body = responseEntity.getBody();
        if (body == null) body = new byte[0];
        return buildResponseData(responseEntity.getStatusCode(), responseEntity.getHeaders(), body);
    }

    public static HttpResponseData convertErrorResponseData(HttpStatusCodeException error) throws IOException {
        HttpHeaders responseHeaders = error.getResponseHeaders();
        byte[] body = error.getResponseBodyAsByteArray();
        return buildResponseData(error.getStatusCode(), responseHeaders, body);
    }

    private static HttpResponseData buildResponseData(HttpStatusCode statusCode, HttpHeaders headers, byte[] body) throws IOException {
        HttpResponseData responseData = new HttpResponseData();
        responseData.setStatus(statusCode.value());
        if (statusCode instanceof HttpStatus hs) {
            responseData.setReason(hs.getReasonPhrase());
        }
        if (headers != null) {
            responseData.getHeaders().putAll(headers);
            responseData.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
        }
        String contentEncoding = responseData.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (contentEncoding != null) {
            byte[] decompressed = decompressBody(contentEncoding, body);
            if (decompressed != null) {
                responseData.setBody(decompressed);
                responseData.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
                responseData.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
            } else {
                responseData.setBody(body);
            }
        } else {
            responseData.setBody(body);
        }
        return responseData;
    }

    private static final int MAX_DECOMPRESSED_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Decompresses the body for recognized Content-Encoding values (gzip, deflate, br, zstd).
     * Returns null for unrecognized encodings, signaling the caller to keep body and headers intact.
     */
    private static byte[] decompressBody(String contentEncoding, byte[] body) throws IOException {
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (GzipCompressorInputStream gis = new GzipCompressorInputStream(new ByteArrayInputStream(body))) {
                return readBounded(gis, MAX_DECOMPRESSED_SIZE);
            }
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            try (DeflateCompressorInputStream dis = new DeflateCompressorInputStream(new ByteArrayInputStream(body))) {
                return readBounded(dis, MAX_DECOMPRESSED_SIZE);
            }
        } else if ("br".equalsIgnoreCase(contentEncoding)) {
            try (BrotliCompressorInputStream bis = new BrotliCompressorInputStream(new ByteArrayInputStream(body))) {
                return readBounded(bis, MAX_DECOMPRESSED_SIZE);
            }
        } else if ("zstd".equalsIgnoreCase(contentEncoding)) {
            try (ZstdCompressorInputStream zis = new ZstdCompressorInputStream(new ByteArrayInputStream(body))) {
                return readBounded(zis, MAX_DECOMPRESSED_SIZE);
            }
        }
        return null;
    }

    private static byte[] readBounded(InputStream is, int maxSize) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int totalRead = 0;
        int n;
        while ((n = is.read(buffer)) != -1) {
            totalRead += n;
            if (totalRead > maxSize) {
                throw new IOException("Decompressed body exceeds maximum size of " + maxSize + " bytes");
            }
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    public static HttpResponseData restTemplateExecute(RestTemplate restTemplate, HttpRequestData httpRequestData) throws IOException {

        // Work with a copy of headers to avoid mutating the input
        HttpHeaders headers = new HttpHeaders(httpRequestData.getHeaders());
        MediaType contentType = headers.getContentType();

        HttpEntity requestHttpEntity;

        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {

            headers.clearContentHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            JSONObject formBody = JSONObject.from(httpRequestData.getBody());

            // Add text fields
            JSONObject parameters = formBody.getJSONObject("parameters");
            if (parameters != null) {
                parameters.forEach((paramName, paramValues) -> {
                    if (paramValues instanceof JSONArray) {
                        ((JSONArray) paramValues).forEach(paramValue -> {
                            body.add(paramName, paramValue);
                        });
                    } else {
                        body.add(paramName, paramValues);
                    }
                });
            }

            // Add file fields
            JSONObject formFiles = formBody.getJSONObject("files");
            if (formFiles != null) {
                formFiles.forEach((paramName, paramValues) -> {
                    if (paramValues instanceof JSONArray) {
                        ((JSONArray) paramValues).forEach(paramValue -> {
                            JSONObject fileItem = (JSONObject) paramValue;
                            String filename = fileItem.getString("filename");
                            String fileType = fileItem.getString("contentType");
                            if (fileType == null || fileType.isBlank()) {
                                fileType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
                            }
                            final String effectiveFileType = fileType;
                            String value = fileItem.getString("value");
                            if (value == null || value.isBlank()) {
                                throw new IllegalArgumentException("Multipart file '" + filename + "' has missing or empty base64 value");
                            }
                            byte[] fileBytes = Base64.getDecoder().decode(value);
                            ByteArrayResource resource = new ByteArrayResource(fileBytes);
                            HttpHeaders fileHeaders = new HttpHeaders();
                            fileHeaders.setContentType(MediaType.parseMediaType(effectiveFileType));
                            fileHeaders.setContentDispositionFormData(paramName, filename);
                            body.add(paramName, new HttpEntity<>(resource, fileHeaders));
                        });
                    }
                });
            }

            requestHttpEntity = new HttpEntity<>(body, headers);

        } else {
            requestHttpEntity = new HttpEntity<>(httpRequestData.bodyBytes(), headers);
        }

        try {


            RequestCallback requestCallback = restTemplate.httpEntityCallback(requestHttpEntity);
            ResponseExtractor<ResponseEntity<byte[]>> responseExtractor = restTemplate.responseEntityExtractor(byte[].class);
            ResponseEntity<byte[]> responseEntity = restTemplate.execute(
                    httpRequestData.getUrl(),
                    HttpMethod.valueOf(httpRequestData.getMethod()),
                    requestCallback,
                    responseExtractor
            );
            // Normal response data conversion
            return convertResponseData(responseEntity);

        } catch (HttpStatusCodeException e) {
            // Error response data conversion
            return convertErrorResponseData(e);
        } catch (RestClientException e) {
            HttpResponseData errorResponse = new HttpResponseData();
            errorResponse.setStatus(502);
            errorResponse.setReason("Upstream connection failed");
            errorResponse.setBody("Upstream connection failed".getBytes(StandardCharsets.UTF_8));
            return errorResponse;
        }

    }

    private static final java.util.Set<String> HOP_BY_HOP_HEADERS = java.util.Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "upgrade", "transfer-encoding"
    );

    public static void flushToHttpResponse(HttpResponseData httpResponseData, HttpServletResponse response) throws IOException {
        response.setStatus(httpResponseData.getStatus());
        httpResponseData.getHeaders().forEach((k, vl) -> {
            String lower = k.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(lower)) return;
            // Content-Length is set explicitly below from the actual body length; skip the upstream value
            if ("content-length".equals(lower)) return;
            vl.forEach(v -> {
                response.addHeader(k, v);
            });
        });
        byte[] body = httpResponseData.bodyBytes();
        if (body.length > 0) {
            response.setContentLength(body.length);
            IOUtils.write(body, response.getOutputStream());
        }
        response.flushBuffer();
    }

}
