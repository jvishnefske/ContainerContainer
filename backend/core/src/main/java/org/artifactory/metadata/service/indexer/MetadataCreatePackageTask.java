package org.artifactory.metadata.service.indexer;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.repo.RepoPath;
import org.jfrog.metadata.client.model.MetadataPackage;

@Slf4j
public class MetadataCreatePackageTask implements Runnable {

    private final MetadataEventService metadataEventService;
    private final MetadataPackage metadataPackage;
    private final RepoPath repoPath;

    public MetadataCreatePackageTask(MetadataEventService metadataEventService, MetadataPackage metadataPackage,
            RepoPath repoPath) {
        this.metadataEventService = metadataEventService;
        this.metadataPackage = metadataPackage;
        this.repoPath = repoPath;
    }

    @Override
    public void run() {
        log.debug("Creating/updating package metadata for '{}' ({})", metadataPackage.getPkgid(), repoPath);
        try {
            metadataEventService.getMetadataClient().packages().createOrUpdate(metadataPackage);
        } catch (Exception e) {
            log.error("Failed to create or update package ({}) in Metadata Service with an error: {}",
                    metadataPackage.getPkgid(), e.getMessage(), e);
        }
    }
}
