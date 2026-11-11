package org.artifactory.lifecycle.converter.postinit.v104;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.ExecutorService;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class UsageRepositoryCleanupConverter extends PostInitConverter {
    private ExecutorService executorService;

    public UsageRepositoryCleanupConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            log.info("Starting to cleanup usage repository");
            executorService =
                    ArtifactoryExecutorFactory.newFixedThreadPool(1, ContextHelper.get(), false);
            executorService.submit(() -> {
                securityService.authenticateAsSystem();
                log.info("Starting cleanup, entering undeploy");
                StatusHolder statusHolder = ContextHelper.get().beanForType(RepositoryService.class)
                        .undeployMultiTransaction(InternalRepoPathFactory.repoRootPath("jfrog-usage-logs"));
                if (statusHolder.isError()) {
                    log.warn("An error has occurred while cleaning usage repo: {}", statusHolder.getStatusMsg());
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Finished cleaning usage repository");
            });
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v7385m001.get())
                && target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7385m001.get())
                && ContextHelper.get().beanForType(RepositoryService.class).
                exists(RepoPathFactory.create("jfrog-usage-logs"));
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
