package org.artifactory.policy.cleanup.bundle;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.artifactory.policy.Policy;

/**
 * @author Chaitanya Vootla
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class BundleCleanupPolicy extends Policy<BundleCleanupPolicyConfig> {

}