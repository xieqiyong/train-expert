package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.ConversationAbortResponse;
import com.databuff.digitalexpert.dao.dto.ConversationFinishedResponse;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesRequest;
import com.databuff.digitalexpert.dao.dto.ConversationMessagesResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSendRequest;
import com.databuff.digitalexpert.dao.dto.ConversationSendResponse;
import com.databuff.digitalexpert.dao.dto.ConversationSessionRequest;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/messages/send")
    public ApiResponse<ConversationSendResponse> sendMessage(@Valid @RequestBody ConversationSendRequest request) {
        return ApiResponse.success(conversationService.sendMessage(request));
    }

    @PostMapping("/messages/list")
    public ApiResponse<ConversationMessagesResponse> listMessages(
            @Valid @RequestBody ConversationMessagesRequest request) {
        return ApiResponse.success(conversationService.listMessages(request));
    }

    @PostMapping("/finished")
    public ApiResponse<ConversationFinishedResponse> checkFinished(
            @Valid @RequestBody ConversationSessionRequest request) {
        return ApiResponse.success(conversationService.checkFinished(request));
    }

    @PostMapping("/abort")
    public ApiResponse<ConversationAbortResponse> abortConversation(
            @Valid @RequestBody ConversationSessionRequest request) {
        return ApiResponse.success(conversationService.abortConversation(request));
    }
}
