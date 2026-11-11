/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.artifactory.addon.conan.ConanAddon;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.addon.eula.EulaService;
import org.artifactory.addon.license.AddRemoveLicenseVerificationResult;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.addon.license.LicenseOperationStatus;
import org.artifactory.addon.license.VerificationResult;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.timeout.AqlQueryTimeout;
import org.artifactory.common.addon.Addon;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.ui.rest.service.state.FooterMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.addon.license.VerificationResult.valid;
import static org.artifactory.ui.rest.service.state.FooterMessage.FooterMessageVisibility.admin;
import static org.artifactory.ui.rest.service.state.FooterMessage.createError;

/**
 * @author Yossi Shaul
 */
@Slf4j
@Component
public class OssAddonsManager implements AddonsManager, AddonsWebManager {

    public static final String OSS_LICENSE_KEY_HASH = "Artifactory OSS";
    public static final String FREE_LICENSE_KEY_HASH = "Artifactory Community Edition for C/C++";
    public static final String JCR_LICENSE_KEY_HASH = "JFrog Container Registry";
    protected ArtifactoryContext context;
    private ImmutableMap<String, AddonInfo> addonPathsByName;
    @Autowired
    private ArtifactoryServersCommonService serversService;
    private final Set<AddonType> jcrAddons = ImmutableSet.of(AddonType.DOCKER, AddonType.PROPERTIES,
            AddonType.SMART_REPO, AddonType.S3, AddonType.DISTRIBUTION, AddonType.AQL, AddonType.AOL);
    private final Set<EntitlementType> supportedEntitlements =
            ImmutableSet.of(EntitlementType.OAUTH, EntitlementType.CROWD,
                    EntitlementType.SAML, EntitlementType.LDAP);

    private VersionResultCached versionResultCached = new VersionResultCached();

    @Autowired
    private void setApplicationContext(ApplicationContext context) {
        this.context = (ArtifactoryContext) context;
        addonPathsByName = this.context.getConfigPaths().getInstalledAddonPaths();
    }

    @Override
    public <T extends Addon> T addonByType(Class<T> type) {
        return context.beanForType(type);
    }

    @Override
    public String getProductName() {
        return "Artifactory";
    }

    @Override
    public String getLicenseRequiredMessage(String licensePageUrl) {
        return "Add-ons are currently disabled.";
    }

    @Override
    public void onNoInstalledLicense(boolean userVisitedLicensePage, NoInstalledLicenseAction action) {
        //Not relevant
    }

    @Override
    public boolean isAdminPageAccessible() {
        AuthorizationService authService = context.beanForType(AuthorizationService.class);
        SecurityService securityService = context.beanForType(SecurityService.class);
        return securityService.isAdmin() || authService.hasRepoPermission(ArtifactoryPermission.MANAGE);
    }

    @Override
    public List<AddonInfo> getInstalledAddons(Set<String> excludedAddonKeys) {
        List<AddonInfo> addonInfos = Lists.newArrayList();
        for (AddonType addonType : AddonType.values()) {
            if (AddonType.AOL.equals(addonType)) {
                continue;
            }
            if (isJcrVersion() && isAddonSupportedInJcr(addonType)
                    && addonPathsByName.containsKey(addonType.getAddonName())) {
                updateAddonState(addonInfos, addonType);
                continue;
            }

            if (isConanCEVersion() && AddonType.CONAN.equals(addonType) &&
                    addonPathsByName.containsKey(addonType.getAddonName())) {
                updateAddonState(addonInfos, addonType);
            } else {
                addonInfos.add(new AddonInfo(addonType.getAddonName(), addonType.getAddonDisplayName(), null,
                        AddonState.INACTIVATED, null, addonType.getDisplayOrdinal()));
            }
        }

        Collections.sort(addonInfos);
        return addonInfos;
    }

    private void updateAddonState(List<AddonInfo> addonInfos, AddonType addonType) {
        AddonInfo addonInfo = addonPathsByName.get(addonType.getAddonName());
        if (!addonInfo.getAddonState().equals(AddonState.DISABLED) &&
                !addonInfo.getAddonState().equals(AddonState.ACTIVATED)) {
            addonInfo.setAddonState(AddonState.ACTIVATED);
        }
        addonInfos.add(addonInfo);
    }

    @Override
    public void prepareAddonManager() {
        // Do nothing as this type of AddonsManager does not need any preparation.
    }

    @Override
    public List<String> getEnabledAddonNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean isLicenseInstalled() {
        return false;
    }

