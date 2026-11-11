package org.artifactory.util;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantIdProviderTest {

    private static final String SHARED_SYSTEM_TECHNICAL_SERVER_NAME = "shared.system.technicalServerName";

    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private SysConfig sysConfig;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AolService aolService;

    private MockedStatic<ContextHelper> contextHelperMock;

    @BeforeEach
    void setUp() {
        ArtifactoryHome.bind(artifactoryHome);
        lenient().when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        lenient().when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        contextHelperMock = mockStatic(ContextHelper.class);
        contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
        lenient().when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        // default to AOL mode; the not-AOL test overrides this
        lenient().when(aolService.isAol()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        contextHelperMock.close();
        ArtifactoryHome.unbind();
        resetCache();
    }

    private void resetCache() throws Exception {
        Field cacheField = TenantIdProvider.class.getDeclaredField("cachedTenantId");
        cacheField.setAccessible(true);
        cacheField.set(null, null);
    }

    @Test
    void getTenantId_returnsEmpty_whenFeatureFlagDisabled() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(false);

        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());

        verify(sysConfig, never()).get(anyString());
    }

    @Test
    void getTenantId_returnsEmpty_whenNotAolMode() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(true);
        when(aolService.isAol()).thenReturn(false);

        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());

        verify(sysConfig, never()).get(anyString());
    }

    @Test
    void getTenantId_returnsTenantId_whenFlagEnabledAndConfigured() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(true);
        String tenantId = "test-tenant-id";
        when(sysConfig.get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME)).thenReturn(Optional.of(tenantId));

        assertEquals(Optional.of(tenantId), TenantIdProvider.getTenantId());
    }

    @Test
    void getTenantId_returnsEmpty_whenFlagEnabledButNotConfigured() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(true);
        when(sysConfig.get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());
    }

    @Test
    void getTenantId_cachesTenantId_afterFirstResolution() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(true);
        String tenantId = "cached-tenant-id";
        when(sysConfig.get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME)).thenReturn(Optional.of(tenantId));

        assertEquals(Optional.of(tenantId), TenantIdProvider.getTenantId());
        assertEquals(Optional.of(tenantId), TenantIdProvider.getTenantId());
        assertEquals(Optional.of(tenantId), TenantIdProvider.getTenantId());

        verify(sysConfig, times(1)).get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME);
    }

    @Test
    void getTenantId_cachesEmptyResult_whenNotConfigured() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.tenantIdHeaderEnabled))
                .thenReturn(true);
        when(sysConfig.get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());
        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());
        assertEquals(Optional.empty(), TenantIdProvider.getTenantId());

        verify(sysConfig, times(1)).get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME);
    }
}
