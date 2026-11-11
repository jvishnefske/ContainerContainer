package org.artifactory.leadartifact.detector;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;

import java.util.Set;

/**
 * Detects artifacts with special meaning per the implementing package (RepoType).
 * Usually such files hold information about the package version they represent or the files that are included in it,
 * i.e. manifest.json in Docker and release-bundle.json.evd in ReleaseBundles (RBv2).
 */
public interface LeadArtifactDetector {

    boolean isLeadArtifact(RepoPath repoPath);

    Set<String> getLeadArtifactExtensions();

    /**
     * Determines package lead files for download/upload consumption, OR
     * determines if an artifact is eligible for monitoring download statistics.
     * <p></p>
     * Each package has it own mandatory lead files for consumption (see {@link #isLeadArtifact(RepoPath)}).
     * An intersection with this lead file can indicate whether we perform download/upload of a package.
     * However, in some cases, such as when the lead artifact is not being downloaded upon pulling a version, but only
     * signifies the anchor of a version, we would like to change the lead file.
     * This consumption lead file will be different from the file extensions appearing in isLeadArtifact, and from
     * LEAD_FILE_EXTENSIONS.
     * <p></p>
     * Therefore, each package will need to supply a whitelist of PACKAGE_LEAD_FILES and override the method
     * default implementation. e.g. for CONAN package, the LEAD_FILE_EXTENSIONS is "conanfile.py", however,
     * "conaninfo.txt" is the right indication for package download/upload.
     */
    default boolean isPackageConsumptionLead(RepoPath repoPath) {
        return isLeadArtifact(repoPath);
    }

    RepoType getRepoType();
}