    @Override
    public boolean isAddonSupported(AddonType addonType) {
        return addonType == AddonType.CONAN && isConanCEVersion() ||
                isJcrVersion() && isAddonSupportedInJcr(addonType);
    }

    @Override
    public boolean isEntitled(@NonNull EntitlementType type) {
        return supportedEntitlements.contains(type);
    }

    @Override
    public boolean isEntitledForced(@NonNull EntitlementType entitlementType) {
        return isEntitled(entitlementType);
    }

    private boolean isAddonSupportedInJcr(AddonType addonType) {
        return jcrAddons.contains(addonType);
    }

    boolean isConanCEVersion() {
        return !addonByType(ConanAddon.class).isDefault();
    }

    boolean isJcrVersion() {
        return !addonByType(DockerAddon.class).isDefault();
    }

    @Override
    public boolean isTrialLicense() {
        return false;
    }

    @Override
    public LicenseOperationStatus addAndActivateLicenses(Set<String> licenseKeys, boolean notifyListeners,
            boolean skipOnlineValidation) {
        if (isConanCEVersion()) {
            throw new UnsupportedOperationException("Operation is not supported on Artifactory Free");
        }
        if (isJcrVersion()) {
            throw new UnsupportedOperationException("Operation is not supported on Artifactory JCR");
        }
        throw new UnsupportedOperationException("Operation is not supported on Artifactory OSS");
    }

