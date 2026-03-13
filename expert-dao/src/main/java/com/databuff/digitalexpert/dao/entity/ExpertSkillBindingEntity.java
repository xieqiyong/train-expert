package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("expert_skill_binding")
public class ExpertSkillBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long expertId;
    private Long skillId;
    private Integer sortNo;
    private LocalDateTime createdAt;
}

