package cn.iocoder.yudao.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** Fail before pools initialize; diagnostics never contain secret values. */
public class PostgresEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("postgres"))) return;
        if (environment.acceptsProfiles(Profiles.of("local", "dev"))) {
            throw new IllegalStateException("postgres cannot be combined with upstream local/dev profiles");
        }
        String url = required(environment, "spring.datasource.dynamic.datasource.master.url", "ZS_PG_URL");
        if (!url.startsWith("jdbc:postgresql://")) throw new IllegalStateException("ZS_PG_URL must use jdbc:postgresql://");
        required(environment, "spring.datasource.dynamic.datasource.master.username", "ZS_PG_USERNAME");
        required(environment, "spring.datasource.dynamic.datasource.master.password", "ZS_PG_PASSWORD");
        required(environment, "spring.data.redis.host", "ZS_REDIS_HOST");
        required(environment, "spring.data.redis.password", "ZS_REDIS_PASSWORD");
        required(environment, "mybatis-plus.encryptor.password", "ZS_APPLICATION_MYBATIS_PLUS_ENCRYPTOR_PASSWORD");
        if (environment.getProperty("yudao.security.mock-enable", Boolean.class, false)) {
            throw new IllegalStateException("Mock authentication is forbidden in the postgres profile");
        }
        if (environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            String user = required(environment, "spring.flyway.user", "ZS_PG_MIGRATION_USERNAME");
            required(environment, "spring.flyway.password", "ZS_PG_MIGRATION_PASSWORD");
            if (user.equals(environment.getProperty("spring.datasource.dynamic.datasource.master.username"))) {
                throw new IllegalStateException("Use a separate migration account, not the runtime account");
            }
        }
    }
    private static String required(ConfigurableEnvironment env, String property, String variable) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("Required PostgreSQL profile setting is missing: " + variable);
        }
        return value;
    }
    @Override
    public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }
}
