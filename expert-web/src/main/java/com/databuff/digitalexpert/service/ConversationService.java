package com.databuff.digitalexpert.service;

import com.databuff.digitalexpert.dao.dto.ConversationAbortResponse;
import com.databuff.digitalexpert.dao.dto.ConversationFinishedResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesRequest;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSendResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSessionRequest;

public interface ConversationService {

    ConversationSendResponse sendMessage(ConversationSendRequest request);

    ConversationMessagesResponse listMessages(ConversationMessagesRequest request);

    ConversationFinishedResponse checkFinished(ConversationSessionRequest request);

    ConversationAbortResponse abortConversation(ConversationSessionRequest request);
}
