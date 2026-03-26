package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.xie.opencode.core.OpenCodeChatService;
import com.xie.opencode.core.chat.ChatAuthCommand;
import com.xie.opencode.core.chat.ChatAuthRemovalView;
import com.xie.opencode.core.chat.ChatAuthView;
import com.xie.opencode.core.chat.ChatBootstrapView;
import com.xie.opencode.core.chat.ChatConversationCommand;
import com.xie.opencode.core.chat.ChatConversationCreateCommand;
import com.xie.opencode.core.chat.ChatConversationRenameCommand;
import com.xie.opencode.core.chat.ChatConversationSummaryView;
import com.xie.opencode.core.chat.ChatConversationView;
import com.xie.opencode.core.chat.ChatMessageAsyncView;
import com.xie.opencode.core.chat.ChatMessageCommand;
import com.xie.opencode.core.chat.ChatPreferencesCommand;
import com.xie.opencode.core.chat.ChatProviderCommand;
import com.xie.opencode.core.model.OpenCodeEvent;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class OpenCodeProxyController {

    @Autowired
    private OpenCodeChatService openCodeChatService;

    @PostMapping("/bootstrap")
    public ApiResponse<ChatBootstrapView> bootstrap() {
        ChatBootstrapView  result = this.openCodeChatService.bootstrap();
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/list")
    public ApiResponse<List<ChatConversationSummaryView>> listConversations() {
        List<ChatConversationSummaryView> result = this.openCodeChatService.listConversations();
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/create")
    public ApiResponse<ChatConversationView> createConversation(@RequestBody(required = false) ChatConversationCreateCommand command) {
        ChatConversationView result = this.openCodeChatService.createConversation(command);
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/rename")
    public ApiResponse<ChatConversationSummaryView> renameConversation(@RequestBody ChatConversationRenameCommand command) {
        ChatConversationSummaryView result = this.openCodeChatService.renameConversation(command);
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/get")
    public ApiResponse<ChatConversationView> getConversation(@RequestBody ChatConversationCommand command) {
        ChatConversationView result = this.openCodeChatService.getConversation(requireConversationId(command));
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/poll")
    public ApiResponse<ChatConversationView> pollConversation(@RequestBody ChatConversationCommand command) {
        ChatConversationView result = this.openCodeChatService.pollConversation(requireConversationId(command));
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/delete")
    public ApiResponse<Void> deleteConversation(@RequestBody ChatConversationCommand command) {
        this.openCodeChatService.deleteConversation(requireConversationId(command));
        return ApiResponse.success(null);
    }

    @PostMapping("/conversations/messages/submit")
    public ResponseEntity<ApiResponse<ChatMessageAsyncView>> submitMessage(@RequestBody ChatMessageCommand command) {
        ChatMessageAsyncView result = this.openCodeChatService.submitMessage(requireConversationId(command), command);
        return ResponseEntity.accepted().body(ApiResponse.success(result));
    }

    @PostMapping("/conversations/messages/sync")
    public ApiResponse<ChatConversationView> sendMessageSync(@RequestBody ChatMessageCommand command) {
        ChatConversationView result = this.openCodeChatService.sendMessage(requireConversationId(command), command);
        return ApiResponse.success(result);
    }

    @PostMapping("/conversations/abort")
    public ApiResponse<Void> abortConversation(@RequestBody ChatConversationCommand command) {
        this.openCodeChatService.abortConversation(requireConversationId(command));
        return ApiResponse.success(null);
    }

    @GetMapping(value = "/conversations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamConversation(@RequestParam(name = "conversationId", required = false) String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        final SseEmitter emitter = new SseEmitter(0L);
        final Disposable disposable = this.openCodeChatService.streamConversation(conversationId.trim()).subscribe(
            event -> sendEvent(emitter, event),
            emitter::completeWithError,
            emitter::complete
        );
        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        emitter.onError(error -> disposable.dispose());
        return emitter;
    }

    @PostMapping("/settings/auth/save")
    public ApiResponse<ChatAuthView> configureAuth(@RequestBody ChatAuthCommand command) {
        ChatAuthView result = this.openCodeChatService.configureAuth(command);
        return ApiResponse.success(result);
    }

    @PostMapping("/settings/auth/remove")
    public ApiResponse<ChatAuthRemovalView> removeAuth(@RequestBody ChatProviderCommand command) {
        ChatAuthRemovalView result = this.openCodeChatService.removeAuth(requireProviderId(command));
        return ApiResponse.success(result);
    }

    @PostMapping("/settings/preferences/save")
    public ApiResponse<ChatBootstrapView> updatePreferences(@RequestBody(required = false) ChatPreferencesCommand command) {
        ChatBootstrapView result = this.openCodeChatService.updatePreferences(command);
        return ApiResponse.success(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadGateway(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(502, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, ex.getMessage()));
    }

    private void sendEvent(SseEmitter emitter, OpenCodeEvent event) {
        try {
            String eventName = event.getType() == null ? "message" : event.getType();
            emitter.send(SseEmitter.event().name(eventName).data(event));
        }
        catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to push OpenCode SSE event", ex);
        }
    }

    private String requireConversationId(ChatConversationCommand command) {
        if (command == null || command.getConversationId() == null || command.getConversationId().trim().length() == 0) {
            throw new IllegalArgumentException("conversationId is required");
        }
        return command.getConversationId().trim();
    }

    private String requireConversationId(ChatMessageCommand command) {
        if (command == null || command.getConversationId() == null || command.getConversationId().trim().length() == 0) {
            throw new IllegalArgumentException("conversationId is required");
        }
        return command.getConversationId().trim();
    }

    private String requireProviderId(ChatProviderCommand command) {
        if (command == null || command.getProviderId() == null || command.getProviderId().trim().length() == 0) {
            throw new IllegalArgumentException("providerId is required");
        }
        return command.getProviderId().trim();
    }
}
