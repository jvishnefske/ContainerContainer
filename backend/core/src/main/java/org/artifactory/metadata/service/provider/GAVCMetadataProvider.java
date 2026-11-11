package org.artifactory.metadata.service.provider;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.jfrog.client.util.PathUtils;

import java.util.Set;

/**
 * Metadata provider interface for GAVC based package types that support Maven Layout (Maven, Gradle, SBT).
 * Lead artifacts are identified by their GAVC coordinates and file extension.
 * POM files are always lead artifacts, unless they are POMs with classifier build
 * (i.e. their name ends with "-build.pom").
 * The build POM is never a lead artifact, as it is used only for build info collection.
 * Starting from Maven 4.0.0, with module 4.1.0, the build POM is deployed alongside the main artifact
 * and is not considered a lead artifact.
 * POM, with packaging type BOM(Bill of Materials) is uploading as a pom file with classifier build
 * and they not a lead artifact.
 *
 * @author Natalie Berni
 */
public class GAVCMetadataProvider extends AbstractMetadataProvider {

    public static final String BUILD_POM = "-build.pom";

    public GAVCMetadataProvider(RepoType type) {
        super(type);
    }

    @Override
    public boolean isLeadArtifact(RepoPath repoPath) {
        return isMavenLeadArtifact(repoPath, getLeadArtifactExtensions());
    }

    /**
     * Check if the given RepoPath is a lead artifact based on its extension and if it's not a build POM.
     * @param repoPath The repository path of the artifact.
     * @param getLeadArtifactExtensions The set of lead artifact extensions.
     * @return true if the RepoPath is a lead artifact, false otherwise.
     */
    public boolean isMavenLeadArtifact(RepoPath repoPath, Set<String> getLeadArtifactExtensions) {
        String extension = PathUtils.getExtension(repoPath.getPath());
        String tarExtension = PathUtils.getTarFamilyExtension(repoPath.getPath());
        return (StringUtils.isNotBlank(tarExtension) && getLeadArtifactExtensions.contains(tarExtension)) ||
                isPomLeadExtension(repoPath, extension, getLeadArtifactExtensions);
    }

    /**
     * Check if the given extension is a lead artifact extension and not a build POM.
     * @param repoPath The repository path of the artifact.
     * @param extension The file extension of the artifact.
     * @param getLeadArtifactExtensions The set of lead artifact extensions.
     * @return true if the extension is a lead artifact extension and not a build POM, false otherwise.
     */
    public boolean isPomLeadExtension(RepoPath repoPath, String extension, Set<String> getLeadArtifactExtensions) {
        return StringUtils.isNotBlank(extension) && getLeadArtifactExtensions.contains(extension)
                && repoPath.getPath().lastIndexOf(BUILD_POM) == -1;
    }
}
