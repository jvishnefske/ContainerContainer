package org.artifactory.engine;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.Repo;
import org.artifactory.util.AolUtils;
import org.jfrog.storage.common.StorageUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static org.artifactory.common.ConstantValues.ignoreListOfReposAllowedSendRedirectUrl;
import static org.artifactory.common.ConstantValues.listOfReposAllowedSendRedirectUrl;
import static org.artifactory.common.ConstantValues.listOfReposForcedCdnRedirectUrl;

/**
 * @author Lior Gur
 */

@Component
@Slf4j
public class DownloadRedirectHelper {

    private final CentralConfigService centralConfig;

    @Autowired
    public DownloadRedirectHelper(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    public boolean isRedirectEnable(Repo responseRepo, RepoDescriptor descriptor) {
        if (log.isDebugEnabled()) {
            log.debug("Repo download settings: repo={}, repoType={}, isDownloadRedirect={}, isCdnRedirect={}, " +
                            "listOfRedirectRepoTypes={}, listOfForcedCdnRepoTypes={}, " + 
                            "ignoreListOfReposAllowedSendRedirectUrl={}",
                    responseRepo.getKey(), descriptor.getType(), descriptor.isDownloadRedirect(),
                    descriptor.isCdnRedirect(), listOfReposAllowedSendRedirectUrl.getString(),
                    listOfReposForcedCdnRedirectUrl.getString(), ignoreListOfReposAllowedSendRedirectUrl.getString());
        }
        if (AolUtils.isAol() && !ignoreListOfReposAllowedSendRedirectUrl.getBoolean()) {
            return isRedirectEnableForThisRepoType(responseRepo) || isCdnForcedForThisRepoType(responseRepo);
        } else {
            return descriptor.isDownloadRedirect();
        }
    }

    private DownloadRedirectConfigDescriptor getDownloadRedirectGlobalConfig() {
        DownloadRedirectConfigDescriptor downloadRedirectConfig =
                centralConfig.getDescriptor().getDownloadRedirectConfig();
        return downloadRedirectConfig != null ? downloadRedirectConfig : new DownloadRedirectConfigDescriptor();
    }

    public boolean isRedirectEnableForThisRepoType(Repo responseRepo) {
        String[] repoTypes = listOfReposAllowedSendRedirectUrl.getString().split(",");
        return Arrays.stream(repoTypes).anyMatch(
                repoType ->
                        (repoType.equalsIgnoreCase(responseRepo.getDescriptor().getType().getDisplayName()) ||
                                repoType.equalsIgnoreCase(responseRepo.getDescriptor().getType().getType())));
    }

    public boolean isRedirectEnableForThisRepoType(String type) {
        return Arrays.stream(listOfReposAllowedSendRedirectUrl.getString().split(",")).anyMatch(
                repoType -> repoType.equalsIgnoreCase(type));
    }

    public boolean isCdnForcedForThisRepoType(Repo responseRepo) {
        String[] repoTypes = listOfReposForcedCdnRedirectUrl.getString().split(",");
        return Arrays.stream(repoTypes).anyMatch(
                repoType ->
                        (repoType.equalsIgnoreCase(responseRepo.getDescriptor().getType().getDisplayName()) ||
                                repoType.equalsIgnoreCase(responseRepo.getDescriptor().getType().getType())));
    }

    public boolean isArtifactGreaterEqualsRedirectThreshold(RepoResource resource) {
        long fileSizeInBytes = resource.getSize();
        long thresholdInBytes;
        if (AolUtils.isAol()) {
            thresholdInBytes = ConstantValues.cloudBinaryProviderRedirectThresholdInBytes.getLong();
        } else {
            thresholdInBytes = (long) StorageUnit.MB.toBytes(getDownloadRedirectGlobalConfig().getFileMinimumSize());
        }
        if (fileSizeInBytes >= thresholdInBytes) {
            log.debug("File '{}' is of size {}, greater or equals to redirect thresholdInBytes {}. Sending redirect",
                    resource.getRepoPath().getId(), fileSizeInBytes, thresholdInBytes);
            return true;
        }
        log.debug("File '{}' is of size {}, less than redirect thresholdInBytes {}. Serving stream instead of redirect",
                resource.getRepoPath().getId(), fileSizeInBytes, thresholdInBytes);
        return false;
    }
}
