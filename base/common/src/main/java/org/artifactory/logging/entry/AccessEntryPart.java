package org.artifactory.logging.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AccessEntryPart {
    private String dateTime;
    private String traceId;
    private String action;
    private String response;
    @JsonProperty("path")
    private String repositoryPath;
    @JsonProperty("msg")
    private String message;
    private String username;
    private String userIp;
    @JsonProperty("type")
    private String authType;
}
