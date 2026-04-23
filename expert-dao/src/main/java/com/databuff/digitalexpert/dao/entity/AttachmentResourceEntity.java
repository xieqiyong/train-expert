package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_attachment_resource")
public class AttachmentResourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String resourceType;
    private String storagePath;
    private String accessUrl;
    private String usagePrompt;
    private String scope;
    private String status;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
