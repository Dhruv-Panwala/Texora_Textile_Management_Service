package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Makes physical quantity storage deterministic without silently rounding
 * historical data. This migration is deliberately database-aware so the H2
 * development profile and PostgreSQL production schema follow the same rules.
 */
public class V25__fixed_point_tenant_integrity extends BaseJavaMigration {
    private static final BigDecimal MAX_METERS_PER_ENTRY = new BigDecimal("10000000.00");
    private static final int MAX_TAKA_NUMBER = 999_999_999;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");

        assertTwoDecimalScale(connection, "taka_entries", "meters");
        assertTwoDecimalScale(connection, "saved_taka_entries", "meters");
        assertTwoDecimalScale(connection, "sales", "total_meters");
        assertTwoDecimalScale(connection, "purchases", "quantity");
        assertNoCrossCompanyRelationships(connection);
        assertNoDuplicateCaseInsensitiveValues(connection);
        assertTakaOrdering(connection);

        alterToNumeric(connection, postgres, "taka_entries", "meters", "NUMERIC(12, 2)");
        alterToNumeric(connection, postgres, "saved_taka_entries", "meters", "NUMERIC(12, 2)");
        alterToNumeric(connection, postgres, "sales", "total_meters", "NUMERIC(14, 2)");
        alterToNumeric(connection, postgres, "purchases", "quantity", "NUMERIC(14, 2)");

        execute(connection, "ALTER TABLE taka_entries ALTER COLUMN entry_order SET NOT NULL");
        execute(connection, "DROP INDEX IF EXISTS ix_taka_entries_sale_order");
        execute(connection, "CREATE UNIQUE INDEX ux_taka_entries_sale_entry_order ON taka_entries (sale_id, entry_order)");

        execute(connection, "ALTER TABLE taka_entries ADD CONSTRAINT chk_taka_entries_taka_no_limit "
                + "CHECK (taka_no <= " + MAX_TAKA_NUMBER + ")");
        execute(connection, "ALTER TABLE taka_entries ADD CONSTRAINT chk_taka_entries_meters_limit "
                + "CHECK (meters <= " + MAX_METERS_PER_ENTRY.toPlainString() + ")");
        execute(connection, "ALTER TABLE saved_taka_entries ADD CONSTRAINT chk_saved_taka_entries_taka_no_positive "
                + "CHECK (taka_no > 0)");
        execute(connection, "ALTER TABLE saved_taka_entries ADD CONSTRAINT chk_saved_taka_entries_taka_no_limit "
                + "CHECK (taka_no <= " + MAX_TAKA_NUMBER + ")");
        execute(connection, "ALTER TABLE saved_taka_entries ADD CONSTRAINT chk_saved_taka_entries_meters_positive "
                + "CHECK (meters > 0)");
        execute(connection, "ALTER TABLE saved_taka_entries ADD CONSTRAINT chk_saved_taka_entries_meters_limit "
                + "CHECK (meters <= " + MAX_METERS_PER_ENTRY.toPlainString() + ")");
        execute(connection, "ALTER TABLE sales ADD CONSTRAINT chk_sales_total_meters_limit "
                + "CHECK (total_meters <= 2000000000.00)");
        execute(connection, "ALTER TABLE purchases ADD CONSTRAINT chk_purchases_quantity_limit "
                + "CHECK (quantity <= " + MAX_METERS_PER_ENTRY.toPlainString() + ")");

        execute(connection, "ALTER TABLE customers ADD CONSTRAINT uq_customers_id_company UNIQUE (id, company_id)");
        execute(connection, "ALTER TABLE suppliers ADD CONSTRAINT uq_suppliers_id_company UNIQUE (id, company_id)");
        execute(connection, "ALTER TABLE sales ADD CONSTRAINT fk_sales_customer_company "
                + "FOREIGN KEY (customer_id, company_id) REFERENCES customers (id, company_id)");
        execute(connection, "ALTER TABLE purchases ADD CONSTRAINT fk_purchases_supplier_company "
                + "FOREIGN KEY (supplier_id, company_id) REFERENCES suppliers (id, company_id)");

