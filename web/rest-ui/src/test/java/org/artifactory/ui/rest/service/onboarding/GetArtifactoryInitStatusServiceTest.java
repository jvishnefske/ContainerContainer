package org.artifactory.ui.rest.service.onboarding;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.eula.EulaService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.system.FirstStartupHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.onboarding.ArtifactoryInitStatusModel;
import org.artifactory.util.AolUtils;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author gavriels
 */
@Test
public class GetArtifactoryInitStatusServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private CentralConfigService configService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AccessService accessService;
    @Mock
    private EulaService eulaService;
    @Mock
    private ArtifactoryCommonDbPropertiesService artifactoryCommonDbPropertiesService;
    @Mock
    private CentralConfigDescriptor configDescriptor;

    private GetArtifactoryInitStatusService getArtifactoryInitStatusService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void initMocks() {
        mocks = MockitoAnnotations.openMocks(this);
        getArtifactoryInitStatusService = new GetArtifactoryInitStatusService(
                addonsManager,
                configService,
                accessService,
                eulaService,
                artifactoryCommonDbPropertiesService);

        when(addonsManager.isLicenseInstalled()).thenReturn(true);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getProxies()).thenReturn(Collections.emptyList());
        when(eulaService.isRequired()).thenReturn(false);
        when(artifactoryCommonDbPropertiesService.getAllDbVersionInfo()).thenReturn(Collections.emptyList());
    }

    @AfterMethod
    public void closeMocks() throws Exception {
        mocks.close();
    }

    @Test
    public void testShouldSkipOnboardingWizard() {
        List<DbVersionInfo> dbVersionInfos = createDbVersionListForTest();
        when(artifactoryCommonDbPropertiesService.getAllDbVersionInfo()).thenReturn(dbVersionInfos.subList(0, 2));
        Assert.assertFalse(getArtifactoryInitStatusService.shouldSkipOnboardingWizard());
        when(artifactoryCommonDbPropertiesService.getAllDbVersionInfo()).thenReturn(dbVersionInfos.subList(0, 3));
        Assert.assertTrue(getArtifactoryInitStatusService.shouldSkipOnboardingWizard());
        dbVersionInfos.remove(2);
        when(artifactoryCommonDbPropertiesService.getAllDbVersionInfo()).thenReturn(dbVersionInfos);
        Assert.assertTrue(getArtifactoryInitStatusService.shouldSkipOnboardingWizard());
    }

    /**
     * The onboarding state reports {@code initialSetupRequired} from the bootstrap state and never probes Access
     * with a default password (IA1).
     */
    @Test
    public void executeReportsInitialSetupRequiredWithoutAnyPasswordProbe() {
        for (boolean required : new boolean[] {true, false}) {
            try (MockedStatic<AuthenticationHelper> authMock =
                        org.mockito.Mockito.mockStatic(AuthenticationHelper.class);
                    MockedStatic<FirstStartupHelper> firstStartupMock =
                            org.mockito.Mockito.mockStatic(FirstStartupHelper.class)) {
                authMock.when(AuthenticationHelper::getAuthentication).thenReturn(authenticatedUser("admin"));
                firstStartupMock.when(FirstStartupHelper::onlyDefaultReposExist).thenReturn(false);
                when(accessService.isInitialSetupRequired()).thenReturn(required);

                RestResponse response = executeService();

                ArtifactoryInitStatusModel model = (ArtifactoryInitStatusModel) response.getIModel();
                Assert.assertNotNull(model);
                Assert.assertEquals(model.isInitialSetupRequired(), required);
                verify(accessService, never()).getAccessClient();
            }
        }
    }

    /**
     * Default flag is enabled: anonymous callers must be rejected with 401 and no model emitted, regardless of
     * the tenant's anonymous-access setting.
     */
    @Test
    public void executeRejectsAnonymousCallerWithUnauthorizedWhenFlagOn() {
        try (MockedStatic<AolUtils> aolMock = org.mockito.Mockito.mockStatic(AolUtils.class);
                MockedStatic<AuthenticationHelper> authMock =
                        org.mockito.Mockito.mockStatic(AuthenticationHelper.class)) {
            aolMock.when(AolUtils::isAol).thenReturn(false);
            authMock.when(AuthenticationHelper::getAuthentication).thenReturn(authenticatedUser(UserInfo.ANONYMOUS));

            RestResponse response = executeService();

            Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);
            Assert.assertNull(response.getIModel(),
                    "no init-status model should be emitted for anonymous callers");
            verify(accessService, never()).getAccessClient();
            verify(addonsManager, never()).isLicenseInstalled();
        }
    }

    /**
     * Same as above but with no authentication on the security context — still treated as anonymous.
     */
    @Test
    public void executeRejectsNullAuthenticationWithUnauthorizedWhenFlagOn() {
        try (MockedStatic<AolUtils> aolMock = org.mockito.Mockito.mockStatic(AolUtils.class);
                MockedStatic<AuthenticationHelper> authMock =
                        org.mockito.Mockito.mockStatic(AuthenticationHelper.class)) {
            aolMock.when(AolUtils::isAol).thenReturn(false);
            authMock.when(AuthenticationHelper::getAuthentication).thenReturn(null);

            RestResponse response = executeService();

            Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);
            Assert.assertNull(response.getIModel());
        }
    }

    /**
     * Opt-out: when the flag is turned off, anonymous callers fall through to the normal flow (back-compat).
     */
    @Test
    public void executeAllowsAnonymousCallerWhenFlagOff() {
        setStringSystemProperty(ConstantValues.onboardingInitStatusRequireAuth, "false");
        try (MockedStatic<AolUtils> aolMock = org.mockito.Mockito.mockStatic(AolUtils.class);
                MockedStatic<AuthenticationHelper> authMock =
                        org.mockito.Mockito.mockStatic(AuthenticationHelper.class);
                MockedStatic<FirstStartupHelper> firstStartupMock =
                        org.mockito.Mockito.mockStatic(FirstStartupHelper.class)) {
            aolMock.when(AolUtils::isAol).thenReturn(false);
            authMock.when(AuthenticationHelper::getAuthentication).thenReturn(authenticatedUser(UserInfo.ANONYMOUS));
            firstStartupMock.when(FirstStartupHelper::onlyDefaultReposExist).thenReturn(false);

            RestResponse response = executeService();

            Assert.assertNotEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);
            Assert.assertNotNull(response.getIModel(),
                    "with the flag off the normal model should still be emitted for anonymous callers");
        }
    }

    private RestResponse executeService() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        getArtifactoryInitStatusService.execute(request, response);
        return response;
    }

    private static Authentication authenticatedUser(String username) {
        return new UsernamePasswordAuthenticationToken(username, "");
    }

    private List<DbVersionInfo> createDbVersionListForTest() {
        return new ArrayList<>(
                Arrays.asList(
                        new DbVersionInfo(new Date().getTime(),
                                "7.20.0",
                                72000900,
                                1111, "7.21.0"),
                        new DbVersionInfo(new Date().getTime(),
                                ArtifactoryServiceVersionProvider.v7100m001.get().getVersion(),
                                (int) ArtifactoryServiceVersionProvider.v7100m001.get().getRevision(),
                                1111, "7.21.0"),
                        new DbVersionInfo(new Date().getTime(),
                                ArtifactoryServiceVersionProvider.v760.get().getVersion(),
                                (int) ArtifactoryServiceVersionProvider.v760.get().getRevision(),
                                1111, "7.21.0"),
                        new DbVersionInfo(new Date().getTime(),
                                "7.8.15",
                                70815900,
                                1111, "7.21.0"))
        );
    }
}
