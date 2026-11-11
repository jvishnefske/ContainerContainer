package org.artifactory.addon.leadartifactdetector;

import org.artifactory.common.addon.Addon;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;

/**
 * The leading artifact identifier
 *
 * @author Natalie Berni
 */
public interface LeadArtifactDetectorAddon extends Addon {
    /**
     * Determines whether the artifact is the leading one for the given type of package.
     */
    default boolean isLeadArtifact(RepoType packageType, RepoPath repoPath) {
        return false;
    }

    /**
     * Determines whether a package file is a leading file for consumption usages.
     * A package lead files are using for counting the number of download/upload of a package.
     * Each package has it own mandatory lead files for consumption. An intersection with this file can indicate
     * whether we perform download/upload of a package.
     */
    default boolean isPackageConsumptionLead(RepoType repoType, RepoPath repoPath) {
        return false;
    }
}
