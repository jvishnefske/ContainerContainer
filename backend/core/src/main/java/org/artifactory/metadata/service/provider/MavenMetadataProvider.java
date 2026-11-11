package org.artifactory.metadata.service.provider;

import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.RepoLayoutUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.addon.xray.XrayAddon.XRAY_MIN_VER_WITH_UNIQUE_SNAPSHOT_SUPPORT;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getModuleInfo;
import static org.artifactory.metadata.service.provider.MavenGroupUtils.getPomVersionFileQuery;

/**
 * @author Uriah Levy
 */
@Component
public class MavenMetadataProvider extends GAVCMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(MavenMetadataProvider.class);
    private static final RepoType type = RepoType.Maven;
    private static final String LEAD_FILE_EXTENSION = "pom";
    private final InternalRepositoryService repositoryService;
    private AddonsManager addonsManager;

    @Autowired
    MavenMetadataProvider(InternalRepositoryService repositoryService, AddonsManager addonsManager) {
        super(type);
        this.repositoryService = repositoryService;
        this.addonsManager = addonsManager;
        setTypeSpecificMetadataTemplate(new MavenTypeSpecificMetadataTemplate());
    }

    /**
     * Getting all children of the same parent, according to layout.
     */
    @Override
    public Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        if (!isLeadArtifactMatchRepoLayout(repositoryService,
                leadArtifactRepoPath, RepoLayoutUtils.MAVEN_2_DEFAULT_NAME)) {
            log.debug("Skipping version files collection for Maven package {} - Repository Layout is not {}",
                    leadArtifactRepoPath, RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
            return Optional.empty();
        }

        return getPomVersionFileQuery(leadArtifactRepoPath, repositoryService);
    }

    @Data
    class MavenTypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {
        @Override
        public Set<String> getExcludedUserProperties() {
            return new HashSet<>();
        }

        @Override
        public Set<String> getLeadFileExtensions() {
            return ImmutableSet.of(LEAD_FILE_EXTENSION);
        }

        //The Name and the PkgidSuffix are the same
        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getPkgidSuffix(repoPath, properties);
        }

        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(moduleInfo -> {
                log.debug("Generating Maven version for path: {} with properties: {}", repoPath, properties);
                String baseRevision = moduleInfo.getBaseRevision();
                String folderIntegrationRevision = moduleInfo.getFolderIntegrationRevision();
                String versionResult;
                if (StringUtils.isBlank(folderIntegrationRevision)) {
                    versionResult =  baseRevision;
                }
                else if (isUniqueSnapshotHandlingSupported()) {
                    versionResult = baseRevision + "-" + moduleInfo.getFileIntegrationRevision();
                }
                else {
                    versionResult = baseRevision + "-" + folderIntegrationRevision;
                }
                log.debug("Providing Maven version: {}", versionResult);
                return versionResult;
            }).orElse(null);
        }

        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getModuleInfo(repoPath, repositoryService).map(info -> getPkgIdSuffixFromLayout(info, repoPath))
                    .orElse(null);
        }

        /**
         * Unique snapshot handling is supported in case:
         * 1. No Xray addon found (XrayAddon has default implementation)
         * 2. Xray config is missing
         * 3. Xray is disabled
         * 4. Xray enabled and is of at least {@link XrayAddon#XRAY_MIN_VER_WITH_UNIQUE_SNAPSHOT_SUPPORT} version
         * Otherwise, fall back to non-unique snapshots.
         * @return <code>true</code> if unique snapshot is supported, <code>false</code> otherwise.
         */
        private boolean isUniqueSnapshotHandlingSupported() {
            XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
            return xrayAddon.isDefault() || xrayAddon.isXrayConfigMissing() || !xrayAddon.isXrayEnabled() ||
                    xrayAddon.isXrayOfMinimalRequiredVersion(XRAY_MIN_VER_WITH_UNIQUE_SNAPSHOT_SUPPORT);
        }
    }
}
