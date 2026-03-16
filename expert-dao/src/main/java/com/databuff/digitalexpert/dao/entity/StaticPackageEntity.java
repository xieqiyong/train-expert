package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("static_package")
public class StaticPackageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String staticType;
    private String description;
    private String packageName;
    private String packagePath;
    private String checksum;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
