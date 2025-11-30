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

package org.apache.flink.examples.serde;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.examples.model.PolymarketEvent;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Kafka deserializer for Polymarket events.
 * Converts JSON bytes from Kafka into PolymarketEvent POJOs.
 */
public class PolymarketEventDeserializer implements DeserializationSchema<PolymarketEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PolymarketEventDeserializer.class);

    private transient ObjectMapper objectMapper;

    @Override
    public void open(InitializationContext context) throws Exception {
        this.objectMapper = createObjectMapper();
    }

    @Override
    public PolymarketEvent deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            LOG.warn("Received null or empty message, returning null");
            return null;
        }

        try {
            // Lazy initialization in case open() wasn't called (e.g., in tests)
            if (objectMapper == null) {
                objectMapper = createObjectMapper();
            }
            return objectMapper.readValue(message, PolymarketEvent.class);
        } catch (IOException e) {
            LOG.error("Failed to deserialize message: {}", new String(message), e);
            // Return null to skip malformed messages rather than failing the job
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(PolymarketEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<PolymarketEvent> getProducedType() {
        return TypeInformation.of(PolymarketEvent.class);
    }

    /**
     * Creates a configured ObjectMapper instance.
     * Configured to ignore unknown properties for forward compatibility.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Ignore unknown fields to allow for schema evolution
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Convenience method for deserializing a JSON string.
     * Useful for testing.
     */
    public PolymarketEvent deserialize(String jsonString) throws IOException {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        return deserialize(jsonString.getBytes());
    }
}
