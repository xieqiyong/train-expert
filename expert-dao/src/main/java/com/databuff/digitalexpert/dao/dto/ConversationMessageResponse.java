package com.databuff.digitalexpert.dao.dto;

import java.util.List;
import java.util.Map;

public record ConversationMessageResponse(
        Map<String, Object> info,
        List<Object> parts
) {
}
