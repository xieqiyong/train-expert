package com.databuff.digitalexpert.dao.dto;

public record TrainingAttachmentRef(
        Long id,
        String name,
        String description,
        String resourceType,
        String storagePath,
        String accessUrl,
        String usagePrompt
) {
}