    @Override
    public LicenseOperationStatus addAndActivateLicense(String licenseKey, boolean notifyListeners,
            boolean skipOnlineValidation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLicenseKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLicensePerProduct(String productKey) {
        return null;
    }

    @Override
    public Date getLicenseValidUntil() {
        return null;
    }

    @Override
    public String getLicenseKeyHash(boolean excludeNewLicensesSuffix) {
        if (isConanCEVersion()) {
            return FREE_LICENSE_KEY_HASH;
        } else if (isJcrVersion()) {
            return JCR_LICENSE_KEY_HASH;
        }
        return OSS_LICENSE_KEY_HASH;
    }

    @Override
    public boolean isValidTargetRepositoryForEdgeNode(VersionRestResult versionRestResult) {
        return false;
    }

    @Override
    public boolean isOssLicensed(String licenseKeyHash) {
        return true;
    }

    @Override
    public boolean isTargetArtifactorySupportMultiPushReplication(VersionRestResult versionRestResult) {
        return false;
    }

    @Override
    public boolean lockdown() {
        return false;
    }

    @Override
    public List<ArtifactoryHaLicenseDetails> getClusterLicensesDetails() {
        String type = getLicenceType();
        ArtifactoryHaLicenseDetails haLicenseDetails =
                new ArtifactoryHaLicenseDetails(type, "", "", "", true, "", "", false);
        return Lists.newArrayList(haLicenseDetails);
    }

    @Override
    public ArtifactoryBaseLicenseDetails getProAndAolLicenseDetails() {
        String type = getLicenceType();
        return new ArtifactoryBaseLicenseDetails(type, "", "", false);
    }

    @Override
    public String getPlatformId() {
        return getArtifactoryRunningMode().name();
    }

    @Override
    public String getFooterMessage() {
        return null;
    }

    @Override
    public FooterMessage getLicenseFooterMessage() {
        return null;
    }

    @Override
    public FooterMessage getEULAFooterMessage() {
        if (isJcrAndEulaNotSigned()) {
            return createError("No signed EULA found. To activate, proceed to sign the EULA.", null, admin);
        }
        return null;
    }

    @Override
    public boolean isLicenseExpired(boolean isXray) {
        return false;
    }

    @Override
    public boolean shouldReAcquireLicense() {
        return false;
    }

    @Override
    public AddRemoveLicenseVerificationResult isLicenseKeyValid(String licenseKey, boolean excludeTrial,
            boolean validateOnline) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLicenses(Set<String> licenses, LicenseOperationStatus status, boolean skipOnlineValidation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeLicenses(Set<String> licenses, LicenseOperationStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseLicense() {
        // No license is available on this implementation
    }

    @Override
    public void activateLicense(Set<String> licenseHashesToIgnore, LicenseOperationStatus status,
            boolean notifyListeners,
            boolean initializing) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetLicenseCache() {
        //nop
    }

    @Override
    public boolean reloadLicensesStorageIfNeeded() {
        return false;
    }

    @Override
    public boolean isCloudProviderSupportingRedirectionConfigured() {
        return false;
    }

    @Override
    public boolean isUploadRequestBlocked(ArtifactoryRequest request,
            ArtifactoryResponse response) {
        return false;
    }

    @Override
    public String getLicenseType() {
        return getProAndAolLicenseDetails().getType().toLowerCase();
    }

    @Override
    public Long getAqlQueryTimeoutByLicense() {
        return AqlQueryTimeout.FREE_TIER.getTimeout();
    }

    @Override
    public boolean isReadOnlyLicense() {
        return false;
    }

    @Override
    public VersionResult getVersionResult(VersionInfo versionInfo, List<EntitlementType> entitlements,
            boolean excludeNewLicenses) {
        Integer cacheKey = Objects.hash(versionInfo.hashCode(), excludeNewLicenses);
        VersionResult versionResult = versionResultCached.getVersionResult(cacheKey);
        if (versionResult == null) {
            versionResult = createVersionResult(versionInfo, entitlements, excludeNewLicenses);
            versionResultCached.updateVersionResult(cacheKey, versionResult);
        }
        return versionResult;
    }

    private VersionResult createVersionResult(VersionInfo versionInfo, List<EntitlementType> entitlements,
            boolean excludeNewLicenses) {
        List<String> addonNames = getInstalledAddons(null)
                .stream()
                .filter(addonInfo -> AddonState.ACTIVATED.equals(addonInfo.getAddonState()))
                .map(AddonInfo::getAddonName)
                .toList();
        // Add the license to the JSON result object only for Artifactory Pro

        String licenseKeyHash = getLicenseKeyHash(excludeNewLicenses);
        // By assigning null we prevent the field from appearing in the result (our parser ignores nulls)
        String license = "".equals(licenseKeyHash) ? null : licenseKeyHash;
        Map<String, Boolean> replicationEntitlementsMap = entitlements.stream()
                .collect(Collectors.toMap(Enum::name, this::isEntitled));
        Map<String, String> servicesVersions =
                Map.of("package_handler_version", versionInfo.getPackageHandlerVersion());

        return new VersionResult(versionInfo.getVersion(),
                versionInfo.getRevision(), servicesVersions,
                addonNames,
                license,
                replicationEntitlementsMap);
    }

    @Override
    public VerificationResult verifyAllArtifactoryServers(boolean running) {
        List<ArtifactoryServer> otherMembers = serversService.getOtherActiveMembers();
        String type = getLicenceType();
        if (!otherMembers.isEmpty()) {
            context.setOffline(); //leave it here
            throw new IllegalStateException(
                    "Found active HA servers in DB, " + type + " is not supported in by active HA " +
                            "environment. Shutting down Artifactory.");
        }
        return valid;
    }

    @Override
    public ArtifactoryRunningMode getArtifactoryRunningMode() {
        if (isConanCEVersion()) {
            return ArtifactoryRunningMode.CONAN;
        } else if (isJcrVersion()) {
            return ArtifactoryRunningMode.JCR;
        }
        return ArtifactoryRunningMode.OSS;
    }

    @Override
    public boolean isPartnerLicense() {
        return false;
    }

    @Override
    public void interceptResponse(ArtifactoryResponse response, ArtifactoryRequest request, boolean checkReadOnly)
            throws IOException {
        interceptResponseInternally(response, true);
    }

    @Override
    public void interceptRestResponse(ArtifactoryResponse response, ArtifactoryRequest request,
            String path) throws IOException {
        for (String s : trialExpirationRestCallToBlock) {
            if (path.startsWith(s)) {
                interceptResponseInternally(response, false);
            }
        }
    }

    private void interceptResponseInternally(ArtifactoryResponse response, boolean doWriteResponse) throws IOException {
        if (isJcrAndEulaNotSigned()) {
            interceptResponseInternally(response, doWriteResponse,
                    "In order to use Artifactory you must accept the EULA first");
        }
    }

    private boolean isJcrAndEulaNotSigned() {
        return getArtifactoryRunningMode().isJCR() && context.beanForType(EulaService.class).isRequired();
    }

    private void interceptResponseInternally(ArtifactoryResponse response, boolean writeResponse, String msg)
            throws IOException {
        if (writeResponse) {
            response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE, msg, log);
        } else {
            throw new HttpResponseException(HttpStatus.SC_SERVICE_UNAVAILABLE, msg);
        }
    }

    private String getLicenceType() {
        String type = "OSS";
        if (isConanCEVersion()) {
            type = "Community Edition for C/C++";
        } else if (isJcrVersion()) {
            type = "JCR Edition";
        }
        return type;
    }

    @Override
    public void onBeforeContextCreated() {
        prepareAddonManager();
    }
}