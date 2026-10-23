package de.trademonitor.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Lossless copy migration: old v1 tables remain an immutable rollback source. */
@Component
@DependsOn("entityManagerFactory")
public class KiScannerSchemaMigration implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public KiScannerSchemaMigration(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ki_scanner_protocol_state "
                + "(id INTEGER PRIMARY KEY, migrated BOOLEAN NOT NULL, v2_active BOOLEAN NOT NULL)");
        // H2 ALTER TABLE commits implicitly: all identity DDL must precede the
        // transaction that copies either table. Explicit old IDs preserve PDF URLs.
        if (jdbc.queryForList("SELECT id FROM ki_scanner_protocol_state WHERE id=1").isEmpty()) {
            prepareSequence("ki_signals", "ki_signals_v2");
            prepareSequence("ki_documents", "ki_documents_v2");
        }
        transaction.executeWithoutResult(status -> {
            if (!jdbc.queryForList("SELECT id FROM ki_scanner_protocol_state WHERE id=1").isEmpty()) {
                return;
            }
            copyLegacy("ki_signals", "ki_signals_v2", false);
            copyLegacy("ki_documents", "ki_documents_v2", true);
            jdbc.update("INSERT INTO ki_scanner_protocol_state VALUES (1, TRUE, FALSE)");
        });
        // Staging is intentionally in memory: restart discards only uncommitted work.
        jdbc.update("UPDATE ki_sync_runs SET status='aborted', finished_at=CURRENT_TIMESTAMP, "
                + "note='Server restart: uncommitted staging discarded' WHERE status='running'");
    }

    private void copyLegacy(String oldTable, String newTable, boolean document) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + newTable, Long.class) != 0) {
            throw new IllegalStateException("KiScanner migration marker missing for nonempty " + newTable);
        }
        List<String> columns = new ArrayList<>();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            try (ResultSet tables = connection.getMetaData().getTables(null, null,
                    oldTable.toUpperCase(java.util.Locale.ROOT), new String[] {"TABLE"})) {
                if (!tables.next()) return;
            }
            try (ResultSet result = connection.getMetaData().getColumns(null, null,
                    oldTable.toUpperCase(java.util.Locale.ROOT), null)) {
                while (result.next()) {
                    String column = result.getString("COLUMN_NAME");
                    columns.add(column);
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("KiScanner legacy table inspection failed", e);
        }
        if (columns.isEmpty()) throw new IllegalStateException("No legacy columns: " + oldTable);
        String names = String.join(",", columns);
        String uid = document ? "CASE WHEN signal_id IS NULL THEN NULL ELSE 'legacy:v1' END" : "'legacy:v1'";
        String label = document ? "CASE WHEN signal_id IS NULL THEN NULL ELSE 'Legacy v1' END" : "'Legacy v1'";
        jdbc.update("INSERT INTO " + newTable + " (" + names + ",source_uid,source_label) "
                + "SELECT " + names + "," + uid + "," + label + " FROM " + oldTable);
        long oldCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + oldTable, Long.class);
        long newCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + newTable, Long.class);
        if (oldCount != newCount) throw new IllegalStateException("Legacy copy row mismatch");
    }

    private void prepareSequence(String oldTable, String newTable) {
        boolean exists;
        try (Connection connection = jdbc.getDataSource().getConnection();
                ResultSet tables = connection.getMetaData().getTables(null, null,
                        oldTable.toUpperCase(java.util.Locale.ROOT), new String[] {"TABLE"})) {
            exists = tables.next();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("KiScanner identity inspection failed", e);
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + newTable, Long.class) != 0)
            throw new IllegalStateException("KiScanner migration marker missing for nonempty " + newTable);
        Long max = exists ? jdbc.queryForObject("SELECT MAX(id) FROM " + oldTable, Long.class) : null;
        long next = max == null ? 1 : Math.addExact(max, 1);
        jdbc.execute("ALTER TABLE " + newTable + " ALTER COLUMN id RESTART WITH " + next);
    }

    public boolean v2Active() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT v2_active FROM ki_scanner_protocol_state WHERE id=1", Boolean.class));
    }

    public void activateV2() {
        jdbc.update("UPDATE ki_scanner_protocol_state SET v2_active=TRUE WHERE id=1");
    }
}
