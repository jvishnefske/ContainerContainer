package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.aol.AolService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.CentralOAuthUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthProviderUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIProvidersTypeEnum;
import org.jfrog.access.client.model.OAuthSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


@ExtendWith(MockitoExtension.class)
public class AddOAuthProviderSettingsTest {

    @Mock
    private AccessService accessService;
    @Mock
    private AolService aolService;
    @Mock
    private ArtifactoryHome home;
    private AutoCloseable mocks;
    private MockedStatic<ArtifactoryHome> homeMock;

    @InjectMocks
    AddOAuthProviderSettings setter;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        homeMock = mockStatic(ArtifactoryHome.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
        homeMock.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testExecute(boolean isSaas) {

        String name = "google central";
        String provider = "google";

        OAuthSetting google = OAuthSetting.builder().name(name).useDefaultProxy(true).providerType(provider).build();

        when(aolService.isAol()).thenReturn(isSaas);

        homeMock.when(ArtifactoryHome::get).thenReturn(home);

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        OAuthProviderUIModel oAuthProviderUIModel1 = new OAuthProviderUIModel();
        oAuthProviderUIModel1.setName(name);
        oAuthProviderUIModel1.setProviderType(provider);
        oAuthProviderUIModel1.setUseDefaultProxy(true);

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");

        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder(servletRequest, null, null, null).model(
                        oAuthProviderUIModel1));

        setter.execute(request, response);

        assertEquals(200, response.getResponseCode());
        assertTrue(response.getEntity().toString()
                .contains(isSaas ? "Proxy is not available in Saas" : "Successfully added OAuth provider google"));
        verify(accessService, times(isSaas ? 0 : 1)).upsertOAuthSettings(List.of(google));
        verify(accessService, Mockito.times(1)).getAllOAuthSettings();
    }

    @ParameterizedTest
    @MethodSource("provideAddOAuthProviderTestCases")
    void testAddOAuthProviderValidation(String name, String providerType, String domain, boolean useDefaultProxy,
            boolean isAol, List<OAuthSetting> existingSettings, String expectedError) {

        OAuthProviderUIModel model = new OAuthProviderUIModel();
        model.setName(name);
        model.setProviderType(providerType);
        model.setDomain(domain);
        model.setUseDefaultProxy(useDefaultProxy);

        when(accessService.getAllOAuthSettings()).thenReturn(existingSettings);

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(model));

        ArtifactoryRestResponse response = new ArtifactoryRestResponse<>();

        setter.execute(request, response);

        assertEquals(expectedError, response.getFeedbackMsg().getError());
    }

    private static Stream<Arguments> provideAddOAuthProviderTestCases() {
        return Stream.of(Arguments.of("existingProvider", "github", null, false, false,
                        List.of(OAuthSetting.builder().name("existingProvider").build()),
                        "Couldn't add provider, already exists."),
                Arguments.of(null, "github", null, false, false, List.of(), "Missing provider name"),
                Arguments.of("newProvider", null, null, false, false, List.of(), "Missing provider Type"),
                Arguments.of("newProvider", "invalidType", null, false, false, List.of(), "Invalid provider Type"),
                Arguments.of("newProvider", "facebook", null, false, false, List.of(), "Invalid provider Type"),
                Arguments.of("githubProvider", "github", "", false, false, List.of(),
                        "Organization cannot be empty for github provider settings"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "GOOGLE", "gITHub"})
    void testAddOAuthProviderAcceptsCaseInsensitiveProviderType(String providerType) {
        when(aolService.isAol()).thenReturn(false);
        when(accessService.getAllOAuthSettings()).thenReturn(List.of());
        homeMock.when(ArtifactoryHome::get).thenReturn(home);

        OAuthProviderUIModel model = new OAuthProviderUIModel();
        model.setName("provider-" + providerType);
        model.setProviderType(providerType);
        if (OAuthUIProvidersTypeEnum.github.name().equalsIgnoreCase(providerType)) {
            model.setDomain("jfrog.com");
        }

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(model));

        ArtifactoryRestResponse response = new ArtifactoryRestResponse<>();

        setter.execute(request, response);

        assertEquals(200, response.getResponseCode());
        verify(accessService, times(1)).upsertOAuthSettings(Mockito.anyList());
    }
}
