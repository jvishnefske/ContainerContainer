package org.artifactory.policy.cleanup.packages;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.artifactory.policy.Policy;

/**
 * @author vovam
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class PackageCleanupPolicy extends Policy<PackageCleanupPolicyConfig> {

}