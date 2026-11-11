package org.artifactory.hqc.service.fullsync;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.federation.FullSyncException;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.FULL_SYNC;

/**
 * @author Tomer Slutsky
 */
@Slf4j
@AllArgsConstructor
@Component
public class FullSyncFileServiceImpl implements FullSyncFileService {
    private final SecurityService securityService;
    private final InternalRepositoryService internalRepositoryService;
    private static final String FULL_SYNC_FILE_SUFFIX = "_fullsync_result";
    /**
     * @param file        is the file that contains the source content
     * @param lastEventId is the last event id this current full sync is working on
     * @return full sync record that is saved in the heavy query cache table that points to the file in system repo
     * that contains the result of the aql
     */
    @Override
    public FullSyncRecord saveFileToSystemRepo(File file, long lastEventId, boolean addTimestamp) {
        try {
            return securityService.callAsSystem(() -> {
                validateSystemRepo();
                String fileName = file.getName();
                long timestamp = System.currentTimeMillis();
                if (addTimestamp) {
                    fileName += fileName + timestamp;
                }
                deployToRepo(file, RepoPathFactory.create(FULL_SYNC_REPO_KEY, fileName));
                log.trace("File {} deployed to repo {}", fileName, FULL_SYNC_REPO_KEY);
                // add to path record last log event id
                return new FullSyncRecord(new FullSyncFileRecord(fileName, FULL_SYNC_REPO_KEY, timestamp), lastEventId);
            });
        } catch (Exception e) {
            throw new FullSyncException("Failed to save file " + file.getAbsolutePath() + " due to :", e);
        }
    }

    @Override
    public FullSyncRecord saveFileToSystemRepo(File file) {
        return saveFileToSystemRepo(file, -1, false);
    }

    @Override
    public void undeployFromRepo(String fileName) {
        log.trace("Trying to undeploy from full sync repo due to an error");
        BasicStatusHolder holder =
                internalRepositoryService.undeploy(RepoPathFactory.create(FULL_SYNC_REPO_KEY, fileName));
        if (holder.hasErrors()) {
            log.trace("Failed to undeploy due to: {}", holder.getLastError());
        } else {
            log.trace("Undeploy from full sync repo was successful");
        }
    }

    private void validateSystemRepo() {
        if (!internalRepositoryService.existsByRepoKey(FULL_SYNC_REPO_KEY)) {
            initSystemRepo();
        }
    }

    private void initSystemRepo() {
        log.trace("{} was not found, therefore creating it as a system repo", FULL_SYNC_REPO_KEY);
        ContextHelper.get().beanForType(SystemRepoConfigFactory.class)
                .buildSystemRepo(FULL_SYNC, FULL_SYNC.getRepoKey());
    }

    public void deployToRepo(File file, RepoPath repoPath) {
        try (FileInputStream stream = new FileInputStream(file)) {
            log.info("Deploy file {} to {}", repoPath.getName(), repoPath.getRepoKey());
            StatusHolder deploy = internalRepositoryService.deploy(repoPath, stream);
            throwIfError(deploy, file);
        } catch (IOException ioException) {
            log.error("Failed to deploy file {} due to: {}", file.getAbsolutePath(), ioException.getMessage());
            throw new FullSyncException(
                    "Failed to deploy file: " + file.getAbsolutePath() + " to system repo due to: ",
                    ioException);
        }
    }

    @Override
    public String getFullSyncFileSuffix() {
        return FULL_SYNC_FILE_SUFFIX;
    }

    private void throwIfError(StatusHolder holder, File file) {
        if (holder.isError()) {
            log.warn("Failed to deploy the file locally", holder.getException());
            throw new FullSyncException("Failed to save file " + file.getAbsolutePath() + " due to ",
                    holder.getException());
        }
    }
}
