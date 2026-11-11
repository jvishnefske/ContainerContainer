package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.ChecksumPolicyType;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Data
@NoArgsConstructor
@GenerateDiffFunction
public class RemoteRepositoryRepoTypeConfig implements RealRepoConfigType {
    private boolean archiveBrowsingEnabled;
    private boolean blackedOut;
    private CdnRedirectRepoConfig cdnRedirectRepoConfig;
    private DownloadRedirectRepoConfig downloadRedirectConfig;
    private List<@NotUnicode String> propertySetRefs = new ArrayList<>();
    private boolean allowAnyHostAuth;
    private boolean blockMismatchingMimeTypes = true;
    @NotUnicode
    private String mismatchingMimeTypesOverrideList;
    private boolean bypassHeadRequests = false;
    private boolean disableUrlNormalization;
    private boolean enableCookieManagement;
    private boolean enableTokenAuthentication;
    private boolean propagateQueryParams;
    private boolean shareConfiguration = false;
    private boolean listRemoteFolderItems = false;
    private boolean synchronizeProperties = false;
    private ContentSynchronisation contentSynchronisation;
    @NotUnicode
    private String clientTlsCertificate;
    private String localAddress;
    @NotUnicode
    private String proxyRef;
    private boolean disableProxy;
    @NotUnicode
    private String remoteRepoLayoutRef;
    private boolean storeArtifactsLocally = true;
    @NotUnicode
    private String url;
    private boolean hardFail;
    private boolean offline;
    private long retrievalCachePeriodSecs = 7200;//2 hr.
    private long metadataRetrievalTimeoutSecs = 60;//1 min.
    private long assumedOfflinePeriodSecs = 300;//5 minutes
    private long missedRetrievalCachePeriodSecs = 1800;//30 min.
    private ChecksumPolicyType checksumPolicyType = ChecksumPolicyType.GEN_IF_ABSENT;
    private int unusedArtifactsCleanupPeriodHours = 0;
    private int socketTimeoutMillis = 15000;
    @NotUnicode
    @Sensitive
    private String password;
    @NotUnicode
    private String username;
    @NotUnicode
    private String queryParams;
    private boolean priorityResolution;
    private boolean handleReleases = true;
    private boolean handleSnapshots = true;
    private boolean sendContext = false;
    private boolean passThrough = false;
    private boolean curated = false;
    private int maxUniqueSnapshots;
    private boolean retrieveSha256FromServer;
    @DynamicSensitive(handler = CustomHttpHeadersSensitiveHandler.class)
    private List<CustomHttpHeader> customHttpHeaders = new ArrayList<>();

