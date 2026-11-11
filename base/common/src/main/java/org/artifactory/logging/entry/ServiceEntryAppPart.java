package org.artifactory.logging.entry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Tomer Slutsky
 */
@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class ServiceEntryAppPart {
    private String dateTime;
    private String service;
    private String logLevel;
    @JsonProperty("traceid")
    private String traceId;
    @JsonProperty("class")
    private String clazz;
    private String line;
    private String thread;
    private String message;
}
