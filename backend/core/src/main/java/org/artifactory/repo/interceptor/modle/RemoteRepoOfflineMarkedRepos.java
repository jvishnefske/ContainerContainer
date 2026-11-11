package org.artifactory.repo.interceptor.modle;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Persisted list of remote repo keys the offline-guard set to offline. Used to scope re-online to
 * repos we ourselves marked, so repos an admin offlined manually are left alone.
 *
 * @author Hussein Alshqerat
 */
@Data
public class RemoteRepoOfflineMarkedRepos {

    private Set<String> repoKeys = new HashSet<>();
}
