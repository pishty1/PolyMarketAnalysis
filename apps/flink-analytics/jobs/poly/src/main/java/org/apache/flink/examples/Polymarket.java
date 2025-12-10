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
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.examples.model.PolymarketEvent;
import org.apache.flink.examples.serde.PolymarketEventDeserializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import java.time.Duration;
import java.time.LocalDateTime;

/** Main class for Polymarket comments analytics. */
public class Polymarket {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 1. Configure Kafka Source with typed deserializer
        KafkaSource<PolymarketEvent> source = KafkaSource.<PolymarketEvent>builder()
                .setBootstrapServers("kafka-service:9092")
                .setTopics("polymarket-messages")
                .setGroupId("flink-analytics-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new PolymarketEventDeserializer())
                .build();

        // 2. Define Watermark Strategy using typed event timestamp
        WatermarkStrategy<PolymarketEvent> watermarkStrategy = WatermarkStrategy
                .<PolymarketEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, timestamp) -> event != null ? event.getTimestamp() : 0L);

        // 3. Create Stream
        DataStream<PolymarketEvent> stream = env.fromSource(source, watermarkStrategy, "Kafka Source");

        // 4. Compute Stats
        Table resultTable = computeMarketStats(tableEnv, stream.filter(event -> event != null));

        // 5. Convert to DataStream
        DataStream<Row> rowStream = tableEnv.toDataStream(resultTable);

        // 6. Sink using JDBC DataStream API
        rowStream.sinkTo(
                JdbcSink.<Row>builder()
                        .withQueryStatement(
                                "INSERT INTO market_stats (parent_entity_id, window_timestamp, count) VALUES (?, ?, ?) " +
                                        "ON CONFLICT (parent_entity_id, window_timestamp) DO UPDATE SET count = EXCLUDED.count",
                                (statement, row) -> {
                                    statement.setLong(1, row.getFieldAs(0)); // parentEntityID
                                    statement.setTimestamp(2, java.sql.Timestamp.valueOf(row.<LocalDateTime>getFieldAs(1))); // window_end
                                    statement.setLong(3, row.getFieldAs(2)); // count
                                })
                        .withExecutionOptions(
                                JdbcExecutionOptions.builder()
                                        .withBatchSize(1000)
                                        .withBatchIntervalMs(200)
                                        .withMaxRetries(5)
                                        .build())
                        .buildAtLeastOnce(
                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                        .withUrl("jdbc:postgresql://postgres-postgresql:5432/polymarket")
                                        .withDriverName("org.postgresql.Driver")
                                        .withUsername("postgres")
                                        .withPassword("postgresTableEnvironmentTest.java")
                                        .build()));

        env.execute("Polymarket Comments Analysis");
    }

    public static Table computeMarketStats(StreamTableEnvironment tableEnv, DataStream<PolymarketEvent> stream) {
        // Convert to Table with watermark propagation
        Table events = tableEnv.fromDataStream(
                stream,
                Schema.newBuilder()
                        .columnByMetadata("rowtime", "TIMESTAMP_LTZ(3)")
                        .watermark("rowtime", "SOURCE_WATERMARK()")
                        .build());

        tableEnv.createTemporaryView("events", events);

        // Execute Aggregation
        return tableEnv.sqlQuery(
                "SELECT " +
                        "  payload.parentEntityID, " +
                        "  window_end, " +
                        "  COUNT(*) " +
                        "FROM TABLE(" +
                        "  TUMBLE(TABLE events, DESCRIPTOR(rowtime), INTERVAL '10' MINUTES)" +
                        ") " +
                        "GROUP BY payload.parentEntityID, window_start, window_end");
    }
}
