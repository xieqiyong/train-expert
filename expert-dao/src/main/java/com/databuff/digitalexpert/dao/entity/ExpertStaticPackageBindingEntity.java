package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("de_expert_static_package_binding")
public class ExpertStaticPackageBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long expertId;
    private Long staticPackageId;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
