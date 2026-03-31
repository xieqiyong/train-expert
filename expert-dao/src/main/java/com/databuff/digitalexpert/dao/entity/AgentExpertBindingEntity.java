package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_agent_expert_binding")
public class AgentExpertBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private Long expertId;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
