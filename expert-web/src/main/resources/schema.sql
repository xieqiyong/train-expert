-- expert_databuff.digital_expert 表定义

CREATE TABLE IF NOT EXISTS `digital_expert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '专家名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专家描述',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '专家状态：DRAFT/TRAINING/STARTED/DISABLED',
  `shared_path` text COLLATE utf8mb4_unicode_ci COMMENT '共享目录路径',
  `config_json_path` text COLLATE utf8mb4_unicode_ci COMMENT '专家配置文件路径',
  `zip_package_path` text COLLATE utf8mb4_unicode_ci COMMENT '专家压缩包路径',
  `last_release_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次发布任务ID',
  `release_version` int unsigned NOT NULL DEFAULT '0' COMMENT '发布版本号',
  `last_training_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次训练任务ID',
  `training_version` int unsigned NOT NULL DEFAULT '0' COMMENT '训练版本号',
  `last_training_session_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次训练会话ID',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '最近一次启动时间',
  `disabled_at` timestamp NULL DEFAULT NULL COMMENT '最近一次禁用时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_digital_expert_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字员工';

-- expert_databuff.expert_mcp_binding 表定义

CREATE TABLE IF NOT EXISTS `expert_mcp_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `expert_id` bigint NOT NULL COMMENT '专家ID',
  `binding_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑定名称',
  `mcp_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MCP服务地址',
  `tool_whitelist_json` json NOT NULL COMMENT '工具白名单JSON',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expert_mcp_binding` (`expert_id`,`binding_name`),
  KEY `idx_expert_mcp_binding_expert_id` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家MCP绑定';

-- expert_databuff.expert_release_task 表定义

CREATE TABLE IF NOT EXISTS `expert_release_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务唯一ID',
  `expert_id` bigint NOT NULL COMMENT '专家ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCEEDED/FAILED',
  `trigger_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发方式',
  `staging_path` text COLLATE utf8mb4_unicode_ci COMMENT '发布暂存目录',
  `config_json_path` text COLLATE utf8mb4_unicode_ci COMMENT '生成的配置文件路径',
  `zip_package_path` text COLLATE utf8mb4_unicode_ci COMMENT '生成的压缩包路径',
  `failure_reason` text COLLATE utf8mb4_unicode_ci COMMENT '失败原因',
  `requested_at` timestamp NOT NULL COMMENT '任务提交时间',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '任务开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '任务结束时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_task_id` (`task_id`),
  KEY `idx_release_task_expert_id` (`expert_id`),
  KEY `idx_release_task_status` (`status`),
  KEY `idx_release_task_expert_status` (`expert_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家发布任务';

-- expert_databuff.expert_training_task 表定义

CREATE TABLE IF NOT EXISTS `expert_training_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务唯一ID',
  `expert_id` bigint NOT NULL COMMENT '专家ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态：PENDING/RUNNING/VERIFYING_ARTIFACTS/IMPORTING_SKILLS/RELEASING/SUCCEEDED/FAILED/TIMEOUT',
  `previous_expert_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练前专家状态',
  `session_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上游会话ID',
  `submit_request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上游请求ID',
  `source_manifest_json` json DEFAULT NULL COMMENT '训练来源清单JSON',
  `output_dir` text COLLATE utf8mb4_unicode_ci COMMENT '训练输出目录',
  `manifest_path` text COLLATE utf8mb4_unicode_ci COMMENT '训练产物清单JSON',
  `release_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联发布任务ID',
  `poll_count` int NOT NULL DEFAULT '0' COMMENT '轮询次数',
  `artifact_verified` tinyint NOT NULL DEFAULT '0' COMMENT '产物是否已校验',
  `failure_reason` text COLLATE utf8mb4_unicode_ci COMMENT '失败原因',
  `requested_at` timestamp NOT NULL COMMENT '任务提交时间',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '任务开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '任务结束时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_task_id` (`task_id`),
  KEY `idx_training_task_expert_id` (`expert_id`),
  KEY `idx_training_task_status` (`status`),
  KEY `idx_training_task_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家训练任务';

-- expert_databuff.expert_skill_binding 表定义

CREATE TABLE IF NOT EXISTS `expert_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `expert_id` bigint NOT NULL COMMENT '专家ID',
  `skill_id` bigint NOT NULL COMMENT '技能包ID',
  `sort_no` int DEFAULT NULL COMMENT '排序号',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expert_skill` (`expert_id`,`skill_id`),
  KEY `idx_expert_skill_expert_id` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家技能绑定';

-- expert_databuff.expert_static_package_binding 表定义

CREATE TABLE IF NOT EXISTS `expert_static_package_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `expert_id` bigint NOT NULL COMMENT '专家ID',
  `static_package_id` bigint NOT NULL COMMENT '静态资源包ID',
  `sort_no` int DEFAULT NULL COMMENT '排序号',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expert_static_package` (`expert_id`,`static_package_id`),
  KEY `idx_expert_static_pkg_expert_id` (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家静态资源包绑定';

-- expert_databuff.skill_package 表定义

CREATE TABLE IF NOT EXISTS `skill_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能描述',
  `package_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '包文件名',
  `package_path` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '包存储路径',
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '校验和',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：ACTIVE/DISABLED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能包';

-- expert_databuff.static_package 表定义

CREATE TABLE IF NOT EXISTS `static_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '静态资源包名称',
  `static_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '静态资源包类型',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '静态资源包描述',
  `package_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '包文件名',
  `package_path` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '包存储路径',
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '校验和',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：ACTIVE/DISABLED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='静态资源包';

ALTER TABLE digital_expert ADD COLUMN prompt TEXT COMMENT '专家提示词';