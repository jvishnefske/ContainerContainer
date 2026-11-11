package org.artifactory.policy.restore;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.artifactory.policy.Policy;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class PackageRestorePolicy extends Policy<PackageRestorePolicyConfig> {

}
