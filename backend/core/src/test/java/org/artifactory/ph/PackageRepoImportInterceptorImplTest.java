package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventContext;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventListener;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventType;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.apache.catalina.connector.Response;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.common.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class PackageRepoImportInterceptorImplTest {
    private static final String REPO_KEY = "repo";
    private static final int IMPORTED_ITEMS_COUNT = 7;
    private static final RepoPath repoPath = RepoPathFactory.create(REPO_KEY, "path");
    @Mock
    private PackageRepositoryEventListener importListener;
    @Mock
    private PackageSpecificationAdapter packageSpecificationAdapter;
    @Mock
    private PackageRepoService repositoryService;
    @Mock
    private MutableStatusHolder statusHolder;
    @Captor
    private ArgumentCaptor<PackageRepositoryEventContext> eventContextCaptor;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws PackageException {
        closeable = MockitoAnnotations.openMocks(this);
        when(packageSpecificationAdapter.isSupportedPackage(REPO_KEY)).thenReturn(true);
        when(repositoryService.getRepoInfo(REPO_KEY)).thenReturn(new RepoInfo(RepoType.LOCAL, PackageType.PYPI));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void importEventNotExecuted() throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(REPO_KEY)).thenReturn(false);

        PackageRepoImportInterceptorImpl repoImportInterceptor =
                new PackageRepoImportInterceptorImpl(importListener, packageSpecificationAdapter, repositoryService);

        repoImportInterceptor.afterRepoImport(repoPath, IMPORTED_ITEMS_COUNT, statusHolder);
        verify(importListener, times(0)).handleEvent(any());
    }

    @Test
    void importEventExecuted() throws PackageException {
        PackageRepoImportInterceptorImpl repoImportInterceptor =
                new PackageRepoImportInterceptorImpl(importListener, packageSpecificationAdapter, repositoryService);

        repoImportInterceptor.afterRepoImport(repoPath, IMPORTED_ITEMS_COUNT, statusHolder);
        verify(importListener, times(1)).handleEvent(eventContextCaptor.capture());
        PackageRepositoryEventContext eventContext = eventContextCaptor.getValue();
        Assertions.assertEquals(PackageRepositoryEventType.REPO_IMPORT, eventContext.eventType());
    }

    @Test
    void packageExceptionHandled() throws PackageException {
        doThrow(new PackageException(Response.SC_INTERNAL_SERVER_ERROR, "", ""))
                .when(importListener).handleEvent(any());

        PackageRepoImportInterceptorImpl repoImportInterceptor =
                new PackageRepoImportInterceptorImpl(importListener, packageSpecificationAdapter, repositoryService);

        Assertions.assertDoesNotThrow(
                () -> repoImportInterceptor.afterRepoImport(repoPath, IMPORTED_ITEMS_COUNT, statusHolder));
        verify(importListener, times(1)).handleEvent(eventContextCaptor.capture());
        PackageRepositoryEventContext eventContext = eventContextCaptor.getValue();
        Assertions.assertEquals(PackageRepositoryEventType.REPO_IMPORT, eventContext.eventType());
    }

    @Test
    void runtimeExceptionHandled() throws PackageException {
        doThrow(new RuntimeException()).when(importListener).handleEvent(any());

        PackageRepoImportInterceptorImpl repoImportInterceptor =
                new PackageRepoImportInterceptorImpl(importListener, packageSpecificationAdapter, repositoryService);

        Assertions.assertDoesNotThrow(
                () -> repoImportInterceptor.afterRepoImport(repoPath, IMPORTED_ITEMS_COUNT, statusHolder));
        verify(importListener, times(1)).handleEvent(eventContextCaptor.capture());
        PackageRepositoryEventContext eventContext = eventContextCaptor.getValue();
        Assertions.assertEquals(PackageRepositoryEventType.REPO_IMPORT, eventContext.eventType());
    }
}