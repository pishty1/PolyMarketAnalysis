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

import java.util.ArrayList;
import java.util.List;

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

    /** Helper to clear the table between tests */
    private void clearTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM market_stats");
        }
    }

    /** Helper to count rows in the table */
    private int countRows() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM market_stats");
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void testSinkWritesToDatabase() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 3. Create Dummy Data (Tuple3 matches your aggregation output)
        // (parent_entity_id, window_end_timestamp, count)
        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                Tuple3.of(100L, 1700000000000L, 5),
                Tuple3.of(101L, 1700000000000L, 10),
                Tuple3.of(102L, 1700000000000L, 15),
                Tuple3.of(103L, 1700000000000L, 25),
                Tuple3.of(104L, 1700000000000L, 30),
                Tuple3.of(105L, 1700000000000L, 35),
                // Duplicate keys should trigger ON CONFLICT UPDATE
                Tuple3.of(100L, 1700000000000L, 20),
                Tuple3.of(102L, 1700000000000L, 50),
                Tuple3.of(104L, 1700000000000L, 100),
                // Update the same keys again to test multiple updates
                Tuple3.of(100L, 1700000000000L, 99),
                Tuple3.of(105L, 1700000000000L, 1)
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

            // Row 1: ID 100 - Updated 3 times: 5 -> 20 -> 99
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("100");
            assertThat(rs.getInt("count")).isEqualTo(99);

            // Row 2: ID 101 - Never updated
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("101");
            assertThat(rs.getInt("count")).isEqualTo(10);

            // Row 3: ID 102 - Updated once: 15 -> 50
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("102");
            assertThat(rs.getInt("count")).isEqualTo(50);

            // Row 4: ID 103 - Never updated
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("103");
            assertThat(rs.getInt("count")).isEqualTo(25);

            // Row 5: ID 104 - Updated once: 30 -> 100
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("104");
            assertThat(rs.getInt("count")).isEqualTo(100);

            // Row 6: ID 105 - Updated once: 35 -> 1
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("105");
            assertThat(rs.getInt("count")).isEqualTo(1);

            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    public void testEmptyStreamNoWrites() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Create an empty stream using fromCollection with explicit type info
        List<Tuple3<Long, Long, Integer>> emptyList = new ArrayList<>();
        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromCollection(
                emptyList,
                org.apache.flink.api.common.typeinfo.Types.TUPLE(
                        org.apache.flink.api.common.typeinfo.Types.LONG,
                        org.apache.flink.api.common.typeinfo.Types.LONG,
                        org.apache.flink.api.common.typeinfo.Types.INT
                )
        );

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Empty Sink");

        // Verify no rows were written
        assertThat(countRows()).isEqualTo(0);
    }

    @Test
    public void testBoundaryValues() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Test boundary values for parent_entity_id and count
        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                // Zero values
                Tuple3.of(0L, 1700000000000L, 0),
                // Large parent_entity_id
                Tuple3.of(Long.MAX_VALUE, 1700000000000L, 1),
                // Negative parent_entity_id (if allowed)
                Tuple3.of(-1L, 1700000000000L, 5),
                // Large count
                Tuple3.of(1L, 1700000000000L, Integer.MAX_VALUE),
                // Negative count
                Tuple3.of(2L, 1700000000000L, -100),
                // Min Long value
                Tuple3.of(Long.MIN_VALUE, 1700000000000L, 42)
        );

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Boundary Values");

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Verify all 6 rows were written
            assertThat(countRows()).isEqualTo(6);

            // Verify specific boundary values
            ResultSet rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats WHERE parent_entity_id = '0'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(0);

            rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats WHERE parent_entity_id = '" + Long.MAX_VALUE + "'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(1);

            rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats WHERE parent_entity_id = '-1'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(5);

            rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats WHERE parent_entity_id = '1'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(Integer.MAX_VALUE);

            rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats WHERE parent_entity_id = '2'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(-100);
        }
    }

    @Test
    public void testMultipleTimeWindowsForSameEntity() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Same entity with different timestamps (composite PK test)
        long baseTimestamp = 1700000000000L;
        long oneHour = 3600000L;

        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                // Entity 100 across 5 different time windows
                Tuple3.of(100L, baseTimestamp, 10),
                Tuple3.of(100L, baseTimestamp + oneHour, 20),
                Tuple3.of(100L, baseTimestamp + 2 * oneHour, 30),
                Tuple3.of(100L, baseTimestamp + 3 * oneHour, 40),
                Tuple3.of(100L, baseTimestamp + 4 * oneHour, 50),
                // Entity 101 across 3 different time windows
                Tuple3.of(101L, baseTimestamp, 100),
                Tuple3.of(101L, baseTimestamp + oneHour, 200),
                Tuple3.of(101L, baseTimestamp + 2 * oneHour, 300),
                // Update entity 100's first window
                Tuple3.of(100L, baseTimestamp, 15),
                // Update entity 101's second window
                Tuple3.of(101L, baseTimestamp + oneHour, 250)
        );

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Multiple Time Windows");

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Should have 8 rows total (5 for entity 100 + 3 for entity 101)
            assertThat(countRows()).isEqualTo(8);

            // Verify entity 100's windows
            ResultSet rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '100' ORDER BY window_timestamp");
            
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(15); // Updated from 10 to 15
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(20);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(30);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(40);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(50);
            assertThat(rs.next()).isFalse();

            // Verify entity 101's windows
            rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '101' ORDER BY window_timestamp");
            
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(100);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(250); // Updated from 200 to 250
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(300);
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    public void testTimestampPrecision() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Test various timestamp values including edge cases
        long epoch = 0L;
        long millisPrecision = 1700000000001L; // Has milliseconds
        long farFuture = 4102444800000L; // Year 2100

        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                Tuple3.of(1L, epoch, 1),
                Tuple3.of(2L, millisPrecision, 2),
                Tuple3.of(3L, farFuture, 3)
        );

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Timestamp Precision");

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Verify epoch timestamp
            ResultSet rs = stmt.executeQuery("SELECT window_timestamp FROM market_stats WHERE parent_entity_id = '1'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getTimestamp("window_timestamp").getTime()).isEqualTo(epoch);

            // Verify millisecond precision is preserved
            rs = stmt.executeQuery("SELECT window_timestamp FROM market_stats WHERE parent_entity_id = '2'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getTimestamp("window_timestamp").getTime()).isEqualTo(millisPrecision);

            // Verify far future timestamp
            rs = stmt.executeQuery("SELECT window_timestamp FROM market_stats WHERE parent_entity_id = '3'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getTimestamp("window_timestamp").getTime()).isEqualTo(farFuture);
        }
    }

    @Test
    public void testLargeDataset() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Generate a larger dataset with 100 entities across 5 time windows
        List<Tuple3<Long, Long, Integer>> data = new ArrayList<>();
        long baseTimestamp = 1700000000000L;
        long oneHour = 3600000L;

        for (int entityId = 1; entityId <= 100; entityId++) {
            for (int window = 0; window < 5; window++) {
                data.add(Tuple3.of((long) entityId, baseTimestamp + window * oneHour, entityId * 10 + window));
            }
        }

        // Add some updates (overwrite every 10th entity's first window)
        for (int entityId = 10; entityId <= 100; entityId += 10) {
            data.add(Tuple3.of((long) entityId, baseTimestamp, 9999));
        }

        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromCollection(data);

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Large Dataset");

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Should have 500 rows (100 entities * 5 windows)
            assertThat(countRows()).isEqualTo(500);

            // Verify entity 10's first window was updated to 9999
            ResultSet rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '10' ORDER BY window_timestamp LIMIT 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(9999);

            // Verify entity 50's first window was updated to 9999
            rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '50' ORDER BY window_timestamp LIMIT 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(9999);

            // Verify entity 15 (not updated) has original value
            rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '15' ORDER BY window_timestamp LIMIT 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(150); // 15 * 10 + 0

            // Verify entity 100's windows
            rs = stmt.executeQuery(
                    "SELECT count FROM market_stats WHERE parent_entity_id = '100' ORDER BY window_timestamp");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(9999); // Updated
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(1001); // 100 * 10 + 1
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(1002); // 100 * 10 + 2
        }
    }

    @Test
    public void testInterleavedUpdates() throws Exception {
        clearTable();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        long timestamp = 1700000000000L;

        // Interleaved inserts and updates across multiple entities
        DataStream<Tuple3<Long, Long, Integer>> stream = env.fromElements(
                Tuple3.of(1L, timestamp, 10),
                Tuple3.of(2L, timestamp, 20),
                Tuple3.of(1L, timestamp, 11),  // Update 1
                Tuple3.of(3L, timestamp, 30),
                Tuple3.of(2L, timestamp, 21),  // Update 2
                Tuple3.of(1L, timestamp, 12),  // Update 1 again
                Tuple3.of(4L, timestamp, 40),
                Tuple3.of(3L, timestamp, 31),  // Update 3
                Tuple3.of(2L, timestamp, 22),  // Update 2 again
                Tuple3.of(5L, timestamp, 50),
                Tuple3.of(1L, timestamp, 13),  // Update 1 third time
                Tuple3.of(4L, timestamp, 41),  // Update 4
                Tuple3.of(3L, timestamp, 32),  // Update 3 again
                Tuple3.of(2L, timestamp, 23),  // Update 2 third time
                Tuple3.of(5L, timestamp, 51)   // Update 5
        );

        stream.sinkTo(new PolymarketJdbcSink(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ));

        env.execute("Test Interleaved Updates");

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Should have 5 rows
            assertThat(countRows()).isEqualTo(5);

            ResultSet rs = stmt.executeQuery("SELECT parent_entity_id, count FROM market_stats ORDER BY parent_entity_id");

            // Verify final values after all updates
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("1");
            assertThat(rs.getInt("count")).isEqualTo(13);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("2");
            assertThat(rs.getInt("count")).isEqualTo(23);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("3");
            assertThat(rs.getInt("count")).isEqualTo(32);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("4");
            assertThat(rs.getInt("count")).isEqualTo(41);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("parent_entity_id")).isEqualTo("5");
            assertThat(rs.getInt("count")).isEqualTo(51);

            assertThat(rs.next()).isFalse();
        }
    }
}
