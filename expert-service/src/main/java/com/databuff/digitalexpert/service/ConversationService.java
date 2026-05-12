package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ConversationAbortResponse;
import com.databuff.digitalexpert.dao.dto.ConversationFinishedResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesRequest;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSendResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSessionRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSessionsRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSessionsResponse;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowRequest;
import com.databuff.digitalexpert.dao.dto.ConversationWorkflowResponse;
import java.util.List;

public interface ConversationService {

    ConversationSendResponse sendMessage(ConversationSendRequest request);

    ConversationMessagesResponse listMessages(ConversationMessagesRequest request);

    default ConversationSessionsResponse listSessions(ConversationSessionsRequest request) {
        return new ConversationSessionsResponse(List.of());
    }

    default ConversationWorkflowResponse listWorkflow(ConversationWorkflowRequest request) {
        return new ConversationWorkflowResponse(request.sessionId(), List.of());
    }

    ConversationFinishedResponse checkFinished(ConversationSessionRequest request);

    ConversationAbortResponse abortConversation(ConversationSessionRequest request);
}
