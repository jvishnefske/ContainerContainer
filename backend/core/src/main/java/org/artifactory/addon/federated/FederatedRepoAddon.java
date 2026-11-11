package org.artifactory.addon.federated;

import org.artifactory.common.addon.Addon;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.exception.MissingRestAddonException;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface FederatedRepoAddon extends Addon {
    FederatedRepo createFederatedRepo(InternalRepositoryService repoService,
            FederatedRepoDescriptor descriptor,
            FederatedRepoConfig repoConfig,
            DbLocalRepo<FederatedRepoDescriptor, FederatedRepoConfig> oldFederatedRepo);

    Set<String> getUnstableRepositories();

    Set<String> getImportConflictsErrors();

    /**
     * Removes a repo from its federation members.
     * Handles both Federated repos and Remote repos with federation enabled.
     *
     * @param repoConfig the repo config to remove (FederatedRepoConfig or RemoteRepoConfig)
     */
    default void removeRepoFromFederationMembers(RepoConfig repoConfig) {
        // Default no-op implementation for OSS
    }

    default void saveAuthenticationMasterToken(String tokenId, String tokenType, String scope,
            String baseUrl,
            String exchangeUrl,
            String accessToken, Long expiresIn) {
        throw new MissingRestAddonException();
    }

    default Collection<AuthenticationToken> getPairAuthenticationTokens() {
        throw new MissingRestAddonException();
    }

    default Optional<AuthenticationToken> getAuthenticationToken(String baseUrl) {
        throw new MissingRestAddonException();
    }

    default void deleteAuthenticationToken(String baseUrl) {
        throw new MissingRestAddonException();
    }

    default void refreshAboutToExpireMasterTokens() {
        throw new MissingRestAddonException();
    }
}
