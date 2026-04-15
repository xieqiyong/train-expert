package com.databuff.digitalexpert.api;

import com.hz.xie.opencode.autoconfigure.OpencodeProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OpencodeSessionProxyController {

    private static final Set<String> REQUEST_HEADER_DENY_LIST = Set.of(
            "connection",
            "content-length",
            "host",
            "transfer-encoding",
            "upgrade"
    );

    private static final Set<String> RESPONSE_HEADER_ALLOW_LIST = Set.of(
            "cache-control",
            "content-type",
            "etag",
            "expires",
            "last-modified"
    );

    private final String baseUrl;
    private final Duration requestTimeout;
    private final String basicAuthHeader;
    private final HttpClient httpClient;

    public OpencodeSessionProxyController(OpencodeProperties properties) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl());
        this.requestTimeout = Duration.ofMillis(properties.readTimeout());
        this.basicAuthHeader = buildBasicAuthHeader(properties);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeout()))
                .build();
    }

    @RequestMapping(
            path = {"/session", "/session/**"},
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public ResponseEntity<byte[]> proxySession(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpRequest upstreamRequest = buildUpstreamRequest(method, request, body);

        try {
            HttpResponse<byte[]> upstreamResponse = httpClient.send(
                    upstreamRequest,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            return toResponseEntity(upstreamResponse);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenCode request interrupted", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenCode request failed", ex);
        }
    }

    private HttpRequest buildUpstreamRequest(HttpMethod method, HttpServletRequest request, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildUpstreamUri(request))
                .timeout(requestTimeout);

        copyRequestHeaders(request, builder);
        if (StringUtils.hasText(basicAuthHeader)) {
            builder.header(HttpHeaders.AUTHORIZATION, basicAuthHeader);
        }

        if (method == HttpMethod.POST) {
            builder.POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            return builder.build();
        }

        builder.GET();
        return builder.build();
    }

    private URI buildUpstreamUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        String queryString = request.getQueryString();
        String target = baseUrl + path + (StringUtils.hasText(queryString) ? "?" + queryString : "");
        return URI.create(target);
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (REQUEST_HEADER_DENY_LIST.contains(normalized)) {
                return;
            }
            request.getHeaders(headerName).asIterator().forEachRemaining(value -> builder.header(headerName, value));
        });
    }

    private ResponseEntity<byte[]> toResponseEntity(HttpResponse<byte[]> upstreamResponse) {
        HttpHeaders headers = new HttpHeaders();
        upstreamResponse.headers().map().forEach((name, values) -> {
            if (RESPONSE_HEADER_ALLOW_LIST.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> headers.add(name, value));
            }
        });

        return ResponseEntity.status(upstreamResponse.statusCode())
                .headers(headers)
                .body(upstreamResponse.body());
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String buildBasicAuthHeader(OpencodeProperties properties) {
        if (!StringUtils.hasText(properties.password())) {
            return null;
        }
        String username = StringUtils.hasText(properties.username()) ? properties.username() : "opencode";
        String token = username + ":" + properties.password();
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
