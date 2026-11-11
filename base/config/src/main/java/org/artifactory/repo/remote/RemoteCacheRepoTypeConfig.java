package org.artifactory.repo.remote;

import lombok.EqualsAndHashCode;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;
import org.jfrog.common.config.diff.DiffIgnore;

import java.util.List;

/**
 * Class represent remote cache configuration
 *
 * @author shayb
 */
@EqualsAndHashCode(callSuper = true)
public class RemoteCacheRepoTypeConfig extends LocalRepositoryRepoTypeConfig {

    // @DiffIgnore is required: @GenerateDiffFunction is @Inherited, so the AP processes this class
    // because its parent LocalRepositoryRepoTypeConfig is annotated. Without @DiffIgnore the AP would
    // attempt to diff remoteRepositoryRepoTypeConfig as a nested object, producing a double-diff
    // alongside the delegated LocalRepositoryRepoTypeConfig fields.
    @DiffIgnore
    private final RemoteRepositoryRepoTypeConfig remoteRepositoryRepoTypeConfig;

    public RemoteCacheRepoTypeConfig(RemoteRepositoryRepoTypeConfig remoteRepositoryRepoTypeConfig) {
        this.remoteRepositoryRepoTypeConfig = remoteRepositoryRepoTypeConfig;
    }

    @Override
    public boolean isArchiveBrowsingEnabled() {
        return remoteRepositoryRepoTypeConfig.isArchiveBrowsingEnabled();
    }

    @Override
    public boolean isBlackedOut() {
        return remoteRepositoryRepoTypeConfig.isBlackedOut();
    }

    @Override
    public CdnRedirectRepoConfig getCdnRedirectRepoConfig() {
        return remoteRepositoryRepoTypeConfig.getCdnRedirectRepoConfig();
    }

    @Override
    public DownloadRedirectRepoConfig getDownloadRedirectConfig() {
        return remoteRepositoryRepoTypeConfig.getDownloadRedirectConfig();
    }

    @Override
    public List<String> getPropertySetRefs() {
        return remoteRepositoryRepoTypeConfig.getPropertySetRefs();
    }

    @Override
    public LocalRepoChecksumPolicyType getChecksumPolicyType() {
        // TODO: 28/02/2022 probably no real meaning in remote repo
        return LocalRepoChecksumPolicyType.CLIENT;
    }

    @Override
    public boolean isPriorityResolution() {
        return remoteRepositoryRepoTypeConfig.isPriorityResolution();
    }

    @Override
    public int getMaxUniqueSnapshots() {
        return remoteRepositoryRepoTypeConfig.getMaxUniqueSnapshots();
    }

    @Override
    public boolean isHandleReleases() {
        return remoteRepositoryRepoTypeConfig.isHandleReleases();
    }

    @Override
    public boolean isHandleSnapshots() {
        return remoteRepositoryRepoTypeConfig.isHandleSnapshots();
    }

    @Override
    public SnapshotVersionBehavior getSnapshotVersionBehavior() {
        // TODO: 28/02/2022 probably no meaning in remote repo, if correct, should be remove in the future
        return SnapshotVersionBehavior.UNIQUE;
    }

    @Override
    public boolean isDownloadRedirect() {
        return remoteRepositoryRepoTypeConfig.isDownloadRedirect();
    }

    @Override
    public boolean isCdnRedirect() {
        return remoteRepositoryRepoTypeConfig.isCdnRedirect();
    }

}
