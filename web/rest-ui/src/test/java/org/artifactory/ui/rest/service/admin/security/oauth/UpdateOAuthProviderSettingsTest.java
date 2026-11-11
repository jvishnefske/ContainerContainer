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
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class UpdateOAuthProviderSettingsTest {

    @Mock
    private AccessService accessService;
    @Mock
    private AolService aolService;
    private AutoCloseable mocks;
    private MockedStatic<ArtifactoryHome> homeMock;

    @InjectMocks
    UpdateOAuthProviderSettings updater;
    @Mock
    private ArtifactoryHome home;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
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

        OAuthSetting google = OAuthSetting.builder().name(name).providerType(provider).useDefaultProxy(false).build();

        when(aolService.isAol()).thenReturn(isSaas);
        when(accessService.getOAuthSetting(name)).thenReturn(Optional.ofNullable(google));
        homeMock.when(ArtifactoryHome::get).thenReturn(home);

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        OAuthProviderUIModel oAuthProviderUIModel1 = new OAuthProviderUIModel();
        oAuthProviderUIModel1.setName(name);
        oAuthProviderUIModel1.setUseDefaultProxy(true);
        oAuthProviderUIModel1.setProviderType(provider);

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");

        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder(servletRequest, null, null, null).model(
                        oAuthProviderUIModel1));

        updater.execute(request, response);

        assertEquals(response.getResponseCode(), 200);
        Mockito.verify(accessService, Mockito.times(1)).getOAuthSetting(name);
        Mockito.verify(accessService, Mockito.times(isSaas ? 0 : 1))
                .upsertOAuthSettings(List.of(google.toBuilder().useDefaultProxy(true).build()));
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void testOAuthProviderValidation(String name, String providerType, String domain,
            Optional<OAuthSetting> existingSetting, String expectedError) {
        OAuthProviderUIModel model = new OAuthProviderUIModel();
        model.setName(name);
        model.setProviderType(providerType);
        model.setDomain(domain);

        when(accessService.getOAuthSetting(name)).thenReturn(existingSetting);

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(model));

        ArtifactoryRestResponse response = new ArtifactoryRestResponse<>();

        updater.execute(request, response);

        assertEquals(expectedError, response.getFeedbackMsg().getError());
    }

    private static Stream<Arguments> provideTestCases() {
        return Stream.of(
                Arguments.of("nonexistent", "github", null, Optional.empty(),
                        "Couldn't update provider, not exist."),
                Arguments.of("github", null, null, Optional.of(
                                OAuthSetting.builder().name("github")
                                        .providerType("github").domain("existingDomain").build()),
                        "Missing provider Type"),
                Arguments.of("github", "invalidType", null, Optional.of(
                                OAuthSetting.builder().name("github")
                                        .providerType("github").domain("existingDomain").build()),
                        "Invalid provider Type"),
                Arguments.of("github", "facebook", null, Optional.of(
                                OAuthSetting.builder().name("github")
                                        .providerType("github").domain("existingDomain").build()),
                        "Invalid provider Type"),
                Arguments.of("github", "github", "", Optional.of(
                                OAuthSetting.builder().name("github")
                                        .providerType("github").domain("existingDomain").build()),
                        "Organization cannot be empty for github provider settings"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "GOOGLE", "gITHub"})
    void testUpdateOAuthProviderAcceptsCaseInsensitiveProviderType(String providerType) {
        String name = "google central";
        OAuthSetting existing = OAuthSetting.builder().name(name).providerType("google").useDefaultProxy(false).build();
        if (OAuthUIProvidersTypeEnum.github.name().equalsIgnoreCase(providerType)) {
            name = "github central";
            existing = OAuthSetting.builder().name(name).providerType("github").domain("jfrog.com")
                    .useDefaultProxy(false).build();
        }

        when(aolService.isAol()).thenReturn(false);
        when(accessService.getOAuthSetting(name)).thenReturn(Optional.of(existing));
        homeMock.when(ArtifactoryHome::get).thenReturn(home);

        OAuthProviderUIModel model = new OAuthProviderUIModel();
        model.setName(name);
        model.setProviderType(providerType);
        model.setUseDefaultProxy(false);
        if (OAuthUIProvidersTypeEnum.github.name().equalsIgnoreCase(providerType)) {
            model.setDomain("jfrog.com");
        }

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<OAuthProviderUIModel> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(model));

        ArtifactoryRestResponse response = new ArtifactoryRestResponse<>();

        updater.execute(request, response);

        assertEquals(200, response.getResponseCode());
        Mockito.verify(accessService, Mockito.times(1)).upsertOAuthSettings(Mockito.anyList());
    }
}
