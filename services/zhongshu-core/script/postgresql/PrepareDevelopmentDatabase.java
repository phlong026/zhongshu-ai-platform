import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.URI;
import java.sql.DriverManager;
/** JDK 17 source-file mode; use the packaged application's dependency classpath. */
class PrepareDevelopmentDatabase {
    public static void main(String[] args) throws Exception {
        if (!"disposable-local-test-only".equals(System.getenv("ZS_DEV_SEED_CONFIRM"))) {
            throw new IllegalStateException("Explicit disposable fixture confirmation required");
        }
        if (args.length == 1 && args[0].equals("hash")) {
            String password = required("ZS_DEV_ADMIN_PASSWORD");
            if (password.length() != 16) throw new IllegalArgumentException("Use a generated 16-character test password");
            System.out.print(new BCryptPasswordEncoder().encode(password)); return;
        }
        String url = required("ZS_PG_URL");
        if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("PostgreSQL only");
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (!("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
            || !uri.getPath().matches("/[a-zA-Z_][a-zA-Z0-9_]*_test") || uri.getUserInfo() != null
            || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Use a plain loopback *_test JDBC URL with explicit port");
        }
        String owner = required("ZS_PG_MIGRATION_USERNAME"), runtime = required("ZS_PG_USERNAME");
        if (!runtime.matches("[a-z][a-z0-9_]{0,62}") || runtime.equals(owner)) {
            throw new IllegalArgumentException("Use separate simple-named migration/runtime roles");
        }
        String password = required("ZS_PG_MIGRATION_PASSWORD");
        Flyway.configure().dataSource(url, owner, password).defaultSchema("public").schemas("public")
            .locations("classpath:db/migration/postgresql").baselineOnMigrate(false).createSchemas(false)
            .cleanDisabled(true).validateOnMigrate(true).load().migrate();
        try (var connection = DriverManager.getConnection(url, owner, password);
             var statement = connection.createStatement(); var reader = connection.createStatement()) {
            try (var tables = reader.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
                while (tables.next()) {
                    String table = tables.getString(1);
                    if (table.matches("(?:system|infra|qrtz)_[a-z0-9_]+")) {
                        statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON public.\"" + table + "\" TO " + runtime);
                    }
                }
            }
            try (var sequences = reader.executeQuery("SELECT sequencename FROM pg_sequences WHERE schemaname='public'")) {
                while (sequences.next()) {
                    String sequence = sequences.getString(1);
                    if (sequence.matches("(?:system|infra)_[a-z0-9_]+")) {
                        statement.execute("GRANT USAGE, SELECT ON SEQUENCE public.\"" + sequence + "\" TO " + runtime);
                    }
                }
            }
            statement.execute("REVOKE ALL ON public.flyway_schema_history FROM " + runtime);
        }
        System.out.println("Development schema migrated; only product table/sequence rights granted to the separate runtime role.");
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
