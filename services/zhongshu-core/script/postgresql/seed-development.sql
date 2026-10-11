-- Explicit test fixture only. Invoke through the guarded shell entry point, never Flyway.
\getenv fixture_password_hash ZS_DEV_ADMIN_PASSWORD_HASH
\getenv fixture_client_secret ZS_DEV_OAUTH_CLIENT_SECRET
BEGIN;
DO $$
BEGIN
  IF current_database() !~ '^[a-zA-Z_][a-zA-Z0-9_]*_test$'
     OR inet_server_addr() NOT IN ('127.0.0.1'::inet, '::1'::inet)
     OR inet_server_addr() IS NULL THEN
    RAISE EXCEPTION 'Development fixture requires a disposable loopback *_test database';
  END IF;
  IF EXISTS (SELECT 1 FROM system_users WHERE username NOT IN ('pgadmina', 'pgadminb')) THEN
    RAISE EXCEPTION 'Refusing a database with non-fixture users';
  END IF;
END $$;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name)
VALUES
 (99001, '系统管理', '', 1, 10, 0, '/system', 'ep:tools', NULL, NULL),
 (99002, '用户管理', 'system:user:query', 2, 1, 99001, 'user', 'ep:avatar', 'system/user/index', 'SystemUser'),
 (99003, '角色管理', 'system:role:query', 2, 2, 99001, 'role', 'ep:user', 'system/role/index', 'SystemRole'),
 (99004, '基础设施', '', 1, 20, 0, '/infra', 'ep:monitor', NULL, NULL),
 (99005, '公共文件', 'infra:file:query', 2, 1, 99004, 'file', 'ep:document', 'infra/file/index', 'InfraFile'),
 (99006, '参数配置', 'infra:config:query', 2, 2, 99004, 'config', 'ep:setting', 'infra/config/index', 'InfraConfig'),
 (99007, '仅测试运维租户访问', 'system:tenant:visit', 3, 99, 99002, '', '', NULL, NULL)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_tenant_package (id, name, menu_ids, remark)
VALUES (99001, 'PG技术验证套餐', '[99001,99002,99003,99004,99005,99006,99007]', '仅技术隔离测试，不代表业务组织模型')
ON CONFLICT (id) DO UPDATE SET menu_ids = EXCLUDED.menu_ids;
INSERT INTO system_tenant (id, name, contact_user_id, contact_name, package_id, expire_time, account_count, websites)
VALUES (99001, 'PG测试租户A', 99001, '测试管理员A', 99001, CURRENT_TIMESTAMP + INTERVAL '1 year', 10, '[]'),
       (99002, 'PG测试租户B', 99002, '测试管理员B', 99001, CURRENT_TIMESTAMP + INTERVAL '1 year', 10, '[]')
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_users (id, username, password, nickname, tenant_id)
VALUES (99001, 'pgadmina', :'fixture_password_hash', '测试管理员A', 99001),
       (99002, 'pgadminb', :'fixture_password_hash', '测试管理员B', 99002)
ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username, password = EXCLUDED.password;
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, tenant_id)
VALUES (99001, '测试租户管理员A', 'tenant_admin', 1, 1, '[]', 0, 1, 99001),
       (99002, '测试租户管理员B', 'tenant_admin', 1, 1, '[]', 0, 1, 99002),
       -- Only A receives this explicit operations fixture role. It is not a business headquarters role.
       (99003, '仅测试运维租户访问', 'pg_visit_operator_fixture', 2, 5, '[]', 0, 1, 99001)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_user_role (id, user_id, role_id, tenant_id)
VALUES (99001, 99001, 99001, 99001), (99002, 99002, 99002, 99002),
       (99003, 99001, 99003, 99001)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_role_menu (id, role_id, menu_id, tenant_id)
SELECT 99000 + row_number() OVER (ORDER BY role_id, menu_id), role_id, menu_id, role_id
FROM (VALUES (99001), (99002)) AS roles(role_id)
CROSS JOIN (VALUES (99001), (99002), (99003), (99004), (99005), (99006)) AS menus(menu_id)
ON CONFLICT (id) DO NOTHING;
-- Do not include the visit button in either ordinary tenant_admin role.
INSERT INTO system_role_menu (id, role_id, menu_id, tenant_id)
VALUES (99013, 99003, 99007, 99001)
ON CONFLICT (id) DO NOTHING;
-- Public DB-backed bytes and private bytes have independent namespaces. No external storage is used.
INSERT INTO infra_file_config (id, name, storage, remark, master, config)
VALUES (99001, 'PG smoke public DB fixture', 1, 'Disposable local download fixture only', false,
 '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1"}')
ON CONFLICT (id) DO NOTHING;
INSERT INTO infra_file_content (id, config_id, path, content)
VALUES (99001, 99001, 'pg-smoke/public-fixture.txt', convert_to('zhongshu-public-smoke-fixture', 'UTF8'))
ON CONFLICT (id) DO NOTHING;
-- Deliberate known-ID fixture owned by technical tenant B; no production authorization adapter is installed.
INSERT INTO infra_private_file (id, tenant_id, object_type, object_id, purpose, name, type, size, content)
VALUES ('00000000-0000-0000-0000-000000000001', 99002, 'pg_test_object', 'object-1', 'verification',
 'private-fixture.txt', 'text/plain', octet_length(convert_to('zhongshu-private-smoke-fixture', 'UTF8')),
 convert_to('zhongshu-private-smoke-fixture', 'UTF8'))
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_oauth2_client (id, client_id, secret, name, logo, status,
 access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes)
VALUES (99001, 'default', :'fixture_client_secret', 'PG本地验证', '', 0, 1800, 86400,
 '[]', '["password","refresh_token"]', '[]')
ON CONFLICT (id) DO UPDATE SET secret = EXCLUDED.secret;
SELECT setval('system_menu_seq', (SELECT max(id) FROM system_menu));
SELECT setval('system_tenant_package_seq', (SELECT max(id) FROM system_tenant_package));
SELECT setval('system_tenant_seq', (SELECT max(id) FROM system_tenant));
SELECT setval('system_users_seq', (SELECT max(id) FROM system_users));
SELECT setval('system_role_seq', (SELECT max(id) FROM system_role));
SELECT setval('system_user_role_seq', (SELECT max(id) FROM system_user_role));
SELECT setval('system_role_menu_seq', (SELECT max(id) FROM system_role_menu));
SELECT setval('system_oauth2_client_seq', (SELECT max(id) FROM system_oauth2_client));
SELECT setval('infra_file_config_seq', (SELECT max(id) FROM infra_file_config));
SELECT setval('infra_file_content_seq', (SELECT max(id) FROM infra_file_content));
COMMIT;
