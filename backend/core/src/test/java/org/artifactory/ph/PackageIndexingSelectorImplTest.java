package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.service.PackageRepoIndexCalculatorService;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PackageIndexingSelectorImplTest {
    private static final String REPO = "repo";
    private static final String PATH = "path";
    @Mock
    private PackageRepoService packageRepoService;
    @Mock
    private PackageSpecificationAdapter packageSpecification;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PackageRepoIndexCalculatorService packageRepoIndexCalculatorService;
    @Mock
    AuthorizationService authorizationService;
    AutoCloseable closeable;
    PackageIndexingSelectorImpl packageIndexingSelector;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(authorizationService.canManage(RepoPathFactory.create(REPO, PATH))).thenReturn(true);
        packageIndexingSelector =
                spy(new PackageIndexingSelectorImpl(packageRepoService, packageSpecification, addonsManager,
                        packageRepoIndexCalculatorService, authorizationService));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        verify(authorizationService, times(1)).canManage(any(RepoPath.class));
        verifyNoMoreInteractions(packageRepoIndexCalculatorService, packageSpecification, packageRepoService,
                addonsManager, authorizationService);
    }

    @Test
    void reindexWithoutManage() {
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(false);
        PackageForbiddenException exception = Assertions.assertThrows(PackageForbiddenException.class,
                () -> packageIndexingSelector.reindex(REPO, PATH));
        Assertions.assertEquals("Skipping indexing request - user without manage permissions",
                exception.getInternalReason());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
        Assertions.assertEquals(403, exception.getStatus());
    }

    @Test
    void reindexWithoutPackageType() throws Exception {
        when(packageRepoService.getPackageType(REPO)).thenReturn(null);
        packageIndexingSelector.reindex(REPO, PATH);
        verify(packageRepoService, times(1)).getPackageType(REPO);
    }

    @Test
    void reindexNotSupportedPackage() throws Exception {
        when(packageRepoService.getPackageType(REPO)).thenReturn(PackageType.DEBIAN);
        packageIndexingSelector.reindex(REPO, PATH);
        verify(packageRepoService, times(1)).getPackageType(REPO);
        verify(packageSpecification, times(1)).isSupportedPackage(PackageType.DEBIAN);
    }

    @Test
    void reindexSupportedPackage() throws Exception {
        when(packageRepoService.getPackageType(REPO)).thenReturn(PackageType.NPM);
        when(packageSpecification.isSupportedPackage(PackageType.NPM)).thenReturn(true);
        packageIndexingSelector.reindex(REPO, PATH);
        verify(packageRepoService, times(1)).getPackageType(REPO);
        verify(packageSpecification, times(1)).isSupportedPackage(PackageType.NPM);
        verify(packageRepoIndexCalculatorService, times(1)).reindexSpecificPath(PackageType.NPM, REPO, PATH);
    }

    @Test
    void reindexSupportedPackageWithoutPath() throws Exception {
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);
        when(packageRepoService.getPackageType(REPO)).thenReturn(PackageType.NPM);
        when(packageSpecification.isSupportedPackage(PackageType.NPM)).thenReturn(true);
        packageIndexingSelector.reindex(REPO, "");
        verify(packageRepoService, times(1)).getPackageType(REPO);
        verify(packageSpecification, times(1)).isSupportedPackage(PackageType.NPM);
        verify(packageRepoIndexCalculatorService, times(1)).reindex(PackageType.NPM, REPO);
    }
}