-- Product baseline: existing System/Infra structure only. No users, tenants, menus or demo data.
-- Java Boolean fields use PostgreSQL boolean; sequences start at 1. Legacy dumps are reference-only.

CREATE TABLE infra_api_access_log (
    id int8 NOT NULL,
  trace_id varchar(64) NOT NULL DEFAULT '',
  user_id int8 NOT NULL DEFAULT 0,
  user_type int2 NOT NULL DEFAULT 0,
  application_name varchar(50) NOT NULL,
  request_method varchar(16) NOT NULL DEFAULT '',
  request_url varchar(255) NOT NULL DEFAULT '',
  request_params text NULL,
  response_body text NULL,
  user_ip varchar(50) NOT NULL,
  user_agent varchar(512) NOT NULL,
  operate_module varchar(50) NULL DEFAULT NULL,
  operate_name varchar(50) NULL DEFAULT NULL,
  operate_type int2 NULL DEFAULT 0,
  begin_time timestamp NOT NULL,
  end_time timestamp NOT NULL,
  duration int4 NOT NULL,
  result_code int4 NOT NULL DEFAULT 0,
  result_msg varchar(512) NULL DEFAULT '',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE infra_api_access_log ADD CONSTRAINT pk_infra_api_access_log PRIMARY KEY (id);

CREATE INDEX idx_infra_api_access_log_01 ON infra_api_access_log (create_time);

CREATE SEQUENCE infra_api_access_log_seq
    START 1;

CREATE TABLE infra_api_error_log (
    id int8 NOT NULL,
  trace_id varchar(64) NOT NULL,
  user_id int8 NOT NULL DEFAULT 0,
  user_type int2 NOT NULL DEFAULT 0,
  application_name varchar(50) NOT NULL,
  request_method varchar(16) NOT NULL,
  request_url varchar(255) NOT NULL,
  request_params varchar(8000) NOT NULL,
  user_ip varchar(50) NOT NULL,
  user_agent varchar(512) NOT NULL,
  exception_time timestamp NOT NULL,
  exception_name varchar(128) NOT NULL DEFAULT '',
  exception_message text NOT NULL,
  exception_root_cause_message text NOT NULL,
  exception_stack_trace text NOT NULL,
  exception_class_name varchar(512) NOT NULL,
  exception_file_name varchar(512) NOT NULL,
  exception_method_name varchar(512) NOT NULL,
  exception_line_number int4 NOT NULL,
  process_status int2 NOT NULL,
  process_time timestamp NULL DEFAULT NULL,
  process_user_id int4 NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE infra_api_error_log ADD CONSTRAINT pk_infra_api_error_log PRIMARY KEY (id);

CREATE INDEX idx_infra_api_error_log_01 ON infra_api_error_log (create_time);

CREATE SEQUENCE infra_api_error_log_seq
    START 1;

CREATE TABLE infra_codegen_column (
    id int8 NOT NULL,
  table_id int8 NOT NULL,
  column_name varchar(200) NOT NULL,
  data_type varchar(100) NOT NULL,
  column_comment varchar(500) NOT NULL,
  nullable bool NOT NULL,
  primary_key bool NOT NULL,
  ordinal_position int4 NOT NULL,
  java_type varchar(32) NOT NULL,
  java_field varchar(64) NOT NULL,
  dict_type varchar(200) NULL DEFAULT '',
  example varchar(64) NULL DEFAULT NULL,
  create_operation bool NOT NULL,
  update_operation bool NOT NULL,
  list_operation bool NOT NULL,
  list_operation_condition varchar(32) NOT NULL DEFAULT '=',
  list_operation_result bool NOT NULL,
  html_type varchar(32) NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_codegen_column ADD CONSTRAINT pk_infra_codegen_column PRIMARY KEY (id);

CREATE INDEX idx_infra_codegen_column_01 ON infra_codegen_column (table_id);

CREATE SEQUENCE infra_codegen_column_seq
    START 1;

CREATE TABLE infra_codegen_table (
    id int8 NOT NULL,
  data_source_config_id int8 NOT NULL,
  scene int2 NOT NULL DEFAULT 1,
  table_name varchar(200) NOT NULL DEFAULT '',
  table_comment varchar(500) NOT NULL DEFAULT '',
  remark varchar(500) NULL DEFAULT NULL,
  module_name varchar(30) NOT NULL,
  business_name varchar(30) NOT NULL,
  class_name varchar(100) NOT NULL DEFAULT '',
  class_comment varchar(50) NOT NULL,
  author varchar(50) NOT NULL,
  template_type int2 NOT NULL DEFAULT 1,
  front_type int2 NOT NULL,
  parent_menu_id int8 NULL DEFAULT NULL,
  master_table_id int8 NULL DEFAULT NULL,
  sub_join_column_id int8 NULL DEFAULT NULL,
  sub_join_many bool NULL DEFAULT NULL,
  tree_parent_column_id int8 NULL DEFAULT NULL,
  tree_name_column_id int8 NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_codegen_table ADD CONSTRAINT pk_infra_codegen_table PRIMARY KEY (id);

CREATE SEQUENCE infra_codegen_table_seq
    START 1;

CREATE TABLE infra_config (
    id int8 NOT NULL,
  category varchar(50) NOT NULL,
  type int2 NOT NULL,
  name varchar(100) NOT NULL DEFAULT '',
  config_key varchar(100) NOT NULL DEFAULT '',
  value varchar(500) NOT NULL DEFAULT '',
  visible bool NOT NULL,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_config ADD CONSTRAINT pk_infra_config PRIMARY KEY (id);

CREATE INDEX idx_infra_config_01 ON infra_config (config_key);

CREATE SEQUENCE infra_config_seq
    START 1;

CREATE TABLE infra_data_source_config (
    id int8 NOT NULL,
  name varchar(100) NOT NULL DEFAULT '',
  url varchar(1024) NOT NULL,
  username varchar(255) NOT NULL,
  password varchar(255) NOT NULL DEFAULT '',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_data_source_config ADD CONSTRAINT pk_infra_data_source_config PRIMARY KEY (id);

CREATE SEQUENCE infra_data_source_config_seq
    START 1;

CREATE TABLE infra_file (
    id int8 NOT NULL,
  config_id int8 NULL DEFAULT NULL,
  name varchar(256) NULL DEFAULT NULL,
  path varchar(512) NOT NULL,
  url varchar(1024) NOT NULL,
  type varchar(128) NULL DEFAULT NULL,
  size int4 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_file ADD CONSTRAINT pk_infra_file PRIMARY KEY (id);

CREATE SEQUENCE infra_file_seq
    START 1;

CREATE TABLE infra_file_config (
    id int8 NOT NULL,
  name varchar(63) NOT NULL,
  storage int2 NOT NULL,
  remark varchar(255) NULL DEFAULT NULL,
  master bool NOT NULL,
  config varchar(4096) NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_file_config ADD CONSTRAINT pk_infra_file_config PRIMARY KEY (id);

CREATE SEQUENCE infra_file_config_seq
    START 1;

CREATE TABLE infra_file_content (
    id int8 NOT NULL,
  config_id int8 NOT NULL,
  path varchar(512) NOT NULL,
  content bytea NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_file_content ADD CONSTRAINT pk_infra_file_content PRIMARY KEY (id);

CREATE INDEX idx_infra_file_content_01 ON infra_file_content (config_id, path);

CREATE SEQUENCE infra_file_content_seq
    START 1;

CREATE TABLE infra_job (
    id int8 NOT NULL,
  name varchar(32) NOT NULL,
  status int2 NOT NULL,
  handler_name varchar(64) NOT NULL,
  handler_param varchar(255) NULL DEFAULT NULL,
  cron_expression varchar(32) NOT NULL,
  retry_count int4 NOT NULL DEFAULT 0,
  retry_interval int4 NOT NULL DEFAULT 0,
  monitor_timeout int4 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_job ADD CONSTRAINT pk_infra_job PRIMARY KEY (id);

CREATE SEQUENCE infra_job_seq
    START 1;

CREATE TABLE infra_job_log (
    id int8 NOT NULL,
  job_id int8 NOT NULL,
  handler_name varchar(64) NOT NULL,
  handler_param varchar(255) NULL DEFAULT NULL,
  execute_index int2 NOT NULL DEFAULT 1,
  begin_time timestamp NOT NULL,
  end_time timestamp NULL DEFAULT NULL,
  duration int4 NULL DEFAULT NULL,
  status int2 NOT NULL,
  result varchar(4000) NULL DEFAULT '',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE infra_job_log ADD CONSTRAINT pk_infra_job_log PRIMARY KEY (id);

CREATE INDEX idx_infra_job_log_01 ON infra_job_log (job_id);

CREATE INDEX idx_infra_job_log_02 ON infra_job_log (create_time);

CREATE SEQUENCE infra_job_log_seq
    START 1;

CREATE TABLE system_dept (
    id int8 NOT NULL,
  name varchar(30) NOT NULL DEFAULT '',
  parent_id int8 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  leader_user_id int8 NULL DEFAULT NULL,
  phone varchar(11) NULL DEFAULT NULL,
  email varchar(50) NULL DEFAULT NULL,
  status int2 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_dept ADD CONSTRAINT pk_system_dept PRIMARY KEY (id);

CREATE SEQUENCE system_dept_seq
    START 1;

CREATE TABLE system_dict_data (
    id int8 NOT NULL,
  sort int4 NOT NULL DEFAULT 0,
  label varchar(100) NOT NULL DEFAULT '',
  value varchar(100) NOT NULL DEFAULT '',
  dict_type varchar(100) NOT NULL DEFAULT '',
  status int2 NOT NULL DEFAULT 0,
  color_type varchar(100) NULL DEFAULT '',
  css_class varchar(100) NULL DEFAULT '',
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_dict_data ADD CONSTRAINT pk_system_dict_data PRIMARY KEY (id);

CREATE SEQUENCE system_dict_data_seq
    START 1;

CREATE TABLE system_dict_type (
    id int8 NOT NULL,
  name varchar(100) NOT NULL DEFAULT '',
  type varchar(100) NOT NULL DEFAULT '',
  status int2 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  deleted_time timestamp NULL DEFAULT NULL
);

ALTER TABLE system_dict_type ADD CONSTRAINT pk_system_dict_type PRIMARY KEY (id);

CREATE SEQUENCE system_dict_type_seq
    START 1;

CREATE TABLE system_login_log (
    id int8 NOT NULL,
  log_type int8 NOT NULL,
  trace_id varchar(64) NOT NULL DEFAULT '',
  user_id int8 NOT NULL DEFAULT 0,
  user_type int2 NOT NULL DEFAULT 0,
  username varchar(50) NOT NULL DEFAULT '',
  result int2 NOT NULL,
  user_ip varchar(50) NOT NULL,
  user_agent varchar(512) NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_login_log ADD CONSTRAINT pk_system_login_log PRIMARY KEY (id);

CREATE INDEX idx_system_login_log_01 ON system_login_log (username);

CREATE INDEX idx_system_login_log_02 ON system_login_log (create_time);

CREATE SEQUENCE system_login_log_seq
    START 1;

CREATE TABLE system_mail_account (
    id int8 NOT NULL,
  mail varchar(255) NOT NULL,
  username varchar(255) NOT NULL,
  password varchar(255) NOT NULL,
  host varchar(255) NOT NULL,
  port int4 NOT NULL,
  ssl_enable bool NOT NULL DEFAULT '0',
  starttls_enable bool NOT NULL DEFAULT '0',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_mail_account ADD CONSTRAINT pk_system_mail_account PRIMARY KEY (id);

CREATE SEQUENCE system_mail_account_seq
    START 1;

CREATE TABLE system_mail_log (
    id int8 NOT NULL,
  user_id int8 NULL DEFAULT NULL,
  user_type int2 NULL DEFAULT NULL,
  to_mails varchar(1024) NOT NULL,
  cc_mails varchar(1024) NULL DEFAULT NULL,
  bcc_mails varchar(1024) NULL DEFAULT NULL,
  account_id int8 NOT NULL,
  from_mail varchar(255) NOT NULL,
  template_id int8 NOT NULL,
  template_code varchar(63) NOT NULL,
  template_nickname varchar(255) NULL DEFAULT NULL,
  template_title varchar(255) NOT NULL,
  template_content text NOT NULL,
  template_params varchar(255) NOT NULL,
  send_status int2 NOT NULL DEFAULT 0,
  send_time timestamp NULL DEFAULT NULL,
  send_message_id varchar(255) NULL DEFAULT NULL,
  send_exception varchar(4096) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_mail_log ADD CONSTRAINT pk_system_mail_log PRIMARY KEY (id);

CREATE SEQUENCE system_mail_log_seq
    START 1;

CREATE TABLE system_mail_template (
    id int8 NOT NULL,
  name varchar(63) NOT NULL,
  code varchar(63) NOT NULL,
  account_id int8 NOT NULL,
  nickname varchar(255) NULL DEFAULT NULL,
  title varchar(255) NOT NULL,
  content varchar(10240) NOT NULL,
  params varchar(255) NOT NULL,
  status int2 NOT NULL,
  remark varchar(255) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_mail_template ADD CONSTRAINT pk_system_mail_template PRIMARY KEY (id);

CREATE SEQUENCE system_mail_template_seq
    START 1;

CREATE TABLE system_menu (
    id int8 NOT NULL,
  name varchar(50) NOT NULL,
  permission varchar(100) NOT NULL DEFAULT '',
  type int2 NOT NULL,
  sort int4 NOT NULL DEFAULT 0,
  parent_id int8 NOT NULL DEFAULT 0,
  path varchar(200) NULL DEFAULT '',
  icon varchar(100) NULL DEFAULT '#',
  component varchar(255) NULL DEFAULT NULL,
  component_name varchar(255) NULL DEFAULT NULL,
  status int2 NOT NULL DEFAULT 0,
  visible bool NOT NULL DEFAULT '1',
  keep_alive bool NOT NULL DEFAULT '1',
  always_show bool NOT NULL DEFAULT '1',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_menu ADD CONSTRAINT pk_system_menu PRIMARY KEY (id);

CREATE SEQUENCE system_menu_seq
    START 1;

CREATE TABLE system_notice (
    id int8 NOT NULL,
  title varchar(50) NOT NULL,
  content text NOT NULL,
  type int2 NOT NULL,
  status int2 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_notice ADD CONSTRAINT pk_system_notice PRIMARY KEY (id);

CREATE SEQUENCE system_notice_seq
    START 1;

CREATE TABLE system_notify_message (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  user_type int2 NOT NULL,
  template_id int8 NOT NULL,
  template_code varchar(64) NOT NULL,
  template_nickname varchar(63) NOT NULL,
  template_content varchar(1024) NOT NULL,
  template_type int4 NOT NULL,
  template_params varchar(255) NOT NULL,
  read_status bool NOT NULL,
  read_time timestamp NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_notify_message ADD CONSTRAINT pk_system_notify_message PRIMARY KEY (id);

CREATE INDEX idx_system_notify_message_01 ON system_notify_message (user_id, user_type, read_status);

CREATE SEQUENCE system_notify_message_seq
    START 1;

CREATE TABLE system_notify_template (
    id int8 NOT NULL,
  name varchar(63) NOT NULL,
  code varchar(64) NOT NULL,
  nickname varchar(255) NOT NULL,
  content varchar(1024) NOT NULL,
  type int2 NOT NULL,
  params varchar(255) NULL DEFAULT NULL,
  status int2 NOT NULL,
  remark varchar(255) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_notify_template ADD CONSTRAINT pk_system_notify_template PRIMARY KEY (id);

CREATE SEQUENCE system_notify_template_seq
    START 1;

CREATE TABLE system_oauth2_access_token (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  user_type int2 NOT NULL,
  user_info varchar(512) NOT NULL,
  access_token varchar(255) NOT NULL,
  refresh_token varchar(32) NOT NULL,
  client_id varchar(255) NOT NULL,
  scopes varchar(255) NULL DEFAULT NULL,
  expires_time timestamp NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_access_token ADD CONSTRAINT pk_system_oauth2_access_token PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_access_token_01 ON system_oauth2_access_token (access_token);

CREATE INDEX idx_system_oauth2_access_token_02 ON system_oauth2_access_token (refresh_token);

CREATE SEQUENCE system_oauth2_access_token_seq
    START 1;

CREATE TABLE system_oauth2_approve (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  user_type int2 NOT NULL,
  client_id varchar(255) NOT NULL,
  scope varchar(255) NOT NULL DEFAULT '',
  approved bool NOT NULL DEFAULT '0',
  expires_time timestamp NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_approve ADD CONSTRAINT pk_system_oauth2_approve PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_approve_01 ON system_oauth2_approve (user_id, user_type, client_id);

CREATE SEQUENCE system_oauth2_approve_seq
    START 1;

CREATE TABLE system_oauth2_client (
    id int8 NOT NULL,
  client_id varchar(255) NOT NULL,
  secret varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  logo varchar(255) NOT NULL,
  description varchar(255) NULL DEFAULT NULL,
  status int2 NOT NULL,
  access_token_validity_seconds int4 NOT NULL,
  refresh_token_validity_seconds int4 NOT NULL,
  redirect_uris varchar(255) NOT NULL,
  authorized_grant_types varchar(255) NOT NULL,
  scopes varchar(255) NULL DEFAULT NULL,
  auto_approve_scopes varchar(255) NULL DEFAULT NULL,
  authorities varchar(255) NULL DEFAULT NULL,
  resource_ids varchar(255) NULL DEFAULT NULL,
  additional_information varchar(4096) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_oauth2_client ADD CONSTRAINT pk_system_oauth2_client PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_client_01 ON system_oauth2_client (client_id);

CREATE SEQUENCE system_oauth2_client_seq
    START 1;

CREATE TABLE system_oauth2_code (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  user_type int2 NOT NULL,
  code varchar(32) NOT NULL,
  client_id varchar(255) NOT NULL,
  scopes varchar(255) NULL DEFAULT '',
  expires_time timestamp NOT NULL,
  redirect_uri varchar(255) NULL DEFAULT NULL,
  state varchar(255) NOT NULL DEFAULT '',
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_code ADD CONSTRAINT pk_system_oauth2_code PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_code_01 ON system_oauth2_code (code);

CREATE SEQUENCE system_oauth2_code_seq
    START 1;

CREATE TABLE system_oauth2_refresh_token (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  refresh_token varchar(32) NOT NULL,
  user_type int2 NOT NULL,
  client_id varchar(255) NOT NULL,
  scopes varchar(255) NULL DEFAULT NULL,
  expires_time timestamp NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_refresh_token ADD CONSTRAINT pk_system_oauth2_refresh_token PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_refresh_token_01 ON system_oauth2_refresh_token (refresh_token);

CREATE SEQUENCE system_oauth2_refresh_token_seq
    START 1;

CREATE TABLE system_operate_log (
    id int8 NOT NULL,
  trace_id varchar(64) NOT NULL DEFAULT '',
  user_id int8 NOT NULL,
  user_type int2 NOT NULL DEFAULT 0,
  type varchar(50) NOT NULL,
  sub_type varchar(50) NOT NULL,
  biz_id int8 NOT NULL,
  action varchar(2000) NOT NULL DEFAULT '',
  success bool NOT NULL DEFAULT '1',
  extra varchar(2000) NOT NULL DEFAULT '',
  request_method varchar(16) NULL DEFAULT '',
  request_url varchar(255) NULL DEFAULT '',
  user_ip varchar(50) NULL DEFAULT NULL,
  user_agent varchar(512) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_operate_log ADD CONSTRAINT pk_system_operate_log PRIMARY KEY (id);

CREATE INDEX idx_system_operate_log_01 ON system_operate_log (user_id);

CREATE INDEX idx_system_operate_log_02 ON system_operate_log (create_time);

CREATE SEQUENCE system_operate_log_seq
    START 1;

CREATE TABLE system_post (
    id int8 NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(50) NOT NULL,
  sort int4 NOT NULL,
  status int2 NOT NULL,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_post ADD CONSTRAINT pk_system_post PRIMARY KEY (id);

CREATE SEQUENCE system_post_seq
    START 1;

CREATE TABLE system_role (
    id int8 NOT NULL,
  name varchar(30) NOT NULL,
  code varchar(100) NOT NULL,
  sort int4 NOT NULL,
  data_scope int2 NOT NULL DEFAULT 1,
  data_scope_dept_ids varchar(500) NOT NULL DEFAULT '',
  status int2 NOT NULL,
  type int2 NOT NULL,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_role ADD CONSTRAINT pk_system_role PRIMARY KEY (id);

CREATE SEQUENCE system_role_seq
    START 1;

CREATE TABLE system_role_menu (
    id int8 NOT NULL,
  role_id int8 NOT NULL,
  menu_id int8 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_role_menu ADD CONSTRAINT pk_system_role_menu PRIMARY KEY (id);

CREATE INDEX idx_system_role_menu_01 ON system_role_menu (role_id);

CREATE SEQUENCE system_role_menu_seq
    START 1;

CREATE TABLE system_sms_channel (
    id int8 NOT NULL,
  signature varchar(12) NOT NULL,
  code varchar(63) NOT NULL,
  status int2 NOT NULL,
  remark varchar(255) NULL DEFAULT NULL,
  api_key varchar(128) NOT NULL,
  api_secret varchar(128) NULL DEFAULT NULL,
  callback_url varchar(255) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_sms_channel ADD CONSTRAINT pk_system_sms_channel PRIMARY KEY (id);

CREATE SEQUENCE system_sms_channel_seq
    START 1;

CREATE TABLE system_sms_code (
    id int8 NOT NULL,
  mobile varchar(11) NOT NULL,
  code varchar(6) NOT NULL,
  create_ip varchar(15) NOT NULL,
  scene int2 NOT NULL,
  today_index int2 NOT NULL,
  used boolean NOT NULL,
  used_time timestamp NULL DEFAULT NULL,
  used_ip varchar(255) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_sms_code ADD CONSTRAINT pk_system_sms_code PRIMARY KEY (id);

CREATE INDEX idx_system_sms_code_01 ON system_sms_code (mobile);

CREATE SEQUENCE system_sms_code_seq
    START 1;

CREATE TABLE system_sms_log (
    id int8 NOT NULL,
  channel_id int8 NOT NULL,
  channel_code varchar(63) NOT NULL,
  template_id int8 NOT NULL,
  template_code varchar(63) NOT NULL,
  template_type int2 NOT NULL,
  template_content varchar(255) NOT NULL,
  template_params varchar(255) NOT NULL,
  api_template_id varchar(63) NOT NULL,
  mobile varchar(11) NOT NULL,
  user_id int8 NULL DEFAULT NULL,
  user_type int2 NULL DEFAULT NULL,
  send_status int2 NOT NULL DEFAULT 0,
  send_time timestamp NULL DEFAULT NULL,
  api_send_code varchar(63) NULL DEFAULT NULL,
  api_send_msg varchar(255) NULL DEFAULT NULL,
  api_request_id varchar(255) NULL DEFAULT NULL,
  api_serial_no varchar(255) NULL DEFAULT NULL,
  receive_status int2 NOT NULL DEFAULT 0,
  receive_time timestamp NULL DEFAULT NULL,
  api_receive_code varchar(63) NULL DEFAULT NULL,
  api_receive_msg varchar(255) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_sms_log ADD CONSTRAINT pk_system_sms_log PRIMARY KEY (id);

CREATE SEQUENCE system_sms_log_seq
    START 1;

CREATE TABLE system_sms_template (
    id int8 NOT NULL,
  type int2 NOT NULL,
  status int2 NOT NULL,
  code varchar(63) NOT NULL,
  name varchar(63) NOT NULL,
  content varchar(255) NOT NULL,
  params varchar(255) NOT NULL,
  remark varchar(255) NULL DEFAULT NULL,
  api_template_id varchar(63) NOT NULL,
  channel_id int8 NOT NULL,
  channel_code varchar(63) NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_sms_template ADD CONSTRAINT pk_system_sms_template PRIMARY KEY (id);

CREATE SEQUENCE system_sms_template_seq
    START 1;

CREATE TABLE system_social_client (
    id int8 NOT NULL,
  name varchar(255) NOT NULL,
  social_type int2 NOT NULL,
  user_type int2 NOT NULL,
  client_id varchar(255) NOT NULL,
  client_secret varchar(255) NOT NULL,
  agent_id varchar(255) NULL DEFAULT NULL,
  public_key varchar(2048) NULL DEFAULT NULL,
  status int2 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_social_client ADD CONSTRAINT pk_system_social_client PRIMARY KEY (id);

CREATE SEQUENCE system_social_client_seq
    START 1;

CREATE TABLE system_social_user (
    id int8 NOT NULL,
  type int2 NOT NULL,
  openid varchar(32) NOT NULL,
  token varchar(256) NULL DEFAULT NULL,
  raw_token_info varchar(1024) NOT NULL,
  nickname varchar(32) NOT NULL,
  avatar varchar(255) NULL DEFAULT NULL,
  raw_user_info varchar(1024) NOT NULL,
  code varchar(256) NOT NULL,
  state varchar(256) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_social_user ADD CONSTRAINT pk_system_social_user PRIMARY KEY (id);

CREATE INDEX idx_system_social_user_01 ON system_social_user (type, openid);

CREATE INDEX idx_system_social_user_02 ON system_social_user (type, code, state);

CREATE SEQUENCE system_social_user_seq
    START 1;

CREATE TABLE system_social_user_bind (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  user_type int2 NOT NULL,
  social_type int2 NOT NULL,
  social_user_id int8 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_social_user_bind ADD CONSTRAINT pk_system_social_user_bind PRIMARY KEY (id);

CREATE INDEX idx_system_social_user_bind_01 ON system_social_user_bind (user_type, social_user_id);

CREATE SEQUENCE system_social_user_bind_seq
    START 1;

CREATE TABLE system_tenant (
    id int8 NOT NULL,
  name varchar(30) NOT NULL,
  contact_user_id int8 NULL DEFAULT NULL,
  contact_name varchar(30) NOT NULL,
  contact_mobile varchar(500) NULL DEFAULT NULL,
  status int2 NOT NULL DEFAULT 0,
  websites varchar(1024) NULL DEFAULT '',
  package_id int8 NOT NULL,
  expire_time timestamp NOT NULL,
  account_count int4 NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_tenant ADD CONSTRAINT pk_system_tenant PRIMARY KEY (id);

CREATE SEQUENCE system_tenant_seq
    START 1;

CREATE TABLE system_tenant_package (
    id int8 NOT NULL,
  name varchar(30) NOT NULL,
  status int2 NOT NULL DEFAULT 0,
  remark varchar(256) NULL DEFAULT '',
  menu_ids varchar(4096) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false
);

ALTER TABLE system_tenant_package ADD CONSTRAINT pk_system_tenant_package PRIMARY KEY (id);

CREATE SEQUENCE system_tenant_package_seq
    START 1;

CREATE TABLE system_user_post (
    id int8 NOT NULL,
  user_id int8 NOT NULL DEFAULT 0,
  post_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_user_post ADD CONSTRAINT pk_system_user_post PRIMARY KEY (id);

CREATE SEQUENCE system_user_post_seq
    START 1;

CREATE TABLE system_user_role (
    id int8 NOT NULL,
  user_id int8 NOT NULL,
  role_id int8 NOT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_user_role ADD CONSTRAINT pk_system_user_role PRIMARY KEY (id);

CREATE INDEX idx_system_user_role_01 ON system_user_role (user_id);

CREATE SEQUENCE system_user_role_seq
    START 1;

CREATE TABLE system_users (
    id int8 NOT NULL,
  username varchar(30) NOT NULL,
  password varchar(100) NOT NULL DEFAULT '',
  nickname varchar(30) NOT NULL,
  remark varchar(500) NULL DEFAULT NULL,
  dept_id int8 NULL DEFAULT NULL,
  post_ids varchar(255) NULL DEFAULT NULL,
  email varchar(50) NULL DEFAULT '',
  mobile varchar(11) NULL DEFAULT '',
  sex int2 NULL DEFAULT 0,
  avatar varchar(512) NULL DEFAULT '',
  status int2 NOT NULL DEFAULT 0,
  login_ip varchar(50) NULL DEFAULT '',
  login_date timestamp NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted boolean NOT NULL DEFAULT false,
  tenant_id int8 NOT NULL DEFAULT 0
);

ALTER TABLE system_users ADD CONSTRAINT pk_system_users PRIMARY KEY (id);

CREATE INDEX idx_system_users_01 ON system_users (username);

CREATE INDEX idx_system_users_02 ON system_users (mobile);

CREATE INDEX idx_system_users_03 ON system_users (email);

CREATE INDEX idx_system_users_04 ON system_users (dept_id);

CREATE SEQUENCE system_users_seq
    START 1;
