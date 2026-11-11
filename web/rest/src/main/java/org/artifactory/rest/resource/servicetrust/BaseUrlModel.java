package org.artifactory.rest.resource.servicetrust;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BaseUrlModel {

    @JsonProperty("base_url")
    private final String baseUrl;
}
