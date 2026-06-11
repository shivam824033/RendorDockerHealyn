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
        assertThat(current.getVersion().getVersion()).isEqualTo("21");
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(21);

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
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_sequences where sequencename = 'patient_number_seq'")) {
                assertThat(rs.next()).as("patient_number_seq sequence exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'patients_patient_number_key' and contype = 'u'")) {
                assertThat(rs.next()).as("patient_number UNIQUE constraint").isTrue();
            }
            // The column DEFAULT must draw from the sequence so new rows are PAT-numbered
            // without any application round-trip.
            try (ResultSet rs = st.executeQuery(
                    "select column_default from information_schema.columns "
                            + "where table_name = 'patients' and column_name = 'patient_number'")) {
                assertThat(rs.next()).as("patient_number column exists").isTrue();
                assertThat(rs.getString(1)).as("patient_number default uses the sequence")
                        .contains("patient_number_seq");
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_tables where tablename = 'appointment_daily_counters'")) {
                assertThat(rs.next()).as("appointment_daily_counters table exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from information_schema.columns "
                            + "where table_name = 'appointments' and column_name = 'appointment_number'")) {
                assertThat(rs.next()).as("appointments.appointment_number column exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'appointments_appointment_number_key' and contype = 'u'")) {
                assertThat(rs.next()).as("appointment_number UNIQUE constraint").isTrue();
            }
            // V18 lineage: the child-kind enum type, the three columns, the self-FKs and indexes.
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_type where typname = 'appointment_child_kind'")) {
                assertThat(rs.next()).as("appointment_child_kind enum type exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select is_nullable from information_schema.columns "
                            + "where table_name = 'appointments' and column_name = 'root_appointment_id'")) {
                assertThat(rs.next()).as("appointments.root_appointment_id column exists").isTrue();
                assertThat(rs.getString(1)).as("root_appointment_id is NOT NULL").isEqualTo("NO");
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from information_schema.columns "
                            + "where table_name = 'appointments' and column_name = 'source_appointment_id'")) {
                assertThat(rs.next()).as("appointments.source_appointment_id column exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from information_schema.columns "
                            + "where table_name = 'appointments' and column_name = 'child_kind'")) {
                assertThat(rs.next()).as("appointments.child_kind column exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'appointments_root_fk' and contype = 'f'")) {
                assertThat(rs.next()).as("root_appointment_id self FK").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_constraint where conname = 'appointments_source_fk' and contype = 'f'")) {
                assertThat(rs.next()).as("source_appointment_id self FK").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_appointments_root'")) {
                assertThat(rs.next()).as("root_appointment_id index").isTrue();
            }
            // V19 timeline: the event-type enum, the append-only events table and its FK indexes.
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_type where typname = 'appointment_event_type'")) {
                assertThat(rs.next()).as("appointment_event_type enum type exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_tables where tablename = 'appointment_events'")) {
                assertThat(rs.next()).as("appointment_events table exists").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_appointment_events_appointment'")) {
                assertThat(rs.next()).as("appointment_events appointment index").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_appointment_events_related'")) {
                assertThat(rs.next()).as("appointment_events related index").isTrue();
            }
            // V20: the REJECTED appointment_status value (first-class request rejection).
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_enum e join pg_type t on t.oid = e.enumtypid "
                            + "where t.typname = 'appointment_status' and e.enumlabel = 'REJECTED'")) {
                assertThat(rs.next()).as("appointment_status REJECTED value exists").isTrue();
            }
            // V21: text_pattern_ops prefix-scan indexes backing the global search autocomplete.
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_appointments_number_pattern'")) {
                assertThat(rs.next()).as("appointment_number prefix-scan index").isTrue();
            }
            try (ResultSet rs = st.executeQuery(
                    "select 1 from pg_indexes where indexname = 'idx_patients_number_pattern'")) {
                assertThat(rs.next()).as("patient_number prefix-scan index").isTrue();
            }
        }
    }
}
