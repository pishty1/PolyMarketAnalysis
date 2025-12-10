package org.apache.flink.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.examples.model.CommentPayload;
import org.apache.flink.examples.model.PolymarketEvent;
import org.apache.flink.examples.serde.PolymarketEventDeserializer;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.apache.flink.util.CloseableIterator;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PolymarketTest {

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    private final PolymarketEventDeserializer deserializer = new PolymarketEventDeserializer();

    // ... [The Deserialization tests (testDeserializeFullEvent, etc.) remain exactly the same] ...
    
    @Test
    public void testDeserializeFullEvent() throws IOException {
        String json = """
                {
                  "topic": "comments",
                  "type": "comment_created",
                  "timestamp": 1753454975808,
                  "payload": {
                    "body": "test comment",
                    "createdAt": "2025-07-25T14:49:35.801298Z",
                    "id": "1763355",
                    "parentCommentID": "1763325",
                    "parentEntityID": 18396,
                    "parentEntityType": "Event",
                    "profile": {
                      "baseAddress": "0xce533188d53a16ed580fd5121dedf166d3482677",
                      "displayUsernamePublic": true,
                      "name": "salted.caramel",
                      "proxyWallet": "0x4ca749dcfa93c87e5ee23e2d21ff4422c7a4c1ee",
                      "pseudonym": "Adored-Disparity"
                    },
                    "reactionCount": 0,
                    "replyAddress": "0x0bda5d16f76cd1d3485bcc7a44bc6fa7db004cdd",
                    "reportCount": 0,
                    "userAddress": "0xce533188d53a16ed580fd5121dedf166d3482677"
                  }
                }
                """;

        PolymarketEvent event = deserializer.deserialize(json.getBytes()); // Note: deserialize usually takes bytes

        assertNotNull(event);
        assertEquals("comments", event.getTopic());
        assertEquals(18396L, event.getParentEntityID());
        
        // ... assertions ...
    }
    
    // ... [Include other deserialization tests here] ...

    @Test
    public void testAggregationPipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // Create test data
        long t1 = 1000L; // Inside Window 1 (0-10 min)
        long t2 = 2000L; // Inside Window 1
        long t3 = 600001L; // Inside Window 2 (10-20 min) - 10 mins + 1ms

        PolymarketEvent event1 = createEvent(t1, 1L);
        PolymarketEvent event2 = createEvent(t2, 1L);
        PolymarketEvent event3 = createEvent(t3, 1L);
        PolymarketEvent event4 = createEvent(t1, 2L);

        DataStream<PolymarketEvent> source = env.fromElements(event1, event2, event3, event4)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<PolymarketEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                );

        Table resultTable = Polymarket.computeMarketStats(tableEnv, source);

        // Convert result Table back to DataStream<Row> to verify results
        DataStream<Row> resultStream = tableEnv.toDataStream(resultTable);

        List<Row> results = new ArrayList<>();
        try (CloseableIterator<Row> iterator = resultStream.executeAndCollect()) {
            while (iterator.hasNext()) {
                results.add(iterator.next());
            }
        }

        // Verify results
        // Expected Schema: parentEntityID (BIGINT), window_end (TIMESTAMP), cnt (BIGINT)
        // Window 1 (0-10m): ID 1 -> count 2
        // Window 1 (0-10m): ID 2 -> count 1
        // Window 2 (10-20m): ID 1 -> count 1

        System.out.println("Results: " + results);

        assertThat(results).hasSize(3);

        // Helper to find row by ID and Window End
        // Window 1 ends at 600,000ms
        long window1End = 600_000L;
        // Window 2 ends at 1,200,000ms
        long window2End = 1_200_000L;

        // ID 1, Window 1: count 2
        Row id1Window1 = results.stream()
                .filter(r -> (long) r.getField(0) == 1L && compareWindowEnd(r.getField(1), window1End))
                .findFirst()
                .orElse(null);
        assertThat(id1Window1).isNotNull();
        assertThat((long) id1Window1.getField(2)).isEqualTo(2L);

        // ID 1, Window 2: count 1
        Row id1Window2 = results.stream()
                .filter(r -> (long) r.getField(0) == 1L && compareWindowEnd(r.getField(1), window2End))
                .findFirst()
                .orElse(null);
        assertThat(id1Window2).isNotNull();
        assertThat((long) id1Window2.getField(2)).isEqualTo(1L);

        // ID 2, Window 1: count 1
        Row id2Window1 = results.stream()
                .filter(r -> (long) r.getField(0) == 2L && compareWindowEnd(r.getField(1), window1End))
                .findFirst()
                .orElse(null);
        assertThat(id2Window1).isNotNull();
        assertThat((long) id2Window1.getField(2)).isEqualTo(1L);
    }

    private boolean compareWindowEnd(Object field, long expectedMillis) {
        long actualMillis;
        if (field instanceof java.time.LocalDateTime) {
            actualMillis = ((java.time.LocalDateTime) field).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else if (field instanceof java.time.Instant) {
            actualMillis = ((java.time.Instant) field).toEpochMilli();
        } else {
            throw new RuntimeException("Unknown time type: " + field.getClass());
        }
        return actualMillis == expectedMillis;
    }

    private PolymarketEvent createEvent(long timestamp, long parentEntityID) {
        CommentPayload payload = new CommentPayload();
        payload.setParentEntityID(parentEntityID);
        return new PolymarketEvent("topic", "type", timestamp, payload);
    }
}