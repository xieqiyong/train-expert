package com.databuff.digitalexpert.dao.dto;

public record AttachmentResourceResponse(
        Long id,
        String name,
        String description,
        String resourceType,
        String storagePath,
        String accessUrl,
        String usagePrompt,
        String scope,
        String status,
        Integer sortNo
) {
}
