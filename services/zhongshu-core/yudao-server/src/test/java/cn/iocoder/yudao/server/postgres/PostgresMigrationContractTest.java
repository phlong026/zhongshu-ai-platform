package cn.iocoder.yudao.server.postgres;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Fast checks complement, never replace, the real PostgreSQL suite. */
class PostgresMigrationContractTest {
    @Test void baselineHasNoSeedDestructionOrManualTransactionBoundary() throws Exception {
        for (String migration : List.of("V1__system_infra_schema.sql", "V2__quartz_schema.sql",
                "V3__bounded_cleanup_indexes.sql", "V4__private_file_schema.sql")) {
            try (var stream = getClass().getResourceAsStream("/db/migration/postgresql/" + migration)) {
                assertNotNull(stream, "Missing packaged migration " + migration);
                String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$", "");
                assertFalse(sql.matches("(?is).*(?:^|[;\\n])\\s*(?:BEGIN|COMMIT|ROLLBACK|START TRANSACTION)\\b.*"),
                    migration + " must let Flyway own the transaction");
                assertFalse(sql.matches("(?is).*(?:^|[;\\n])\\s*(?:INSERT|DROP|TRUNCATE|DELETE|UPDATE)\\b.*"),
                    migration + " must remain schema-only and non-destructive");
            }
        }
    }
}
