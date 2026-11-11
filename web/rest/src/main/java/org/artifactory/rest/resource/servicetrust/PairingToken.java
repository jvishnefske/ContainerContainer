package org.artifactory.rest.resource.servicetrust;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PairingToken {
    @JsonProperty("pairing_token")
    private final String pairingToken;
}
