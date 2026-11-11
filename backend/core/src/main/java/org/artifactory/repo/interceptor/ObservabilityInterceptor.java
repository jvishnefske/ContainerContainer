package org.artifactory.repo.interceptor;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
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
 * @author Tomer Slutsky
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObservabilityInterceptor extends StorageInterceptorAdapter {

    public static final String REPO_IS_FORBIDDEN = " repo is forbidden";
    private final SecurityService securityService;

    @Override
    public void beforeCreate(VfsItem fsItem, MutableStatusHolder statusHolder) {
        if (SystemRepos.isObservabilityRepo(fsItem.getRepoKey()) &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
            setStatusWarn("Deployment into " + fsItem.getRepoKey() + REPO_IS_FORBIDDEN, statusHolder);
        }
    }

    @Override
    public void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder status) {
        if (SystemRepos.isObservabilityRepo(repoKey)) {
            setStatusWarn("Deletion of Observability repository " + repoKey + " is not allowed.", status);
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
                    setStatusWarn("Deletion of artifacts from repository " + repoKey + " is not allowed.",
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
        if (SystemRepos.isObservabilityRepo(sourceItem.getRepoKey()) &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
            setStatusWarn("Copy/Move from " + sourceItem.getRepoKey() + REPO_IS_FORBIDDEN, status);
            return REJECT;
        }
        if (SystemRepos.isObservabilityRepo(targetRepoPath.getRepoKey()) &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
            setStatusWarn("Copy/Move into " + targetRepoPath.getRepoKey() + REPO_IS_FORBIDDEN, status);
            return REJECT;
        }

        return ALLOW;
    }

    private void setStatusWarn(String errMsg, MutableStatusHolder statusHolder) {
        statusHolder.warn(errMsg, SC_FORBIDDEN, new CancelException(errMsg, SC_FORBIDDEN), log);
    }
}
