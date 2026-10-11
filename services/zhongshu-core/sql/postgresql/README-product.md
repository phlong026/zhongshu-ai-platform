# PostgreSQL product baseline

The only executable migration source is `yudao-server/src/main/resources/db/migration/postgresql`:
V1 has 43 existing System/Infra tables and sequences; V2 has 11 empty Quartz tables;
V3 adds bounded-cleanup indexes; V4 adds isolated private-file byte storage.
No migration contains users, tenants, menus, demo jobs or unapproved business modules.
The imported `ruoyi-vue-pro.sql` and `quartz.sql` here are reference-only dumps with
destructive statements and/or demonstration data. Never run them on a product database.

## Safe profile and migrations

The default profile is `postgres`, overridable via SPRING_PROFILES_ACTIVE. Never
combine it with upstream local/dev. Required variables:

- ZS_PG_URL, ZS_PG_USERNAME, ZS_PG_PASSWORD
- ZS_REDIS_HOST, ZS_REDIS_PASSWORD; optional ZS_REDIS_PORT and ZS_REDIS_DATABASE
- ZS_APPLICATION_MYBATIS_PLUS_ENCRYPTOR_PASSWORD

Use ZS_LOG_FILE for persistent operational logs; fallback is the JVM temporary
folder. The raw non-prod request-body console logger is disabled; sanitized access
logging remains on. Actuator exposes health/info only. Mock authentication is
forbidden. Captcha stays on; only a disposable E2E environment should set
ZS_CAPTCHA_ENABLED=false. Jobs are off until ZS_QUARTZ_ENABLED=true. No usable
external social/payment/AI credential or default administrator is supplied.

Flyway migration is off by default. An approved deployment may explicitly set
ZS_DB_MIGRATE=true plus ZS_PG_MIGRATION_USERNAME/PASSWORD for a separate schema owner.
Pre-provision the target schema. For non-public schema use matching ZS_PG_SCHEMA and
JDBC currentSchema. Remove migration credentials and disable migration after use.
Runtime must be NOSUPERUSER/NOCREATEDB/NOCREATEROLE, without schema ownership/CREATE.
Grant CONNECT, schema USAGE, business-table DML and product-sequence USAGE/SELECT.
Do not grant runtime rights on flyway_schema_history or unrelated objects.

Checksums are validated, clean/automatic baseline/out-of-order migration disabled.
Non-empty unversioned databases fail closed. Existing databases require a reviewed
schema/data comparison, backup and explicit adoption plan. Do not force-baseline a
legacy int2-boolean schema. Java Boolean fields (`deleted`, `system_sms_code.used`)
use native PostgreSQL boolean in this new baseline. No tenant-to-organization
business mapping is introduced.

Applied versions are immutable; corrections use new forward migrations. Never
repair checksums to hide failure. PostgreSQL transactional DDL rolls back a failed
migration; investigate and validate history before retry. Rehearse backup/restore
separately for destructive/data-changing upgrades. This baseline is not production
backup/restore UAT.

## True PostgreSQL tests

With JDK 17, Maven and a dedicated disposable PostgreSQL database, supply:
TEST_PG_URL, TEST_PG_USERNAME, TEST_PG_PASSWORD, TEST_PG_RUNTIME_USERNAME and
TEST_PG_RUNTIME_PASSWORD. The owner needs CREATE SCHEMA; runtime is a separate
low-privilege role with no global default grant on migration-history tables.

```sh
mvn -Ppostgres-it -pl yudao-server -am verify
```

If the execution sandbox prohibits Mockito's dynamic JVM attach, use its explicit
agent for the test JVM only. Bootstrap the fixed test dependency before the first
test fork, including on a cold cache; this is not a production JVM option. Use the
normal reactor dependency lifecycle, which also works with Maven's HTTP proxy for
HTTPS repositories. The managed Mockito core version is 5.17.0:

```sh
# JAVA_HOME must point to JDK 17; use the same Maven repository in both commands.
MAVEN_REPO="${MAVEN_REPO:-$HOME/.m2/repository}"
mvn -B -Dmaven.repo.local="$MAVEN_REPO" \
  -pl yudao-framework/yudao-spring-boot-starter-test -am -DskipTests test-compile
test -s "$MAVEN_REPO/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
mvn -B -Ppostgres-it -pl yudao-server -am -Dmaven.repo.local="$MAVEN_REPO" \
  "-DargLine=-Xmx768m -javaagent:$MAVEN_REPO/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" verify
```

