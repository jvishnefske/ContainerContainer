package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.resource.PackageApiEndPoint;
import com.jfrog.ph.acl.service.PackageResourceServiceAdapter;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.repo.PackageType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.model.RepoConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
public class PackageResourceServiceImpl implements PackageResourceService {

    private final PackageResourceServiceAdapter packageResourceServiceAdapter;
    private final PackageSpecificationAdapter packageSpecificationAdapter;
    private static final String PACKAGE_TYPE = "packageType";

    public PackageResourceServiceImpl(PackageResourceServiceAdapter packageResourceServiceAdapter,
            PackageSpecificationAdapter packageSpecificationAdapter) {
        this.packageResourceServiceAdapter = packageResourceServiceAdapter;
        this.packageSpecificationAdapter = packageSpecificationAdapter;
    }

    @Override
    public Response reindex(@NonNull PackageType packageType, @NonNull String repoKey, Boolean async)
            throws PackageException {
        CurrentActivity.PACKAGE.put(packageType);
        if (!packageSpecificationAdapter.isSupportedPackage(packageType)) {
            return null;
        }
        return packageResourceServiceAdapter.reindex(packageType, repoKey, async);
    }

    @Override
    public Object getVersionedSubResource(@NonNull PackageType packageType, String version, @NonNull String repoKey,
            HttpServletRequest request)
            throws PackageException {
        CurrentActivity.PACKAGE.put(packageType);
        if (!packageSpecificationAdapter.isSupportedPackage(packageType)) {
            return null;
        }
        return packageResourceServiceAdapter.getVersionedSubResource(packageType, version, repoKey, request);
    }

    @Override
    public Object getNonVersionedSubResource(@NonNull PackageType packageType, @NonNull String context,
            HttpServletRequest request) throws PackageException {
        CurrentActivity.PACKAGE.put(packageType);
        if (!packageSpecificationAdapter.isSupportedPackage(packageType)) {
            return null;
        }
        if (PackageType.TERRAFORMBACKEND == packageType) {
            // special case as terraformbackend packageType is under "terraform" api
            packageType = PackageType.TERRAFORM;
        }
        return packageResourceServiceAdapter.getNonVersionedSubResource(packageType, context, request);
    }

    @Override
    public PackageApiEndPoint getPackageEndPoint(@NonNull PackageType packageType, @NonNull String endpointName,
            @NonNull String context, HttpServletRequest request) throws PackageException {
        CurrentActivity.PACKAGE.put(packageType);
        if (!packageSpecificationAdapter.isSupportedPackage(packageType)) {
            return null;
        }
        return packageResourceServiceAdapter.getPackageEndPointSubResource(packageType,
                endpointName, context, request);
    }

    @Override
    public Response getPackageInfo(String repoKey, String path) throws PackageException {
        if (!packageSpecificationAdapter.isSupportedPackage(repoKey)) {
            return null;
        }
        return packageResourceServiceAdapter.getPackageInfo(repoKey, path);
    }

    @Override
    public Response getVersionNativeReadme(String type, String fullRepoPath) {
        try {
            return packageResourceServiceAdapter.getVersionNativeReadme(type, fullRepoPath);
        } catch (Exception e) {
            log.warn("Failed to extract readme for path '{}': {}", fullRepoPath, e.getMessage());
            log.debug("Failed to extract readme for path '{}'", fullRepoPath, e);
            return null;
        }
    }

    @Override
    public Map<RepoListType, List<String>> getVirtualAllowedRepos(RepoConfig repoConfig,
            Map<RepoListType, List<String>> repoLists) {
        Map<String, String> packageTypeConfig = repoConfig.getPackageTypeConfig();

        // "packageTypeConfig" may not have all the properties that are needed downstream
        // selectively get them from "repoConfig" and put in "packageTypeConfig"
        String packageType = repoConfig.getPackageType().getType().toUpperCase();
        packageTypeConfig.put(PACKAGE_TYPE, packageType);

        Map<String, List<String>> repos = repoLists.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getListType(), Map.Entry::getValue));

        var filteredRepos = packageSpecificationAdapter.getVirtualAllowedRepos(packageTypeConfig, repos);

        return Map.of(
                RepoListType.AVAILABLE, filteredRepos.get(RepoListType.AVAILABLE.getListType()),
                RepoListType.SELECTED, filteredRepos.get(RepoListType.SELECTED.getListType())
        );
    }
}
