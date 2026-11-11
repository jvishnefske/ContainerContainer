package org.artifactory.intransit.interceptor;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.exception.ReleaseBundleArtifactValidationException;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.release.bundle.ReleaseBundleUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

/**
 * @author Eli Skoran 06/11/2022
 */
@Component
@Slf4j
@AllArgsConstructor
public class IntransitInterceptor extends StorageInterceptorAdapter {

    private AuthorizationService authorizationService;
    private SecurityService securityService;

    static final PermissionScope REQUIRED_SCOPE_TOKEN =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL, "_intransit", "admin",
                    Action.EXECUTE);

    @Override
    public void beforeCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder status) {
        RepoPath repoPath = fsItem.getRepoPath();
        if (!isIntransitRepo(repoPath)) {
            return;
        }

        if (!fsItem.isFile()) {
            return;
        }

        boolean enabled = ConstantValues.enhancedDistributionRepoSecurityEnabled.getBoolean();

        if (enabled && !isAllowedToAct("beforeCreate", repoPath.toString())) {
            String message = "upload to " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden";
            status.error(message, HttpStatus.SC_FORBIDDEN, new CancelException(message, HttpStatus.SC_FORBIDDEN), log);
            return;
        }

        validateArtifactPartOfBundle(repoPath, status);
    }

    private static void validateArtifactPartOfBundle(@NonNull RepoPath repoPath, @NonNull MutableStatusHolder status) {
        RepoPath targetRepoPath;
        Pair<String, String> nameVersion;
        try {
            targetRepoPath = ReleaseBundleUtils.extractTargetPathFromIntransitPath(repoPath);
            nameVersion = ReleaseBundleUtils.extractReleaseBundleNameAndVersionFromIntransitPath(repoPath);
        } catch (IllegalArgumentException e) {
            setUploadError(status);
            return;
        }

        try {
            boolean isPartOfBundle = getReleaseBundleAddon()
                    .isArtifactPartOfTargetBundle(nameVersion.getKey(), nameVersion.getValue(),
                            targetRepoPath.toPath());

            if (!isPartOfBundle) {
                setUploadError(status);
            }
        } catch (ReleaseBundleArtifactValidationException e) {
            final String errorMessage = "Failed validating artifact %s which is taking part of the release bundle %s/%s"
                    .formatted(targetRepoPath.toPath(), nameVersion.getKey(), nameVersion.getValue());
            log.error(errorMessage, e);
            status.error(errorMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    new CancelException(errorMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR), log);
        }
    }

    private static void setUploadError(@NonNull MutableStatusHolder status) {
        String message = "upload to " + SystemRepos.IN_TRANSIT_REPO_KEY +
                " is only allowed for artifacts that are part of a release bundle";
        status.error(message, HttpStatus.SC_FORBIDDEN, new CancelException(message, HttpStatus.SC_FORBIDDEN), log);
    }

    @Override
    public void assertDeletePathAllowed(RepoPath repoPath, MutableStatusHolder status) {
        if (!isIntransitRepo(repoPath)) {
            return;
        }

        boolean enabled = ConstantValues.enhancedDistributionRepoSecurityEnabled.getBoolean();

        if (enabled && !isAllowedToAct("assertDeletePathAllowed", repoPath.toString())) {
            String message = "external operations on " + SystemRepos.IN_TRANSIT_REPO_KEY + " are forbidden";
            status.error(message, HttpStatus.SC_FORBIDDEN, new CancelException(message, HttpStatus.SC_FORBIDDEN), log);
        }
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem<? extends ItemInfo> sourceItem,
            RepoPath targetRepoPath, MutableStatusHolder statusHolder) {
        return validateCopyMove(sourceItem, targetRepoPath, statusHolder, "isCopyOrMoveAllowed", "copy/move");
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        MutableStatusHolder statusHolder = context.getStatusHolder();
        List<VfsItem<? extends ItemInfo>> sourceItems = context.getSourceItems();
        List<RepoPath> targetRepoPaths = context.getTargetRepoPaths();

        for (int i = 0; i < sourceItems.size(); i++) {
            validateCopyMove(sourceItems.get(i), targetRepoPaths.get(i), statusHolder, "beforeCopyFiles", "copying");
        }
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext context) {
        MutableStatusHolder statusHolder = context.getStatusHolder();
        List<VfsItem<? extends ItemInfo>> sourceItems = context.getSourceItems();
        List<RepoPath> targetRepoPaths = context.getTargetRepoPaths();

        for (int i = 0; i < sourceItems.size(); i++) {
            validateCopyMove(sourceItems.get(i), targetRepoPaths.get(i), statusHolder, "beforeMoveFiles", "moving");
        }
    }

    @Override
    public void beforeMove(VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder, Properties properties) {

        validateCopyMove(sourceItem, targetRepoPath, statusHolder, "beforeMove", "moving");
    }

    @Override
    public void beforePropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {

        validatePropChange(fsItem, statusHolder);
    }

    @Override
    public void beforePropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key) {
        validatePropChange(fsItem, statusHolder);
    }

    private void validatePropChange(@NonNull VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder) {
        if (!isIntransitRepo(fsItem.getRepoPath())) {
            return;
        }

        boolean enabled = ConstantValues.enhancedDistributionRepoSecurityEnabled.getBoolean();

        if (enabled && !isAllowedToAct("validatePropChange", fsItem.getRepoPath().toString())) {
            String message =
                    "modifying properties on an item from " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden";
            statusHolder.error(message, HttpStatus.SC_FORBIDDEN, new CancelException(message, HttpStatus.SC_FORBIDDEN),
                    log);
        }
    }

    private boolean isSystemUser() {
        String userName = securityService.currentUsername();
        return SecurityService.USER_SYSTEM.equals(userName);
    }

    private boolean isIntransitRepo(@NonNull RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        return SystemRepos.IN_TRANSIT_REPO_KEY.equals(repoKey);
    }

    private boolean isAllowedToAct(@NonNull String action, @NonNull String path) {
        boolean isSystem = isSystemUser();
        if (isSystem) {
            log.debug("{} is triggered on item: {} by system user. will not block",
                    action, path);
            return true;
        }

        if (authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)) {
            log.debug("{} is triggered on an item: {} with the required scope token. Will not block",
                    action, path);
            return true;
        }

        return false;
    }

    private CopyMoveCheckResult validateCopyMove(
            @NonNull VfsItem<? extends ItemInfo> sourceItem,
            @NonNull RepoPath targetRepoPath,
            @NonNull MutableStatusHolder statusHolder,
            @NonNull String invokingMethodName,
            @NonNull String operation
    ) {
        boolean enabled = ConstantValues.enhancedDistributionRepoSecurityEnabled.getBoolean();
        if (!enabled) {
            return ALLOW;
        }

        boolean isSourceInTransit = isIntransitRepo(sourceItem.getRepoPath());
        boolean isTargetInTransit = isIntransitRepo(targetRepoPath);

        boolean allowed;
        if (!isSourceInTransit && !isTargetInTransit) {
            allowed = true;
        } else if (isSourceInTransit && isAllowedToAct(invokingMethodName, sourceItem.getRepoPath().toString())) {
            // copy/move from _intransit is allowed for token + system
            allowed = true;
        } else if (isTargetInTransit && isSystemUser()) {
            // copy/move to _intransit is allowed only for system user
            allowed = true;
        } else {
            String message = operation + " to/from " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden";
            statusHolder.error(message, HttpStatus.SC_FORBIDDEN, new CancelException(message, HttpStatus.SC_FORBIDDEN),
                    log);
            allowed = false;
        }
        return allowed ? ALLOW : REJECT;
    }

    @NonNull
    private static ReleaseBundleAddon getReleaseBundleAddon() {
        return ContextHelper.get().beanForType(AddonsManager.class)
                .addonByType(ReleaseBundleAddon.class);
    }
}
