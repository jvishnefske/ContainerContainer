package org.artifactory.addon.observability;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.artifactory.addon.observability.LogShippingInfo.NODE_ID_FIELD;
import static org.artifactory.addon.observability.LogShippingInfo.SERVICE_ID_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.LOG_NAME_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.PRODUCT_NAME_FIELD;
import static org.artifactory.addon.observability.UsageInfo.CONSOLIDATED_FIELD;
import static org.artifactory.addon.observability.UsageInfo.TYPE_FIELD;
import static org.testng.Assert.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
public class UsageInfoTest {

    private static final String PRODUCT = "product";
    private static final String LOG_NAME = "log-name";
    private static final String SERVICE_ID = "service-id";
    private static final String NODE_ID = "node-id";
    private static final String LOG_TYPE_USAGE = "usage";
    private static final String CONSOLIDATED = "true";
    private static final InputStream INPUT_STREAM = new ByteArrayInputStream("input-stream".getBytes());

    @Test
    public void testBuilder() {
        UsageInfo usageInfo = UsageInfo.builder()
                .productName(PRODUCT)
                .logName(LOG_NAME)
                .serviceId(SERVICE_ID)
                .nodeId(NODE_ID)
                .type(LOG_TYPE_USAGE)
                .consolidated(CONSOLIDATED)
                .logFileInputStream(INPUT_STREAM)
                .build();
        assertEquals(usageInfo.getProductName(), PRODUCT);
        assertEquals(usageInfo.getLogName(), LOG_NAME);
        assertEquals(usageInfo.getServiceId(), SERVICE_ID);
        assertEquals(usageInfo.getNodeId(), NODE_ID);
        assertEquals(usageInfo.getType(), LOG_TYPE_USAGE);
        assertEquals(usageInfo.getConsolidated(), CONSOLIDATED);
        assertEquals(usageInfo.getLogFileInputStream(), INPUT_STREAM);
    }

    @Test
    public void testGetProperties() {
        UsageInfo usageInfo = UsageInfo.builder()
                .productName(PRODUCT)
                .logName(LOG_NAME)
                .serviceId(SERVICE_ID)
                .nodeId(NODE_ID)
                .type(LOG_TYPE_USAGE)
                .consolidated(CONSOLIDATED)
                .logFileInputStream(INPUT_STREAM)
                .build();
        Map<String, String> properties = usageInfo.getProperties();
        assertEquals(properties.size(), 6);
        assertEquals(properties.get(LOG_NAME_FIELD), LOG_NAME);
        assertEquals(properties.get(PRODUCT_NAME_FIELD), PRODUCT);
        assertEquals(properties.get(NODE_ID_FIELD), NODE_ID);
        assertEquals(properties.get(TYPE_FIELD), LOG_TYPE_USAGE);
        assertEquals(properties.get(CONSOLIDATED_FIELD), CONSOLIDATED);
        assertEquals(properties.get(SERVICE_ID_FIELD), SERVICE_ID);
    }
}