        if (postgres) {
            execute(connection, "CREATE UNIQUE INDEX uk_users_lower_username ON users (LOWER(username))");
            execute(connection, "CREATE UNIQUE INDEX uk_users_lower_email ON users (LOWER(email))");
            execute(connection, "CREATE UNIQUE INDEX uk_company_profiles_workspace_lower_trade_name "
                    + "ON company_profiles (workspace_id, LOWER(trade_name))");
            execute(connection, "CREATE INDEX idx_purchases_pending_company_due "
                    + "ON purchases (company_id, due_date, id) WHERE status = 'PENDING'");
            execute(connection, "CREATE INDEX idx_sales_pending_company_due "
                    + "ON sales (company_id, due_date, id) WHERE status = 'PENDING'");
        } else {
            execute(connection, "CREATE INDEX idx_purchases_pending_company_due "
                    + "ON purchases (company_id, status, due_date, id)");
            execute(connection, "CREATE INDEX idx_sales_pending_company_due "
                    + "ON sales (company_id, status, due_date, id)");
        }
    }

    private void assertTwoDecimalScale(Connection connection, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column
                + " IS NOT NULL AND ABS((" + column + " * 100) - ROUND(" + column + " * 100)) > 0.0000001";
        assertNoRows(connection, sql, table + "." + column + " contains values with more than two decimal places");
    }

    private void assertNoCrossCompanyRelationships(Connection connection) throws SQLException {
        assertNoRows(connection,
                "SELECT COUNT(*) FROM sales s JOIN customers c ON c.id = s.customer_id "
                        + "WHERE s.company_id <> c.company_id",
                "sales contains a customer from another company");
        assertNoRows(connection,
                "SELECT COUNT(*) FROM purchases p JOIN suppliers s ON s.id = p.supplier_id "
                        + "WHERE p.company_id <> s.company_id",
                "purchases contains a supplier from another company");
    }

    private void assertNoDuplicateCaseInsensitiveValues(Connection connection) throws SQLException {
        for (DuplicateCheck check : List.of(
                new DuplicateCheck("users", "username", null),
                new DuplicateCheck("users", "email", null),
                new DuplicateCheck("company_profiles", "trade_name", "workspace_id"))) {
            String grouping = check.scope() == null ? "LOWER(" + check.column() + ")"
                    : check.scope() + ", LOWER(" + check.column() + ")";
            String where = check.where() == null ? "" : " WHERE " + check.where();
            assertNoRows(connection, "SELECT COUNT(*) FROM (SELECT " + grouping + ", COUNT(*) AS matches FROM "
                    + check.table() + where + " GROUP BY " + grouping + " HAVING COUNT(*) > 1) duplicates",
                    check.table() + "." + check.column() + " contains case-insensitive duplicates");
        }
    }

    private void assertTakaOrdering(Connection connection) throws SQLException {
        assertNoRows(connection, "SELECT COUNT(*) FROM taka_entries WHERE entry_order IS NULL",
                "taka_entries contains rows without an entry order");
        assertNoRows(connection, "SELECT COUNT(*) FROM (SELECT sale_id, entry_order, COUNT(*) AS matches "
                + "FROM taka_entries GROUP BY sale_id, entry_order HAVING COUNT(*) > 1) duplicates",
                "taka_entries contains duplicate ordering within a sale");
    }

    private void alterToNumeric(Connection connection, boolean postgres, String table, String column, String type)
            throws SQLException {
        String sql = postgres
                ? "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE " + type
                : "ALTER TABLE " + table + " ALTER COLUMN " + column + " " + type;
        execute(connection, sql);
    }

    private void assertNoRows(Connection connection, String sql, String message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            if (result.next() && result.getLong(1) > 0) {
                throw new SQLException("V25 cannot continue: " + message + ". Resolve the data before retrying the migration.");
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record DuplicateCheck(String table, String column, String scope) {
        private String where() {
            return "users".equals(table) && "email".equals(column) ? "email IS NOT NULL" : null;
        }
    }
}
