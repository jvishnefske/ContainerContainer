package org.artifactory.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;
import org.slf4j.Logger;

/**
 * @author Uriah Levy
 */
@Data
@GenerateDiffFunction
public class LocalRepositoryRepoTypeConfig implements RealRepoConfigType {
    @MetadataInfo(skip = true)
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LocalRepositoryRepoTypeConfig.class);
    @MetadataInfo(defaultValue = "false")
    private boolean archiveBrowsingEnabled;
    @MetadataInfo(defaultValue = "false")
    @Getter
    private boolean blackedOut;
    private CdnRedirectRepoConfig cdnRedirectRepoConfig;
    private DownloadRedirectRepoConfig downloadRedirectConfig;
    @MetadataInfo(skip = true)
    private List<@NotUnicode String> propertySetRefs = new ArrayList<>(); // ex-xml-idref
    @MetadataInfo(defaultValue = "client-checksums")
    private LocalRepoChecksumPolicyType checksumPolicyType = LocalRepoChecksumPolicyType.CLIENT;
    @MetadataInfo(defaultValue = "false")
    private boolean priorityResolution;
    @MetadataInfo(defaultValue = "0")
    private int maxUniqueSnapshots;
    @MetadataInfo(defaultValue = "true")
    private boolean handleReleases = true;
    @MetadataInfo(defaultValue = "true")
    private boolean handleSnapshots = true;
    @MetadataInfo(defaultValue = "Unique")
    private SnapshotVersionBehavior snapshotVersionBehavior = SnapshotVersionBehavior.UNIQUE;

    public LocalRepositoryRepoTypeConfig() {
    }

    public LocalRepositoryRepoTypeConfig(LocalRepositoryRepoTypeConfig localRepositoryRepoTypeConfig) {
        this.archiveBrowsingEnabled = localRepositoryRepoTypeConfig.archiveBrowsingEnabled;
        this.blackedOut = localRepositoryRepoTypeConfig.blackedOut;
        if (localRepositoryRepoTypeConfig.getCdnRedirectRepoConfig() != null) {
            this.cdnRedirectRepoConfig =
                    new CdnRedirectRepoConfig(localRepositoryRepoTypeConfig.getCdnRedirectRepoConfig());
        }
        if (localRepositoryRepoTypeConfig.getDownloadRedirectConfig() != null) {
            this.downloadRedirectConfig =
                    new DownloadRedirectRepoConfig(localRepositoryRepoTypeConfig.getDownloadRedirectConfig());
        }
        this.propertySetRefs = new ArrayList<>(localRepositoryRepoTypeConfig.getPropertySetRefs());
        this.checksumPolicyType = localRepositoryRepoTypeConfig.getChecksumPolicyType();
        this.priorityResolution = localRepositoryRepoTypeConfig.isPriorityResolution();
        this.maxUniqueSnapshots = localRepositoryRepoTypeConfig.getMaxUniqueSnapshots();
        this.handleReleases = localRepositoryRepoTypeConfig.isHandleReleases();
        this.handleSnapshots = localRepositoryRepoTypeConfig.isHandleSnapshots();
        this.snapshotVersionBehavior = localRepositoryRepoTypeConfig.getSnapshotVersionBehavior();
    }

    public boolean isDownloadRedirect() {
        return downloadRedirectConfig != null && downloadRedirectConfig.isEnabled();
    }

    @JsonIgnore
    public void setDownloadRedirect(boolean enableRedirect) {
        if (downloadRedirectConfig == null) {
            downloadRedirectConfig = new DownloadRedirectRepoConfig();
        }
        downloadRedirectConfig.setEnabled(enableRedirect);
    }

    public void setBlackedOut(boolean blackedOut) {
        log.debug("Setting blacked out to: '{}' for local repo config: '{}'", blackedOut, this);
        if (this.blackedOut != blackedOut) {
            log.debug("Setting blacked out to: '{}' for local repo config: '{}' with stacktrace", blackedOut, this,
                    new Throwable());
        }
        this.blackedOut = blackedOut;
    }

    public boolean isCdnRedirect() {
        return cdnRedirectRepoConfig != null && cdnRedirectRepoConfig.isEnabled();
    }

    @JsonIgnore
    public void setCdnRedirect(boolean enableCdnRedirect) {
        if (cdnRedirectRepoConfig == null) {
            cdnRedirectRepoConfig = new CdnRedirectRepoConfig();
        }
        cdnRedirectRepoConfig.setEnabled(enableCdnRedirect);
    }
}
