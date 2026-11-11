package org.artifactory.application.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * This class should be used to get "commit context" properties, that will be added to a "lead file"
 * in the scope of AppTrust integration.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
public class CommitContextPropertiesProvider {

    private static final String REVISION = "revision";
    private static final String REVISION_PROP_KEY = "jf.revision";
    private static final String VCS_URL = "vcsUrl";
    private static final String VCS_URL_PROP_KEY = "jf.vcsUrl";
    private static final String BRANCH = "branch";
    private static final String BRANCH_PROP_KEY = "jf.branch";

    @Nonnull
    public Map<String, String> retrieveCommitContextProperties(@Nonnull JwtAccessToken jwtAccessToken) {
        try {
            if (!ConstantValues.appTrustCommitContextDataEnabled.getBoolean()) {
                log.debug("AppTrust commit context feature is disabled. Returning empty map");
                return Map.of();
            }
            String extensionJson = jwtAccessToken.getExtension();
            if (StringUtils.isBlank(extensionJson)) {
                log.debug("The authentication token's 'ext' is missing. Returning empty map");
                return Map.of();
            }
            var extensions = JsonUtils.getInstance().readValue(extensionJson, Map.class);
            var vcsCommitInfo = (Map<String, String>) extensions.get("vcsCommit");
            if (vcsCommitInfo == null || vcsCommitInfo.isEmpty()) {
                log.debug("The 'vcsCommit' is empty or missing. Returning empty map");
                return Map.of();
            }
            checkVcsCommitInfoData(vcsCommitInfo);
            return mapToArtifactoryProperties(vcsCommitInfo);
        } catch (Exception e) {
            log.warn("Failed to retrieve commit info for AppTrust integration", e);
            return Map.of();
        }
    }

    private void checkVcsCommitInfoData(Map<String, String> vcsCommitInfo) {
        checkFieldsExist(vcsCommitInfo, REVISION, VCS_URL, BRANCH);
    }

    private void checkFieldsExist(Map<String, String> vcsCommitInfo, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (!vcsCommitInfo.containsKey(fieldName)) {
                logMissingFieldWarning(fieldName);
            }
        }
    }

    private void logMissingFieldWarning(String fieldName) {
        log.debug("'vcsCommit' is missing '{}' field", fieldName);
    }

    private Map<String, String> mapToArtifactoryProperties(Map<String, String> vcsCommitInfo) {
        Map<String, String> properties = new HashMap<>();
        if (vcsCommitInfo.containsKey(REVISION)) {
            properties.put(REVISION_PROP_KEY, vcsCommitInfo.get(REVISION));
        }
        if (vcsCommitInfo.containsKey(VCS_URL)) {
            properties.put(VCS_URL_PROP_KEY, vcsCommitInfo.get(VCS_URL));
        }
        if (vcsCommitInfo.containsKey(BRANCH)) {
            properties.put(BRANCH_PROP_KEY, vcsCommitInfo.get(BRANCH));
        }
        return properties;
    }
}