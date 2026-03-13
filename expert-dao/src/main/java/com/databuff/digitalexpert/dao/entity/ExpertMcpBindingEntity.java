package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("expert_mcp_binding")
public class ExpertMcpBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long expertId;
    private String bindingName;
    private String mcpUrl;
    private String toolWhitelistJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

