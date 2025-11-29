CREATE TABLE IF NOT EXISTS market_stats (
    parent_entity_id VARCHAR(255),
    window_timestamp TIMESTAMP,
    count INT,
    PRIMARY KEY (parent_entity_id, window_timestamp)
);
