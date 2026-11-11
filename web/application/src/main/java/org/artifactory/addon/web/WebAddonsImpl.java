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

package org.artifactory.addon.web;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.security.UserInfo;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.lang.String.format;
import static org.artifactory.util.HttpUtils.UNIFIED_JFROG_PLATFORM_UI;

/**
 * Default implementation of the addons interface. Represents a normal execution of artifactory.
 * <p/>
 * <strong>NOTE!</strong> Do not create anonymous or non-static inner classes in addon
 *
 * @author freds
 * @author Yossi Shaul
 */
@Component
public final class WebAddonsImpl implements CoreAddons {
    private static final Logger log = LoggerFactory.getLogger(WebAddonsImpl.class);

    @Autowired
    private CentralConfigService centralConfigService;

    @Override
    public String getListBrowsingVersion() {
        VersionInfo versionInfo = centralConfigService.getProductVersionInfo();
        return format("Artifactory/%s", versionInfo.getVersion());
    }

    @Override
    public String getArtifactoryServerName() {
        return centralConfigService.getDescriptor().getServerName();
    }

    /**
     * Get the Artifactory URL for mail operations. The URL in this implementations is taken from the Mail server
     * configs adding the '/webapp/' suffix.
     *
     * @return The Artifactory URL from the mail server configurations
     */
    @Override
    public String getMailConfigArtifactoryUrl() {
        // mail configurations is always the best match
        CentralConfigDescriptor centralConfigDescriptor = centralConfigService.getDescriptor();
        MailServerDescriptor mailServer = centralConfigDescriptor.getMailServer();
        if (mailServer != null && StringUtils.isNotBlank(mailServer.getArtifactoryUrl())) {
            return PathUtils.trimTrailingSlashes(mailServer.getArtifactoryUrl()) + UNIFIED_JFROG_PLATFORM_UI;
        }
        // request is the second best match
        String urlFromRequest = HttpUtils.getArtifactoryUrlFromRequestIfPresent();
        if (StringUtils.isNotBlank(urlFromRequest)) {
            return PathUtils.trimTrailingSlashes(urlFromRequest) + UNIFIED_JFROG_PLATFORM_UI;
        }
        // baseUrl is the last option
        String baseUrl = centralConfigDescriptor.getUrlBase();
        if (StringUtils.isNotBlank(baseUrl)) {
            return PathUtils.trimTrailingSlashes(baseUrl) + UNIFIED_JFROG_PLATFORM_UI;
        }
        return "";
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    @Nonnull
    public List<String> getPlatformAdminUsersEmails() {
        List<UserInfo> allUsers = ContextHelper.get().beanForType(UserGroupService.class).getAllUsers(true);
        List<String> adminEmails = Lists.newArrayList();
        for (UserInfo user : allUsers) {
            if (user.isEffectiveAdmin()) {
                if (StringUtils.isNotBlank(user.getEmail())) {
                    adminEmails.add(user.getEmail());
                } else {
                    log.debug("The user: '{}' has no email address.", user.getUsername());
                }
            }
        }
        return adminEmails;
    }

    @Override
    public boolean validateTargetHasDifferentLicense(String targetLicenseHash, String artifactoryId) {
        AddonsManager addonsManager = getAddonsManager();
        if (addonsManager.isEntitlementEnforced()) {
            log.debug("Entitlements are enforced, skiping license validation");
            return true;
        }
        if (Strings.isNullOrEmpty(targetLicenseHash)) {
            log.debug("LicenseHash is empty, validation isn't possible");
        } else {
            if (!addonsManager.getLicenseKeyHash(false).equals(targetLicenseHash)) {
                return true;
            } else {
                if (!Strings.isNullOrEmpty(artifactoryId)) {
                    return HttpUtils.getHostId().equals(artifactoryId);
                } else {
                    log.debug("LicenseHash is equal to currently used license, but artifactoryId is empty, " +
                            "validation of destination and source artifactories being same instance isn't possible");
                }
            }
        }
        return false;
    }

    @Override
    public void validateTargetHasDifferentLicenseKeyHash(String targetLicenseHash, List<String> addons) {
        AddonsManager addonsManager = getAddonsManager();
        // Skip Trial license
        if (isTrial(addonsManager)) {
            log.debug("Source has trial license, skipping target instance license validation.");
            return;
        }

        if (StringUtils.isNotBlank(targetLicenseHash) && targetLicenseHash.equals("Artifactory OSS")) {
            throw new IllegalArgumentException(
                    "Replication to remote open-source Artifactory instance is not supported.");
        }

        if (StringUtils.isNotBlank(targetLicenseHash) && targetLicenseHash.equals("JFrog Container Registry")) {
            throw new IllegalArgumentException(
                    "Replication to remote JFrog Container Registry instance is not supported.");
        }

        if (StringUtils.isNotBlank(targetLicenseHash) &&
                targetLicenseHash.equalsIgnoreCase("Artifactory Community Edition for C/C++")) {
            throw new IllegalArgumentException(
                    "Replication to remote Artifactory community edition instance is not supported.");
        }

        if (StringUtils.isBlank(targetLicenseHash)) {
            if (addons == null || !addons.contains(AddonType.REPLICATION.getAddonName())) {
                throw new IllegalArgumentException(
                        "Replication between an open-source Artifactory instance is not supported.");
            }

            throw new IllegalArgumentException(
                    "Could not retrieve license key from remote target, user must have deploy permissions.");
        }
        if (addonsManager.getLicenseKeyHash(false).equals(targetLicenseHash)) {
            throw new IllegalArgumentException("Replication between same-license servers is not supported.");
        }
    }

    @Override
    public void validateMultiPushReplicationSupportedForTargetLicense(VersionRestResult versionRestResult,
            boolean isMultiPushConfigure, String targetUrl) {
        AddonsManager addonsManager = getAddonsManager();
        if (isMultiPushConfigure && !addonsManager.isTargetArtifactorySupportMultiPushReplication(versionRestResult)) {
            log.info("Multi Push Replication is not supported for target :{}", targetUrl);
            throw new IllegalArgumentException(
                    "Multi Push Replication is supported for targets with an enterprise license only");
        }
    }

    @Override
    public String getBuildNum() {
        VersionInfo versionInfo = centralConfigService.getProductVersionInfo();
        return format("%s rev %s", versionInfo.getVersion(), versionInfo.getRevision());
    }

    private boolean isTrial(AddonsManager addonsManager) {
        return addonsManager.isLicenseInstalled() &&
                "Trial".equalsIgnoreCase(addonsManager.getProAndAolLicenseDetails().getType());
    }

    private AddonsManager getAddonsManager() {
        return ContextHelper.get().beanForType(AddonsManager.class);
    }
}
