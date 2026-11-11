package org.artifactory.metrics.providers.features;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.metrics.exception.MaxSizeExceededException;
import org.artifactory.metrics.model.FeatureUsage;
import org.artifactory.metrics.model.IntegrationsFeatureGroup;
import org.artifactory.metrics.model.IntegrationsProductUsage;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author shivaramr
 */
@Test
public class IntegrationsFeatureTest {


    IntegrationsFeature integrationsFeature = new IntegrationsFeature();

    @Test(expectedExceptions = MaxSizeExceededException.class, expectedExceptionsMessageRegExp = ".*no more data will" +
            " be aggregated.*")
    public void testMaxProductSize() throws MaxSizeExceededException {
        //insert products that exceeds the limit
        for (int i = 0; i <= 100; i++) {
            IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();
            integrationsProductUsage.setProductId("test" + Integer.toString(i));

            List<FeatureUsage> featureUsages = new ArrayList<>();
            featureUsages.add(new FeatureUsage("testfeature", 1, null));
            integrationsProductUsage.setFeatures(featureUsages);

            integrationsFeature.addUsageData(integrationsProductUsage);
        }

    }

    @Test(expectedExceptions = MaxSizeExceededException.class, expectedExceptionsMessageRegExp = ".*Max capacity is .*")
    public void testMaxFeaturesSize() throws MaxSizeExceededException {
        //insert features per product that exceeds the limit
        integrationsFeature.clearData();
        IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();
        integrationsProductUsage.setProductId("test");
        List<FeatureUsage> featureUsages = new ArrayList<>();

        for (int i = 0; i <= 100; i++) {

            featureUsages.add(new FeatureUsage("featureId" + Integer.toString(i), 1, null));
            integrationsProductUsage.setFeatures(featureUsages);

        }
        integrationsFeature.addUsageData(integrationsProductUsage);

    }

    @Test
    public void testMaxUniqueClientsSize() throws MaxSizeExceededException {
        integrationsFeature.clearData();
        IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();
        integrationsProductUsage.setProductId("test");
        integrationsProductUsage.setFeatures(new ArrayList<>());
        for (int i = 0; i <= 10000; i++) {
            integrationsProductUsage.setUniqueClientId(Integer.toString(i));
            integrationsFeature.addUsageData(integrationsProductUsage);
        }
        assertEquals(integrationsFeature.productUniqueUsageMap.get("test").size(), 10000);
    }

    @Test(expectedExceptions = MaxSizeExceededException.class, expectedExceptionsMessageRegExp = ".*Total length of " +
            "attributes string is capped at.*")
    public void testMaxAttributeSize() throws MaxSizeExceededException {
        //insert attributes per feature that exceed limit
        integrationsFeature.clearData();
        IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();
        integrationsProductUsage.setProductId("test");
        List<FeatureUsage> featureUsages = new ArrayList<>();
        Map<String, Object> attributes = new HashMap<>();
        StringBuffer jsonBuffer = new StringBuffer().append("{");
        for (int i = 0; i < 10000; i++) {
            String fieldName = "field" + Integer.toString(i);
            String value = "value" + Integer.toString(i);
            jsonBuffer.append("\"");
            jsonBuffer.append(fieldName);
            jsonBuffer.append("\":");
            jsonBuffer.append("\"");
            jsonBuffer.append(value);
            if (i != 10000) { jsonBuffer.append("\","); }

        }
        jsonBuffer.append("}");
        attributes.put("featureData", jsonBuffer.toString());
        featureUsages.add(new FeatureUsage("featureId", 1, attributes));
        integrationsProductUsage.setFeatures(featureUsages);

        integrationsFeature.addUsageData(integrationsProductUsage);

    }

    @Test
    public void testAddUsage() throws MaxSizeExceededException {
        integrationsFeature.clearData();

        IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();
        integrationsProductUsage.setProductId("test");

        List<FeatureUsage> featureUsages = new ArrayList<>();
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> attributeData = new HashMap<>();
        attributeData.put("field", "value");
        attributes.put("testfeatureData", attributeData);
        featureUsages.add(new FeatureUsage("testfeature", 1, attributes));
        integrationsProductUsage.setFeatures(featureUsages);


        integrationsFeature.addUsageData(integrationsProductUsage);
        assertEquals(integrationsFeature.productUsageMap.size(), 1);
        Map<String, FeatureUsage> featureUsagesInMap = integrationsFeature.productUsageMap.get("test");
        FeatureUsage featureUsageInMap = featureUsagesInMap.get("testfeature");

        assertNotNull(featureUsageInMap.getAttributes());
        assertTrue(featureUsageInMap.getAttributes().containsKey("testfeatureData"));

        integrationsFeature.clearData();
        assertEquals(integrationsFeature.productUsageMap.size(), 0);

    }

