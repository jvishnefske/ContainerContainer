package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageHeavyQueryCacheConfig;
import com.jfrog.ph.acl.service.PackageHeavyQueryCacheService;
import org.jfrog.storage.hqc.model.HeavyQueryCacheConfig;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class PackageHeavyQueryCacheServiceImplTest {
    private static final String ENTRY_KEY = "entryKey";
    private static final String HQC_TYPE = "hqcType";

    @Mock
    private HeavyQueryCacheService heavyQueryCacheService;
    @Mock
    private Callable<String> callable;
    @Mock
    private PackageHeavyQueryCacheConfig hqcConfig;
    private AutoCloseable closeable;
    private PackageHeavyQueryCacheService packageHeavyQueryCacheService;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        packageHeavyQueryCacheService = new PackageHeavyQueryCacheServiceImpl(heavyQueryCacheService);
        when(hqcConfig.getHqcType()).thenReturn(HQC_TYPE);
        when(hqcConfig.getEntryKey()).thenReturn(ENTRY_KEY);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void get() {
        packageHeavyQueryCacheService.get(callable, String.class, hqcConfig);
        verify(heavyQueryCacheService, times(1)).get(eq(callable), eq(String.class), eq(ENTRY_KEY), eq(HQC_TYPE),
                any(HeavyQueryCacheConfig.class));
    }

    @Test
    void getParams() {
        Assertions.assertThrows(NullPointerException.class,
                () -> packageHeavyQueryCacheService.get(null, String.class, hqcConfig));
        verify(heavyQueryCacheService, times(0)).get(any(), any(), any(), any(), any());
        Assertions.assertThrows(NullPointerException.class,
                () -> packageHeavyQueryCacheService.get(callable, null, hqcConfig));
        verify(heavyQueryCacheService, times(0)).get(any(), any(), any(), any(), any());
        Assertions.assertThrows(NullPointerException.class,
                () -> packageHeavyQueryCacheService.get(callable, String.class, null));
        verify(heavyQueryCacheService, times(0)).get(any(), any(), any(), any(), any());
        Assertions.assertDoesNotThrow(
                () -> packageHeavyQueryCacheService.get(callable, String.class, hqcConfig));
        verify(heavyQueryCacheService, times(1)).get(eq(callable), eq(String.class), eq(ENTRY_KEY), eq(HQC_TYPE),
                any(HeavyQueryCacheConfig.class));
    }

    @Test
    void evict() {
        packageHeavyQueryCacheService.evict(HQC_TYPE, "args");
        verify(heavyQueryCacheService, times(1)).evict(HQC_TYPE, "args");
    }

    @Test
    void evictParams() {
        Assertions.assertThrows(NullPointerException.class,
                () -> packageHeavyQueryCacheService.evict(null, ENTRY_KEY));
        verify(heavyQueryCacheService, times(0)).evict(HQC_TYPE, ENTRY_KEY);
        Assertions.assertDoesNotThrow(
                () -> packageHeavyQueryCacheService.evict(HQC_TYPE, ENTRY_KEY));
        verify(heavyQueryCacheService, times(1)).evict(HQC_TYPE, ENTRY_KEY);
    }

}