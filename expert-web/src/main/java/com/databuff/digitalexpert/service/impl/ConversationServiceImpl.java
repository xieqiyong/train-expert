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
import com.hz.liusu.opencode.proxy.ConversationClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationClient conversationClient;

    @Override
    public ConversationSendResponse sendMessage(ConversationSendRequest request) {
        try {
            ConversationClient.ConversationSubmitResult result = conversationClient.sendMessage(
                    new ConversationClient.ConversationRequest(
                            request.sessionId(),
                            request.message(),
                            request.filePaths(),
                            request.directory(),
                            request.workspace(),
                            request.providerId(),
                            request.modelId(),
                            request.agent(),
                            request.system(),
                            request.variant()
                    )
            );
            return new ConversationSendResponse(result.sessionId(), result.messageId(), Boolean.TRUE);
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
            List<Map<String, Object>> rawMessages = conversationClient.listMessages(
                    request.sessionId(),
                    request.limit(),
                    request.directory(),
                    request.workspace()
            );
            List<ConversationMessageResponse> messages = new ArrayList<>();
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
            boolean finished = conversationClient.isSessionFinished(
                    request.sessionId(),
                    request.directory(),
                    request.workspace()
            );
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
            conversationClient.abortConversation(
                    request.sessionId(),
                    request.directory(),
                    request.workspace()
            );
            return new ConversationAbortResponse(request.sessionId(), Boolean.TRUE);
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "中止会话失败: " + ex.getMessage()
            );
        }
    }
}
