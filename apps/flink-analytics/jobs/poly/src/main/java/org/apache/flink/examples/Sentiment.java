package org.apache.flink.examples;

import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;

public class Sentiment {
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
                'properties.group.id' = 'flink-sentiment-group',
                'scan.startup.mode' = 'latest-offset',
                'format' = 'json',
                'json.fail-on-missing-field' = 'false'
            )
        """);

        // 3. Define JDBC Sink DDL
        // The PRIMARY KEY ensures Upsert semantics (Insert or Update)
        tEnv.executeSql("""
            CREATE TABLE
                market_sentiment (
                parent_entity_id STRING,
                window_timestamp TIMESTAMP(3),
                combined_comments STRING,
                sentiment_result STRING,
                PRIMARY KEY (parent_entity_id, window_timestamp) NOT ENFORCED
            )
            WITH
            (
                'connector' = 'jdbc',
                'url' = 'jdbc:postgresql://postgres-postgresql:5432/polymarket',
                'table-name' = 'market_sentiment',
                'username' = 'postgres',
                'password' = 'postgres'
            )
        """);


        tEnv.executeSql("""
            CREATE MODEL sentiment_model INPUT (`input_text` STRING) OUTPUT (`sentiment_result` STRING)
            WITH
                (
                    'provider' = 'openai',
                    'endpoint' = 'http://host.minikube.internal:11434/v1/chat/completions',
                    'model' = 'qwen2.5:0.5b',
                    'api-key' = 'ollama',
                    'system-prompt' = 'Analyze the sentiment of the input text and respond with ONLY one word: Positive, Negative, or Neutral.'
                )
        """);

        tEnv.executeSql("""
            INSERT INTO
                market_sentiment
            WITH
                aggregated_data AS (
                    SELECT
                        payload.parentEntityID AS parent_entity_id_long,
                        window_end AS window_timestamp,
                        LISTAGG (payload.body, '|||') AS combined_comments
                    FROM
                        TABLE (
                            TUMBLE (
                                TABLE source_events,
                                DESCRIPTOR (event_time),
                                INTERVAL '10' MINUTES
                            )
                        )
                    WHERE
                        payload.body IS NOT NULL
                        AND payload.body <> ''
                    GROUP BY
                        payload.parentEntityID,
                        window_start,
                        window_end
                )
            SELECT
                CAST(parent_entity_id_long AS STRING) AS parent_entity_id,
                window_timestamp,
                combined_comments,
                sentiment_result
            FROM
                ML_PREDICT (
                    TABLE aggregated_data,
                    MODEL sentiment_model,
                    DESCRIPTOR (combined_comments)
                )
        """);
    }
}
