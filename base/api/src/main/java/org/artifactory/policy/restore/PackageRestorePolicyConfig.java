package org.artifactory.policy.restore;

import lombok.Builder;
import org.artifactory.policy.PolicyConfig;

import java.util.List;

@Builder(toBuilder = true)
public record PackageRestorePolicyConfig(Boolean restoreToOriginal,
                                         String fallbackDestinationPath,
                                         String centralDestinationPath,
                                         List<String> includedProjects) implements PolicyConfig {
}
