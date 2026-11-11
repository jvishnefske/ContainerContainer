package org.artifactory.api.security.access;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Eli Fishman
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class ProxyTokenRequest implements AccessRestRequest {

    @JsonProperty("grant_type")
    private String grantType;

    @JsonProperty("username")
    private String username;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("refreshable")
    private Boolean refreshable;

    @JsonProperty("description")
    private String description;

    @JsonProperty("audience")
    private String audience;

    @JsonProperty("include_reference_token")
    private Boolean includeReferenceToken;
}
