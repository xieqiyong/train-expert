CREATE TABLE IF NOT EXISTS `de_conversation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `session_id` varchar(128) NOT NULL COMMENT 'opencode session id',
  `directory` text COMMENT 'conversation directory context',
  `workspace` text COMMENT 'conversation workspace context',
  `session_title` varchar(128) DEFAULT NULL COMMENT 'session title',
  `provider_id` varchar(128) DEFAULT NULL COMMENT 'llm provider id',
  `model_id` varchar(128) DEFAULT NULL COMMENT 'llm model id',
  `agent` varchar(128) DEFAULT NULL COMMENT 'agent name',
  `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/IDLE/ABORTED/FAILED',
  `last_error` text COMMENT 'last sync error',
  `last_message_id` varchar(128) DEFAULT NULL COMMENT 'latest opencode message id',
  `message_count` int NOT NULL DEFAULT '0' COMMENT 'latest snapshot message count',
  `workflow_count` int NOT NULL DEFAULT '0' COMMENT 'latest snapshot workflow event count',
  `last_synced_at` timestamp NULL DEFAULT NULL COMMENT 'latest sync time',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT 'finish time',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_de_conversation_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='conversation session snapshot';

CREATE TABLE IF NOT EXISTS `de_conversation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `session_id` varchar(128) NOT NULL COMMENT 'opencode session id',
  `message_id` varchar(128) NOT NULL COMMENT 'opencode message id',
  `role` varchar(32) DEFAULT NULL COMMENT 'message role',
  `sort_no` int NOT NULL COMMENT 'message order in snapshot',
  `info_json` json NOT NULL COMMENT 'raw message info json',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_de_conversation_message` (`session_id`,`message_id`),
  KEY `idx_de_conversation_message_sort` (`session_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='conversation message snapshot';

CREATE TABLE IF NOT EXISTS `de_conversation_workflow_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `session_id` varchar(128) NOT NULL COMMENT 'opencode session id',
  `message_id` varchar(128) DEFAULT NULL COMMENT 'opencode message id',
  `part_id` varchar(128) DEFAULT NULL COMMENT 'opencode part id',
  `event_type` varchar(64) NOT NULL COMMENT 'message.updated/message.part.updated/session.idle',
  `workflow_kind` varchar(32) NOT NULL COMMENT 'MESSAGE/TEXT/REASONING/TOOL/SESSION',
  `role` varchar(32) DEFAULT NULL COMMENT 'message role',
  `part_type` varchar(64) DEFAULT NULL COMMENT 'part type',
  `tool_name` varchar(128) DEFAULT NULL COMMENT 'tool name',
  `tool_call_id` varchar(128) DEFAULT NULL COMMENT 'tool call id',
  `tool_status` varchar(64) DEFAULT NULL COMMENT 'tool status',
  `content_text` mediumtext COMMENT 'normalized content',
  `detail_json` json DEFAULT NULL COMMENT 'raw part or session detail json',
  `sort_no` int NOT NULL COMMENT 'workflow order in snapshot',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  KEY `idx_de_conversation_workflow_session_sort` (`session_id`,`sort_no`),
  KEY `idx_de_conversation_workflow_message` (`session_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='conversation workflow snapshot';
