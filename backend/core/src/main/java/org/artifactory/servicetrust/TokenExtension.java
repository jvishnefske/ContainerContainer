package org.artifactory.servicetrust;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author igoru
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenExtension {

    public static final TokenExtension REVOCABLE_TOKEN = new TokenExtension(null, null, null, true);

    @JsonProperty("base_url")
    private String baseUrl;

    @JsonProperty("exchange_url")
    private String exchangeUrl;

    @JsonProperty("pairing_url")
    private String pairingUrl;

    @JsonProperty("revocable")
    private boolean revocable = true;
}
