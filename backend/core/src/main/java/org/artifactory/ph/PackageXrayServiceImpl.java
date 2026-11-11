package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageXrayGateStatus;
import com.jfrog.ph.acl.service.PackageXrayService;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.springframework.stereotype.Service;
import org.artifactory.addon.xray.ArtifactXrayInfo;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import java.util.Map;

/**
 * @author Shahaf Golan
 */
@Slf4j
@Service
public class PackageXrayServiceImpl implements PackageXrayService {

    /**
     * Must stay aligned with {@code XrayClientConstants.ARTIFACT_BLOCKED_UNSCANNED}.
     * This module cannot depend on the Xray Java client.
     */
    private static final String ARTIFACT_BLOCKED_UNSCANNED = "Artifact not yet scanned";

    private static final Map<String, EntitlementType> PACKAGE_TYPE_TO_ENTITLEMENT =
            Map.of(PackageType.SKILLS.name(), EntitlementType.AI_CATALOG,
                    PackageType.AGENTPACKAGES.name(), EntitlementType.AI_CATALOG);

    private final AddonsManager addonsManager;
    private final RepositoryService repositoryService;

    public PackageXrayServiceImpl(AddonsManager addonsManager,
            RepositoryService repositoryService) {
        this.addonsManager = addonsManager;
        this.repositoryService = repositoryService;
    }

    @Override
    public boolean isXrayEnabledForRepo(String repoKey, boolean forceCheckEntitlement) {
        return isEntitlementAligned(repoKey, forceCheckEntitlement) &&
                addonsManager.addonByType(XrayAddon.class).isXrayConfPerRepoAndIntegrationEnabled(repoKey);
    }

    @Override
    public PackageXrayGateStatus getArtifactXrayGateStatus(String repoKey, String path, boolean forceCheckEntitlement) {
        if (!isEntitlementAligned(repoKey, forceCheckEntitlement)) {
            log.warn("Repo {} is considered as a repo without entitlement or not aligned entitlement for the feature",
                    repoKey);
            return PackageXrayGateStatus.NOT_IN_ENTITLEMENT;
        }
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        if (!xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoKey)) {
            log.debug("Xray is disabled for repo {}", repoKey);
            return PackageXrayGateStatus.XRAY_DISABLED_FOR_REPO;
        }
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        ArtifactXrayInfo info = xrayAddon.getArtifactXrayInfo(repoPath);
        if (!info.isArtifactProcessed()) {
            log.debug("The artifact {} in repo {} has not scanned yet", path, repoKey);
            return PackageXrayGateStatus.SCAN_IN_PROGRESS;
        }
        if (info.isBlocked()) {
            if (ARTIFACT_BLOCKED_UNSCANNED.equals(info.getBlockReason())) {
                log.debug("The artifact {} in repo {} is blocked because it is unscanned", path, repoKey);
                return PackageXrayGateStatus.SCAN_IN_PROGRESS;
            }
            log.debug("The artifact {} in repo {} is blocked by xray", path, repoKey);
            return PackageXrayGateStatus.BLOCKED;
        }
        log.debug("The artifact {} in repo {} is approved by xray", path, repoKey);
        return PackageXrayGateStatus.APPROVED;
    }

    private boolean isEntitlementAligned(String repoKey, boolean forceCheckEntitlement) {
        if (!forceCheckEntitlement) {
            log.debug("Disabled the entitlement check for repo: {}", repoKey);
            return true;
        }
        RepoDescriptor descriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (descriptor != null && descriptor.getType() != null) {
            String typeValue = descriptor.getType().getType();
            if (typeValue != null) {
                EntitlementType entitlementType = PACKAGE_TYPE_TO_ENTITLEMENT
                        .get(typeValue.toUpperCase());
                if (entitlementType != null) {
                    return addonsManager.isEntitled(entitlementType);
                }
                log.warn("Could not get entitlement for entitlement list for repoKey: {} repoType:{}", repoKey,
                        typeValue.toUpperCase());
            }
        }
        log.warn("Could not get repo descriptor or repo type for {}", repoKey);
        return false;
    }
}
