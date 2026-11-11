package org.artifactory.storage.db.itest;

import com.google.common.collect.Multimap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.repo.RepoPath;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.ArtifactoryResourceType;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableRepoAcl;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.UserInfo;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Yevdo Abramov
 * Created on 05/11/2020
 */
public class DummyAuthorizationService implements AuthorizationService {
    @Override
    public List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<RepoAcl> getAllRepoAcls(ArtifactoryPermission permission) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(ArtifactoryPermission permission) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(ArtifactoryPermission permission) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<RepoAcl> getAllRepoAcls() {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(boolean excludeInternal) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(boolean getByLicense) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean canManage(PermissionTarget target) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean canRead(GroupInfo group, RepoPath path) {
        return false;
    }

    @Override
    public boolean canAnnotate(GroupInfo group, RepoPath path) {
        return false;
    }

    @Override
    public boolean canDelete(GroupInfo group, RepoPath path) {
        return false;
    }

    @Override
    public boolean userHasPermissionsOnRepositoryRoot(String repoKey) {
        return false;
    }

    @Override
    public boolean userHasPermissionsOnRepositoryRoot(String repoKey,
            @Nonnull Collection<ArtifactoryPermission> permission, @Nullable Map<String, Boolean> cache) {
        return false;
    }

    @Override
    public PermissionHeuristicScore getStrongestReadPermissionTarget(String repoKey) {
        return null;
    }

    @Override
    public boolean canAccessTrashcan() {
        return false;
    }

    @Override
    public boolean canRestoreFromTrashcan(RepoPath repoPath) {
        return false;
    }

    @Override
    public boolean isGrantedByAccessToken(PermissionScope permissionScope) {
        return false;
    }

    @Override
    public boolean principalCanDo(@NonNull PermissionScope permissionScope) {
        return false;
    }

    @Override
    public Optional<AccessPrincipal> getCurrentAccessPrincipal() {
        return Optional.empty();
    }

    @Override
    public boolean canDeploy(GroupInfo group, RepoPath path) {
        return false;
    }

    @Override
    public boolean canRead(RepoPath path) {
        return false;
    }

    @Override
    public boolean canAnnotate(RepoPath repoPath) {
        return false;
    }

    @Override
    public boolean canDelete(RepoPath path) {
        return false;
    }

    @Override
    public boolean canDeploy(RepoPath path) {
        return false;
    }

    @Override
    public boolean canManage(RepoPath path) {
        return false;
    }

    @Override
    public boolean hasRepoPermission(ArtifactoryPermission permission) {
        return false;
    }

    @Override
    public boolean hasRepoPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific) {
        return false;
    }

    @Override
    public boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission) {
        return false;
    }

    @Override
    public boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific) {
        return false;
    }

    @Override
    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission) {
        return false;
    }

    @Override
    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific) {
        return false;
    }

    @Override
    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific,
            boolean getByLicense) {
        return false;
    }

    @Override
    public boolean hasPermission(ArtifactoryPermission permission) {
        return false;
    }

    @Override
    public boolean hasPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific) {
        return false;
    }

    @Override
    public boolean hasPermission(PermissionScopeResourceType type, RepoPath repoPath,
            ArtifactoryPermission permission) {
        return false;
    }

    @Override
    public boolean hasBuildBasicReadPermission() {
        return false;
    }

    @Override
    public boolean canRead(UserInfo user, RepoPath path) {
        return false;
    }

    @Override
    public boolean canAnnotate(UserInfo user, RepoPath path) {
        return false;
    }

    @Override
    public boolean canDelete(UserInfo user, RepoPath path) {
        return false;
    }

    @Override
    public boolean canDeploy(UserInfo user, RepoPath path) {
        return false;
    }

    @Override
    public boolean canManage(UserInfo user, RepoPath path) {
        return false;
    }

    @Override
    public boolean canManage(GroupInfo group, RepoPath path) {
        return false;
    }

    @Override
    public void createAcl(Acl acl) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void deleteAcl(Acl acl) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void updateAcl(Acl acl) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void updateAcl(Acl acl, boolean effectiveAdmin) {
        throw unexpectedMethodCallException();
    }

    @Override
    public RepoAcl getRepoAcl(String permTargetName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public BuildAcl getBuildAcl(String permTargetName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public ReleaseBundleAcl getReleaseBundleAcl(String permTargetName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public RepoAcl convertNewAclCachedRepoKeysToRemote(MutableRepoAcl acl) {
        throw unexpectedMethodCallException();
    }

    @Override
    public Multimap<PermissionTarget, AceInfo> getUserPermissionAndItsGroups(String username,
            ArtifactoryResourceType type) {
        throw unexpectedMethodCallException();
    }

    @Override
    public Multimap<PermissionTarget, AceInfo> getGroupsPermissions(List<String> groups, ArtifactoryResourceType type) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<RepoAcl> getRepoPathAcls(RepoPath repoPath) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<BuildAcl> getBuildPathAcls(String buildName, String buildNumber, String buildStarted, String repo) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<BuildAcl> getBuildPathAcls(RepoPath repoPath, boolean excludeInternal) {
        throw unexpectedMethodCallException();
    }

    @Override
    public List<ReleaseBundleAcl> getReleaseBundleAcls(RepoPath repoPath) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isInternalAcl(Acl<? extends PermissionTarget> acl) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void removeAllUserAces(String username) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void removeAllGroupAces(String groupName) {
        throw unexpectedMethodCallException();
    }

    private RuntimeException unexpectedMethodCallException() {
        return new UnsupportedOperationException(
                "This is a dummy implementation, none of its methods should be called!");
    }
}
