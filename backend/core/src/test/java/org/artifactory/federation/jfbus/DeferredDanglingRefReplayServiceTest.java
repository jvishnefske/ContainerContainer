package org.artifactory.federation.jfbus;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.DeferredDanglingRefCleanupDao;
import org.artifactory.federation.DeferredDanglingRefCleanupDao.DeferredDanglingRefEntry;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.schedule.ArtifactoryExecutorService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeferredDanglingRefReplayServiceTest
        extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private FederatedRepoService federatedRepoService;
    @Mock
    private DeferredDanglingRefCleanupDao danglingRefCleanupDao;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryExecutorService executorService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        getBound().setProperty(
                ConstantValues.virtualRepoFederationEnabled, "true");
        InternalArtifactoryContext ctx =
                mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(ctx);
        doAnswer(invocation -> {
            return invocation.<java.util.concurrent.Callable<?>>
                    getArgument(0).call();
        }).when(securityService).callAsSystem(any());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executorService).execute(any());
    }

    @AfterEach
    @Override
    public void unbindArtifactoryHome() {
        ArtifactoryContextThreadBinder.unbind();
        try {
            mocks.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        super.unbindArtifactoryHome();
    }

    private DeferredDanglingRefReplayService createService() {
        return new DeferredDanglingRefReplayService(
                federatedRepoService, danglingRefCleanupDao,
                securityService, executorService);
    }

    @Test
    void onContextReady_deletesResolvedRows() throws Exception {
        when(danglingRefCleanupDao.findAll()).thenReturn(List.of(
                new DeferredDanglingRefEntry("virt1", "child1"),
                new DeferredDanglingRefEntry("virt2", "child2")));
        when(federatedRepoService
                .runDanglingRefCleanupRetry("virt1", "child1"))
                .thenReturn(true);
        when(federatedRepoService
                .runDanglingRefCleanupRetry("virt2", "child2"))
                .thenReturn(false);

        DeferredDanglingRefReplayService service = createService();
        service.onContextReady();

        verify(executorService).execute(any());
        verify(danglingRefCleanupDao).delete("virt1", "child1");
        verify(danglingRefCleanupDao, never())
                .delete("virt2", "child2");
    }

    @Test
    void onContextReady_doesNothing_whenNoPendingRows()
            throws Exception {
        when(danglingRefCleanupDao.findAll()).thenReturn(List.of());

        DeferredDanglingRefReplayService service = createService();
        service.onContextReady();

        verify(executorService).execute(any());
        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(any(), any());
    }

    @Test
    void onContextReady_skipsReplay_whenFindAllFails() throws Exception {
        when(danglingRefCleanupDao.findAll()).thenThrow(
                new SQLException("db unavailable"));

        DeferredDanglingRefReplayService service = createService();
        service.onContextReady();

        verify(executorService).execute(any());
        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(any(), any());
        verify(danglingRefCleanupDao, never()).delete(any(), any());
    }

    @Test
    void onContextReady_keepsRow_whenCleanupThrows() throws Exception {
        when(danglingRefCleanupDao.findAll()).thenReturn(List.of(
                new DeferredDanglingRefEntry("virt", "child")));
        when(federatedRepoService.runDanglingRefCleanupRetry("virt", "child"))
                .thenThrow(new IllegalStateException("simulated"));

        DeferredDanglingRefReplayService service = createService();
        service.onContextReady();

        verify(danglingRefCleanupDao, never()).delete(any(), any());
    }

}
