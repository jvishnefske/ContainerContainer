package org.artifactory.repo.service;

import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Mor Merhav
 */
public interface RepoConfigChangesMapFunction
        extends Function<Set<? extends RepoConfig>, Map<RepoDetailsType, List<RepoConfigChange>>> {

}
