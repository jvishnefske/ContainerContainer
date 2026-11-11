package org.artifactory.ph;

import org.artifactory.common.resource.BaseResource;
import com.jfrog.ph.acl.exception.PackageVirtualTraversalException;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import org.artifactory.request.InternalRequestContext;

public interface PackageVirtualResolutionService {
    /**
     * The method allows deciding whether to use the legacy virtual resolution strategy;
     *
     * @param context  The request context containing virtual repoKey and additional information
     * @return True in case that the package support the new virtual resolution framework
     */
    boolean shouldUsePackageVirtualResolution(InternalRequestContext context);

    /**
     * Used  by Artifactory to get item resolution by the package layer
     *
     * @param virtualRepo the virtual repository key
     * @param context     request context
     * @return Item Info
     */
    BaseResource getItemInfoFromVirtual(PackageRepo virtualRepo, PackageRequestContext context) throws
            PackageVirtualTraversalException;
}
