package org.artifactory.livelogs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author Andrey Tashlitsky
 */
@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiveLogsConfig {

    private List<String> logs;
    private List<String> nodes;
    @JsonProperty("refresh_rate_millis")
    private long refreshRateMillis;
}
