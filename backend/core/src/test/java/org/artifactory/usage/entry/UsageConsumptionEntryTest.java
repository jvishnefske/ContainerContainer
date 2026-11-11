package org.artifactory.usage.entry;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
public class UsageConsumptionEntryTest {

    private final Map<String, Object> linkedHashMap = new LinkedHashMap<>();

    @BeforeClass
    public void beforeClass() {
        linkedHashMap.put("type", "per_pipeline_step");
        linkedHashMap.put("start", "1607618859781");
        linkedHashMap.put("end", "1607618861966");
        linkedHashMap.put("project_key", "04936349056");
        linkedHashMap.put("run", "1261");
        linkedHashMap.put("step_key", "45097650497");
    }

    @Test(dataProvider = "testUsageConsumptionEntryToStringDataProvider")
    public void testUsageConsumptionEntryToString(UsageConsumptionEntry usageConsumptionEntry, String expectedString) {
        assertTrue(usageConsumptionEntry.toString().matches(expectedString));
    }

    @DataProvider
    private Object[][] testUsageConsumptionEntryToStringDataProvider() {
        return new Object[][] {
                {UsageConsumptionEntry.builder()
                        .name("jfrt_projects_active_total")
                        .value("2")
                        .timestamp(true)
                        .build(),
                        "jfrt_projects_active_total 2 (\\d+)$"},
                {UsageConsumptionEntry.builder()
                        .name("jfrt_projects_active_total")
                        .labels(null)
                        .value("2")
                        .timestamp(true)
                        .build(),
                        "jfrt_projects_active_total 2 (\\d+)$"},
                {UsageConsumptionEntry.builder()
                        .name("jfrt_projects_active_total")
                        .value("2")
                        .timestamp(false)
                        .build(),
                        "jfrt_projects_active_total 2$"},
                {UsageConsumptionEntry.builder()
                        .name("jfrt_ice_storage_current_total_size_bytes")
                        .value("213478645")
                        .timestamp(true)
                        .build(),
                        "jfrt_ice_storage_current_total_size_bytes 213478645 (\\d+)$"},
                {UsageConsumptionEntry.builder()
                        .name("jfpdn_traffic_transferred_bytes")
                        .labels(Map.of("type", "download"))
                        .value("32475")
                        .timestamp(true)
                        .build(),
                        "jfpdn_traffic_transferred_bytes \\{type=\"download\"\\} 32475 (\\d+)$"},
                {UsageConsumptionEntry.builder()
                        .name("jfpip_build_time_seconds")
                        .labels(linkedHashMap)
                        .value("2.185")
                        .timestamp(true)
                        .build(),
                        "jfpip_build_time_seconds \\{type=\"per_pipeline_step\", start=\"1607618859781\", " +
                                "end=\"1607618861966\", project_key=\"04936349056\", run=\"1261\", " +
                                "step_key=\"45097650497\"\\} 2.185 (\\d+)$"}
        };
    }
}
