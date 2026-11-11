package org.artifactory.federation;

import java.util.Set;

/**
 * @author Tomer Slutsky
 */
public record RunningFullSyncs(Set<String> repoKeys) {
}
