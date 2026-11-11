package org.artifactory.throtlling.common.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.artifactory.throtlling.rrl.enums.RequestRateLimiterType;
import org.junit.jupiter.api.Test;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IRateLimiterTypeTest {
    @Test
    void testDeserialization() throws Exception {
        String json = """
                {
                    "rlType": "DOCKERHUB_REQUEST_REMOTE_REPO",
                    "permitsPerTimeFrame": 2147483647,
                    "timeFrameMillis": 60000
                }
                    """;
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimiterSettings settings = objectMapper.readValue(json, RateLimiterSettings.class);

        assertNotNull(settings);
        assertEquals(RequestRateLimiterType.DOCKERHUB_REQUEST_REMOTE_REPO, settings.getRlType());
        assertEquals(2147483647, settings.getPermitsPerTimeFrame());
        assertEquals(60000, settings.getTimeFrameMillis());
    }

    @Test
    void testSerialization() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RateLimiterSettings settings = new RateLimiterSettings(LOW_PRIORITY, 2, 6, 2);
        String val = objectMapper.writeValueAsString(settings);

        RateLimiterSettings settings2 = objectMapper.readValue(val, RateLimiterSettings.class);
        assertNotNull(settings);
        assertEquals(settings, settings2);

    }
}