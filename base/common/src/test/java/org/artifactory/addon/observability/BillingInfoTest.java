package org.artifactory.addon.observability;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.artifactory.addon.observability.BillingInfo.FEATURE_FIELD;
import static org.artifactory.addon.observability.BillingInfo.TYPE_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.LOG_NAME_FIELD;
import static org.artifactory.addon.observability.ObservabilityInfo.PRODUCT_NAME_FIELD;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class BillingInfoTest {

    private static final String COOL_PRODUCT = "cool-product";
    private static final String COOL_TYPE = "cool-type";
    private static final String COOL_LOG_NAME = "cool-log-name";
    private static final String COOL_FEATURE = "cool-feature";
    private static final InputStream COOL_INPUT_STREAM = new ByteArrayInputStream("cool-input-stream".getBytes());

    @Test
    public void testBuilder() {
        BillingInfo billingInfo = BillingInfo.builder()
                .productName(COOL_PRODUCT)
                .type(COOL_TYPE)
                .logName(COOL_LOG_NAME)
                .feature(COOL_FEATURE)
                .logFileInputStream(COOL_INPUT_STREAM)
                .build();
        assertEquals(billingInfo.getProductName(), COOL_PRODUCT);
        assertEquals(billingInfo.getType(), COOL_TYPE);
        assertEquals(billingInfo.getLogName(), COOL_LOG_NAME);
        assertEquals(billingInfo.getFeature(), COOL_FEATURE);
        assertEquals(billingInfo.getLogFileInputStream(), COOL_INPUT_STREAM);
    }

    @Test
    public void testGetProperties() {
        BillingInfo billingInfo = BillingInfo.builder()
                .productName(COOL_PRODUCT)
                .type(COOL_TYPE)
                .logName(COOL_LOG_NAME)
                .feature(COOL_FEATURE)
                .logFileInputStream(COOL_INPUT_STREAM)
                .build();
        Map<String, String> properties = billingInfo.getProperties();
        assertEquals(properties.size(), 4);
        assertEquals(properties.get(LOG_NAME_FIELD), COOL_LOG_NAME);
        assertEquals(properties.get(PRODUCT_NAME_FIELD), COOL_PRODUCT);
        assertEquals(properties.get(TYPE_FIELD), COOL_TYPE);
        assertEquals(properties.get(FEATURE_FIELD), COOL_FEATURE);
    }
}
