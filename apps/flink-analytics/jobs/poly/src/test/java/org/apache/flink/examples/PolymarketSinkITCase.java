package org.apache.flink.examples;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class PolymarketSinkITCase {

    // 1. Spin up a Postgres Docker container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("polymarket")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    public static void start() throws Exception {
        postgres.start();
        // 2. Initialize the Database Schema
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE market_stats (
                    parent_entity_id VARCHAR(255) NOT NULL,
                    window_timestamp TIMESTAMP NOT NULL,
                    count INTEGER,
                    PRIMARY KEY (parent_entity_id, window_timestamp)
                )
            """);
        }
    }

    @AfterAll
    public static void stop() {
        postgres.stop();
    }

    @Test
    public void testSinkWritesToDatabase() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 3. Create Dummy Data (Tuple3 matches your aggregation output)
        // (parent_entity_id, window_end_timestamp, count)
        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                Tuple3.of(100L, 1700000000000L, 5),
                Tuple3.of(101L, 1700000000000L, 10),
                // This duplicate key should trigger the ON CONFLICT UPDATE if your SQL is correct
                Tuple3.of(100L, 1700000000000L, 20) 
        );

        // 4. Attach YOUR Custom Sink
        // We get the JDBC URL dynamically from the running container
        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        // 5. Execute the Job
        env.execute("Test Sink");

        // 6. Verify Data in Database
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats ORDER BY parent_entity_id");

            // Row 1: ID 100. 
            // We sent 5, then sent 20. Since it's ON CONFLICT UPDATE, we expect 20 (or 25 if you changed logic to sum).
            // Your sink code was: DO UPDATE SET count = EXCLUDED.count (so it replaces).
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("100");
            assertThat(rs.getInt("count")).isEqualTo(20);

            // Row 2: ID 101
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("101");
            assertThat(rs.getInt("count")).isEqualTo(10);

            assertThat(rs.next()).isFalse();
        }
    }
}
