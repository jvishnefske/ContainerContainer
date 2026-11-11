package org.artifactory.api.security.access;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Eli Fishman
 */
@Data
public class ProxyTokenResponse {
    @JsonProperty("token_id")
    private String tokenId;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("username")
    private String username;

    @JsonProperty("refresh_token")
    private String refreshToken;

}
