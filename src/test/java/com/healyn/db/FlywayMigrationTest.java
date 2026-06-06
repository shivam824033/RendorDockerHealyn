package com.healyn.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void all_migrations_apply_to_fresh_database() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Pins the latest migration version as a tripwire — bump it with every new migration.
        MigrationInfo current = flyway.info().current();
        assertThat(current.getVersion().getVersion()).isEqualTo("15");
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(15);

        DataSource ds = flyway.getConfiguration().getDataSource();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_account_one_primary'")) {
                assertThat(rs.next()).as("partial unique index for primary patient").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'blackout_windows_no_overlap' and contype = 'x'")) {
                assertThat(rs.next()).as("blackout EXCLUDE constraint").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'appointments_no_physio_overlap' and contype = 'x'")) {
                assertThat(rs.next()).as("appointment physio overlap EXCLUDE constraint").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_tables where tablename = 'discussion_messages'")) {
                assertThat(rs.next()).as("discussion_messages table exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_tables where tablename = 'discussion_read_markers'")) {
                assertThat(rs.next()).as("discussion_read_markers table exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_tables where tablename = 'fcm_tokens'")) {
                assertThat(rs.next()).as("fcm_tokens table exists").isTrue();
            }
        }
    }
}