On a direct connection, an alternative fixed-artifact bootstrap is
`mvn -B -f yudao-dependencies/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -Dartifact=org.mockito:mockito-core:5.17.0 -Dtransitive=false`.
That plugin's legacy resolver requires a protocol-matching HTTPS proxy entry;
prefer the lifecycle command above when the environment supplies an HTTP proxy.

Missing configuration fails; there is no H2 fallback or silent skip. Tests create
random zs_it_* schemas and drop only those they created. The 16 PostgreSQL cases
exercise all five actual cleanup mappers (invalid/zero/negative limits, boundaries,
ordering, physical deletion, repeated/concurrent batches), migrations on fresh and
upgrade schemas, repeat safety, sequence/Boolean/time/JSON behavior, actual mapper
pagination, private-file bytea roundtrip and explicit tenant predicates, and denial
of runtime DDL/history writes. H2 unit tests remain separate evidence.

Do not start a real Redis on the unit-test jedismock port (16379). Run ordinary
unit tests with no real Redis, and start a dedicated real instance on another port
(e.g. 26379) only for the API phase. The PG integration cases do not require Redis.

## Explicit development fixture and actual API smoke

No test users are in migration files. `script/postgresql/seed-development.sh`
requires loopback PGHOST, explicit numeric PGPORT, a simple PGDATABASE ending in
_test, ZS_DEV_SEED_CONFIRM=disposable-local-test-only, externally generated BCrypt
ZS_DEV_ADMIN_PASSWORD_HASH and ZS_DEV_OAUTH_CLIENT_SECRET. It removes libpq target
indirection and validates the server address inside SQL. It refuses non-fixture
users, creates technical tenants 99001/99002 and users pgadmina/pgadminb, and six
System/Infra navigation entries. A separate seventh permission button and fixture
role give only A the existing operations tenant-visit permission, so the smoke test
can prove a successful operations visit still cannot read B's private attachment.
It also seeds one public DB-backed file and a separate known-ID private file. These
fixtures are not production account provisioning or an approved business organization
model; no production object authorizer is added.

After building the jar, start dedicated PostgreSQL and Redis in the same execution
network environment. Supply the runtime/migration variables above, matching
PGHOST/PGPORT/PGDATABASE, explicit ZS_REDIS_PORT and:

```sh
export ZS_DEV_SEED_CONFIRM=disposable-local-test-only
export ZS_DEV_REDIS_CONFIRM=dedicated-disposable-test-instance
bash script/postgresql/run-api-smoke.sh
```

Before any writes the runner validates one matching plain loopback JDBC/libpq target;
query/userinfo/fragment and service/hostaddr overrides are rejected. Redis must be
a confirmed dedicated disposable loopback instance; database 15 alone is not proof.
The runner generates a random 16-character password (the existing API maximum),
migrates with the owner, grants only product-table/sequence runtime rights, seeds,
and boots the actual jar using the runtime role. It checks health, unavailable
sensitive Actuator endpoints, anonymous/invalid login rejection, two logins and
menus, real paged user isolation, forged tenant/unauthorized visit-tenant rejection,
public anonymous download with exact bytes, private-ID refusal through the public
route, authorized operations visit still denied private bytes, private uploads denied
without an authorizer, refresh/logout invalidation and absence of observed passwords/
tokens from stdout and rolling logs. The Node supervisor sends SIGTERM after API
assertions, waits for the Java child to close, then scans every current-run plain or
gzip log fragment. Unexpected exit, shutdown timeout, missing/corrupt logs or a
captured secret cause failure without printing sensitive error details. It preserves
the dedicated test database. No credentials are printed.

Fast destination/seed and lifecycle/log guards (11 tests):
`node --test script/postgresql/seed-development.test.mjs script/postgresql/api-smoke-supervisor.test.mjs`.
Dependencies are java, node, unzip and psql; no automatic software installation.
Logs are in yudao-server/target/postgres-api-smoke/. This is API evidence, not browser
UI validation, business-private-file authorizer UAT or a production deployment.
