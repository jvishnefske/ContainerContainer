package org.artifactory.metadata.service;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Re-indexes data on the Metadata Server for the given Repository types
 *
 * @author Uriah Levy
 */
public class MetadataReindexConverter implements MetadataConverter {
    private static final Logger log = LoggerFactory.getLogger(MetadataReindexConverter.class);

    private Set<RepoType> repoTypes;
    private boolean deleteFirst;
    private ExecutorService asyncExecutor;


    MetadataReindexConverter(Set<RepoType> repoTypes, boolean deleteFirst) {
        this.repoTypes = repoTypes;
        this.deleteFirst = deleteFirst;
        this.asyncExecutor = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(ConstantValues.metadataMigrationEventOperatorThreads.getInt())
                .maxThreads(ConstantValues.metadataMigrationEventOperatorThreads.getInt())
                .keepAliveTimeout(10L)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .allowCoreThreadTimeOut(true)
                .queue(new LinkedBlockingQueue<>())
                .daemon(true)
                .threadNameFormat("metadata-reindex-converter-thread-%d")
                .build(), ContextHelper.get(), false);
    }

    @Override
    public void convert() {
        MetadataEventService metadataEventService = ContextHelper.get().beanForType(MetadataEventService.class);
        RepositoryService repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        repoTypes.stream()
                .map(repositoryService::getRepoConfigsByPackageType)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(RepoConfig::getKey)
                .peek(repoKey -> log.info("Re-indexing {} on Metadata Server", repoKey))
                .forEach(repoKey -> asyncExecutor
                        .submit(() -> metadataEventService.reindexSync(List.of(repoKey), deleteFirst)));
    }

    @Override
    public boolean isInterested(ArtifactoryServiceVersion fromVersion) {
        // The upgrade is from a pre-migration version, no real need to run this converter
        return !fromVersion.before(ArtifactoryServiceVersionProvider.v700m001.get());
    }
}
