package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageResourceServiceAdapter;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.artifactory.ph.RepoListType.AVAILABLE;
import static org.artifactory.ph.RepoListType.SELECTED;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Volodymyr Shulga
 */
class PackageResourceServiceImplTest {

    private PackageResourceServiceImpl packageResourceService;

    @Mock
    private PackageSpecificationAdapter packageSpecificationAdapter;
    @Mock
    private PackageResourceServiceAdapter packageResourceServiceAdapter;
    @Mock
    private HttpServletRequest request;

    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = openMocks(this);
        packageResourceService = new PackageResourceServiceImpl(packageResourceServiceAdapter,
                packageSpecificationAdapter);
    }

    @AfterEach
    void destroy() throws Exception {
        closeable.close();
    }

    @Test
    void getVersionedResourceV3() throws PackageException {
        String repoKey = "repo";
        String version = "v3";
        assertNull(packageResourceService.getVersionedSubResource(PackageType.NUGET, version, repoKey, request));

        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NUGET)).thenReturn(true);
        packageResourceService.getVersionedSubResource(PackageType.NUGET, version, repoKey, request);
        verify(packageResourceServiceAdapter).getVersionedSubResource(PackageType.NUGET, version, repoKey, request);
    }

    @Test
    void getVersionedResourceV2() throws PackageException {
        String repoKey = "repo";
        String version = null;
        assertNull(packageResourceService.getVersionedSubResource(PackageType.NUGET, version, repoKey, request));
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NUGET)).thenReturn(true);

        packageResourceService.getVersionedSubResource(PackageType.NUGET, version, repoKey, request);
        verify(packageResourceServiceAdapter).getVersionedSubResource(PackageType.NUGET, version, repoKey, request);
    }

    @Test
    void getApiEndpoint() throws PackageException {
        String repoKey = "repo";
        String beanIdentifier = "GetDistribution";
        assertNull(packageResourceService.getPackageEndPoint(PackageType.PYPI, beanIdentifier, repoKey, request));
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.PYPI)).thenReturn(true);

        packageResourceService.getPackageEndPoint(PackageType.PYPI, beanIdentifier, repoKey, request);
        verify(packageResourceServiceAdapter).getPackageEndPointSubResource(PackageType.PYPI, beanIdentifier, repoKey,
                request);
    }

    @Test
    void reindex() throws PackageException {
        String repoKey = "repo";
        assertNull(packageResourceService.reindex(PackageType.NUGET, repoKey, null));

        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NUGET)).thenReturn(true);
        packageResourceService.reindex(PackageType.NUGET, repoKey, null);
        verify(packageResourceServiceAdapter).reindex(PackageType.NUGET, repoKey, null);
    }

    @Test
    void getNonVersionedSubResource() throws PackageException {
        String repoKey = "repo";
        assertNull(packageResourceService.getNonVersionedSubResource(PackageType.NUGET, repoKey, request));
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NUGET)).thenReturn(true);

        packageResourceService.getNonVersionedSubResource(PackageType.NUGET, repoKey, request);
        verify(packageResourceServiceAdapter).getNonVersionedSubResource(PackageType.NUGET, repoKey, request);
    }

    @Test
    void getNonVersionedSubResourceTerraformBackend() throws PackageException {
        String repoKey = "repo";
        assertNull(packageResourceService.getNonVersionedSubResource(PackageType.TERRAFORMBACKEND, repoKey, request));
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.TERRAFORMBACKEND)).thenReturn(true);

        packageResourceService.getNonVersionedSubResource(PackageType.TERRAFORMBACKEND, repoKey, request);
        verify(packageResourceServiceAdapter).getNonVersionedSubResource(PackageType.TERRAFORM, repoKey, request);
    }

    @Test
    void getPackageInfo() throws PackageException {
        String repoKey = "repo";
        String path = "dir/package";
        assertNull(packageResourceService.getPackageInfo(repoKey, path));
        when(packageSpecificationAdapter.isSupportedPackage(repoKey)).thenReturn(true);

        packageResourceService.getPackageInfo(repoKey, path);
        verify(packageResourceServiceAdapter).getPackageInfo(repoKey, path);
    }

    @Test
    void getVersionNativeReadme() throws PackageException {
        String path = "repo/dir/package";
        assertNull(packageResourceService.getVersionNativeReadme("npm", path));

        verify(packageResourceServiceAdapter).getVersionNativeReadme("npm", path);
    }

    @Test
    void getVersionNativeReadme_extractionFailure_returnsNull() throws PackageException {
        String path = "repo/dir/package";
        when(packageResourceServiceAdapter.getVersionNativeReadme("npm", path))
                .thenThrow(new RuntimeException("Corrupt archive"));

        assertNull(packageResourceService.getVersionNativeReadme("npm", path));
    }

    @Test
    void getVersionNativeReadme_packageException_returnsNull() throws PackageException {
        String path = "repo/dir/package";
        when(packageResourceServiceAdapter.getVersionNativeReadme("npm", path))
                .thenThrow(new PackageException(500, "Failed to extract readme", "Failed to extract readme"));

        assertNull(packageResourceService.getVersionNativeReadme("npm", path));
    }

    @Test
    void getVirtualAllowedRepos() {
        RepoConfig repoConfig = VirtualRepoConfig.create("vir-nug-repo", RepoType.NuGet);
        Map<RepoListType, List<String>> repoLists = Map.of(
                AVAILABLE, List.of("repo1", "repo2"),
                SELECTED, List.of("repo3", "repo4")
        );

        Map<String, List<String>> filteredLists = Map.of(
                "available", List.of("repo1", "repo2"),
                "selected", List.of("repo3", "repo4")
        );

        when(packageSpecificationAdapter.getVirtualAllowedRepos(any(), any())).thenReturn(filteredLists);
        assertNotNull(packageResourceService.getVirtualAllowedRepos(repoConfig, repoLists));
    }
}