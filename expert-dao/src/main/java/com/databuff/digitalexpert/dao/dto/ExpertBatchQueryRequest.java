package com.databuff.digitalexpert.dao.dto;

import com.databuff.digitalexpert.dao.enums.ExpertType;
import java.util.List;

public record ExpertBatchQueryRequest(
        List<String> names,
        ExpertType expertType

) {
}
