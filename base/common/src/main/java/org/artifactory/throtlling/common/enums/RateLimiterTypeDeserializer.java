package org.artifactory.throtlling.common.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.rrl.enums.RequestRateLimiterType;

import java.io.IOException;

public class RateLimiterTypeDeserializer extends JsonDeserializer<IRateLimiterType> {

    @Override
    public IRateLimiterType deserialize(JsonParser p, DeserializationContext context) throws IOException {
        String typeName = p.getValueAsString();

        try {
            return RequestRateLimiterType.valueOf(typeName);
        } catch (IllegalArgumentException ignored) {
            // Do nothing
        }

        try {
            return QueryRateLimiterType.valueOf(typeName);
        } catch (IllegalArgumentException ignored) {
            // Do nothing
        }

        throw new IOException("Invalid rlType value: " + typeName);
    }
}
