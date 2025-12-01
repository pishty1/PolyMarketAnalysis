/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.examples.model.PolymarketEvent;
import org.apache.flink.examples.serde.PolymarketEventDeserializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/** Main class for Polymarket comments analytics. */
public class Polymarket {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Configure Kafka Source with typed deserializer
        KafkaSource<PolymarketEvent> source = KafkaSource.<PolymarketEvent>builder()
                .setBootstrapServers("kafka-service:9092")
                .setTopics("polymarket-messages")
                .setGroupId("flink-analytics-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new PolymarketEventDeserializer())
                .build();

        // 2. Define Watermark Strategy using typed event timestamp
        WatermarkStrategy<PolymarketEvent> watermarkStrategy = WatermarkStrategy
                .<PolymarketEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, timestamp) -> event != null ? event.getTimestamp() : 0L);

        // 3. Create Stream
        DataStream<PolymarketEvent> stream = env.fromSource(source, watermarkStrategy, "Kafka Source");

        // 4. Filter out null events (malformed messages) and process
        DataStream<Tuple3<Long, Long, Integer>> aggregatedStream = createAggregationPipeline(
                stream.filter(event -> event != null));

        aggregatedStream.addSink(JdbcSink.sink(
                        "INSERT INTO market_stats (parent_entity_id, window_timestamp, count) VALUES (?, ?, ?) " +
                        "ON CONFLICT (parent_entity_id, window_timestamp) DO UPDATE SET count = EXCLUDED.count",
                        (statement, tuple) -> {
                            statement.setString(1, String.valueOf(tuple.f0));
                            statement.setTimestamp(2, new java.sql.Timestamp(tuple.f1));
                            statement.setInt(3, tuple.f2);
                        },
                        JdbcExecutionOptions.builder()
                                .withBatchSize(1000)
                                .withBatchIntervalMs(200)
                                .withMaxRetries(5)
                                .build(),
                        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                .withUrl("jdbc:postgresql://postgres-postgresql:5432/polymarket")
                                .withDriverName("org.postgresql.Driver")
                                .withUsername("postgres")
                                .withPassword("postgres")
                                .build()));
        env.execute("Polymarket Comments Analysis");
    }

    public static DataStream<Tuple3<Long, Long, Integer>> createAggregationPipeline(DataStream<PolymarketEvent> stream) {
        return stream
                .keyBy(event -> event.getParentEntityID())
                // Window (10 minutes event time)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                // Use incremental aggregation to avoid buffering all events in state
                .aggregate(new CountAggregator(), new CountWithWindowTimestamp());
    }

    /**
     * AggregateFunction that incrementally counts events.
     * This keeps state size constant (1 integer) instead of buffering all events.
     */
    public static class CountAggregator
            implements AggregateFunction<PolymarketEvent, Integer, Integer> {

        @Override
        public Integer createAccumulator() {
            return 0;
        }

        @Override
        public Integer add(PolymarketEvent value, Integer accumulator) {
            return accumulator + 1;
        }

        @Override
        public Integer getResult(Integer accumulator) {
            return accumulator;
        }

        @Override
        public Integer merge(Integer a, Integer b) {
            return a + b;
        }
    }

    /**
     * ProcessWindowFunction that receives the pre-aggregated count and outputs with window timestamp.
     * Combined with CountAggregator via aggregate() for memory-efficient incremental aggregation.
     */
    public static class CountWithWindowTimestamp
            extends ProcessWindowFunction<Integer, Tuple3<Long, Long, Integer>, Long, TimeWindow> {

        @Override
        public void process(
                Long key,
                Context context,
                Iterable<Integer> counts,
                Collector<Tuple3<Long, Long, Integer>> out) {

            // We receive only one pre-aggregated count per window
            Integer count = counts.iterator().next();
            long windowEnd = context.window().getEnd();
            out.collect(Tuple3.of(key, windowEnd, count));
        }
    }
}
