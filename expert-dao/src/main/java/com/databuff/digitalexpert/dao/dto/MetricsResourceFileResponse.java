package com.databuff.digitalexpert.dao.dto;

public record MetricsResourceFileResponse(
        String type1,
        String fileName,
        String storagePath,
        Long attachmentResourceId,
        Integer itemCount
) {
}
