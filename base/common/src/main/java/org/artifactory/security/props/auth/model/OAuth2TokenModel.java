package org.artifactory.security.props.auth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author BarakH
 */
@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OAuth2TokenModel implements OauthModel {

    private String accessToken;
    private String tokenType;
    private int expiresIn;
    private String refreshToken;
    private String scope;

    public OAuth2TokenModel(String accessToken) {
        this.accessToken = accessToken;
        this.expiresIn = 3600;
        this.tokenType = "bearer";
    }
}
