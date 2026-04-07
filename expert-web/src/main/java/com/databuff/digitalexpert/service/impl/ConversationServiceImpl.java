package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.ConversationAbortResponse;
import com.databuff.digitalexpert.dao.dto.ConversationFinishedResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessageResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesRequest;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSendResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSessionRequest;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.ConversationService;
import com.hz.xie.opencode.autoconfigure.OpencodeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

public class ConversationServiceImpl implements ConversationService {

    private static final int FILE_TEXT_LIMIT = 32 * 1024;

    private final RestClient restClient;

    @Autowired
    public ConversationServiceImpl(OpencodeProperties properties) {
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

    @Override
    public ConversationSendResponse sendMessage(ConversationSendRequest request) {
        try {
            validateModel(request);
            String sessionId = resolveSessionId(request);
            restClient.post()
                    .uri(uriBuilder -> buildSessionMessageUri(
                            uriBuilder,
                            sessionId,
                            request.directory(),
                            request.workspace()
                    ))
                    .body(buildPromptBody(request))
                    .retrieve()
                    .toBodilessEntity();
            return new ConversationSendResponse(sessionId, null, Boolean.TRUE);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "发送会话消息失败: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationMessagesResponse listMessages(ConversationMessagesRequest request) {
        try {
            List<Map<String, Object>> rawMessages = restClient.get()
                    .uri(uriBuilder -> buildSessionMessagesListUri(
                            uriBuilder,
                            request.sessionId(),
                            request.limit(),
                            request.directory(),
                            request.workspace()
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<ConversationMessageResponse> messages = new ArrayList<>();
            if (rawMessages == null) {
                return new ConversationMessagesResponse(request.sessionId(), messages);
            }
            for (Map<String, Object> item : rawMessages) {
                if (item == null) {
                    continue;
                }
                Object infoObject = item.get("info");
                Object partsObject = item.get("parts");
                Map<String, Object> info = infoObject instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                List<Object> parts = partsObject instanceof List<?> list
                        ? (List<Object>) list
                        : List.of();
                messages.add(new ConversationMessageResponse(info, parts));
            }
            return new ConversationMessagesResponse(request.sessionId(), messages);
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "查询会话消息失败: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationFinishedResponse checkFinished(ConversationSessionRequest request) {
        try {
            Map<String, Map<String, Object>> statusMap = restClient.get()
                    .uri(uriBuilder -> buildSessionStatusUri(
                            uriBuilder,
                            request.directory(),
                            request.workspace()
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {
                    });
            Map<String, Object> status = statusMap != null ? statusMap.get(request.sessionId()) : null;
            boolean finished = isFinished(status, request);
            return new ConversationFinishedResponse(request.sessionId(), finished);
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "查询会话状态失败: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationAbortResponse abortConversation(ConversationSessionRequest request) {
        try {
            restClient.post()
                    .uri(uriBuilder -> buildAbortSessionUri(
                            uriBuilder,
                            request.sessionId(),
                            request.directory(),
                            request.workspace()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return new ConversationAbortResponse(request.sessionId(), Boolean.TRUE);
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "中止会话失败: " + ex.getMessage()
            );
        }
    }

    private void validateModel(ConversationSendRequest request) {
        boolean hasProviderId = StringUtils.hasText(request.providerId());
        boolean hasModelId = StringUtils.hasText(request.modelId());
        if (hasProviderId != hasModelId) {
            throw BusinessException.badRequest(
                    ErrorCode.INVALID_REQUEST,
                    "providerId 和 modelId 必须同时传入"
            );
        }
    }

    private String resolveSessionId(ConversationSendRequest request) {
        if (StringUtils.hasText(request.sessionId())) {
            return request.sessionId().trim();
        }
        Map<String, Object> session = restClient.post()
                .uri(uriBuilder -> buildCreateSessionUri(
                        uriBuilder,
                        request.directory(),
                        request.workspace()
                ))
                .body(Map.of("title", buildSessionTitle(request.message())))
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

    private Map<String, Object> buildPromptBody(ConversationSendRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parts", buildParts(request));
        if (StringUtils.hasText(request.providerId()) && StringUtils.hasText(request.modelId())) {
            body.put("model", Map.of(
                    "providerID", request.providerId().trim(),
                    "modelID", request.modelId().trim()
            ));
        }
        if (StringUtils.hasText(request.agent())) {
            body.put("agent", request.agent().trim());
        }
        if (StringUtils.hasText(request.system())) {
            body.put("system", request.system().trim());
        }
        if (StringUtils.hasText(request.variant())) {
            body.put("variant", request.variant().trim());
        }
        return body;
    }

    private List<Map<String, Object>> buildParts(ConversationSendRequest request) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of(
                "type", "text",
                "text", request.message().trim()
        ));
        if (request.filePaths() == null) {
            return parts;
        }
        for (String filePath : request.filePaths()) {
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

    private boolean isFinished(Map<String, Object> status, ConversationSessionRequest request) {
        if (status == null || status.isEmpty()) {
            restClient.get()
                    .uri(uriBuilder -> buildGetSessionUri(
                            uriBuilder,
                            request.sessionId(),
                            request.directory(),
                            request.workspace()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        }
        String type = extractString(status, "type");
        return "idle".equals(type);
    }

    private String buildSessionTitle(String message) {
        String title = message.trim().replaceAll("\\s+", " ");
        if (title.length() <= 32) {
            return title;
        }
        return title.substring(0, 32);
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
