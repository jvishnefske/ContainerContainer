package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventContext;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventListener;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventType;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.interceptor.StorageAggregationInterceptor;

@Slf4j

public class PackageRepoImportInterceptorImpl implements StorageAggregationInterceptor {
    private final PackageRepositoryEventListener importListener;
    private final PackageSpecificationAdapter packageSpecificationAdapter;
    private final PackageRepoService repositoryService;

    public PackageRepoImportInterceptorImpl(PackageRepositoryEventListener packagesListener,
            PackageSpecificationAdapter packageSpecificationAdapter, PackageRepoService repositoryService) {
        this.importListener = packagesListener;
        this.packageSpecificationAdapter = packageSpecificationAdapter;
        this.repositoryService = repositoryService;
    }

    @Override
    public void afterRepoImport(RepoPath rootRepoPath, int itemsCount, MutableStatusHolder status) {
        final String repoKey = rootRepoPath.getRepoKey();
        try {
            if (packageSpecificationAdapter.isSupportedPackage(repoKey)) {
                RepoInfo repoInfo = repositoryService.getRepoInfo(repoKey);
                final PackageRepositoryEventContext repoImportEvent =
                        new PackageRepositoryEventContext(repoKey, repoInfo, PackageRepositoryEventType.REPO_IMPORT);
                importListener.handleEvent(repoImportEvent);
            }
        } catch (Exception e) {
            if (e instanceof PackageException pe) {
                log.error("Failed to handle afterRepoImport event on {}: {}, {}, {}, {}", repoKey,
                        pe.getStatus(), pe.getInternalReason(), pe.getPublicReason(), e.getMessage(), e);
            } else {
                log.error("Failed to handle afterRepoImport event on {}: {}", repoKey, e.getMessage(), e);
            }
        }
    }
}
