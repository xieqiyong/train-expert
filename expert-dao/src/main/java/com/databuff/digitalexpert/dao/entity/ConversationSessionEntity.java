package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_conversation_session")
public class ConversationSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    private String directory;
    private String workspace;
    @TableField("session_title")
    private String sessionTitle;
    @TableField("provider_id")
    private String providerId;
    @TableField("model_id")
    private String modelId;
    private String agent;
    private String status;
    @TableField("last_error")
    private String lastError;
    @TableField("last_message_id")
    private String lastMessageId;
    @TableField("message_count")
    private Integer messageCount;
    @TableField("workflow_count")
    private Integer workflowCount;
    @TableField("last_synced_at")
    private LocalDateTime lastSyncedAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
