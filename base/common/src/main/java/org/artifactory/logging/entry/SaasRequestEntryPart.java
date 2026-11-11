package org.artifactory.logging.entry;

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
public class SaasRequestEntryPart extends SaasBaseRequestEntryPart {
    private String client;
    private String userAgent;
}
