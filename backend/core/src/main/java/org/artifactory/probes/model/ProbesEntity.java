package org.artifactory.probes.model;

import lombok.Builder;
import lombok.Value;

/**
 * @author Andrey Tashlitsky
 */
@Value
@Builder
public class ProbesEntity {
    String code;
    String message;
}
