package com.databuff.digitalexpert.service.conversation;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.databuff.digitalexpert.dao.dto.ConversationMessageResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowEventResponse;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowResponse;
import com.databuff.digitalexpert.dao.entity.ConversationMessageEntity;
import com.databuff.digitalexpert.dao.entity.ConversationSessionEntity;
import com.databuff.digitalexpert.dao.entity.ConversationWorkflowEventEntity;
import com.databuff.digitalexpert.dao.mapper.ConversationMessageMapper;
import com.databuff.digitalexpert.dao.mapper.ConversationSessionMapper;
import com.databuff.digitalexpert.dao.mapper.ConversationWorkflowEventMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationStorageService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_ABORTED = "ABORTED";
    public static final String STATUS_FAILED = "FAILED";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };

    private final ConversationSessionMapper conversationSessionMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationWorkflowEventMapper conversationWorkflowEventMapper;

    public ConversationStorageService(
            ConversationSessionMapper conversationSessionMapper,
            ConversationMessageMapper conversationMessageMapper,
            ConversationWorkflowEventMapper conversationWorkflowEventMapper
    ) {
        this.conversationSessionMapper = conversationSessionMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.conversationWorkflowEventMapper = conversationWorkflowEventMapper;
    }

    @Transactional
    public void saveAcceptedRequest(String sessionId, ConversationSendRequest request, String sessionTitle) {
        ConversationSessionEntity entity = findSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new ConversationSessionEntity();
            entity.setSessionId(sessionId);
            entity.setCreatedAt(now);
            entity.setMessageCount(0);
            entity.setWorkflowCount(0);
        }
        entity.setDirectory(firstNonBlank(trimToNull(request.directory()), entity.getDirectory()));
        entity.setWorkspace(firstNonBlank(trimToNull(request.workspace()), entity.getWorkspace()));
        entity.setSessionTitle(firstNonBlank(sessionTitle, entity.getSessionTitle()));
        entity.setProviderId(firstNonBlank(trimToNull(request.providerId()), entity.getProviderId()));
        entity.setModelId(firstNonBlank(trimToNull(request.modelId()), entity.getModelId()));
        entity.setAgent(firstNonBlank(trimToNull(request.agent()), entity.getAgent()));
        entity.setStatus(STATUS_RUNNING);
        entity.setLastError(null);
        entity.setFinishedAt(null);
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            conversationSessionMapper.insert(entity);
        } else {
            conversationSessionMapper.updateById(entity);
        }
    }

    public ConversationSessionContext resolveContext(String sessionId, String directory, String workspace) {
        ConversationSessionEntity entity = findSession(sessionId);
        String resolvedDirectory = StringUtils.hasText(directory)
                ? directory.trim()
                : entity != null ? entity.getDirectory() : null;
        String resolvedWorkspace = StringUtils.hasText(workspace)
                ? workspace.trim()
                : entity != null ? entity.getWorkspace() : null;
        return new ConversationSessionContext(sessionId, resolvedDirectory, resolvedWorkspace);
    }

    public String getSessionStatus(String sessionId) {
        ConversationSessionEntity entity = findSession(sessionId);
        return entity == null ? null : entity.getStatus();
    }

    @Transactional
    public void markStatus(String sessionId, String status, String errorMessage) {
        ConversationSessionEntity entity = findSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new ConversationSessionEntity();
            entity.setSessionId(sessionId);
            entity.setCreatedAt(now);
            entity.setMessageCount(0);
            entity.setWorkflowCount(0);
        }
        entity.setStatus(status);
        entity.setLastError(limitError(errorMessage));
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(now);
        if (Objects.equals(status, STATUS_IDLE) || Objects.equals(status, STATUS_ABORTED) || Objects.equals(status, STATUS_FAILED)) {
            entity.setFinishedAt(now);
        }
        if (entity.getId() == null) {
            conversationSessionMapper.insert(entity);
        } else {
            conversationSessionMapper.updateById(entity);
        }
    }

    @Transactional
    public void replaceSnapshot(String sessionId, String status, List<Map<String, Object>> rawMessages) {
        LocalDateTime now = LocalDateTime.now();
        conversationWorkflowEventMapper.delete(new LambdaQueryWrapper<ConversationWorkflowEventEntity>()
                .eq(ConversationWorkflowEventEntity::getSessionId, sessionId));
        conversationMessageMapper.delete(new LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getSessionId, sessionId));

        int messageSort = 0;
        int eventSort = 0;
        String lastMessageId = null;
        for (Map<String, Object> item : rawMessages) {
            if (item == null) {
                continue;
            }
            Map<String, Object> info = safeMap(item.get("info"));
            List<Object> parts = safeList(item.get("parts"));
            String messageId = firstNonBlank(stringValue(info.get("id")), "msg_" + (messageSort + 1));
            lastMessageId = messageId;

            ConversationMessageEntity messageEntity = new ConversationMessageEntity();
            messageEntity.setSessionId(sessionId);
            messageEntity.setMessageId(messageId);
            messageEntity.setRole(stringValue(info.get("role")));
            messageEntity.setSortNo(messageSort++);
            messageEntity.setInfoJson(writeJson(info));
            messageEntity.setCreatedAt(now);
            messageEntity.setUpdatedAt(now);
            conversationMessageMapper.insert(messageEntity);

            ConversationWorkflowEventEntity messageEvent = new ConversationWorkflowEventEntity();
            messageEvent.setSessionId(sessionId);
            messageEvent.setMessageId(messageId);
            messageEvent.setEventType("message.updated");
            messageEvent.setWorkflowKind("MESSAGE");
            messageEvent.setRole(stringValue(info.get("role")));
            messageEvent.setContentText(null);
            messageEvent.setDetailJson(writeJson(info));
            messageEvent.setSortNo(eventSort++);
            messageEvent.setCreatedAt(now);
            messageEvent.setUpdatedAt(now);
            conversationWorkflowEventMapper.insert(messageEvent);

            for (int i = 0; i < parts.size(); i++) {
                Object partObject = parts.get(i);
                Map<String, Object> part = safeMap(partObject);
                String partId = firstNonBlank(stringValue(part.get("id")), messageId + "#part_" + i);
                ConversationWorkflowEventEntity partEvent = new ConversationWorkflowEventEntity();
                partEvent.setSessionId(sessionId);
                partEvent.setMessageId(messageId);
                partEvent.setPartId(partId);
                partEvent.setEventType("message.part.updated");
                partEvent.setWorkflowKind(resolveWorkflowKind(stringValue(part.get("type"))));
                partEvent.setRole(stringValue(info.get("role")));
                partEvent.setPartType(stringValue(part.get("type")));
                partEvent.setToolName(firstNonBlank(stringValue(part.get("tool")), stringValue(part.get("name"))));
                partEvent.setToolCallId(firstNonBlank(stringValue(part.get("callID")), stringValue(part.get("callId"))));
                partEvent.setToolStatus(resolveToolStatus(part));
                partEvent.setContentText(resolveContent(part));
                partEvent.setDetailJson(writeJson(partObject));
                partEvent.setSortNo(eventSort++);
                partEvent.setCreatedAt(now);
                partEvent.setUpdatedAt(now);
                conversationWorkflowEventMapper.insert(partEvent);
            }
        }

        ConversationWorkflowEventEntity sessionEvent = new ConversationWorkflowEventEntity();
        sessionEvent.setSessionId(sessionId);
        sessionEvent.setEventType(Objects.equals(status, STATUS_IDLE) ? "session.idle" : "session.status");
        sessionEvent.setWorkflowKind("SESSION");
        sessionEvent.setContentText(status);
        sessionEvent.setDetailJson(writeJson(Map.of("status", status)));
        sessionEvent.setSortNo(eventSort++);
        sessionEvent.setCreatedAt(now);
        sessionEvent.setUpdatedAt(now);
        conversationWorkflowEventMapper.insert(sessionEvent);

        ConversationSessionEntity sessionEntity = findSession(sessionId);
        if (sessionEntity == null) {
            sessionEntity = new ConversationSessionEntity();
            sessionEntity.setSessionId(sessionId);
            sessionEntity.setCreatedAt(now);
        }
        sessionEntity.setStatus(status);
        sessionEntity.setLastError(null);
        sessionEntity.setLastMessageId(lastMessageId);
        sessionEntity.setMessageCount(messageSort);
        sessionEntity.setWorkflowCount(eventSort);
        sessionEntity.setLastSyncedAt(now);
        sessionEntity.setUpdatedAt(now);
        if (Objects.equals(status, STATUS_IDLE) || Objects.equals(status, STATUS_ABORTED) || Objects.equals(status, STATUS_FAILED)) {
            sessionEntity.setFinishedAt(now);
        } else {
            sessionEntity.setFinishedAt(null);
        }
        if (sessionEntity.getId() == null) {
            conversationSessionMapper.insert(sessionEntity);
        } else {
            conversationSessionMapper.updateById(sessionEntity);
        }
    }

    public ConversationMessagesResponse listMessages(String sessionId, Integer limit) {
        List<ConversationMessageEntity> allMessages = conversationMessageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ConversationMessageEntity::getSortNo)
        );
        if (allMessages.isEmpty()) {
            return new ConversationMessagesResponse(sessionId, List.of());
        }
        List<ConversationMessageEntity> selectedMessages = tailLimit(allMessages, limit);
        Set<String> messageIds = selectedMessages.stream()
                .map(ConversationMessageEntity::getMessageId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ConversationWorkflowEventEntity> partEvents = conversationWorkflowEventMapper.selectList(
                new LambdaQueryWrapper<ConversationWorkflowEventEntity>()
                        .eq(ConversationWorkflowEventEntity::getSessionId, sessionId)
                        .eq(ConversationWorkflowEventEntity::getEventType, "message.part.updated")
                        .in(!messageIds.isEmpty(), ConversationWorkflowEventEntity::getMessageId, messageIds)
                        .orderByAsc(ConversationWorkflowEventEntity::getSortNo)
        );
        Map<String, List<Object>> partsByMessageId = new LinkedHashMap<>();
        for (ConversationWorkflowEventEntity event : partEvents) {
            partsByMessageId.computeIfAbsent(event.getMessageId(), key -> new ArrayList<>())
                    .add(readJsonObject(event.getDetailJson()));
        }

        List<ConversationMessageResponse> responses = new ArrayList<>(selectedMessages.size());
        for (ConversationMessageEntity message : selectedMessages) {
            responses.add(new ConversationMessageResponse(
                    readJsonMap(message.getInfoJson()),
                    partsByMessageId.getOrDefault(message.getMessageId(), List.of())
            ));
        }
        return new ConversationMessagesResponse(sessionId, responses);
    }

    public ConversationWorkflowResponse listWorkflow(String sessionId, Integer limit) {
        List<ConversationWorkflowEventEntity> events = conversationWorkflowEventMapper.selectList(
                new LambdaQueryWrapper<ConversationWorkflowEventEntity>()
                        .eq(ConversationWorkflowEventEntity::getSessionId, sessionId)
                        .orderByAsc(ConversationWorkflowEventEntity::getSortNo)
        );
        List<ConversationWorkflowEventEntity> selectedEvents = tailLimit(events, limit);
        List<ConversationWorkflowEventResponse> responses = new ArrayList<>(selectedEvents.size());
        for (ConversationWorkflowEventEntity event : selectedEvents) {
            responses.add(new ConversationWorkflowEventResponse(
                    event.getEventType(),
                    event.getWorkflowKind(),
                    event.getRole(),
                    event.getMessageId(),
                    event.getPartId(),
                    event.getPartType(),
                    event.getToolName(),
                    event.getToolCallId(),
                    event.getToolStatus(),
                    event.getContentText(),
                    readJsonObject(event.getDetailJson()),
                    event.getSortNo()
            ));
        }
        return new ConversationWorkflowResponse(sessionId, responses);
    }

    private ConversationSessionEntity findSession(String sessionId) {
        return conversationSessionMapper.selectOne(new LambdaQueryWrapper<ConversationSessionEntity>()
                .eq(ConversationSessionEntity::getSessionId, sessionId)
                .last("limit 1"));
    }

    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private List<Object> safeList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String writeJson(Object value) {
        try {
            return JSONObject.toJSONString(value);
        } catch (JSONException ex) {
            return "{}";
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return JSONObject.parseObject(json);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Object readJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSONObject.parseObject(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveWorkflowKind(String partType) {
        if (!StringUtils.hasText(partType)) {
            return "UNKNOWN";
        }
        return switch (partType) {
            case "text" -> "TEXT";
            case "reasoning" -> "REASONING";
            case "tool" -> "TOOL";
            case "step-start", "step-finish" -> "STEP";
            case "file", "patch", "snapshot" -> "FILE";
            default -> "UNKNOWN";
        };
    }

    private String resolveContent(Map<String, Object> part) {
        if (part.isEmpty()) {
            return null;
        }
        String type = stringValue(part.get("type"));
        if ("text".equals(type) || "reasoning".equals(type)) {
            return stringValue(part.get("text"));
        }
        if ("tool".equals(type)) {
            Object state = part.get("state");
            if (state instanceof Map<?, ?> stateMap) {
                Object output = ((Map<?, ?>) stateMap).get("output");
                if (output != null) {
                    return writeJson(output);
                }
                Object error = ((Map<?, ?>) stateMap).get("error");
                if (error != null) {
                    return writeJson(error);
                }
            }
            Object input = part.get("input");
            return input == null ? null : writeJson(input);
        }
        Object text = part.get("text");
        if (text != null) {
            return String.valueOf(text);
        }
        return writeJson(part);
    }

    private String resolveToolStatus(Map<String, Object> part) {
        Object state = part.get("state");
        if (state instanceof Map<?, ?> stateMap) {
            Object status = ((Map<?, ?>) stateMap).get("status");
            if (status != null) {
                return String.valueOf(status);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String limitError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        String value = errorMessage.trim();
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private <T> List<T> tailLimit(List<T> source, Integer limit) {
        if (source.isEmpty() || limit == null || limit <= 0 || source.size() <= limit) {
            return source;
        }
        return new ArrayList<>(source.subList(source.size() - limit, source.size()));
    }
}
