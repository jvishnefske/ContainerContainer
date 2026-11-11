package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageVirtualTraversalException;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import com.jfrog.ph.acl.virtual.resolution.PackageVirtualResolutionAdapter;
import lombok.NonNull;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.request.InternalRequestContext;


import static org.artifactory.repo.virtual.VirtualRepo.PACKAGE_HANDLER_ENABLED;

public class PackageVirtualResolutionServiceImpl implements PackageVirtualResolutionService{

    private final PackageVirtualResolutionAdapter packageHandlerVirtualService;

    public PackageVirtualResolutionServiceImpl(PackageVirtualResolutionAdapter
            packageVirtualResolutionAdapter) {
        this.packageHandlerVirtualService = packageVirtualResolutionAdapter;
    }

    @Override
    public boolean shouldUsePackageVirtualResolution(@NonNull InternalRequestContext context) {
        boolean isPackageHandlerEnabled = context.getRequest() != null
                && context.getRequest().getHeaderValues(PACKAGE_HANDLER_ENABLED).hasMoreElements();

        return isPackageHandlerEnabled ||
                packageHandlerVirtualService.shouldUsePackageVirtualResolution(context.getRepoKey());
    }

    @Override
    public BaseResource getItemInfoFromVirtual(PackageRepo virtualRepo, PackageRequestContext context)
            throws PackageVirtualTraversalException {
        return packageHandlerVirtualService.getItemInfoFromVirtual(virtualRepo, context);
    }
}

