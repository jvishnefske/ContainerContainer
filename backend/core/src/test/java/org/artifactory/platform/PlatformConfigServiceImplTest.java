package org.artifactory.platform;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.openMocks;

public class PlatformConfigServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AccessService accessService;
    @Mock
    private GrpcPlatformConfigClient grpcPlatformConfigClient;
    @InjectMocks
    private PlatformConfigServiceImpl platformConfigService;
    private AutoCloseable mocks;
    private PlatformGeneralConfig expectedCGeneralConfig;
    private PlatformSystemMessageConfig expectedSystemMessageConfig;

    @BeforeEach
    public void beforeEach() {
        mocks = openMocks(this);
        doReturn(accessService).when(artifactoryContext).beanForType(AccessService.class);
        doReturn(grpcPlatformConfigClient).when(accessService).getPlatformConfigClient();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        expectedCGeneralConfig = PlatformGeneralConfig.builder()
                .dateFormat("dd-MM-yy")
                .helpLinksEnabled(false)
                .logo("http://some.logo.com")
                .build();
        expectedSystemMessageConfig = PlatformSystemMessageConfig.builder()
                .systemMessageEnabled(true)
                .systemMessage("Hello World!")
                .systemMessageTitle("COOL")
                .systemMessageTitleColor("#429f47")
                .build();
    }

    @AfterEach
    public void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(grpcPlatformConfigClient);
        mocks.close();
    }

    @Test
    void testGetPlatformGeneralConfigEmpty() {
        doReturn(Optional.empty()).when(grpcPlatformConfigClient)
                .getConfig(anyString(), eq(PlatformGeneralConfig.class));
        PlatformGeneralConfig actualConfig = platformConfigService.getPlatformGeneralConfig();
        assertEquals("dd-MM-yy HH:mm:ss z", actualConfig.getDateFormat());
        assertTrue(actualConfig.isHelpLinksEnabled());
        assertNull(actualConfig.getLogo());
        verify(grpcPlatformConfigClient, times(1)).getConfig(anyString(), eq(PlatformGeneralConfig.class));
    }

    @Test
    void testGetPlatformGeneralConfig() {
        doReturn(Optional.of(expectedCGeneralConfig)).when(grpcPlatformConfigClient)
                .getConfig(anyString(), eq(PlatformGeneralConfig.class));
        PlatformGeneralConfig actualConfig = platformConfigService.getPlatformGeneralConfig();
        assertEquals(expectedCGeneralConfig.getDateFormat(), actualConfig.getDateFormat());
        assertEquals(expectedCGeneralConfig.isHelpLinksEnabled(), actualConfig.isHelpLinksEnabled());
        assertEquals(expectedCGeneralConfig.getLogo(), actualConfig.getLogo());
        verify(grpcPlatformConfigClient, times(1)).getConfig(anyString(), eq(PlatformGeneralConfig.class));
    }

    @Test
    void testGetPlatformSystemMessageConfigEmpty() {
        doReturn(Optional.empty()).when(grpcPlatformConfigClient)
                .getConfig(anyString(), eq(PlatformSystemMessageConfig.class));
        PlatformSystemMessageConfig actualConfig = platformConfigService.getPlatformSystemMessageConfig();
        assertFalse(actualConfig.isSystemMessageEnabled());
        assertNull(actualConfig.getSystemMessage());
        assertNull(actualConfig.getSystemMessageTitle());
        assertEquals("#429f46", actualConfig.getSystemMessageTitleColor());
        verify(grpcPlatformConfigClient, times(1)).getConfig(anyString(), eq(PlatformSystemMessageConfig.class));
    }

    @Test
    void testGetPlatformSystemMessageConfig() {
        doReturn(Optional.of(expectedSystemMessageConfig)).when(grpcPlatformConfigClient)
                .getConfig(anyString(), eq(PlatformSystemMessageConfig.class));
        PlatformSystemMessageConfig actualConfig = platformConfigService.getPlatformSystemMessageConfig();
        assertEquals(expectedSystemMessageConfig.isSystemMessageEnabled(), actualConfig.isSystemMessageEnabled());
        assertEquals(expectedSystemMessageConfig.getSystemMessage(), actualConfig.getSystemMessage());
        assertEquals(expectedSystemMessageConfig.getSystemMessageTitle(), actualConfig.getSystemMessageTitle());
        assertEquals(expectedSystemMessageConfig.getSystemMessageTitleColor(),
                actualConfig.getSystemMessageTitleColor());
        verify(grpcPlatformConfigClient, times(1)).getConfig(anyString(), eq(PlatformSystemMessageConfig.class));
    }

    @Test
    void testUpdatePlatformGeneralConfig() {
        platformConfigService.updatePlatformGeneralConfig("dd-MM-yy", false, "http://some.logo.com");
        verify(grpcPlatformConfigClient, times(1))
                .updateConfig(anyString(), any(), eq(PlatformGeneralConfig.class), eq(false));
    }

    @Test
    void testUpdatePlatformSystemMessageConfig() {
        platformConfigService.updatePlatformSystemMessageConfig(true, "Hello World!", "#429f46", "COOL");
        verify(grpcPlatformConfigClient, times(1))
                .updateConfig(anyString(), any(), eq(PlatformSystemMessageConfig.class), eq(false));
    }

    @Test
    @Disabled("Flaky")
    void testUpdatePlatformConfigIfNeeded() {
        platformConfigService.updatePlatformGeneralConfig(expectedCGeneralConfig.getDateFormat(),
                expectedCGeneralConfig.isHelpLinksEnabled(), expectedCGeneralConfig.getLogo());
        platformConfigService.updatePlatformSystemMessageConfig(expectedSystemMessageConfig.isSystemMessageEnabled(),
                expectedSystemMessageConfig.getSystemMessageTitle(),
                expectedSystemMessageConfig.getSystemMessageTitleColor(),
                expectedSystemMessageConfig.getSystemMessage());
        platformConfigService.updatePlatformConfigIfNeeded();
        verify(grpcPlatformConfigClient, times(1))
                .updateConfig(anyString(), any(), eq(PlatformGeneralConfig.class), eq(false));
        verify(grpcPlatformConfigClient, times(1))
                .updateConfig(anyString(), any(), eq(PlatformSystemMessageConfig.class), eq(false));
    }
}
