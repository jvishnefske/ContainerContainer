package org.artifactory.metadata.service.indexer;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.repo.RepoPath;

@Slf4j
public class MetadataDeletePackageTask implements Runnable {

    private final MetadataEventService metadataEventService;
    private final RepoPath repoPath;

    public MetadataDeletePackageTask(MetadataEventService metadataEventService, RepoPath repoPath) {
        this.metadataEventService = metadataEventService;
        this.repoPath = repoPath;
    }

    @Override
    public void run() {
        log.debug("Delete package from Metadata service at {}", repoPath);
        metadataEventService.deletePathFromMetadataServerUsingMetadataClient(repoPath);
    }
}
