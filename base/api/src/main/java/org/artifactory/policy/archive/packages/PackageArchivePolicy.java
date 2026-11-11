package org.artifactory.policy.archive.packages;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyType;

/**
 * @author Kunal Mazumdar
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class PackageArchivePolicy extends Policy<PackageArchivePolicyConfig> {
    @Override
    public PolicyType getType() {
        return PolicyType.ARCHIVE;
    }
}