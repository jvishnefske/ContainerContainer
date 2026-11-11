package org.artifactory.repo.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryResultType;
import org.artifactory.sapi.search.VfsQueryRow;
import org.artifactory.sapi.search.VfsQueryService;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;

@Slf4j
@Component
public class PomVirtualCacheCleaner implements VirtualCacheCleaner {

    private final VfsQueryService vfsQueryService;

    private final InternalRepositoryService repositoryService;

    @Autowired
    public PomVirtualCacheCleaner(VfsQueryService vfsQueryService, InternalRepositoryService repositoryService) {
        this.vfsQueryService = vfsQueryService;
        this.repositoryService = repositoryService;
    }

    @Override
    public long cleanCache(@Nonnull VirtualRepoConfig repoConfig) {
        int maxAgeHours = ConstantValues.virtualCleanupMaxAgeHours.getInt();
        if (maxAgeHours < 0) {
            log.debug("Cleanup of virtual caches is disabled");
            return 0L;
        }
        int maxBatchSize = ConstantValues.virtualCleanupMaxBatch.getInt();
        int maxIterations = ConstantValues.virtualCleanupMaxIterations.getInt();
        boolean cont = true;
        long totalResults = 0;
        long expiryTime = new DateTime().minusHours(maxAgeHours).getMillis();
        while (cont && maxIterations-- > 0) {
            long resultCount = batchCleanCache(repoConfig, expiryTime, maxBatchSize);
            totalResults += resultCount;
            cont = resultCount > 0 && resultCount >= maxBatchSize;
        }
        return totalResults;
    }

    private long batchCleanCache(@Nonnull VirtualRepoConfig repoConfig, long expiryTime, int maxBatchSize) {
        VfsQuery query = vfsQueryService.createQuery().expectedResult(VfsQueryResultType.FILE)
                .name(ConstantValues.virtualCleanupNamePattern.getString()).comp(VfsComparatorType.CONTAINS)
                .setSingleRepoKey(repoConfig.getKey())
                .prop("created")// old bug - should be modified instead of created
                .comp(VfsComparatorType.LOWER_THAN_EQUAL)
                .val(expiryTime);
        VfsQueryResult queryResult = query.execute(maxBatchSize);
        long resultCount = queryResult.getCount();
        log.debug("Found {} cached files in {}", resultCount, repoConfig.getKey());
        for (VfsQueryRow result : queryResult.getAllRows()) {
            log.trace("Undeploying old cached file {}", result.getItem().getName());
            repositoryService.undeploy(result.getItem().getRepoPath());
        }
        return resultCount;
    }

    @Override
    public boolean shouldRun(VirtualRepoConfig virtualRepoConfig) {
        return virtualRepoConfig.getPackageType().isMavenGroup();
    }
}
