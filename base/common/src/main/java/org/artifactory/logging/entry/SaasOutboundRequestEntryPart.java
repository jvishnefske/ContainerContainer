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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SaasOutboundRequestEntryPart extends SaasBaseRequestEntryPart {
    private String remoteRepository;
    private String sha1;
}
