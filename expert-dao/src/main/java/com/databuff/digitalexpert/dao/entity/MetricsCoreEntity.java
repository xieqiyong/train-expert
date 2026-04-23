package com.databuff.digitalexpert.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dc_databuff.dc_databuff_metrics_core")
public class MetricsCoreEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String type1;
    private String type2;
    private String type3;
    private String app;
    @TableField("`database`")
    private String databaseName;
    private String measurement;
    @TableField("`desc`")
    private String descText;
    @TableField("tagKey")
    private String tagKey;
    @TableField("tagValue")
    private String tagValue;
    @TableField("`fields`")
    private String fields;
    @TableField("isOpen")
    private Integer isOpen;
    @TableField("metric_type")
    private String metricType;
    @TableField("metric_source")
    private String metricSource;
    private Integer builtin;
}
