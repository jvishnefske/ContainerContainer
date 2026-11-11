package org.artifactory.ph;

import org.artifactory.common.resource.BaseResource;
import com.jfrog.ph.acl.exception.PackageVirtualTraversalException;
import com.jfrog.ph.acl.resource.PackageUnfoundedResource;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.virtual.VirtualDownloadStrategy;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.UnfoundRepoResource;

/**
 * This implementation of the virtual download strategy throws all the resolution and merging responsibility to
 * the package layer
 */
public class PackageVirtualDownloadStrategy implements VirtualDownloadStrategy {
    private final VirtualRepo virtualRepo;
    private final PackageVirtualResolutionService packageVirtualResolutionService;

    public PackageVirtualDownloadStrategy(VirtualRepo virtualRepo,
            PackageVirtualResolutionService packageVirtualResolutionService) {
        this.virtualRepo = virtualRepo;
        this.packageVirtualResolutionService = packageVirtualResolutionService;
    }

    /**
     * Get RepoResource by context (repoKey and path)
     *
     * @param context Request context that contains all the information required for the resolution/merging
     * @return found repo resource if found, else un-found resource
     */
    @Override
    public RepoResource getInfo(InternalRequestContext context) throws PackageVirtualTraversalException {
        // resolve info using the package layer
        BaseResource res = packageVirtualResolutionService.getItemInfoFromVirtual(virtualRepo, context);
        // In case of un-found resource, translate it to the core "UnfoundRepoResource" which doesn't exist
        // in the package layer
        if (res instanceof PackageUnfoundedResource packageUnfoundedResource) {
            RepoPath repoPath = RepoPathFactory.create(res.getRepoKey(), res.getPath());
            res = new UnfoundRepoResource(repoPath, packageUnfoundedResource.getReason(),
                    ((PackageUnfoundedResource) res).getStatusCode());
        }
        return (RepoResource) res;
    }
}