    public RemoteRepositoryRepoTypeConfig(RemoteRepositoryRepoTypeConfig remoteRepositoryRepoTypeConfig) {
        this.archiveBrowsingEnabled = remoteRepositoryRepoTypeConfig.isArchiveBrowsingEnabled();
        this.blackedOut = remoteRepositoryRepoTypeConfig.isBlackedOut();
        if (remoteRepositoryRepoTypeConfig.getCdnRedirectRepoConfig() != null) {
            this.cdnRedirectRepoConfig =
                    new CdnRedirectRepoConfig(remoteRepositoryRepoTypeConfig.getCdnRedirectRepoConfig());
        }
        if (remoteRepositoryRepoTypeConfig.getDownloadRedirectConfig() != null) {
            this.downloadRedirectConfig =
                    new DownloadRedirectRepoConfig(remoteRepositoryRepoTypeConfig.getDownloadRedirectConfig());
        }
        this.propertySetRefs = new ArrayList<>(remoteRepositoryRepoTypeConfig.getPropertySetRefs());
        this.allowAnyHostAuth = remoteRepositoryRepoTypeConfig.isAllowAnyHostAuth();
        this.blockMismatchingMimeTypes = remoteRepositoryRepoTypeConfig.isBlockMismatchingMimeTypes();
        this.mismatchingMimeTypesOverrideList = remoteRepositoryRepoTypeConfig.getMismatchingMimeTypesOverrideList();
        this.bypassHeadRequests = remoteRepositoryRepoTypeConfig.isBypassHeadRequests();
        this.disableUrlNormalization = remoteRepositoryRepoTypeConfig.isDisableUrlNormalization();
        this.enableCookieManagement = remoteRepositoryRepoTypeConfig.isEnableCookieManagement();
        this.enableTokenAuthentication = remoteRepositoryRepoTypeConfig.isEnableTokenAuthentication();
        this.propagateQueryParams = remoteRepositoryRepoTypeConfig.isPropagateQueryParams();
        this.shareConfiguration = remoteRepositoryRepoTypeConfig.isShareConfiguration();
        this.listRemoteFolderItems = remoteRepositoryRepoTypeConfig.isListRemoteFolderItems();
        this.synchronizeProperties = remoteRepositoryRepoTypeConfig.isSynchronizeProperties();
        if (remoteRepositoryRepoTypeConfig.getContentSynchronisation() != null) {
            this.contentSynchronisation =
                    new ContentSynchronisation(remoteRepositoryRepoTypeConfig.getContentSynchronisation());
        }
        this.clientTlsCertificate = remoteRepositoryRepoTypeConfig.getClientTlsCertificate();
        this.localAddress = remoteRepositoryRepoTypeConfig.getLocalAddress();
        this.proxyRef = remoteRepositoryRepoTypeConfig.getProxyRef();
        this.disableProxy = remoteRepositoryRepoTypeConfig.isDisableProxy();
        this.remoteRepoLayoutRef = remoteRepositoryRepoTypeConfig.getRemoteRepoLayoutRef();
        this.storeArtifactsLocally = remoteRepositoryRepoTypeConfig.isStoreArtifactsLocally();
        this.url = remoteRepositoryRepoTypeConfig.getUrl();
        this.hardFail = remoteRepositoryRepoTypeConfig.isHardFail();
        this.offline = remoteRepositoryRepoTypeConfig.isOffline();
        this.retrievalCachePeriodSecs = remoteRepositoryRepoTypeConfig.getRetrievalCachePeriodSecs();
        this.metadataRetrievalTimeoutSecs = remoteRepositoryRepoTypeConfig.getMetadataRetrievalTimeoutSecs();
        this.assumedOfflinePeriodSecs = remoteRepositoryRepoTypeConfig.getAssumedOfflinePeriodSecs();
        this.missedRetrievalCachePeriodSecs = remoteRepositoryRepoTypeConfig.getMissedRetrievalCachePeriodSecs();
        this.checksumPolicyType = remoteRepositoryRepoTypeConfig.getChecksumPolicyType();
        this.unusedArtifactsCleanupPeriodHours = remoteRepositoryRepoTypeConfig.getUnusedArtifactsCleanupPeriodHours();
        this.socketTimeoutMillis = remoteRepositoryRepoTypeConfig.getSocketTimeoutMillis();
        this.password = remoteRepositoryRepoTypeConfig.getPassword();
        this.username = remoteRepositoryRepoTypeConfig.getUsername();
        this.queryParams = remoteRepositoryRepoTypeConfig.getQueryParams();
        this.priorityResolution = remoteRepositoryRepoTypeConfig.isPriorityResolution();
        this.handleReleases = remoteRepositoryRepoTypeConfig.isHandleReleases();
        this.handleSnapshots = remoteRepositoryRepoTypeConfig.isHandleSnapshots();
        this.sendContext = remoteRepositoryRepoTypeConfig.isSendContext();
        this.passThrough = remoteRepositoryRepoTypeConfig.isPassThrough();
        this.curated = remoteRepositoryRepoTypeConfig.isCurated();
        this.maxUniqueSnapshots = remoteRepositoryRepoTypeConfig.getMaxUniqueSnapshots();
        this.retrieveSha256FromServer = remoteRepositoryRepoTypeConfig.isRetrieveSha256FromServer();
        if (remoteRepositoryRepoTypeConfig.getCustomHttpHeaders() != null) {
            setCustomHttpHeaders(remoteRepositoryRepoTypeConfig.getCustomHttpHeaders().stream()
                    .map(CustomHttpHeader::new)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    public String getQueryParams() {
        return StringUtils.removeStart(queryParams, "?");
    }

    public ContentSynchronisation getContentSynchronisation() {
        if (contentSynchronisation == null) {
            contentSynchronisation = new ContentSynchronisation();
        }
        return contentSynchronisation;
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

    public void setCustomHttpHeaders(List<CustomHttpHeader> customHttpHeaders) {
        this.customHttpHeaders = customHttpHeaders == null ? new ArrayList<>() : customHttpHeaders;
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
