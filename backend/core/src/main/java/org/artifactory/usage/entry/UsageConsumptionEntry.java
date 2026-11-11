package org.artifactory.usage.entry;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents an entry for the Usage Consumption log.
 * <p/>
 * Examples:
 * <pre>
 * jfrt_projects_active_total 2 1614622006790
 * jfrt_ice_storage_current_total_size_bytes 213478645 1617302881405
 * jfpdn_traffic_transferred_bytes {type="download"} 32475 1617302881405
 * jfpip_build_time_seconds {type="per_pipeline_step", start="1607618859781", end="1607618861966",
 * project_key="04936349056", run="1261", step_key="45097650497"} 2.185 1607618880949
 * </pre>
 * More info:
 * <p/>
 * <pre>
 * https://sysdig.com/blog/prometheus-metrics
 * https://github.com/OpenObservability/OpenMetrics
 * </pre>
 *
 * @author Andrey Tashlitsky
 */
@Builder
public class UsageConsumptionEntry {

    private final String name;
    @Builder.Default
    private final Map<String, Object> labels = new HashMap<>();
    private final String value;
    @Builder.Default
    private final boolean timestamp = true;

    @Override
    public String toString() {
        return String.format("%s%s %s%s", name, labelsToString(labels), value, getTimestampIfNeeded());
    }

    private String labelsToString(Map<String, Object> labels) {
        if (labels == null || labels.size() == 0) {
            return StringUtils.EMPTY;
        }
        return StringUtils.SPACE + labels.entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + entry.getValue() + "\"")
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String getTimestampIfNeeded() {
        if (!timestamp) {
            return StringUtils.EMPTY;
        }
        return StringUtils.SPACE + Instant.now().toEpochMilli();
    }
}
