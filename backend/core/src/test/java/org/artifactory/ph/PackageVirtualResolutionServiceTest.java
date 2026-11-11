package org.artifactory.ph;


import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.virtual.resolution.PackageVirtualResolutionAdapter;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.fs.RepoResource;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class PackageVirtualResolutionServiceTest {
    private static final String VIRTUAL_REPO_KEY_1 = "virtualRepoKey1";
    private static final String VIRTUAL_REPO_KEY_2 = "virtualRepoKey2";
    private static final String PACKAGE_HANDLER_ENABLED = "packageHandlerEnabled";

    @Mock
    private PackageVirtualResolutionAdapter packageVirtualAdapter;
    @Mock
    private RepoResource resource;
    @Mock
    private Request request;
    @Mock
    private InternalRequestContext requestContext;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() throws PackageException {
        this.autoCloseable = openMocks(this);
        when(requestContext.getRequest()).thenReturn(request);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }


    @Test
    void shouldNotUsePackageVirtualResolution() {
        PackageVirtualResolutionServiceImpl packageVirtualResolutionService =
                new PackageVirtualResolutionServiceImpl(packageVirtualAdapter);
        when(packageVirtualAdapter.shouldUsePackageVirtualResolution(VIRTUAL_REPO_KEY_1)).thenReturn(true);
        when(packageVirtualAdapter.shouldUsePackageVirtualResolution(VIRTUAL_REPO_KEY_2)).thenReturn(false);

        // return empty enumeration here to simulate no header
        when(request.getHeaderValues(PACKAGE_HANDLER_ENABLED)).thenReturn(Collections.emptyEnumeration());
        when(requestContext.getRepoKey()).thenReturn(VIRTUAL_REPO_KEY_1);
        boolean result = packageVirtualResolutionService.shouldUsePackageVirtualResolution(requestContext);
        Assertions.assertTrue(result);
        when(requestContext.getRepoKey()).thenReturn(VIRTUAL_REPO_KEY_2);
        result = packageVirtualResolutionService.shouldUsePackageVirtualResolution(requestContext);
        Assertions.assertFalse(result);

        verify(packageVirtualAdapter, times(2)).shouldUsePackageVirtualResolution(any());
    }

    @Test
    void shouldUsePackageVirtualResolution() {
        PackageVirtualResolutionServiceImpl packageVirtualResolutionService =
                new PackageVirtualResolutionServiceImpl(packageVirtualAdapter);
        when(packageVirtualAdapter.shouldUsePackageVirtualResolution(VIRTUAL_REPO_KEY_1)).thenReturn(true);
        when(packageVirtualAdapter.shouldUsePackageVirtualResolution(VIRTUAL_REPO_KEY_2)).thenReturn(false);
        final InternalRequestContext requestContext = Mockito.mock(InternalRequestContext.class);
        // return empty enumeration here to simulate no header
        when(request.getHeaderValues(PACKAGE_HANDLER_ENABLED)).thenReturn(Collections.enumeration(List.of("true")));
        when(requestContext.getRepoKey()).thenReturn(VIRTUAL_REPO_KEY_1);
        boolean result = packageVirtualResolutionService.
                shouldUsePackageVirtualResolution(requestContext);
        Assertions.assertTrue(result);
        when(requestContext.getRepoKey()).thenReturn(VIRTUAL_REPO_KEY_2);
        result = packageVirtualResolutionService.
                shouldUsePackageVirtualResolution(requestContext);
        Assertions.assertFalse(result);
        verify(packageVirtualAdapter, times(2)).shouldUsePackageVirtualResolution(any());
    }

    @Test
    void getItemInfoFromVirtual() throws PackageException {
        PackageVirtualResolutionServiceImpl packageVirtualResolutionService =
                new PackageVirtualResolutionServiceImpl(packageVirtualAdapter);
        when(resource.getPath()).thenReturn("test");
        when(packageVirtualAdapter.getItemInfoFromVirtual(any(), any())).thenReturn(resource);
        BaseResource res = packageVirtualResolutionService.getItemInfoFromVirtual(any(), any());
        Assertions.assertEquals("test", res.getPath());
        verify(packageVirtualAdapter, times(1)).getItemInfoFromVirtual(any(), any());
    }
}
