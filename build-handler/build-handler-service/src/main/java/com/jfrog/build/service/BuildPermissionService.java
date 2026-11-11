package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildSecurityDescriptor;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.access.common.PermissionScopeResourceType.ARTIFACT;
import static org.jfrog.access.common.PermissionScopeResourceType.BUILD;

@RequiredArgsConstructor
public class BuildPermissionService {

    public static final String PERMISSION_READ = "read";
    public static final String PERMISSION_ANNOTATE = "annotate";
    public static final String PERMISSION_WRITE = "write";
    public static final String PERMISSION_DELETE = "delete";
    public static final String PERMISSION_MANAGE = "manage";

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    public boolean hasBasicReadPermissionForAllBuilds() {
        return bridgeToArtifactory.isAdmin() || isBuildGlobalBasicReadAllowed();
    }

    @NonNull
    public List<String> getArtifactActions(
            @NonNull String repoKey,
            @NonNull String path) {
        return bridgeToArtifactory.getArtifactActions(repoKey, path);
    }

    public void assertCanManageBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        if (!canManageBuild(buildName, buildNumber, buildStarted, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to manage build info. Manage permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    @NonNull
    public BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        return bridgeToArtifactory.getBuildEffectivePermissions(buildName, buildNumber, buildStarted, buildRepo);
    }

    @NonNull
    public BuildPermissionsModel getBuildEffectivePermissionsByRepoPath(
            @NonNull String repoKey,
            @NonNull String path) {
        return bridgeToArtifactory.getBuildEffectivePermissionsByRepoPath(repoKey, path);
    }

    @NonNull
    public List<String> getBuildPermissionsByUser(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String username) {
        return bridgeToArtifactory.getBuildPermissionsByUser(buildName, buildNumber, buildStarted, buildRepo, username);
    }

    @NonNull
    public List<String> getBuildPermissionsByGroup(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String groupName) {
        return bridgeToArtifactory.getBuildPermissionsByGroup(buildName, buildNumber, buildStarted, buildRepo,
                groupName);
    }

    public boolean isBuildBasicRead(@NonNull BuildRun build) {
        return isBuildBasicRead(build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo());
    }

    public boolean isBuildBasicRead(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        return isBuildGlobalBasicReadAllowed() || canReadBuild(buildName, buildNumber, buildStarted, buildRepo);
    }

    public boolean isBuildGlobalBasicReadAllowed() {
        BuildSecurityDescriptor security = bridgeToArtifactory.getSecurityDescriptor();
        boolean buildGlobalBasicReadAllowed = security.isBuildGlobalBasicReadAllowed();
        boolean buildGlobalBasicReadForAnonymous = security.isBuildGlobalBasicReadForAnonymous();
        if (bridgeToArtifactory.isAnonymous()) {
            return buildGlobalBasicReadForAnonymous && buildGlobalBasicReadAllowed;
        }
        return buildGlobalBasicReadAllowed;
    }

    public boolean isBuildBasicRead(
            @NonNull String buildName,
            @NonNull String buildRepo) {
        return isBuildGlobalBasicReadAllowed() || canReadBuildPath(buildRepo, buildName);
    }

    private boolean canReadBuildPath(
            @NonNull String repoKey,
            @NonNull String buildName) {
        return canReadArtifact(buildRootPathInRepo(repoKey, buildName));
    }

    public void assertBasicReadPermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        if (!isBuildBasicRead(buildName, buildNumber, buildStarted, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to access build info. "
                            + "'Global Basic Read' Flag turned on or read permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertBasicReadPermissions(
            @NonNull String buildName,
            @NonNull String buildRepo) {
        if (!isBuildBasicRead(buildName, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to access build info. "
                            + "'Global Basic Read' Flag turned on or read permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertReadPermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        if (!canReadBuild(buildName, buildNumber, buildStarted, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to access build info. Read permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertDeletePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        if (!canDeleteBuild(buildName, buildNumber, buildStarted, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to delete build info. Delete permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertDeletePermissions(
            @NonNull String buildName,
            @NonNull String buildRepo) {
        if (!canDeleteBuildName(buildName, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to delete build info. Delete permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertUploadPermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        if (!canUploadBuild(buildName, buildNumber, buildStarted, buildRepo)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to upload build info. Upload permission is needed.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    public void assertCanViewBuildPermissions(@NonNull String projectKey) {
        if (!canViewBuildPermissions(projectKey)) {
            throw forbiddenException(
                    "The user: '%s' is not authorized to view build permissions.",
                    bridgeToArtifactory.getCurrentUsername());
        }
    }

    private boolean canViewBuildPermissions(@NonNull String projectKey) {
        return bridgeToArtifactory.isAdmin()
                || bridgeToArtifactory.isProjectAdmin(projectKey)
                || bridgeToArtifactory.isResourceManager();
    }

    public boolean canReadBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        BuildRepoPath repoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, buildRepo);
        return canReadArtifact(repoPath.toString()) || canReadBuild(repoPath.toString());
    }

    public boolean canUploadBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        BuildRepoPath repoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, buildRepo);
        return canWriteArtifact(repoPath.toString());
    }

    public boolean canDeleteBuildName(
            @NonNull String buildName,
            @NonNull String buildRepo) {
        String repoPath = buildRootPathInRepo(buildRepo, buildName);
        return canDeleteArtifact(repoPath);
    }

    public boolean canDeleteBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        BuildRepoPath repoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, buildRepo);
        return canDeleteArtifact(repoPath.toString());
    }

    public boolean canManageBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        BuildRepoPath repoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, buildRepo);
        return canManageArtifact(repoPath.toString());
    }

    public boolean canReadArtifact(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(ARTIFACT, PERMISSION_READ, repoPath);
    }

    public boolean canAnnotate(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(ARTIFACT, PERMISSION_ANNOTATE, repoPath);
    }

    public boolean canWriteArtifact(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(ARTIFACT, PERMISSION_WRITE, repoPath);
    }

    public boolean canDeleteArtifact(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(ARTIFACT, PERMISSION_DELETE, repoPath);
    }

    public boolean canManageArtifact(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(ARTIFACT, PERMISSION_MANAGE, repoPath);
    }

    public boolean canReadBuild(@NonNull String repoPath) {
        return bridgeToArtifactory.hasPermission(BUILD, PERMISSION_READ, repoPath);
    }

    @NonNull
    private String buildRootPathInRepo(
            @NonNull String repoKey,
            @NonNull String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("Cannot generate path - name is missing");
        }
        return repoKey + "/" + name + "/";
    }
}
