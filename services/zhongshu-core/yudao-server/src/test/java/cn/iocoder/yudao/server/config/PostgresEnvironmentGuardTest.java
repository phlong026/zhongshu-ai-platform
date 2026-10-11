package cn.iocoder.yudao.server.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class PostgresEnvironmentGuardTest {
    private final PostgresEnvironmentGuard guard = new PostgresEnvironmentGuard();
    @Test void missingSettingsFailClearlyWithoutLoggingValues() {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("postgres");
        assertTrue(assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null))
            .getMessage().contains("ZS_PG_URL"));
    }
    @Test void legacyProfileCannotLeakIntoPostgres() {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("postgres", "local");
        assertTrue(assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null))
            .getMessage().contains("cannot be combined"));
    }
    @Test void rejectsMysqlAndMockAuthentication() {
        MockEnvironment env = configured();
        env.setProperty("spring.datasource.dynamic.datasource.master.url", "jdbc:mysql://localhost/example");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null));
        env.setProperty("spring.datasource.dynamic.datasource.master.url", "jdbc:postgresql://localhost/test");
        env.setProperty("yudao.security.mock-enable", "true");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null));
    }
    @Test void migrationRequiresSeparateCredentials() {
        MockEnvironment env = configured(); assertDoesNotThrow(() -> guard.postProcessEnvironment(env, null));
        env.setProperty("spring.flyway.enabled", "true");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null));
        env.setProperty("spring.flyway.user", "runtime"); env.setProperty("spring.flyway.password", "test-only-value");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(env, null));
        env.setProperty("spring.flyway.user", "migrator");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(env, null));
    }
    private MockEnvironment configured() {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("postgres");
        env.setProperty("spring.datasource.dynamic.datasource.master.url", "jdbc:postgresql://localhost/test");
        env.setProperty("spring.datasource.dynamic.datasource.master.username", "runtime");
        env.setProperty("spring.datasource.dynamic.datasource.master.password", "test-only-value");
        env.setProperty("spring.data.redis.host", "localhost"); env.setProperty("spring.data.redis.password", "test-only-value");
        env.setProperty("mybatis-plus.encryptor.password", "test-only-value"); return env;
    }
}
