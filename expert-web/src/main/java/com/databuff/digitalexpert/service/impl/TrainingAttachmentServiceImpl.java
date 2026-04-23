package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.dao.bo.TrainingAttachmentContext;
import com.databuff.digitalexpert.dao.dto.TrainingAttachmentRef;
import com.databuff.digitalexpert.service.TrainingAttachmentProvider;
import com.databuff.digitalexpert.service.TrainingAttachmentService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrainingAttachmentServiceImpl implements TrainingAttachmentService {

    @Autowired(required = false)
    private List<TrainingAttachmentProvider> providers = List.of();

    @Override
    public List<TrainingAttachmentRef> resolve(TrainingAttachmentContext context) {
        if (context == null || providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<TrainingAttachmentRef> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (TrainingAttachmentProvider provider : providers) {
            if (provider == null || !provider.supports(context)) {
                continue;
            }
            List<TrainingAttachmentRef> resolved = provider.resolve(context);
            if (resolved == null || resolved.isEmpty()) {
                continue;
            }
            for (TrainingAttachmentRef attachment : resolved) {
                if (attachment == null) {
                    continue;
                }
                String key = attachment.id() != null
                        ? "id:" + attachment.id()
                        : "path:" + attachment.storagePath();
                if (keys.add(key)) {
                    result.add(attachment);
                }
            }
        }
        if (!result.isEmpty()) {
            log.info("训练附件资源已解析, mode={}, count={}", context.mode(), result.size());
        }
        return List.copyOf(result);
    }
}
