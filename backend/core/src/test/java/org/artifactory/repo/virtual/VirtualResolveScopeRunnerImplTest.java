package org.artifactory.repo.virtual;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.threadlocal.cache.SupplierWithError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualResolveScopeRunnerImplTest {

    private InternalRepositoryConfigService repositoryConfigService;
    private VirtualResolveScopeRunnerImpl runner;
    private SupplierWithError<String, Exception> supplier;
    private SupplierWithError<String, IllegalStateException> throwingSupplier;

    @BeforeEach
    void setUp() {
        repositoryConfigService = mock(InternalRepositoryConfigService.class);
        runner = new VirtualResolveScopeRunnerImpl(repositoryConfigService);
        supplier = () -> "done";
        throwingSupplier = () -> {
            throw new IllegalStateException("boom");
        };
    }

    @Test
    void run_shouldDelegateToRepositoryConfigServiceScopedCache() throws Exception {
        when(repositoryConfigService.runWithVirtualResolveConfigCache("virtual-repo", supplier))
                .thenReturn("done");

        String result = runner.run("virtual-repo", supplier);

        assertEquals("done", result);
        verify(repositoryConfigService).runWithVirtualResolveConfigCache("virtual-repo", supplier);
    }

    @Test
    void run_shouldPropagateException_whenScopedCacheRunnerThrows() throws Exception {
        when(repositoryConfigService.runWithVirtualResolveConfigCache("virtual-repo", throwingSupplier))
                .thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> runner.run("virtual-repo", throwingSupplier));
    }
}
