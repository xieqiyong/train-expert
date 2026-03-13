CREATE TABLE IF NOT EXISTS skill_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    package_name VARCHAR(128) NOT NULL,
    package_path VARCHAR(512) NOT NULL,
    checksum VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_skill_package_skill_id UNIQUE (skill_id)
);

CREATE TABLE IF NOT EXISTS static_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    static_package_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    package_name VARCHAR(128) NOT NULL,
    package_path VARCHAR(512) NOT NULL,
    checksum VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_static_package_id UNIQUE (static_package_id)
);

CREATE TABLE IF NOT EXISTS digital_expert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    shared_path VARCHAR(512),
    config_json_path VARCHAR(512),
    zip_package_path VARCHAR(512),
    last_release_task_id VARCHAR(64),
    release_version INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    disabled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_digital_expert_expert_id UNIQUE (expert_id)
);

CREATE TABLE IF NOT EXISTS expert_skill_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_id VARCHAR(64) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    sort_no INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_expert_skill UNIQUE (expert_id, skill_id)
);

CREATE TABLE IF NOT EXISTS expert_static_package_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_id VARCHAR(64) NOT NULL,
    static_package_id VARCHAR(64) NOT NULL,
    sort_no INT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_expert_static_package UNIQUE (expert_id, static_package_id)
);

CREATE TABLE IF NOT EXISTS expert_mcp_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_id VARCHAR(64) NOT NULL,
    binding_name VARCHAR(128) NOT NULL,
    mcp_url VARCHAR(512) NOT NULL,
    tool_whitelist_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_expert_mcp_binding UNIQUE (expert_id, binding_name)
);

CREATE TABLE IF NOT EXISTS expert_release_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    expert_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    active_task_key VARCHAR(64),
    staging_path VARCHAR(512),
    config_json_path VARCHAR(512),
    zip_package_path VARCHAR(512),
    failure_reason VARCHAR(2000),
    requested_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_release_task_id UNIQUE (task_id),
    CONSTRAINT uk_release_active_task UNIQUE (active_task_key)
);

CREATE INDEX idx_expert_mcp_binding_expert_id ON expert_mcp_binding (expert_id);
CREATE INDEX idx_release_task_expert_id ON expert_release_task (expert_id);
