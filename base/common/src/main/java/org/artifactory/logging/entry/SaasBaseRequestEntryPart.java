package org.artifactory.logging.entry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Tomer Slutsky
 */
@Getter
@SuperBuilder
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class SaasBaseRequestEntryPart {
    @JsonProperty("traceid")
    private String traceId;
    private String user;
    private String method;
    private String path;
    private long length;
}
