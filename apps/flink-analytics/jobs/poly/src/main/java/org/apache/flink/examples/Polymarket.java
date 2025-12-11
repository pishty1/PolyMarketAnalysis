package org.apache.flink.examples;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

public class Polymarket {

    public static void main(String[] args) {
        // 1. Pure Table Environment (No StreamExecutionEnvironment needed)
        EnvironmentSettings settings = EnvironmentSettings.inStreamingMode();
        TableEnvironment tEnv = TableEnvironment.create(settings);

        // Enable checkpointing for fault tolerance
        tEnv.getConfig().getConfiguration().setString("execution.checkpointing.interval", "10s");

        // 2. Define Kafka Source DDL
        // We map the JSON fields directly here. 
        // Note the `WATERMARK` definition handles event time.
        tEnv.executeSql("""
            CREATE TABLE source_events (
                `topic` STRING,
                `type` STRING,
                `timestamp` BIGINT,
                `payload` ROW<
                    parentEntityID BIGINT, 
                    body STRING
                >,
                `event_time` AS TO_TIMESTAMP_LTZ(`timestamp`, 3),
                WATERMARK FOR `event_time` AS `event_time` - INTERVAL '10' SECOND
            ) WITH (
                'connector' = 'kafka',
                'topic' = 'polymarket-messages',
                'properties.bootstrap.servers' = 'kafka-service:9092',
                'properties.group.id' = 'flink-analytics-group',
                'scan.startup.mode' = 'earliest-offset',
                'format' = 'json',
                'json.fail-on-missing-field' = 'false'
            )
        """);

        // 3. Define JDBC Sink DDL
        // The PRIMARY KEY ensures Upsert semantics (Insert or Update)
        tEnv.executeSql("""
            CREATE TABLE sink_stats (
                parent_entity_id BIGINT,
                window_timestamp TIMESTAMP(3),
                cnt BIGINT,
                PRIMARY KEY (parent_entity_id, window_timestamp) NOT ENFORCED
            ) WITH (
                'connector' = 'jdbc',
                'url' = 'jdbc:postgresql://postgres-postgresql:5432/polymarket',
                'table-name' = 'market_stats',
                'username' = 'postgres',
                'password' = 'postgres',
                'driver' = 'org.postgresql.Driver'
            )
        """);

        // 4. The Logic (Insert Select)
        // This runs the query and pipes it to the sink immediately.
        tEnv.executeSql("""
            INSERT INTO sink_stats
            SELECT 
                payload.parentEntityID,
                window_end,
                COUNT(*)
            FROM TABLE(
                TUMBLE(TABLE source_events, DESCRIPTOR(event_time), INTERVAL '10' MINUTES)
            )
            GROUP BY payload.parentEntityID, window_start, window_end
        """);
    }
}