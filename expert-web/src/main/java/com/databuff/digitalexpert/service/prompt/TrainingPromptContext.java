package com.databuff.digitalexpert.service.prompt;

import com.databuff.digitalexpert.dao.dto.TrainingSourceRequest;
import com.databuff.digitalexpert.dao.enums.TrainingSourceRole;
import java.nio.file.Path;
import java.util.List;
import org.springframework.util.StringUtils;

public record TrainingPromptContext(
        String trainingGoal,
        List<TrainingSourceRequest> sources,
        String skillDirName,
        Path skillRootDirectory,
        Path versionDirectory,
        Path specSkillPath,
        Path appInfoInputPath
) {

    public Path staticPackageDirectory() {
        return versionDirectory.resolve("static_package");
    }

    public boolean hasAppInfoSource() {
        return appInfoInputPath != null;
    }

    public List<TrainingSourceRequest> trainingSources() {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .filter(source -> !isAttachmentSource(source))
                .toList();
    }

    public List<TrainingSourceRequest> attachmentSources() {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .filter(this::isAttachmentSource)
                .toList();
    }

    public void appendAttachmentResources(StringBuilder builder) {
        List<TrainingSourceRequest> attachments = attachmentSources();
        if (attachments.isEmpty()) {
            return;
        }
        builder.append("附件资源:\n");
        for (int i = 0; i < attachments.size(); i++) {
            TrainingSourceRequest attachment = attachments.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(resolveAttachmentName(attachment))
                    .append(" [")
                    .append(attachment.sourceType())
                    .append("] ")
                    .append(attachment.sourceValue());
            if (StringUtils.hasText(attachment.accessUrl())) {
                builder.append("，访问地址: ").append(attachment.accessUrl().trim());
            }
            if (StringUtils.hasText(attachment.usagePrompt())) {
                builder.append("，使用说明: ").append(attachment.usagePrompt().trim());
            }
            builder.append("\n");
        }
        builder.append("请将附件资源作为补充上下文使用，不要替代主输入源。\n");
    }

    private boolean isAttachmentSource(TrainingSourceRequest source) {
        return source != null
                && StringUtils.hasText(source.sourceRole())
                && TrainingSourceRole.ATTACHMENT.name().equalsIgnoreCase(source.sourceRole().trim());
    }

    private String resolveAttachmentName(TrainingSourceRequest attachment) {
        if (StringUtils.hasText(attachment.sourceName())) {
            return attachment.sourceName().trim();
        }
        if (attachment.attachmentId() != null) {
            return "附件 " + attachment.attachmentId();
        }
        return "附件";
    }
}
