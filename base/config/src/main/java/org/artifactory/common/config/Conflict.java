package org.artifactory.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Conflict {
    @JsonProperty("field")
    String fieldKey;
    @JsonProperty("reason")
    String reason;

}
