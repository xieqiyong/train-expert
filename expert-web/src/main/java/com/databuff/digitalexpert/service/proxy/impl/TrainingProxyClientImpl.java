package com.databuff.digitalexpert.service.proxy.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.proxy.TrainingProxyClient;
import com.xie.opencode.core.SessionManager;
import com.xie.opencode.core.model.CreateSessionRequest;
import com.xie.opencode.core.model.SendMessageRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TrainingProxyClientImpl implements TrainingProxyClient {

    private final SessionManager sessionManager;

    public TrainingProxyClientImpl(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public ProxySubmitResult submitTraining(String taskId,
                                            String prompt,
                                            List<String> filePaths,
                                            String outputDir) {
        if (!StringUtils.hasText(prompt)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "Training prompt must not be blank"
            );
        }
        try {
            String sessionId = sessionManager.createSession(new CreateSessionRequest(taskId));
            if (!StringUtils.hasText(sessionId)) {
                throw BusinessException.badRequest(
                        ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                        "OpenCode did not return a sessionId"
                );
            }
            sessionManager.sendMessageAsync(sessionId, SendMessageRequest.text(prompt));
            return new ProxySubmitResult(sessionId, sessionId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to submit training task to OpenCode, taskId={}", taskId, ex);
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "Failed to submit training task to OpenCode: " + ex.getMessage()
            );
        }
    }

    @Override
    public boolean isSessionFinished(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "SessionId must not be blank"
            );
        }
        try {
            return sessionManager.isSessionFinished(sessionId);
        } catch (Exception ex) {
            log.error("Failed to query OpenCode session status, sessionId={}", sessionId, ex);
            throw BusinessException.badRequest(
                    ErrorCode.TRAINING_PROXY_REQUEST_FAILED,
                    "Failed to query OpenCode session status: " + ex.getMessage()
            );
        }
    }
}