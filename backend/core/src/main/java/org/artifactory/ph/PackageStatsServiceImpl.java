package org.artifactory.ph;

import com.jfrog.ph.acl.service.PackageStatsService;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.fs.service.StatsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Bridges the package-handler {@link PackageStatsService} ACL onto Artifactory's {@link StatsService}.
 *
 * @author Linoy Levi
 */
@Slf4j
@Service
public class PackageStatsServiceImpl implements PackageStatsService {

    private final StatsService statsService;

    public PackageStatsServiceImpl(@Qualifier("statsServiceImpl") StatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public void fileDownloaded(String repoKey, String path, String downloadedBy, long downloadedTime,
            boolean fromAnotherArtifactory) {
        statsService.fileDownloaded(RepoPathFactory.create(repoKey, path), downloadedBy, downloadedTime,
                fromAnotherArtifactory);
    }
}
