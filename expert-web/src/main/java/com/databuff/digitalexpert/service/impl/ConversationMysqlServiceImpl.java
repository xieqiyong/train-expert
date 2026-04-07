package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.dto.ConversationAbortResponse;
import com.databuff.digitalexpert.dao.dto.ConversationFinishedResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesRequest;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSendResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSessionRequest;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowRequest;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowResponse;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.ConversationService;
import com.databuff.digitalexpert.service.conversation.ConversationOpencodeClient;
import com.databuff.digitalexpert.service.conversation.ConversationSessionContext;
import com.databuff.digitalexpert.service.conversation.ConversationStorageService;
import com.databuff.digitalexpert.service.conversation.ConversationSyncService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Primary
@Service
public class ConversationMysqlServiceImpl implements ConversationService {

    private final ConversationOpencodeClient conversationOpencodeClient;
    private final ConversationStorageService conversationStorageService;
    private final ConversationSyncService conversationSyncService;

    public ConversationMysqlServiceImpl(
            ConversationOpencodeClient conversationOpencodeClient,
            ConversationStorageService conversationStorageService,
            ConversationSyncService conversationSyncService
    ) {
        this.conversationOpencodeClient = conversationOpencodeClient;
        this.conversationStorageService = conversationStorageService;
        this.conversationSyncService = conversationSyncService;
    }

    @Override
    public ConversationSendResponse sendMessage(ConversationSendRequest request) {
        try {
            validateModel(request);
            String sessionId = resolveSessionId(request);
            String sessionTitle = buildSessionTitle(request.message());
            conversationStorageService.saveAcceptedRequest(sessionId, request, sessionTitle);
            conversationOpencodeClient.sendPromptAsync(
                    sessionId,
                    request.directory(),
                    request.workspace(),
                    request.message(),
                    request.filePaths(),
                    request.providerId(),
                    request.modelId(),
                    request.agent(),
                    request.system(),
                    request.variant()
            );
            conversationSyncService.scheduleSync(sessionId, request.directory(), request.workspace());
            return new ConversationSendResponse(sessionId, null, Boolean.TRUE);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "send conversation message failed: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationMessagesResponse listMessages(ConversationMessagesRequest request) {
        try {
            ConversationSessionContext context = conversationStorageService.resolveContext(
                    request.sessionId(),
                    request.directory(),
                    request.workspace()
            );
            ConversationMessagesResponse response = conversationStorageService.listMessages(request.sessionId(), request.limit());
            if (response.messages().isEmpty()) {
                conversationSyncService.syncOnce(context.sessionId(), context.directory(), context.workspace());
                response = conversationStorageService.listMessages(request.sessionId(), request.limit());
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "list conversation messages failed: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationWorkflowResponse listWorkflow(ConversationWorkflowRequest request) {
        try {
            ConversationWorkflowResponse response = conversationStorageService.listWorkflow(
                    request.sessionId(),
                    request.limit()
            );
            if (response.events().isEmpty()) {
                ConversationSessionContext context = conversationStorageService.resolveContext(request.sessionId(), null, null);
                conversationSyncService.syncOnce(context.sessionId(), context.directory(), context.workspace());
                response = conversationStorageService.listWorkflow(request.sessionId(), request.limit());
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "list conversation workflow failed: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationFinishedResponse checkFinished(ConversationSessionRequest request) {
        try {
            String storedStatus = conversationStorageService.getSessionStatus(request.sessionId());
            boolean finished = ConversationStorageService.STATUS_IDLE.equals(storedStatus)
                    || ConversationStorageService.STATUS_ABORTED.equals(storedStatus)
                    || ConversationStorageService.STATUS_FAILED.equals(storedStatus);
            if (!finished) {
                ConversationSessionContext context = conversationStorageService.resolveContext(
                        request.sessionId(),
                        request.directory(),
                        request.workspace()
                );
                conversationSyncService.scheduleSync(context.sessionId(), context.directory(), context.workspace());
            }
            return new ConversationFinishedResponse(request.sessionId(), finished);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "check conversation finished failed: " + ex.getMessage()
            );
        }
    }

    @Override
    public ConversationAbortResponse abortConversation(ConversationSessionRequest request) {
        try {
            ConversationSessionContext context = conversationStorageService.resolveContext(
                    request.sessionId(),
                    request.directory(),
                    request.workspace()
            );
            conversationOpencodeClient.abortSession(
                    context.sessionId(),
                    context.directory(),
                    context.workspace()
            );
            conversationStorageService.markStatus(request.sessionId(), ConversationStorageService.STATUS_ABORTED, null);
            conversationSyncService.scheduleSync(context.sessionId(), context.directory(), context.workspace());
            return new ConversationAbortResponse(request.sessionId(), Boolean.TRUE);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(
                    ErrorCode.CONVERSATION_PROXY_REQUEST_FAILED,
                    "abort conversation failed: " + ex.getMessage()
            );
        }
    }

    private void validateModel(ConversationSendRequest request) {
        boolean hasProviderId = StringUtils.hasText(request.providerId());
        boolean hasModelId = StringUtils.hasText(request.modelId());
        if (hasProviderId != hasModelId) {
            throw BusinessException.badRequest(
                    ErrorCode.INVALID_REQUEST,
                    "providerId and modelId must be provided together"
            );
        }
    }

    private String resolveSessionId(ConversationSendRequest request) {
        if (StringUtils.hasText(request.sessionId())) {
            return request.sessionId().trim();
        }
        return conversationOpencodeClient.createSession(
                buildSessionTitle(request.message()),
                request.directory(),
                request.workspace()
        );
    }

    private String buildSessionTitle(String message) {
        String title = message.trim().replaceAll("\\s+", " ");
        if (title.length() <= 32) {
            return title;
        }
        return title.substring(0, 32);
    }
}
