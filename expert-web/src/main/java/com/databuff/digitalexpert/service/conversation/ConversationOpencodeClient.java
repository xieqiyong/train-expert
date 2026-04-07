package com.databuff.digitalexpert.service.conversation;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.hz.xie.opencode.autoconfigure.OpencodeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class ConversationOpencodeClient {

    private static final int FILE_TEXT_LIMIT = 32 * 1024;

    private final RestClient restClient;

    public ConversationOpencodeClient(OpencodeProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.password())) {
            String username = StringUtils.hasText(properties.username()) ? properties.username() : "opencode";
            builder.defaultHeaders(headers -> headers.setBasicAuth(username, properties.password()));
        }
        this.restClient = builder.build();
    }

    public String createSession(String title, String directory, String workspace) {
        Map<String, Object> session = restClient.post()
                .uri(uriBuilder -> buildCreateSessionUri(uriBuilder, directory, workspace))
                .body(Map.of("title", title))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        String sessionId = extractString(session, "id");
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "创建会话失败: 未返回 sessionId"
            );
        }
        return sessionId;
    }

    public void sendPromptAsync(
            String sessionId,
            String directory,
            String workspace,
            String message,
            List<String> filePaths,
            String providerId,
            String modelId,
            String agent,
            String system,
            String variant
    ) {
        restClient.post()
                .uri(uriBuilder -> buildSessionMessageUri(uriBuilder, sessionId, directory, workspace))
                .body(buildPromptBody(message, filePaths, providerId, modelId, agent, system, variant))
                .retrieve()
                .toBodilessEntity();
    }

    public List<Map<String, Object>> listMessages(
            String sessionId,
            Integer limit,
            String directory,
            String workspace
    ) {
        List<Map<String, Object>> rawMessages = restClient.get()
                .uri(uriBuilder -> buildSessionMessagesListUri(uriBuilder, sessionId, limit, directory, workspace))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        return rawMessages == null ? List.of() : rawMessages;
    }

    public Map<String, Object> getSessionStatus(String sessionId, String directory, String workspace) {
        Map<String, Map<String, Object>> statusMap = restClient.get()
                .uri(uriBuilder -> buildSessionStatusUri(uriBuilder, directory, workspace))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {
                });
        return statusMap == null ? null : statusMap.get(sessionId);
    }

    public void ensureSessionExists(String sessionId, String directory, String workspace) {
        restClient.get()
                .uri(uriBuilder -> buildGetSessionUri(uriBuilder, sessionId, directory, workspace))
                .retrieve()
                .toBodilessEntity();
    }

    public void abortSession(String sessionId, String directory, String workspace) {
        restClient.post()
                .uri(uriBuilder -> buildAbortSessionUri(uriBuilder, sessionId, directory, workspace))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> buildPromptBody(
            String message,
            List<String> filePaths,
            String providerId,
            String modelId,
            String agent,
            String system,
            String variant
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parts", buildParts(message, filePaths));
        if (StringUtils.hasText(providerId) && StringUtils.hasText(modelId)) {
            body.put("model", Map.of(
                    "providerID", providerId.trim(),
                    "modelID", modelId.trim()
            ));
        }
        if (StringUtils.hasText(agent)) {
            body.put("agent", agent.trim());
        }
        if (StringUtils.hasText(system)) {
            body.put("system", system.trim());
        }
        if (StringUtils.hasText(variant)) {
            body.put("variant", variant.trim());
        }
        return body;
    }

    private List<Map<String, Object>> buildParts(String message, List<String> filePaths) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of(
                "type", "text",
                "text", message.trim()
        ));
        if (filePaths == null) {
            return parts;
        }
        for (String filePath : filePaths) {
            Map<String, Object> filePart = buildFilePart(filePath);
            if (!filePart.isEmpty()) {
                parts.add(filePart);
            }
        }
        return parts;
    }

    private Map<String, Object> buildFilePart(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return Map.of();
        }
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return Map.of();
            }
            String content = readFilePreview(path);
            String mimeType = Files.probeContentType(path);

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "file");
            source.put("path", path.toString());
            source.put("text", Map.of(
                    "value", content,
                    "start", 0,
                    "end", content.length()
            ));

            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", "file");
            part.put("mime", StringUtils.hasText(mimeType) ? mimeType : "application/octet-stream");
            part.put("filename", path.getFileName().toString());
            part.put("url", path.toUri().toString());
            part.put("source", source);
            return part;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String readFilePreview(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= FILE_TEXT_LIMIT) {
                return content;
            }
            return content.substring(0, FILE_TEXT_LIMIT);
        } catch (Exception ex) {
            return "";
        }
    }

    private URI buildCreateSessionUri(UriBuilder uriBuilder, String directory, String workspace) {
        uriBuilder.path("/session");
        appendContextQuery(uriBuilder, directory, workspace);
        return uriBuilder.build();
    }

    private URI buildSessionStatusUri(UriBuilder uriBuilder, String directory, String workspace) {
        uriBuilder.path("/session/status");
        appendContextQuery(uriBuilder, directory, workspace);
        return uriBuilder.build();
    }

    private URI buildGetSessionUri(UriBuilder uriBuilder, String sessionId, String directory, String workspace) {
        uriBuilder.path("/session/{sessionID}");
        appendContextQuery(uriBuilder, directory, workspace);
        return uriBuilder.build(sessionId);
    }

    private URI buildSessionMessagesListUri(
            UriBuilder uriBuilder,
            String sessionId,
            Integer limit,
            String directory,
            String workspace
    ) {
        uriBuilder.path("/session/{sessionID}/message");
        appendContextQuery(uriBuilder, directory, workspace);
        if (limit != null) {
            uriBuilder.queryParam("limit", limit);
        }
        return uriBuilder.build(sessionId);
    }

    private URI buildSessionMessageUri(UriBuilder uriBuilder, String sessionId, String directory, String workspace) {
        uriBuilder.path("/session/{sessionID}/prompt_async");
        appendContextQuery(uriBuilder, directory, workspace);
        return uriBuilder.build(sessionId);
    }

    private URI buildAbortSessionUri(UriBuilder uriBuilder, String sessionId, String directory, String workspace) {
        uriBuilder.path("/session/{sessionID}/abort");
        appendContextQuery(uriBuilder, directory, workspace);
        return uriBuilder.build(sessionId);
    }

    private void appendContextQuery(UriBuilder uriBuilder, String directory, String workspace) {
        if (StringUtils.hasText(directory)) {
            uriBuilder.queryParam("directory", directory.trim());
        }
        if (StringUtils.hasText(workspace)) {
            uriBuilder.queryParam("workspace", workspace.trim());
        }
    }

    private String extractString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }
}
