package org.artifactory.repo.virtual;

import org.artifactory.fs.RepoResource;
import com.jfrog.ph.acl.exception.PackageVirtualTraversalException;
import org.artifactory.request.InternalRequestContext;

/**
 * The download strategy Manages the virtual repository tasks:
 * 1. artifact resolution
 * 2. metadata merging for metadata resolution
 * 3. priority resolution
 * 4. matrix param download
 * 5. external dependencies override
 *
 * We support two strategies:
 * 1: maven_centric (legacy)
 * 2. generic which is being used by the Package handler trough the ExposedDownloadService
 */
public interface VirtualDownloadStrategy {

    /**
     * Resolve fileInfo from the virtual repository and its sub-repositories
     * @param context Request context that contains all the information required for the resolution/merging
     * @return resource that contains the resolution result which is item info
     */
    RepoResource getInfo(InternalRequestContext context) throws PackageVirtualTraversalException;

}
