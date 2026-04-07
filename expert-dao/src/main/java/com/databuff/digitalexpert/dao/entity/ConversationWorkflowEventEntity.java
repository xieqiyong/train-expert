package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_conversation_workflow_event")
public class ConversationWorkflowEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    @TableField("message_id")
    private String messageId;
    @TableField("part_id")
    private String partId;
    @TableField("event_type")
    private String eventType;
    @TableField("workflow_kind")
    private String workflowKind;
    private String role;
    @TableField("part_type")
    private String partType;
    @TableField("tool_name")
    private String toolName;
    @TableField("tool_call_id")
    private String toolCallId;
    @TableField("tool_status")
    private String toolStatus;
    @TableField("content_text")
    private String contentText;
    @TableField("detail_json")
    private String detailJson;
    @TableField("sort_no")
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
