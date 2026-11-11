package org.artifactory.repo.interceptor;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.CancelException;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.springframework.stereotype.Service;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

/**
 * @author Shahar Palmor
 */
@Slf4j
@Service
@AllArgsConstructor
public class FullSyncStorageInterceptor extends StorageInterceptorAdapter {

    private final SecurityService securityService;
    private static final String error = " repo is forbidden";

    @Override
    public void beforeCreate(VfsItem fsItem, MutableStatusHolder statusHolder) {
        if (SystemRepos.isFullSyncRepo(fsItem.getRepoKey()) &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
            setStatusError("Deployment into " + fsItem.getRepoKey() + error, statusHolder);
        }
    }

    @Override
    public void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder status) {
        if (SystemRepos.isFullSyncRepo(repoKey)) {
            status.error("Deletion of Full Sync Info repository " + repoKey + " is not allowed.", SC_FORBIDDEN, log);
        }
    }

    @Override
    public void beforeDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (!ctx.isTriggeredByMove()) {
            String repoKey = fsItem.getRepoKey();
            if (SystemRepos.SystemRepoConfig.isDeleteOnlyBySuperUser(repoKey)) {
                boolean dashboardUser = isDashboardUser();
                boolean superUser = SecurityService.USER_SYSTEM.equals(securityService.currentUsername())
                        || dashboardUser;
                if (!superUser) {
                    setStatusError("Deletion of artifacts from repository " + repoKey + " is not allowed.",
                            statusHolder);
                }
            }
        }
    }

    @VisibleForTesting
    boolean isDashboardUser() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        return aolService.isAol() && aolService.isDashboardUser();
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem sourceItem, RepoPath targetRepoPath, MutableStatusHolder status) {
        if (SystemRepos.isFullSyncRepo(sourceItem.getRepoKey())) {
            if (!SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
                setStatusError("Copy/Move from " + sourceItem.getRepoKey() + error, status);
                return REJECT;
            }
        } else if (SystemRepos.isFullSyncRepo(targetRepoPath.getRepoKey()) &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
            setStatusError("Copy/Move into " + targetRepoPath.getRepoKey() + error, status);
            return REJECT;
        }
        return ALLOW;
    }

    private void setStatusError(String errMsg, MutableStatusHolder statusHolder) {
        statusHolder.error(errMsg, SC_FORBIDDEN, new CancelException(errMsg, SC_FORBIDDEN), log);
    }
}
