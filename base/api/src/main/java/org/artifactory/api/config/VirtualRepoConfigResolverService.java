package org.artifactory.api.config;

import jakarta.annotation.Nullable;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Service interface for resolving virtual repository configurations.
 * Provides methods to retrieve virtual repository configurations by key,
 * get all repository configuration keys, and retrieve repository configurations
 * in a specific order based on a given set of repository keys.
 *
 * @author Mor Merhav
 */
public interface VirtualRepoConfigResolverService {

    @Nullable
    VirtualRepoConfig getVirtualRepoConfigByKey(String repoKey);

    Set<String> getAllRepoConfigKeys();

    LinkedHashSet<RepoConfig> getRepoConfigsByKeysInSpecificOrder(VirtualRepoConfig virtualRepoConfig,
            Set<String> allRepoKeys);
}