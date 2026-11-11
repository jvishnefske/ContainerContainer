package org.artifactory.addon.curation.packages;

import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RemoteRepoBase;
import org.artifactory.request.InternalRequestContext;

import javax.annotation.Nullable;

/**
 * Service for handling curation-compliant package versions.
 *
 * @author Ron Malka
 */
public interface PackagesCurationCompliantVersionService {

    /**
     * Checks if the repository is configured to support curation compliant versions.
     *
     * @param res        The request context containing information about the request.
     * @param remoteRepo The repository to check.
     * @return true if the repository supports curation compliant versions, false otherwise.
     */
    boolean canCallFilterBlockedVersions(RepoResource res, RemoteRepoBase<?, ?> remoteRepo);


    /**
     * Retrieves the curated metadata resource for a given repository resource.
     * This method applies curation policies to filter and modify metadata based on
     * the repository's curation configuration.
     *
     * @param requestContext The internal request context containing request information
     * @param repo           The remote repository from which to retrieve the curated metadata
     * @param res            The original repository resource to be curated
     * @return The curated metadata resource, or null if no curation is applied or available
     */
    @Nullable
    RepoResource getCuratedMetadataResource(InternalRequestContext requestContext, RemoteRepoBase<?, ?> repo,
            RepoResource res) throws PackageBlockedByCurationException;
}
