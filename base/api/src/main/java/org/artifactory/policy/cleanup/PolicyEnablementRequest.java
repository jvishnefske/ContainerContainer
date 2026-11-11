package org.artifactory.policy.cleanup;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Oleksandr Legenkiy
 */
@Data
@NoArgsConstructor
public class PolicyEnablementRequest {

    private boolean enabled;
}
