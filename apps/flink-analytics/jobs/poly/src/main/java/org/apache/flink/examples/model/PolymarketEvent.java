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

package org.apache.flink.examples.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * POJO representing a Polymarket event message from Kafka.
 * This is the root object containing topic, type, timestamp, and payload.
 */
public class PolymarketEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topic;
    private String type;
    private long timestamp;
    private CommentPayload payload;

    // Default constructor required for Jackson deserialization
    public PolymarketEvent() {
    }

    public PolymarketEvent(String topic, String type, long timestamp, CommentPayload payload) {
        this.topic = topic;
        this.type = type;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    // Getters and Setters
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public CommentPayload getPayload() {
        return payload;
    }

    public void setPayload(CommentPayload payload) {
        this.payload = payload;
    }

    /**
     * Convenience method to get the parent entity ID from the payload.
     * Returns 0 if payload is null.
     */
    public long getParentEntityID() {
        return payload != null ? payload.getParentEntityID() : 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolymarketEvent that = (PolymarketEvent) o;
        return timestamp == that.timestamp &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(type, that.type) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, type, timestamp, payload);
    }

    @Override
    public String toString() {
        return "PolymarketEvent{" +
                "topic='" + topic + '\'' +
                ", type='" + type + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
