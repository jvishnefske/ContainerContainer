package org.artifactory.addon.observability;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.artifactory.addon.observability.LogShippingInfo.NODE_ID_FIELD;
import static org.artifactory.addon.observability.LogShippingInfo.SERVICE_ID_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.LOG_NAME_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.PRODUCT_NAME_FIELD;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class LogShippingInfoTest {

    private static final String COOL_PRODUCT = "cool-product";
    private static final String COOL_LOG_NAME = "cool-log-name";
    private static final String COOL_SERVICE_ID = "cool-service-id";
    private static final String COOL_NODE_ID = "cool-node-id";
    private static final InputStream COOL_INPUT_STREAM = new ByteArrayInputStream("cool-input-stream".getBytes());

    @Test
    public void testBuilder() {
        LogShippingInfo logShippingInfo = LogShippingInfo.builder()
                .productName(COOL_PRODUCT)
                .logName(COOL_LOG_NAME)
                .serviceId(COOL_SERVICE_ID)
                .nodeId(COOL_NODE_ID)
                .logFileInputStream(COOL_INPUT_STREAM)
                .build();
        assertEquals(logShippingInfo.getProductName(), COOL_PRODUCT);
        assertEquals(logShippingInfo.getLogName(), COOL_LOG_NAME);
        assertEquals(logShippingInfo.getServiceId(), COOL_SERVICE_ID);
        assertEquals(logShippingInfo.getNodeId(), COOL_NODE_ID);
        assertEquals(logShippingInfo.getLogFileInputStream(), COOL_INPUT_STREAM);
    }

    @Test
    public void testGetProperties() {
        LogShippingInfo logShippingInfo = LogShippingInfo.builder()
                .productName(COOL_PRODUCT)
                .logName(COOL_LOG_NAME)
                .serviceId(COOL_SERVICE_ID)
                .nodeId(COOL_NODE_ID)
                .logFileInputStream(COOL_INPUT_STREAM)
                .build();
        Map<String, String> properties = logShippingInfo.getProperties();
        assertEquals(properties.size(), 4);
        assertEquals(properties.get(LOG_NAME_FIELD), COOL_LOG_NAME);
        assertEquals(properties.get(PRODUCT_NAME_FIELD), COOL_PRODUCT);
        assertEquals(properties.get(NODE_ID_FIELD), COOL_NODE_ID);
        assertEquals(properties.get(SERVICE_ID_FIELD), COOL_SERVICE_ID);
    }
}
