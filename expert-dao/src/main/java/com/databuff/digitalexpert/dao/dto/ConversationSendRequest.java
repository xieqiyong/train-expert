package com.databuff.digitalexpert.dao.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ConversationSendRequest(
        String sessionId,
        @NotBlank String message,
        List<String> filePaths,
        String directory,
        String workspace,
        String providerId,
        String modelId,
        String agent,
        String system,
        String variant
) {
}
