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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.conan.ConanAddon;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.provider.DockerMetadataProvider;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.service.PropertiesService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.mime.DockerNaming.MANIFEST_FILENAME;
import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;

/**
 * @author Yinon Avraham
 */
@Slf4j
public class NodeRepoTypeHelper {
    private static final String APPLICATION_VND_OCI_IMAGE_MANIFEST_V_1_JSON =
            "application/vnd.oci.image.manifest.v1+json";
    private static final String APPLICATION_VND_OCI_IMAGE_INDEX_V_1_JSON = "application/vnd.oci.image.index.v1+json";
    private static final String APPLICATION_VND_CNCF_HELM_CONFIG_V_1_JSON = "application/vnd.cncf.helm.config.v1+json";
    private static final String OCI_ARTIFACT_TYPE = "oci.artifact.type";
    private final AddonsManager addonsManager;
    private final RepositoryService repoService;
    private final PropertiesService propertiesService;
    private final PackageSpecification packageSpecification;

    public NodeRepoTypeHelper(AddonsManager addonsManager, RepositoryService repoService,
            PropertiesService propertiesService, PackageSpecification packageSpecification) {
        this.addonsManager = addonsManager;
        this.repoService = repoService;
        this.propertiesService = propertiesService;
        this.packageSpecification = packageSpecification;
    }

    public boolean isDockerFileTypeAndSupported(RepoPath repoPath) {
        if (!isDockerSupported(repoPath)) {
            return false;
        }
        return repoService.getChildrenNames(repoPath).contains("json.json");
    }

    public boolean isDockerManifestFolder(RepoPath repoPath) {
        if (!isDockerSupported(repoPath)) {
            return false;
        }
        DockerAddon dockerAddon = addonsManager.addonByType(DockerAddon.class);
        return dockerAddon.isDockerManifestFolder(repoPath);
    }

    /**
     * This method assumes that isDockerManifestFolder(repoPath) == true, hence some of the checks are skipped
     */
    public RepoType getOCIFolderType(@Nonnull RepoPath repoPath) {
        try {
            String ociManifestMediaType = APPLICATION_VND_OCI_IMAGE_MANIFEST_V_1_JSON;
            Properties properties = propertiesService.getProperties(new RepoPathImpl(repoPath, MANIFEST_FILENAME));
            if (properties.isEmpty()) {
                ociManifestMediaType = APPLICATION_VND_OCI_IMAGE_INDEX_V_1_JSON;
                properties = propertiesService.getProperties(new RepoPathImpl(repoPath, MANIFEST_LIST_FILENAME));
            }
            String manifestType = properties.getFirst(DockerMetadataProvider.MANIFEST_TYPE_PROP);
            if (!ociManifestMediaType.equals(manifestType)) {
                return RepoType.Docker;
            }
            String currentArtifactType = properties.getFirst(OCI_ARTIFACT_TYPE);
            if (APPLICATION_VND_CNCF_HELM_CONFIG_V_1_JSON.equals(currentArtifactType)) {
                return RepoType.HelmOCI;
            }
            return RepoType.OCI;

        } catch (Exception e) {
            log.debug("Assuming the folder does not contain oci image due to error", e);
            return RepoType.Docker;
        }
    }

    private boolean isDockerSupported(RepoPath repoPath) {
        if (!addonsManager.isAddonSupported(AddonType.DOCKER) && !addonsManager.isAddonSupported(AddonType.OCI) &&
                !addonsManager.isAddonSupported(AddonType.HELMOCI)) {
            return false;
        }
        return isRepoSupportType(repoPath, RepoType.Docker) || isRepoSupportType(repoPath, RepoType.OCI) ||
                isRepoSupportType(repoPath, RepoType.HelmOCI);
    }

    public boolean isConanReferenceFolder(RepoPath repoPath) {
        boolean conanEnabled = isRepoSupportType(repoPath, RepoType.Conan);
        ConanAddon conanAddon = addonsManager.addonByType(ConanAddon.class);
        return conanEnabled && conanAddon.isConanReferenceFolder(repoPath);
    }

    public boolean isNixReferenceFolder(RepoPath repoPath) {
        boolean nixEnabled = isRepoSupportType(repoPath, RepoType.Nix);
        return nixEnabled && packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
    }

    public boolean isConanPackageFolder(RepoPath repoPath) {
        boolean conanEnabled = isRepoSupportType(repoPath, RepoType.Conan);
        ConanAddon conanAddon = addonsManager.addonByType(ConanAddon.class);
        return conanEnabled && conanAddon.isConanPackageFolder(repoPath);
    }

    public boolean isRepoSupportType(RepoPath repoPath, RepoType expectedRepoType) {
        LocalConfig localRepo = repoService.localFederatedOrCachedRepoConfigByKey(repoPath.getRepoKey());
        if (localRepo != null) {
            RepoType repoType = localRepo.getPackageType();
            return isRepoSupportType(expectedRepoType, repoType);
        }
        return false;
    }

    private boolean isRepoSupportType(RepoType expectedRepoType, RepoType actualRepoType) {
        return (expectedRepoType == actualRepoType || isDistributionRepo(actualRepoType));
    }

    private boolean isDistributionRepo(RepoType actualRepoType) {
        return RepoType.Distribution == actualRepoType;
    }

    /**
     * Check if folder is a Docker manifest folder AND get the OCI type in one optimized call.
     *
     * @param repoPath The folder path to check
     * @return Optional containing the icon type if it's a manifest folder, empty otherwise
     */
    public Optional<String> getDockerManifestIconType(@Nonnull RepoPath repoPath) {
        if (!isDockerSupported(repoPath)) {
            return Optional.empty();
        }
        try {
            List<String> fileNames = Arrays.asList(MANIFEST_FILENAME, MANIFEST_LIST_FILENAME);
            Map<String, Properties> manifestProps = propertiesService.getPropertiesForFiles(repoPath, fileNames);
            if (manifestProps.isEmpty()) {
                return Optional.empty();
            }
            String iconType = determineOCITypeFromProperties(repoPath, manifestProps);
            return Optional.of(iconType);
        } catch (Exception e) {
            log.debug("Error checking docker manifest folder: {}", repoPath, e);
            return Optional.empty();
        }
    }

    private String determineOCITypeFromProperties(@Nonnull RepoPath repoPath,
            @Nonnull Map<String, Properties> manifestProps) {
        String ociManifestMediaType = APPLICATION_VND_OCI_IMAGE_MANIFEST_V_1_JSON;
        Properties properties = manifestProps.get(MANIFEST_FILENAME);
        if (properties == null || properties.isEmpty()) {
            ociManifestMediaType = APPLICATION_VND_OCI_IMAGE_INDEX_V_1_JSON;
            properties = manifestProps.get(MANIFEST_LIST_FILENAME);
        }
        if (properties == null || properties.isEmpty()) {
            return RepoType.Docker.getType();
        }
        if (isRepoSupportType(repoPath, RepoType.HelmOCI)) {
            return RepoType.HelmOCI.getType();
        }
        String manifestType = properties.getFirst(DockerMetadataProvider.MANIFEST_TYPE_PROP);
        if (!ociManifestMediaType.equals(manifestType)) {
            return RepoType.Docker.getType();
        }
        String currentArtifactType = properties.getFirst(OCI_ARTIFACT_TYPE);
        if (APPLICATION_VND_CNCF_HELM_CONFIG_V_1_JSON.equals(currentArtifactType)) {
            return RepoType.HelmOCI.getType();
        }
        return RepoType.OCI.getType();
    }
}
