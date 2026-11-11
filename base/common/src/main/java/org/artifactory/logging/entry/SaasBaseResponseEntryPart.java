package org.artifactory.logging.entry;

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
public class SaasBaseResponseEntryPart {
    private String dateTime;
    private long length;
    private String status;
    private long duration;
}
