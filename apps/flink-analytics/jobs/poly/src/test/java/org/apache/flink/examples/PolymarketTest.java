package org.apache.flink.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.examples.model.CommentPayload;
import org.apache.flink.examples.model.PolymarketEvent;
import org.apache.flink.examples.serde.PolymarketEventDeserializer;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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

        PolymarketEvent event = deserializer.deserialize(json);

        assertNotNull(event);
        assertEquals("comments", event.getTopic());
        assertEquals("comment_created", event.getType());
        assertEquals(1753454975808L, event.getTimestamp());
        assertEquals(18396L, event.getParentEntityID());

        CommentPayload payload = event.getPayload();
        assertNotNull(payload);
        assertEquals("test comment", payload.getBody());
        assertEquals("1763355", payload.getId());
        assertEquals("Event", payload.getParentEntityType());

        assertNotNull(payload.getProfile());
        assertEquals("salted.caramel", payload.getProfile().getName());
        assertEquals("Adored-Disparity", payload.getProfile().getPseudonym());
    }

    @Test
    public void testDeserializeMinimalEvent() throws IOException {
        String json = "{\"timestamp\": 1678886400000, \"payload\": {\"parentEntityID\": 123}}";
        PolymarketEvent event = deserializer.deserialize(json);

        assertNotNull(event);
        assertEquals(1678886400000L, event.getTimestamp());
        assertEquals(123L, event.getParentEntityID());
    }

    @Test
    public void testDeserializeNullPayload() throws IOException {
        String json = "{\"timestamp\": 1678886400000}";
        PolymarketEvent event = deserializer.deserialize(json);

        assertNotNull(event);
        assertEquals(1678886400000L, event.getTimestamp());
        assertEquals(0L, event.getParentEntityID()); // Returns 0 when payload is null
    }

    @Test
    public void testDeserializeMalformedJson() throws IOException {
        String json = "not valid json";
        PolymarketEvent event = deserializer.deserialize(json);

        assertNull(event); // Should return null for malformed JSON
    }

    @Test
    public void testDeserializeEmptyString() throws IOException {
        PolymarketEvent event = deserializer.deserialize("");
        assertNull(event);
    }

    @Test
    public void testDeserializeIgnoresUnknownFields() throws IOException {
        String json = "{\"timestamp\": 1678886400000, \"unknownField\": \"value\", \"payload\": {\"parentEntityID\": 123, \"newField\": 42}}";
        PolymarketEvent event = deserializer.deserialize(json);

        assertNotNull(event);
        assertEquals(1678886400000L, event.getTimestamp());
        assertEquals(123L, event.getParentEntityID());
    }

    @Test
    public void testAggregationPipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Clear static sink values
        CollectSink.values.clear();

        // Create test data
        // Window size is 10 minutes (600,000 ms)
        // Window 1: 0 - 600,000
        long t1 = 1000L; // Inside Window 1
        long t2 = 2000L; // Inside Window 1
        long t3 = 600001L; // Inside Window 2

        String json1 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t1);
        String json2 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t2);
        String json3 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t3);
        String json4 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 2}}", t1);

        // Deserialize to PolymarketEvent objects
        PolymarketEvent event1 = deserializer.deserialize(json1);
        PolymarketEvent event2 = deserializer.deserialize(json2);
        PolymarketEvent event3 = deserializer.deserialize(json3);
        PolymarketEvent event4 = deserializer.deserialize(json4);

        DataStream<PolymarketEvent> source = env.fromElements(event1, event2, event3, event4)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<PolymarketEvent>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                                .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                );

        DataStream<Tuple3<Long, Long, Integer>> result = Polymarket.createAggregationPipeline(source);

        result.addSink(new CollectSink());

        env.execute("Test Aggregation");

        // Verify results
        // We expect:
        // ID 1, Window 1 (end=600000): count 2
        // ID 1, Window 2 (end=1200000): count 1
        // ID 2, Window 1 (end=600000): count 1

        List<Tuple3<Long, Long, Integer>> results = CollectSink.values;
        assertThat(results).hasSize(3);

        // Verify window timestamps are correct (window end, not message timestamp)
        // Window 1 ends at 600,000ms
        long window1End = 600_000L;
        // Window 2 ends at 1,200,000ms
        long window2End = 1_200_000L;

        // ID 1 in Window 1 should have count 2 and window timestamp 600000
        Tuple3<Long, Long, Integer> id1Window1 = results.stream()
                .filter(t -> t.f0 == 1L && t.f1 == window1End)
                .findFirst()
                .orElse(null);
        assertThat(id1Window1).isNotNull();
        assertThat(id1Window1.f2).isEqualTo(2);

        // ID 1 in Window 2 should have count 1 and window timestamp 1200000
        Tuple3<Long, Long, Integer> id1Window2 = results.stream()
                .filter(t -> t.f0 == 1L && t.f1 == window2End)
                .findFirst()
                .orElse(null);
        assertThat(id1Window2).isNotNull();
        assertThat(id1Window2.f2).isEqualTo(1);

        // ID 2 in Window 1 should have count 1 and window timestamp 600000
        Tuple3<Long, Long, Integer> id2Window1 = results.stream()
                .filter(t -> t.f0 == 2L && t.f1 == window1End)
                .findFirst()
                .orElse(null);
        assertThat(id2Window1).isNotNull();
        assertThat(id2Window1.f2).isEqualTo(1);
    }

    private static class CollectSink implements SinkFunction<Tuple3<Long, Long, Integer>> {
        public static final List<Tuple3<Long, Long, Integer>> values = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(Tuple3<Long, Long, Integer> value, Context context) {
            values.add(value);
        }
    }
}

