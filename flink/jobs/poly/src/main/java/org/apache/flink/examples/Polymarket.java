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
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Main class for executing SQL scripts. */
public class Polymarket {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Configure Kafka Source
        // Note: "kafka-service:9092" is the internal K8s DNS you set up
        KafkaSource<String> source =
                KafkaSource.<String>builder()
                        .setBootstrapServers("kafka-service:9092")
                        .setTopics("polymarket-messages")
                        .setGroupId("flink-analytics-group")
                        .setStartingOffsets(OffsetsInitializer.latest())
                        .setValueOnlyDeserializer(new SimpleStringSchema())
                        .build();

        // 2. Define Watermark Strategy
        WatermarkStrategy<String> watermarkStrategy = WatermarkStrategy
                .<String>forBoundedOutOfOrderness(Duration.ofSeconds(20))
                .withTimestampAssigner((event, timestamp) -> extractTimestamp(event));

        // 3. Create Stream
        DataStream<String> stream =
                env.fromSource(source, watermarkStrategy, "Kafka Source");

        // 4. Process Data (Parse JSON -> Extract ID & Timestamp -> Count)
        stream.map(Polymarket::parseJsonToTuple)
                // We need to tell Flink the types because of Java Type Erasure hello

                .returns(
                        org.apache.flink.api.common.typeinfo.Types.TUPLE(
                                org.apache.flink.api.common.typeinfo.Types.LONG,
                                org.apache.flink.api.common.typeinfo.Types.LONG,
                                org.apache.flink.api.common.typeinfo.Types.INT))
                .keyBy(value -> value.f0)
                // 5. Window (10 minutes event time)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                // 6. Sum the counts (field 2 of the tuple)
                .sum(2)
                // Format the output before printing
                .map(Polymarket::formatResult)
                // 7. Output to console (Check TaskManager logs to see this)
                .print();
        env.execute("Polymarket Comments Analysis");
    }

    public static long extractTimestamp(String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonString);
            if (node.has("timestamp")) {
                return node.get("timestamp").asLong();
            }
        } catch (Exception e) {
            // Fallback or log error
                e.printStackTrace();
        }
        return 0L;
    }

    public static Tuple3<Long, Long, Integer> parseJsonToTuple(String jsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(jsonString);
        Long parentEntityID = 0L;
        if (node.has("payload") && node.get("payload").has("parentEntityID")) {
            parentEntityID = node.get("payload").get("parentEntityID").asLong();
        }
        Long timestamp = 0L;
        if (node.has("timestamp")) {
            timestamp = node.get("timestamp").asLong();
        }
        return Tuple3.of(parentEntityID, timestamp, 1);
    }

    private static String formatResult(Tuple3<Long, Long, Integer> tuple) {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        String formattedTime = formatter.format(Instant.ofEpochMilli(tuple.f1));
        return String.format(
                "+++ParentEntityID: %d, Time: %s, Count: %d", tuple.f0, formattedTime, tuple.f2);
    }
}
