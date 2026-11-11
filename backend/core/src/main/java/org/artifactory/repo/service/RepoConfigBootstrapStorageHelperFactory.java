package org.artifactory.repo.service;

import lombok.experimental.UtilityClass;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;

import java.util.HashSet;

/**
 * @author Mor Merhav
 */
@UtilityClass
public class RepoConfigBootstrapStorageHelperFactory {

    public RepoConfigBootstrapStorageHelper create() {
        var artifactoryContext = ContextHelper.get();
        var interceptors =
                new HashSet<>(artifactoryContext.beansForType(RepoConfigChangeInterceptor.class).values());
        var repositoryConfigStorageService =
                artifactoryContext.beanForType(RepositoryConfigStorageService.class);
        var repositoryConfigService =
                artifactoryContext.beanForType(RepositoryConfigService.class);
        var repoConfigChangesMapFunction = artifactoryContext.beanForType(RepoConfigChangesMapFunction.class);
        return new RepoConfigBootstrapStorageHelper(
                repositoryConfigStorageService, interceptors, repositoryConfigService::reloadRepoConfigs,
                repoConfigChangesMapFunction);
    }
}
