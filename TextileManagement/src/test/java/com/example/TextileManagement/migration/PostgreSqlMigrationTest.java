package com.example.TextileManagement.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
class PostgreSqlMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void migratesFromV1ToLatestWithPostgresIndexesAndFixedPointColumns() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

        try (Connection connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT data_type, numeric_scale FROM information_schema.columns "
                    + "WHERE table_name = 'taka_entries' AND column_name = 'meters'")) {
                assertTrue(result.next());
                assertEquals("numeric", result.getString(1));
                assertEquals(2, result.getInt(2));
            }
            try (ResultSet result = statement.executeQuery("SELECT indexdef FROM pg_indexes "
                    + "WHERE indexname = 'idx_sales_pending_company_due'")) {
                assertTrue(result.next());
                assertTrue(result.getString(1).contains("WHERE (status ="));
                assertTrue(result.getString(1).contains("PENDING"));
            }
            seedPendingPaymentData(statement);
            assertPlanUses(statement, "sales", "idx_sales_pending_company_due");
            assertPlanUses(statement, "purchases", "idx_purchases_pending_company_due");
        }
    }

    private void seedPendingPaymentData(Statement statement) throws Exception {
        statement.executeUpdate("INSERT INTO workspaces (name, slug, status) VALUES ('Plan test', 'plan-test', 'ACTIVE')");
        statement.executeUpdate("INSERT INTO company_profiles (workspace_id, trade_name) VALUES (1, 'Plan test company')");
        statement.executeUpdate("INSERT INTO customers (company_id, name) VALUES (1, 'Plan customer')");
        statement.executeUpdate("INSERT INTO suppliers (company_id, name) VALUES (1, 'Plan supplier')");
        statement.executeUpdate("""
                INSERT INTO sales (sale_date, customer_id, company_id, quality, challan_no, challan_count,
                                   financial_year, due_date, status, rate, amount, total_meters)
                SELECT CURRENT_DATE, 1, 1, 'PLAN', number, 1, '2026-2027',
                       CURRENT_DATE + (number % 45), CASE WHEN number % 5 = 0 THEN 'PENDING' ELSE 'PAID' END,
                       1.00, 1.00, 1.00
                FROM generate_series(1, 5000) AS number
                """);
        statement.executeUpdate("""
                INSERT INTO purchases (purchase_date, supplier_id, company_id, material_type, quantity, rate, amount,
                                       due_date, status)
                SELECT CURRENT_DATE, 1, 1, 'BEAM', 1.00, 1.00, 1.00,
                       CURRENT_DATE + (number % 45), CASE WHEN number % 5 = 0 THEN 'PENDING' ELSE 'PAID' END
                FROM generate_series(1, 5000) AS number
                """);
        statement.execute("ANALYZE sales");
        statement.execute("ANALYZE purchases");
    }

    private void assertPlanUses(Statement statement, String table, String indexName) throws Exception {
        try (ResultSet result = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS) SELECT id, due_date FROM " + table
                + " WHERE company_id = 1 AND status = 'PENDING' ORDER BY due_date, id LIMIT 25")) {
            StringBuilder plan = new StringBuilder();
            while (result.next()) {
                plan.append(result.getString(1)).append('\n');
            }
            assertTrue(plan.toString().contains(indexName), plan.toString());
        }
    }
}
