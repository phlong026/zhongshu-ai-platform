package cn.iocoder.yudao.server.postgres;

import cn.iocoder.yudao.module.infra.dal.mysql.job.JobLogMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.logger.ApiAccessLogMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.logger.ApiErrorLogMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.PrivateFileMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.PrivateFileDO;
import cn.iocoder.yudao.module.infra.controller.admin.job.vo.log.JobLogPageReqVO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.incrementer.PostgreKeyGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL only: mvn -Ppostgres-it -pl yudao-server -am verify. Never falls back to H2. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresFoundationIntegrationTest {
    private static final String LOCATION = "classpath:db/migration/postgresql";
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final List<String> createdSchemas = new ArrayList<>();
    private DriverManagerDataSource admin;
    private JdbcTemplate jdbc;
    private SqlSessionFactory sessions;
    private String schema;
    private String secondSchema;
    record CleanupCase(String table, String timeColumn, Class<?> mapper, String method) {}
    static Stream<CleanupCase> cleanupCases() {
        return Stream.of(
            new CleanupCase("infra_job_log", "create_time", JobLogMapper.class, "deleteByCreateTimeLt"),
            new CleanupCase("infra_api_access_log", "create_time", ApiAccessLogMapper.class, "deleteByCreateTimeLt"),
            new CleanupCase("infra_api_error_log", "create_time", ApiErrorLogMapper.class, "deleteByCreateTimeLt"),
            new CleanupCase("system_oauth2_access_token", "expires_time", OAuth2AccessTokenMapper.class, "deleteByExpiresTimeLt"),
            new CleanupCase("system_oauth2_refresh_token", "expires_time", OAuth2RefreshTokenMapper.class, "deleteByExpiresTimeLt"));
    }
    @BeforeAll
    void connectAndMigrate() throws Exception {
        String url = required("TEST_PG_URL");
        assertTrue(url.startsWith("jdbc:postgresql://"), "Use a disposable real PostgreSQL database");
        admin = new DriverManagerDataSource(url, required("TEST_PG_USERNAME"), required("TEST_PG_PASSWORD"));
        try (Connection c = admin.getConnection()) {
            assertEquals("PostgreSQL", c.getMetaData().getDatabaseProductName());
            System.out.println("PostgreSQL integration server: " + c.getMetaData().getDatabaseProductVersion());
        }
        schema = createSchema(); secondSchema = createSchema();
        DriverManagerDataSource dataSource = scoped(schema);
        jdbc = new JdbcTemplate(dataSource);
        assertEquals(4, flyway(schema, null).migrate().migrationsExecuted);
        // Actual MyBatis-Plus parsing, binding, CRUD injection and mapper invocation.
        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("postgres-it", new JdbcTransactionFactory(), dataSource));
        config.setMapUnderscoreToCamelCase(true);
        GlobalConfig global = new GlobalConfig();
        global.setSqlInjector(new DefaultSqlInjector());
        global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.INPUT)
            .setLogicDeleteValue("true").setLogicNotDeleteValue("false")
            .setKeyGenerators(List.of(new PostgreKeyGenerator())));
        GlobalConfigUtils.setGlobalConfig(config, global);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        config.addInterceptor(interceptor);
        cleanupCases().forEach(c -> config.addMapper(c.mapper()));
        config.addMapper(PrivateFileMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(config);
    }
    @AfterAll
    void removeOnlyCreatedFixtures() {
        if (admin == null) return;
        JdbcTemplate root = new JdbcTemplate(admin);
        for (String own : createdSchemas) {
            assertTrue(own.matches("zs_it_[a-f0-9]{32}"));
            root.execute("DROP SCHEMA " + own + " CASCADE");
        }
    }
    @ParameterizedTest(name = "bounded physical cleanup: {0}")
    @MethodSource("cleanupCases")
    void fiveMappersDeleteOnlyOldBoundedRows(CleanupCase c) throws Exception {
        jdbc.execute("TRUNCATE " + c.table());
        for (long id = 1; id <= 5; id++) seed(c, id, CUTOFF.minusSeconds(1), id == 2);
        seed(c, 6, CUTOFF, false); seed(c, 7, CUTOFF.plusSeconds(1), false);
        assertEquals(0, cleanup(c, CUTOFF, null));
        assertEquals(0, cleanup(c, CUTOFF, 0));
        assertEquals(0, cleanup(c, CUTOFF, -1));
        assertEquals(0, cleanup(c, null, 2));
        assertEquals(7, count(c));
        assertEquals(2, cleanup(c, CUTOFF, 2));
        assertEquals(List.of(3L, 4L, 5L, 6L, 7L), ids(c));
        assertEquals(2, cleanup(c, CUTOFF, 2));
        assertEquals(1, cleanup(c, CUTOFF, 2));
        assertEquals(0, cleanup(c, CUTOFF, 2));
        assertEquals(List.of(6L, 7L), ids(c));
    }
    @ParameterizedTest(name = "concurrent cleanup remains bounded: {0}")
    @MethodSource("cleanupCases")
    void concurrentCleanupNeverExceedsRequestedBatch(CleanupCase c) throws Exception {
        jdbc.execute("TRUNCATE " + c.table());
        for (long id = 1; id <= 20; id++) seed(c, id, CUTOFF.minusSeconds(1), false);
        seed(c, 21, CUTOFF, false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> delete = () -> { start.await(); return cleanup(c, CUTOFF, 3); };
            Future<Integer> first = pool.submit(delete), second = pool.submit(delete);
            start.countDown();
            int a = first.get(20, TimeUnit.SECONDS), b = second.get(20, TimeUnit.SECONDS);
            assertTrue(a >= 0 && a <= 3 && b >= 0 && b <= 3);
            assertEquals(21 - a - b, count(c));
            // Statements may select the same candidates; a future run drains the remainder.
            while (cleanup(c, CUTOFF, 3) != 0) { /* drain */ }
            assertEquals(List.of(21L), ids(c));
        } finally { pool.shutdownNow(); }
    }
    @Test
    void emptyMigrationUpgradeAndRepeatAreSafe() {
        Flyway first = flyway(schema, null);
        assertEquals(0, first.migrate().migrationsExecuted);
        assertThrows(Exception.class, first::clean);
        assertEquals(1, flyway(secondSchema, "1").migrate().migrationsExecuted);
        JdbcTemplate other = new JdbcTemplate(scoped(secondSchema));
        other.update("INSERT INTO infra_config (id, category, name, config_key, value, type, visible) "
            + "VALUES (999, 'test', 'preserved', 'pg.it', 'retained', 1, true)");
        assertEquals(3, flyway(secondSchema, null).migrate().migrationsExecuted);
        assertEquals("retained", other.queryForObject("SELECT value FROM infra_config WHERE id=999", String.class));
        assertEquals(0, flyway(secondSchema, null).migrate().migrationsExecuted);
        assertEquals(columns(schema), columns(secondSchema));
        assertEquals(55, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
            + "WHERE table_schema=? AND table_name<>'flyway_schema_history'", Integer.class, schema));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM system_users", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM system_tenant", Integer.class));
    }
    @Test
    void postgresMetadataSequenceBooleanTimestampAndPagination() {
        Long a = jdbc.queryForObject("SELECT nextval('infra_config_seq')", Long.class);
        Long b = jdbc.queryForObject("SELECT nextval('infra_config_seq')", Long.class);
        assertNotNull(a); assertEquals(a + 1, b);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS boolean)", Boolean.class, true));
        assertEquals(CUTOFF, jdbc.queryForObject("SELECT CAST(? AS timestamp)", LocalDateTime.class, CUTOFF));
        assertEquals(List.of(3, 4), jdbc.queryForList("SELECT n FROM generate_series(1, 5) n ORDER BY n LIMIT 2 OFFSET 2", Integer.class));
        assertEquals("name", jdbc.queryForObject("SELECT CAST(? AS jsonb)->>'label'", String.class, "{\"label\":\"name\"}"));
        assertEquals("boolean", jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
            + "WHERE table_schema=? AND table_name='system_users' AND column_name='deleted'", String.class, schema));
    }
    @Test
    void actualMapperPaginationUsesPostgresDialect() {
        CleanupCase logs = cleanupCases().findFirst().orElseThrow();
        jdbc.execute("TRUNCATE infra_job_log");
        for (long id = 1; id <= 5; id++) seed(logs, id, CUTOFF, false);
        try (SqlSession session = sessions.openSession(true)) {
            JobLogPageReqVO request = new JobLogPageReqVO(); request.setPageNo(2); request.setPageSize(2);
            var page = session.getMapper(JobLogMapper.class).selectPage(request);
            assertEquals(5L, page.getTotal());
            assertEquals(List.of(3L, 2L), page.getList().stream().map(row -> row.getId()).toList());
        }
    }
    @Test
    void privateFileMapperRoundTripsBytesAndRequiresMatchingTenant() {
        String id = UUID.randomUUID().toString(); byte[] bytes = {0, 1, 127, -128, -1};
        try (SqlSession session = sessions.openSession(true)) {
            PrivateFileMapper mapper = session.getMapper(PrivateFileMapper.class);
            PrivateFileDO file = new PrivateFileDO();
            file.setId(id); file.setTenantId(99001L); file.setObjectType("pg_test_object");
            file.setObjectId("object-1"); file.setPurpose("verification"); file.setName("test.bin");
            file.setType("application/octet-stream"); file.setSize((long) bytes.length); file.setContent(bytes);
            file.setCreateTime(CUTOFF); file.setUpdateTime(CUTOFF); file.setDeleted(false);
            assertEquals(1, mapper.insert(file));
            assertNull(mapper.selectMetadata(id, 99002L)); assertNull(mapper.selectContent(id, 99002L));
            assertNull(mapper.selectMetadata(id, 99001L).getContent());
            assertArrayEquals(bytes, mapper.selectContent(id, 99001L).getContent());
            jdbc.update("UPDATE infra_private_file SET deleted=true WHERE id=?", id);
            assertNull(mapper.selectContent(id, 99001L));
        }
        assertThrows(Exception.class, () -> jdbc.update("UPDATE infra_private_file SET size=0 WHERE id=?", id));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE infra_private_file SET size=10485761 WHERE id=?", id));
    }
    @Test
    void runtimeAccountCanUseBusinessTablesButCannotChangeSchemaOrHistory() {
        String username = required("TEST_PG_RUNTIME_USERNAME");
        assertTrue(username.matches("[a-z][a-z0-9_]{0,62}")); assertNotEquals(admin.getUsername(), username);
        jdbc.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + username);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + schema + ".infra_private_file TO " + username);
        DriverManagerDataSource runtime = new DriverManagerDataSource(admin.getUrl(), username, required("TEST_PG_RUNTIME_PASSWORD"));
        Properties properties = new Properties(); properties.setProperty("currentSchema", schema);
        runtime.setConnectionProperties(properties);
        JdbcTemplate low = new JdbcTemplate(runtime);
        assertFalse(Boolean.TRUE.equals(low.queryForObject("SELECT rolsuper OR rolcreatedb OR rolcreaterole "
            + "FROM pg_roles WHERE rolname=current_user", Boolean.class)));
        assertNotNull(low.queryForObject("SELECT count(*) FROM infra_private_file", Integer.class));
        assertThrows(Exception.class, () -> low.execute("CREATE TABLE forbidden_ddl (id int)"));
        assertThrows(Exception.class, () -> low.execute("DELETE FROM flyway_schema_history"));
    }
    @Test
    void refusesNonEmptyUnversionedSchema() {
        String unversioned = createSchema();
        new JdbcTemplate(scoped(unversioned)).execute("CREATE TABLE keep_me (id integer)");
        assertThrows(Exception.class, () -> flyway(unversioned, null).migrate());
    }
    private int cleanup(CleanupCase c, LocalDateTime cutoff, Integer limit) throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            Object mapper = session.getMapper(c.mapper());
            return (Integer) c.mapper().getMethod(c.method(), LocalDateTime.class, Integer.class).invoke(mapper, cutoff, limit);
        }
    }
    private void seed(CleanupCase c, long id, LocalDateTime time, boolean deleted) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        jdbc.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema=? "
            + "AND table_name=? AND is_nullable='NO' AND column_default IS NULL ORDER BY ordinal_position", rs -> {
                String column = rs.getString(1), type = rs.getString(2);
                values.put(column, type.contains("timestamp") ? time : type.equals("boolean") ? false
                    : type.contains("int") ? 1L : "");
            }, schema, c.table());
        values.put("id", id); values.put(c.timeColumn(), time); values.put("deleted", deleted);
        jdbc.update("INSERT INTO " + c.table() + " (" + String.join(",", values.keySet()) + ") VALUES ("
            + String.join(",", Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    private int count(CleanupCase c) { return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM " + c.table(), Integer.class)); }
    private List<Long> ids(CleanupCase c) { return jdbc.queryForList("SELECT id FROM " + c.table() + " ORDER BY id", Long.class); }
    private List<Map<String, Object>> columns(String target) {
        return new JdbcTemplate(admin).queryForList("SELECT table_name,column_name,data_type,is_nullable, "
            + "replace(column_default, ? || '.', '<schema>.') AS column_default "
            + "FROM information_schema.columns WHERE table_schema=? ORDER BY table_name,ordinal_position", target, target);
    }
    private String createSchema() {
        String name = "zs_it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + name); createdSchemas.add(name); return name;
    }
    private DriverManagerDataSource scoped(String target) {
        DriverManagerDataSource result = new DriverManagerDataSource(admin.getUrl(), admin.getUsername(), admin.getPassword());
        Properties properties = new Properties(); properties.setProperty("currentSchema", target);
        result.setConnectionProperties(properties); return result;
    }
    private Flyway flyway(String target, String version) {
        var config = Flyway.configure().dataSource(admin).locations(LOCATION).schemas(target).defaultSchema(target)
            .createSchemas(false).baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true);
        if (version != null) config.target(version); return config.load();
    }
    private static String required(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " is required; PostgreSQL integration must not silently skip");
        assertFalse(value.isBlank(), name + " must not be blank"); return value;
    }
}
