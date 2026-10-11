-- Private delivery bytes are independent from the public FileClient namespace.
CREATE TABLE infra_private_file (
    id varchar(36) PRIMARY KEY,
    tenant_id bigint NOT NULL,
    object_type varchar(64) NOT NULL,
    object_id varchar(128) NOT NULL,
    purpose varchar(64) NOT NULL,
    name varchar(255) NOT NULL,
    type varchar(64) NOT NULL,
    size bigint NOT NULL CHECK (size BETWEEN 1 AND 10485760),
    content bytea NOT NULL,
    creator varchar(64) DEFAULT '',
    updater varchar(64) DEFAULT '',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
);
CREATE INDEX idx_infra_private_file_object ON infra_private_file (tenant_id, object_type, object_id);
