package org.apache.flink.examples;

import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.planner.factories.TestValuesTableFactory;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolymarketTest {

    @RegisterExtension
    static final MiniClusterExtension MINI_CLUSTER_RESOURCE =
            new MiniClusterExtension(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberTaskManagers(1)
                            .setNumberSlotsPerTaskManager(4)
                            .build());

    private static final String SOURCE_DDL_TEMPLATE = """
            CREATE TEMPORARY TABLE source_events (
                `topic` STRING,
                `type` STRING,
                `timestamp` BIGINT,
                `payload` ROW<
                    parentEntityID BIGINT,
                    body STRING
                >,
                `event_time` AS TO_TIMESTAMP_LTZ(`timestamp`, 3),
                WATERMARK FOR `event_time` AS `event_time` - INTERVAL '1' SECOND
            ) WITH (
                'connector' = 'values',
                'data-id' = '%s',
                'bounded' = 'true'
            )
            """;

    private static final String SINK_DDL_TEMPLATE = """
            CREATE TEMPORARY TABLE %s (
                parent_entity_id BIGINT,
                window_timestamp TIMESTAMP(3),
                cnt BIGINT,
                PRIMARY KEY (parent_entity_id, window_timestamp) NOT ENFORCED
            ) WITH (
                'connector' = 'values',
                'sink-insert-only' = 'false'
            )
            """;

    private static final String INSERT_SQL_TEMPLATE = """
            INSERT INTO %s
            SELECT
                payload.parentEntityID,
                window_end,
                COUNT(*)
            FROM TABLE(
                TUMBLE(TABLE source_events, DESCRIPTOR(event_time), INTERVAL '10' MINUTES)
            )
            GROUP BY payload.parentEntityID, window_start, window_end
            """;

    @Test
    void testPolymarketLogicAggregatesTwoWindows() throws Exception {
        TableEnvironment tEnv = createTableEnvironment();
        long baseTime = Instant.parse("2023-10-01T10:00:00Z").toEpochMilli();

        List<Row> events = Arrays.asList(
                Row.of("topic1", "type1", baseTime, Row.of(1L, "body1")),
                Row.of("topic1", "type1", baseTime + Duration.ofMinutes(5).toMillis(), Row.of(1L, "body2")),
                Row.of("topic1", "type1", baseTime + Duration.ofMinutes(11).toMillis(), Row.of(1L, "body3"))
        );

        String sinkTableName = createSinkTableName();
        List<String> results = executePolymarketLogic(tEnv, events, sinkTableName);

        assertThat(results).hasSize(2);

        Map<Long, Map<LocalDateTime, Long>> parsed = parseWindowResults(results);
        LocalDateTime firstWindow = toLocalDateTime(baseTime + Duration.ofMinutes(10).toMillis());
        LocalDateTime secondWindow = toLocalDateTime(baseTime + Duration.ofMinutes(20).toMillis());

        assertThat(parsed).containsKey(1L);
        Map<LocalDateTime, Long> windows = parsed.get(1L);
        assertThat(windows).containsEntry(firstWindow, 2L);
        assertThat(windows).containsEntry(secondWindow, 1L);
    }

    @Test
    void testPolymarketLogicHandlesSingleEvent() throws Exception {
        TableEnvironment tEnv = createTableEnvironment();
        long baseTime = Instant.parse("2024-01-02T00:00:00Z").toEpochMilli();

        Row singleEvent = Row.of("topic-single", "type-single", baseTime, Row.of(42L, "only-body"));
        String sinkTableName = createSinkTableName();
        List<String> results = executePolymarketLogic(tEnv, Collections.singletonList(singleEvent), sinkTableName);

        assertThat(results).hasSize(1);

        Map<Long, Map<LocalDateTime, Long>> parsed = parseWindowResults(results);
        LocalDateTime expectedWindow = toLocalDateTime(baseTime + Duration.ofMinutes(10).toMillis());

        assertThat(parsed).containsKey(42L);
        assertThat(parsed.get(42L)).containsEntry(expectedWindow, 1L);
    }

    @Test
    void testPolymarketLogicHandlesThreeConsecutiveWindows() throws Exception {
        TableEnvironment tEnv = createTableEnvironment();
        long baseTime = Instant.parse("2023-10-01T10:00:00Z").toEpochMilli();

        List<Row> events = Arrays.asList(
                Row.of("topic1", "type1", baseTime, Row.of(1L, "body-a")),
                Row.of("topic1", "type1", baseTime + Duration.ofMinutes(1).toMillis(), Row.of(1L, "body-b")),
                Row.of("topic1", "type1", baseTime + Duration.ofMinutes(10).toMillis(), Row.of(1L, "body-c")),
                Row.of("topic1", "type1", baseTime + Duration.ofMinutes(21).toMillis(), Row.of(1L, "body-d"))
        );

        String sinkTableName = createSinkTableName();
        List<String> results = executePolymarketLogic(tEnv, events, sinkTableName);

        assertThat(results).hasSize(3);

        Map<Long, Map<LocalDateTime, Long>> parsed = parseWindowResults(results);
        LocalDateTime windowEnd10 = toLocalDateTime(baseTime + Duration.ofMinutes(10).toMillis());
        LocalDateTime windowEnd20 = toLocalDateTime(baseTime + Duration.ofMinutes(20).toMillis());
        LocalDateTime windowEnd30 = toLocalDateTime(baseTime + Duration.ofMinutes(30).toMillis());

        assertThat(parsed).containsKey(1L);
        Map<LocalDateTime, Long> windows = parsed.get(1L);
        assertThat(windows).containsEntry(windowEnd10, 2L);
        assertThat(windows).containsEntry(windowEnd20, 1L);
        assertThat(windows).containsEntry(windowEnd30, 1L);
        assertThat(windows).hasSize(3);
    }

    private static TableEnvironment createTableEnvironment() {
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        return TableEnvironment.create(settings);
    }

    private static List<String> executePolymarketLogic(TableEnvironment tEnv, List<Row> data, String sinkTableName) throws Exception {
        String dataId = TestValuesTableFactory.registerData(data);
        tEnv.executeSql(String.format(SOURCE_DDL_TEMPLATE, dataId));
        tEnv.executeSql(String.format(SINK_DDL_TEMPLATE, sinkTableName));
        tEnv.executeSql(String.format(INSERT_SQL_TEMPLATE, sinkTableName)).await();
        List<String> results = TestValuesTableFactory.getResultsAsStrings(sinkTableName);
        Collections.sort(results);
        return results;
    }

    private static Map<Long, Map<LocalDateTime, Long>> parseWindowResults(List<String> results) {
        Map<Long, Map<LocalDateTime, Long>> parsed = new HashMap<>();
        for (String result : results) {
            int start = result.indexOf('[');
            int end = result.lastIndexOf(']');
            if (start == -1 || end == -1) {
                continue;
            }
            String payload = result.substring(start + 1, end);
            String[] parts = payload.split(",\\s+");
            long parentEntityId = Long.parseLong(parts[0]);
            LocalDateTime windowTimestamp = LocalDateTime.parse(parts[1]);
            long count = Long.parseLong(parts[2]);
            parsed.computeIfAbsent(parentEntityId, ignored -> new HashMap<>()).put(windowTimestamp, count);
        }
        return parsed;
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static String createSinkTableName() {
        return "sink_stats_" + UUID.randomUUID().toString().replace("-", "_");
    }
}