    public void testAggregatedCount() throws MaxSizeExceededException {
        integrationsFeature.clearData();

        IntegrationsProductUsage integrationsProductUsage1 = new IntegrationsProductUsage();
        integrationsProductUsage1.setProductId("test");
        List<FeatureUsage> featureUsages1 = new ArrayList<>();
        featureUsages1.add(new FeatureUsage("testfeature", 0, null));
        integrationsProductUsage1.setFeatures(featureUsages1);
        integrationsFeature.addUsageData(integrationsProductUsage1);

        IntegrationsProductUsage integrationsProductUsage2 = new IntegrationsProductUsage();
        integrationsProductUsage2.setProductId("test");
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> attributeData = new HashMap<>();
        attributeData.put("field", "value");
        attributes.put("featureData", attributeData);
        List<FeatureUsage> featureUsages2 = new ArrayList<>();
        featureUsages2.add(new FeatureUsage("testfeature", 3, attributes));
        integrationsProductUsage2.setFeatures(featureUsages2);
        integrationsFeature.addUsageData(integrationsProductUsage2);

        // Number of entries should still be 1
        assertEquals(integrationsFeature.productUsageMap.size(), 1);

        Map<String, FeatureUsage> featureUsagesInMap = integrationsFeature.productUsageMap.get("test");
        FeatureUsage featureUsageInMap = featureUsagesInMap.get("testfeature");

        // Count is summation of 3 + 1(for 0)
        assertEquals(featureUsageInMap.getCount(), 4);

        // first time, attribue = null, second time it has featureData
        assertNotNull(featureUsageInMap.getAttributes());
        assertTrue(featureUsageInMap.getAttributes().containsKey("featureData"));

        integrationsFeature.clearData();
        assertEquals(integrationsFeature.productUsageMap.size(), 0);

    }

    public void testAggregatedUniqueClients() throws MaxSizeExceededException {
        integrationsFeature.clearData();
        IntegrationsProductUsage integrationsProductUsage = new IntegrationsProductUsage();

        integrationsProductUsage.setProductId("product-id-1");
        integrationsProductUsage.setFeatures(new ArrayList<>());

        // Add the same unique client ID twice
        integrationsProductUsage.setUniqueClientId("1");
        integrationsFeature.addUsageData(integrationsProductUsage);
        assertEquals(integrationsFeature.productUniqueUsageMap.get("product-id-1"), Sets.newHashSet("1"));
        integrationsProductUsage.setUniqueClientId("1");
        integrationsFeature.addUsageData(integrationsProductUsage);
        assertEquals(integrationsFeature.productUniqueUsageMap.get("product-id-1"), Sets.newHashSet("1"));

        // Add a different unique client ID
        integrationsProductUsage.setUniqueClientId("2");
        integrationsFeature.addUsageData(integrationsProductUsage);
        assertEquals(integrationsFeature.productUniqueUsageMap.get("product-id-1"), Sets.newHashSet("1", "2"));

        // Add the same unique client ID for a different product
        integrationsProductUsage.setProductId("product-id-2");
        integrationsProductUsage.setUniqueClientId("3");
        integrationsFeature.addUsageData(integrationsProductUsage);
        assertEquals(integrationsFeature.productUniqueUsageMap.get("product-id-1"), Sets.newHashSet("1", "2"));
        assertEquals(integrationsFeature.productUniqueUsageMap.get("product-id-2"), Sets.newHashSet("3"));
    }

    public void testUniqueClientInGetFeature() throws MaxSizeExceededException {
        integrationsFeature.clearData();

        // Create integration feature for product-id-1
        IntegrationsProductUsage integrationsProductUsage =
                new IntegrationsProductUsage("product-id-1", new ArrayList<>(), "1");
        integrationsFeature.addUsageData(integrationsProductUsage);
        integrationsProductUsage.setUniqueClientId("2");
        integrationsFeature.addUsageData(integrationsProductUsage);

        // Create integration feature for produce-id-2
        integrationsProductUsage.setProductId("product-id-2");
        integrationsProductUsage.setUniqueClientId("3");
        integrationsFeature.addUsageData(integrationsProductUsage);

        // Run getFeature
        IntegrationsFeatureGroup featureGroup = (IntegrationsFeatureGroup) integrationsFeature.getFeature();

        // Check the returned FeatureGroup
        ObjectNode root = JacksonFactory.createObjectMapper().valueToTree(featureGroup);
        for (JsonNode jsonNode : root.get("features")) {
            if (jsonNode.get("name").asText().equals("product-id-1")) {
                // Check unique count of product-id-1
                assertEquals(jsonNode.get("uniqueCount").asInt(), 2);
            } else {
                // Check unique count of product-id-2
                assertEquals(jsonNode.get("uniqueCount").asInt(), 1);
            }
        }
    }
}
