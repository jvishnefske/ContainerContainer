package org.artifactory.repo.confighandler.manager;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelperFactory;
import org.springframework.stereotype.Component;

/**
 * @author Mor Merhav
 * A factory for {@link UpdateOrReloadStrategy} objects based on given {@link UpdateOrReloadStrategyMode}
 */
@Slf4j
@Component
public class UpdateOrReloadStrategyFactory {

    /**
     * Factory method for creating {@link UpdateOrReloadStrategy} objects
     * @param strategyMode the mode deciding which strategy object to create
     * @return an update or reload strategy corresponding to the given strategy mode, or
     * {@link UpdateOrReloadStrategyMode#STANDARD} if no valid mode was provided
     */
    public UpdateOrReloadStrategy create(UpdateOrReloadStrategyMode strategyMode) {
        if (strategyMode == null) {
            return createStandardStrategy();
        }
        return switch (strategyMode) {
            case STANDARD -> createStandardStrategy();
            case SYSTEM_STARTUP -> createSystemStartupStrategy();
        };
    }

    private UpdateOrReloadStrategy createSystemStartupStrategy() {
        return new SystemStartupUpdateOrReloadStrategy(RepoConfigBootstrapStorageHelperFactory.create());
    }

    private UpdateOrReloadStrategy createStandardStrategy() {
        ArtifactoryContext context = ContextHelper.get();
        InternalRepositoryService repositoryService = context.beanForType(InternalRepositoryService.class);
        RepositoryConfigService repositoryConfigService =
                context.beanForType(RepositoryConfigService.class);
        return new StandardUpdateOrReloadStrategy(repositoryService);
    }

    /**
     * Enum to used to create different {@link UpdateOrReloadStrategy} objects
     */
    public enum UpdateOrReloadStrategyMode {
        SYSTEM_STARTUP,
        STANDARD
    }
}
