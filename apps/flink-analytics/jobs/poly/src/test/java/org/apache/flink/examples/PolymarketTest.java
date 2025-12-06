package org.apache.flink.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.examples.model.CommentPayload;
import org.apache.flink.examples.model.PolymarketEvent;
import org.apache.flink.examples.serde.PolymarketEventDeserializer;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
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

        // Create test data
        long t1 = 1000L; // Inside Window 1
        long t2 = 2000L; // Inside Window 1
        long t3 = 600001L; // Inside Window 2

        String json1 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t1);
        String json2 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t2);
        String json3 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 1}}", t3);
        String json4 = String.format("{\"timestamp\": %d, \"payload\": {\"parentEntityID\": 2}}", t1);

        // Note: Assuming your deserializer has a method for String or you convert to bytes here
        PolymarketEvent event1 = deserializer.deserialize(json1.getBytes());
        PolymarketEvent event2 = deserializer.deserialize(json2.getBytes());
        PolymarketEvent event3 = deserializer.deserialize(json3.getBytes());
        PolymarketEvent event4 = deserializer.deserialize(json4.getBytes());

        DataStream<PolymarketEvent> source = env.fromElements(event1, event2, event3, event4)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<PolymarketEvent>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                                .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                );

        DataStream<Tuple3<Long, Long, Integer>> resultStream = Polymarket.createAggregationPipeline(source);

        // --- NEW APPROACH: executeAndCollect ---
        // This replaces result.addSink(new CollectSink()) and env.execute()
        
        List<Tuple3<Long, Long, Integer>> results = new ArrayList<>();
        
        try (CloseableIterator<Tuple3<Long, Long, Integer>> iterator = resultStream.executeAndCollect()) {
            iterator.forEachRemaining(results::add);
        }

        // --- VERIFICATION ---
        
        assertThat(results).hasSize(3);

        // Window 1 ends at 600,000ms
        long window1End = 600_000L;
        // Window 2 ends at 1,200,000ms
        long window2End = 1_200_000L;

        // ID 1, Window 1: count 2
        Tuple3<Long, Long, Integer> id1Window1 = results.stream()
                .filter(t -> t.f0 == 1L && t.f1 == window1End)
                .findFirst()
                .orElse(null);
        assertThat(id1Window1).isNotNull();
        assertThat(id1Window1.f2).isEqualTo(2);

        // ID 1, Window 2: count 1
        Tuple3<Long, Long, Integer> id1Window2 = results.stream()
                .filter(t -> t.f0 == 1L && t.f1 == window2End)
                .findFirst()
                .orElse(null);
        assertThat(id1Window2).isNotNull();
        assertThat(id1Window2.f2).isEqualTo(1);

        // ID 2, Window 1: count 1
        Tuple3<Long, Long, Integer> id2Window1 = results.stream()
                .filter(t -> t.f0 == 2L && t.f1 == window1End)
                .findFirst()
                .orElse(null);
        assertThat(id2Window1).isNotNull();
        assertThat(id2Window1.f2).isEqualTo(1);
    }
}