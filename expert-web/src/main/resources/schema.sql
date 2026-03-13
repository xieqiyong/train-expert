CREATE TABLE IF NOT EXISTS skill_package (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键',
    name            VARCHAR(128)    NOT NULL                        COMMENT '技能名称',
    description     VARCHAR(512)    NOT NULL                        COMMENT '技能描述',
    package_name    VARCHAR(128)    NOT NULL                        COMMENT '包名',
    package_path    TEXT            NOT NULL                        COMMENT '包存储路径',
    checksum        VARCHAR(64)     NULL                            COMMENT '包校验和',
    status          VARCHAR(32)     NOT NULL                        COMMENT '状态: ACTIVE/DISABLED',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能包';

CREATE TABLE IF NOT EXISTS static_package (
    id                BIGINT        NOT NULL AUTO_INCREMENT         COMMENT '主键',
    name              VARCHAR(128)  NOT NULL                        COMMENT '包名称',
    description       VARCHAR(512)  NULL                            COMMENT '包描述',
    package_name      VARCHAR(128)  NOT NULL                        COMMENT '包文件名',
    package_path      TEXT          NOT NULL                        COMMENT '包存储路径',
    checksum          VARCHAR(64)   NULL                            COMMENT '包校验和',
    status            VARCHAR(32)   NOT NULL                        COMMENT '状态: ACTIVE/DISABLED',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='静态资源包';

CREATE TABLE IF NOT EXISTS digital_expert (
    id                   BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键',
    name                 VARCHAR(128)    NOT NULL                   COMMENT '专家名称',
    description          VARCHAR(512)    NULL                       COMMENT '专家描述',
    status               VARCHAR(32)     NOT NULL                   COMMENT '状态: ACTIVE/DISABLED/RELEASING',
    shared_path          TEXT            NULL                       COMMENT '共享目录路径',
    config_json_path     TEXT            NULL                       COMMENT '配置文件路径',
    zip_package_path     TEXT            NULL                       COMMENT '发布包路径',
    last_release_task_id VARCHAR(64)     NULL                       COMMENT '最近一次发布任务ID',
    release_version      INT UNSIGNED    NOT NULL DEFAULT 0         COMMENT '发布版本号，单调递增',
    started_at           TIMESTAMP       NULL                       COMMENT '最近启用时间',
    disabled_at          TIMESTAMP       NULL                       COMMENT '最近禁用时间',
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字专家';

CREATE TABLE IF NOT EXISTS expert_skill_binding (
    id         BIGINT      NOT NULL AUTO_INCREMENT   COMMENT '主键',
    expert_id  BIGINT      NOT NULL                  COMMENT '专家ID',
    skill_id   BIGINT      NOT NULL                  COMMENT '技能ID',
    sort_no    INT         NULL                      COMMENT '排序号',
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_expert_skill UNIQUE (expert_id, skill_id),
    INDEX idx_expert_skill_expert_id (expert_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家技能绑定';

CREATE TABLE IF NOT EXISTS expert_static_package_binding (
    id                BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    expert_id         BIGINT      NOT NULL                 COMMENT '专家ID',
    static_package_id BIGINT      NOT NULL                 COMMENT '静态包ID',
    sort_no           INT         NULL                     COMMENT '排序号',
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_expert_static_package UNIQUE (expert_id, static_package_id),
    INDEX idx_expert_static_pkg_expert_id (expert_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家静态包绑定';

CREATE TABLE IF NOT EXISTS expert_mcp_binding (
    id                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    expert_id          BIGINT      NOT NULL                  COMMENT '专家ID',
    binding_name       VARCHAR(128) NOT NULL                 COMMENT '绑定别名',
    mcp_url            VARCHAR(512) NOT NULL                 COMMENT 'MCP服务地址',
    tool_whitelist_json JSON         NOT NULL                COMMENT '工具白名单配置(JSON数组)',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_expert_mcp_binding UNIQUE (expert_id, binding_name),
    INDEX idx_expert_mcp_binding_expert_id (expert_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家MCP服务绑定';

CREATE TABLE IF NOT EXISTS expert_release_task (
    id               BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    task_id          VARCHAR(64)     NOT NULL                 COMMENT '任务唯一标识',
    expert_id        BIGINT          NOT NULL                 COMMENT '专家ID',
    status           VARCHAR(32)     NOT NULL                 COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED',
    trigger_type     VARCHAR(32)     NOT NULL                 COMMENT '触发类型: MANUAL/AUTO',
    active_task_key  VARCHAR(64)     NULL                     COMMENT '活跃任务互斥锁key',
    staging_path     TEXT            NULL                     COMMENT '暂存目录路径',
    config_json_path TEXT            NULL                     COMMENT '本次发布配置文件路径',
    zip_package_path TEXT            NULL                     COMMENT '本次发布包路径',
    failure_reason   TEXT            NULL                     COMMENT '失败原因',
    requested_at     TIMESTAMP       NOT NULL                 COMMENT '任务请求时间',
    started_at       TIMESTAMP       NULL                     COMMENT '任务开始时间',
    finished_at      TIMESTAMP       NULL                     COMMENT '任务完成时间',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_release_task_id    UNIQUE (task_id),
    CONSTRAINT uk_release_active_task UNIQUE (active_task_key),
    INDEX idx_release_task_expert_id   (expert_id),
    INDEX idx_release_task_status      (status),
    INDEX idx_release_task_expert_status (expert_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家发布任务';