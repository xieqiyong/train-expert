package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_conversation_message")
public class ConversationMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private String sessionId;
    @TableField("message_id")
    private String messageId;
    private String role;
    @TableField("sort_no")
    private Integer sortNo;
    @TableField("info_json")
    private String infoJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
