package org.apache.flink.examples;

import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.api.java.tuple.Tuple3;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PolymarketJdbcSink implements Sink<Tuple3<Long, Long, Integer>> {

    private final String url;
    private final String username;
    private final String password;

    public PolymarketJdbcSink(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public SinkWriter<Tuple3<Long, Long, Integer>> createWriter(WriterInitContext context) throws IOException {
        return new PostgresWriter(url, username, password);
    }

    // The Writer handles the actual DB connection and batching
    private static class PostgresWriter implements SinkWriter<Tuple3<Long, Long, Integer>> {
        private final Connection connection;
        private final PreparedStatement statement;
        private final List<Tuple3<Long, Long, Integer>> batch;
        private static final int BATCH_SIZE = 1000;

        public PostgresWriter(String url, String user, String password) throws IOException {
            try {
                this.connection = DriverManager.getConnection(url, user, password);
                // Your idempotent SQL query
                String sql = "INSERT INTO market_stats (parent_entity_id, window_timestamp, count) VALUES (?, ?, ?) " +
                             "ON CONFLICT (parent_entity_id, window_timestamp) DO UPDATE SET count = EXCLUDED.count";
                this.statement = connection.prepareStatement(sql);
                this.batch = new ArrayList<>(BATCH_SIZE);
            } catch (SQLException e) {
                throw new IOException("Failed to open JDBC connection", e);
            }
        }

        @Override
        public void write(Tuple3<Long, Long, Integer> element, Context context) throws IOException {
            batch.add(element);
            if (batch.size() >= BATCH_SIZE) {
                flush(false);
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            if (batch.isEmpty()) {
                return;
            }
            try {
                for (Tuple3<Long, Long, Integer> tuple : batch) {
                    statement.setString(1, String.valueOf(tuple.f0));
                    statement.setTimestamp(2, new java.sql.Timestamp(tuple.f1));
                    statement.setInt(3, tuple.f2);
                    statement.addBatch();
                }
                statement.executeBatch();
                batch.clear();
            } catch (SQLException e) {
                throw new IOException("Failed to execute JDBC batch", e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                // Flush remaining records
                if (!batch.isEmpty()) {
                    flush(true);
                }
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                throw new IOException("Error closing JDBC resources", e);
            }
        }
    }
}