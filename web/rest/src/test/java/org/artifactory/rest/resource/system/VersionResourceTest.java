package org.artifactory.rest.resource.system;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.VersionResult;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.artifactory.common.ConstantValues.systemVersionEndpointDisabledForAnonymous;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author avivbr
 */
class VersionResourceTest extends ArtifactoryHomeBoundJUnitTest {

    private final List<EntitlementType> replicationEntitlements =
            List.of(EntitlementType.REPO_REPLICATION,
                    EntitlementType.MULTIPUSH_REPLICATION,
                    EntitlementType.EVENT_BASED_PULL_REPLICATION,
                    EntitlementType.SMART_REMOTE_TARGET_FOR_EDGE);
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private SecurityService securityService;
    private VersionResource tester;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        this.tester = spy(new VersionResource());
        this.tester.setCentralConfigService(centralConfigService);
        this.tester.setAddonsManager(addonsManager);
        this.tester.setSecurityService(securityService);
        this.tester.setHttpHeaders(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        this.autoCloseable.close();
    }

    @Test
    void getArtifactoryServiceVersion() {
        String SERVICE_VERSION = "2.2.2", SERVICE_REVISION = "222", SERVICE_PACKAGE_HANDLER_VERSION = "2.500.0";
        VersionInfo versionInfo = new VersionInfo(SERVICE_VERSION, SERVICE_REVISION, SERVICE_PACKAGE_HANDLER_VERSION);
        when(centralConfigService.getServiceVersionInfo()).thenReturn(versionInfo);
        this.setStringSystemProperty(systemVersionEndpointDisabledForAnonymous, "FALSE");
        when(addonsManager.getInstalledAddons(any())).thenReturn(List.of());
        when(addonsManager.getLicenseKeyHash(anyBoolean())).thenReturn("");
        when(addonsManager.isEntitled(any())).thenReturn(false);

        Map<String, String> servicesVersions = Map.of(
                "package_handler_version", SERVICE_PACKAGE_HANDLER_VERSION);
        Map<String, Boolean> expectedEntitlements = new HashMap<>();
        expectedEntitlements.put("REPO_REPLICATION", false);
        expectedEntitlements.put("MULTIPUSH_REPLICATION", false);
        expectedEntitlements.put("EVENT_BASED_PULL_REPLICATION", false);
        expectedEntitlements.put("SMART_REMOTE_TARGET_FOR_EDGE", false);

        VersionResult versionResult = new VersionResult(versionInfo.getVersion(),
                versionInfo.getRevision(),
                servicesVersions,
                List.of(), null, expectedEntitlements);
        when(addonsManager.getVersionResult(any(), any(), anyBoolean())).thenReturn(versionResult);

        VersionRestResult expected =
                getVersionRestResultWithVersionsOnly(SERVICE_VERSION, SERVICE_REVISION,
                        SERVICE_PACKAGE_HANDLER_VERSION);

        Response response = tester.getArtifactoryServiceVersion();

        verify(tester, times(1)).getVersionRestResult(versionInfo);
        verify(addonsManager, times(1)).getVersionResult(versionInfo, replicationEntitlements, false);

        Assertions.assertEquals(SC_OK, response.getStatus());
        Assertions.assertEquals(expected, response.getEntity());
        Assertions.assertEquals(expected.toString(), response.getEntity().toString());
    }

    @Test
    public void getArtifactoryVersion() {
        String ARTIFACTORY_VERSION = "7.69", ARTIFACTORY_REVISION = "77";
        VersionInfo versionInfo = new VersionInfo(ARTIFACTORY_VERSION, ARTIFACTORY_REVISION);

        when(centralConfigService.getProductVersionInfo()).thenReturn(versionInfo);

        this.setStringSystemProperty(systemVersionEndpointDisabledForAnonymous, "FALSE");
        when(addonsManager.getInstalledAddons(any())).thenReturn(List.of());
        when(addonsManager.getLicenseKeyHash(anyBoolean())).thenReturn("");
        when(addonsManager.isEntitled(any())).thenReturn(false);
        VersionResult versionResult = mock(VersionResult.class);
        when(addonsManager.getVersionResult(versionInfo, replicationEntitlements, false)).thenReturn(versionResult);

        tester.getArtifactoryVersion();

        verify(tester, times(1)).getVersionRestResult(versionInfo);
        verify(addonsManager, times(1)).getVersionResult(versionInfo, replicationEntitlements, false);
    }

    @Nonnull
    private static VersionRestResult getVersionRestResultWithVersionsOnly(String SERVICE_VERSION,
            String SERVICE_REVISION,
            String SERVICE_PACKAGE_HANDLER_VERSION) {
        Map<String, Boolean> expectedEntitlements = new HashMap<>();
        expectedEntitlements.put("REPO_REPLICATION", false);
        expectedEntitlements.put("MULTIPUSH_REPLICATION", false);
        expectedEntitlements.put("EVENT_BASED_PULL_REPLICATION", false);
        expectedEntitlements.put("SMART_REMOTE_TARGET_FOR_EDGE", false);

        Map<String, String> servicesVersions = Map.of("package_handler_version", SERVICE_PACKAGE_HANDLER_VERSION);

        return new VersionRestResult(SERVICE_VERSION, SERVICE_REVISION, servicesVersions, List.of(), null,
                expectedEntitlements);
    }

    @ParameterizedTest
    @MethodSource("checkAnonymousAccessNotAllowedParams")
    void checkAnonymousAccessNotAllowed(String name, boolean endpointDisabledForAnonymous, boolean isAnonymous,
            boolean exception) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.systemVersionEndpointDisabledForAnonymous.getPropertyName(),
                        Boolean.toString(endpointDisabledForAnonymous));
        when(securityService.isAnonAccessEnabled()).thenReturn(isAnonymous);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        when(authentication.getName()).thenReturn(name);

        SecurityContext securityContext = mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        if (exception) {
            assertThrows(AuthorizationRestException.class, () -> tester.checkAnonymousAccessNotAllowed());
        } else {
            assertDoesNotThrow(() -> tester.checkAnonymousAccessNotAllowed());
        }
    }

    public static Stream<Arguments> checkAnonymousAccessNotAllowedParams() {
        return Stream.of(
                Arguments.of("", true, false, false),
                Arguments.of(null, true, false, false),
                Arguments.of("asda:122", true, false, false),
                Arguments.of(UserInfo.ANONYMOUS, true, true, true),
                Arguments.of("token:" + UserInfo.ANONYMOUS, true, true, true),
                Arguments.of("token:saddkasnudnaoin", true, false, false),
                Arguments.of("", false, false, false),
                Arguments.of(null, false, false, false),
                Arguments.of("asda:122", false, false, false),
                Arguments.of(UserInfo.ANONYMOUS, false, true, false),
                Arguments.of("token:" + UserInfo.ANONYMOUS, false, true, false),
                Arguments.of("token:saddkasnudnaoin", false, false, false)
        );
    }
}