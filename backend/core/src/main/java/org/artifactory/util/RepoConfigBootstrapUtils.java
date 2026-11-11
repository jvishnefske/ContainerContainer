package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.reader.CentralConfigReader;
import org.artifactory.repo.service.RepositoryConfigBootstrapServiceImpl;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_BOOTSTRAP_FILE;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_IMPORT_FILE;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_IMPORT_FILE;

/**
 * @author Mor Merhav
 * Utility class to be used by {@link RepositoryConfigBootstrapServiceImpl} and
 * {@link org.artifactory.config.CentralConfigServiceImpl}
 */
@UtilityClass
public class RepoConfigBootstrapUtils {

    /**
     * Verify if both {@link org.artifactory.common.home.ArtifactoryHome#ARTIFACTORY_REPO_CONFIG_IMPORT_FILE}
     * and {@link org.artifactory.common.home.ArtifactoryHome#ARTIFACTORY_CONFIG_IMPORT_FILE} exist and the latter
     * contains repositories to be backwards compatibly bootstrapped.
     *
     * @throws IllegalStateException if the conflict exists
     */
    @SuppressWarnings("deprecation")
    public void verifyConflictingRepoBootstrap() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        boolean repoConfigImportFileExists = artifactoryHome.getArtifactoryRepoConfigImportFile().exists();
        boolean centralConfigImportFileExists = artifactoryHome.getArtifactoryConfigImportFile().exists();
        boolean centralConfigBootstrapFileExists = artifactoryHome.getArtifactoryConfigBootstrapFile().exists();

        if (repoConfigImportFileExists && (centralConfigBootstrapFileExists || centralConfigImportFileExists)) {
            String centralDescriptorConfigXml =
                    centralConfigImportFileExists ? artifactoryHome.getImportConfigXml() :
                            artifactoryHome.getBootstrapConfigXml();

            MutableCentralConfigDescriptor centralConfigDescriptor =
                    new CentralConfigReader().readAndConvert(centralDescriptorConfigXml, false, false, false).getLeft();

            if (centralConfigDescriptor.getRepoCount() > 0) {
                throw new IllegalStateException(String.format(
                        "Cannot have both '%s' and '%s' which contains repositories in 'etc/artifactory' dir",
                        ARTIFACTORY_REPO_CONFIG_IMPORT_FILE, centralConfigImportFileExists ?
                                ARTIFACTORY_CONFIG_IMPORT_FILE : ARTIFACTORY_CONFIG_BOOTSTRAP_FILE));
            }
        }
    }
}
