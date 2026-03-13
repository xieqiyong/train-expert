package com.databuff.digitalexpert.dao.dto;

import java.util.List;

public record ExpertConfigMcpResponse(
        String bindingName,
        String mcpUrl,
        List<String> toolWhitelist
) {
}

