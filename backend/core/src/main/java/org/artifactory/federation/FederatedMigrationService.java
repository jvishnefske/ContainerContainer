package org.artifactory.federation;

import org.artifactory.descriptor.repo.FederatedMember;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Migration scaffolding for federated local repositories (local → federated and federated → local conversions).
 * Kept separate from {@link FederationEnabledRepoService} so that when the migration era is over,
 * this interface and its implementation are the only things to delete.
 */
public interface FederatedMigrationService {

    void migrate(String repoKey);

    HashMap<String, String> convertFederatedToLocal(List<String> repoKeys);

    void enableRepos(List<String> repoTypes);

    boolean isStatusChanged(String repoKey, List<FederatedMember> newMembers, Set<FederatedMember> oldMembers);

    boolean isModeChanged(String repoKey, List<FederatedMember> newMembers, Set<FederatedMember> oldMembers);
}
