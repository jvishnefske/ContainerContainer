package org.artifactory.config;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.oauth.OAuthProvidersTypeEnum;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class ArtifactorySystemConfigImplTest {

    private static final String KEY_CENTRAL_OAUTH_PROVIDERS = "artifactory.security.centralOAuth.providers";

    @Mock
    private SysConfig sysConfig;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @DataProvider
    public static Object[][] centralOAuthProvider() {
        return new Object[][] {
                {OAuthProvidersTypeEnum.google.name(), "", "", "", "", "", "", "", false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "", "", "", "", "", "", false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "", "", "", "", "", false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "", "", "", "", false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "apiUrl", "", "", "", false, true,
                        false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "apiUrl", "authUrl", "", "",
                        false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "apiUrl", "authUrl", "tokenUrl",
                        "", false, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "apiUrl", "authUrl", "tokenUrl",
                        "secret", true, true, false},
                {OAuthProvidersTypeEnum.google.name(), "name", "id", "basicUrl", "apiUrl", "authUrl", "tokenUrl",
                        "secret", true, true, true},
                {"nonExistingProvider", "name", "id", "basicUrl", "apiUrl", "authUrl", "tokenUrl", "secret", false,
                        true, false},
        };
    }

    @Test(dataProvider = "centralOAuthProvider")
    public void testGetCentralOAuthDefaultConfig(String providerType, String name, String id, String basicUrl,
            String apiUrl, String authUrl, String tokenUrl, String secret, boolean expectEntity, boolean pkce,
            boolean useDefaultProxy) {
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "name"), ""))
                .thenReturn(name);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "id"), ""))
                .thenReturn(id);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "basicUrl"), ""))
                .thenReturn(basicUrl);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "apiUrl"), ""))
                .thenReturn(apiUrl);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "authUrl"), ""))
                .thenReturn(authUrl);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "tokenUrl"), ""))
                .thenReturn(tokenUrl);
        Mockito.when(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "secret"), ""))
                .thenReturn(secret);
        Mockito.when(sysConfig.getBoolean(getCentralOAuthProviderConfigKey(providerType, "pkce"), false))
                .thenReturn(pkce);
        Mockito.when(sysConfig.getBoolean(getCentralOAuthProviderConfigKey(providerType, "useDefaultProxy"), false))
                .thenReturn(useDefaultProxy);

        ArtifactorySystemConfig artifactorySystemConfig = new ArtifactorySystemConfigImpl(sysConfig);
        CentralOAuthSettings got = artifactorySystemConfig.getCentralOAuthDefaultConfig();

        CentralOAuthSettings expected = new CentralOAuthSettings(false, List.of());
        if (expectEntity) {
            OAuthProviderSettings expectedProvider = new OAuthProviderSettings();
            expectedProvider.setName(name);
            expectedProvider.setId(id);
            expectedProvider.setBasicUrl(basicUrl);
            expectedProvider.setApiUrl(apiUrl);
            expectedProvider.setAuthUrl(authUrl);
            expectedProvider.setTokenUrl(tokenUrl);
            expectedProvider.setSecret(secret);
            expectedProvider.setProviderType(providerType);
            expectedProvider.setCentral(true);
            expectedProvider.setPkce(pkce);
            expectedProvider.setUseDefaultProxy(useDefaultProxy);

            expected.setProviders(List.of(expectedProvider));
        }

        assertEquals(expected, got);
    }

    private String getCentralOAuthProviderConfigKey(String providerType, String property) {
        return String.format("%s.%s.%s", KEY_CENTRAL_OAUTH_PROVIDERS, providerType, property);
    }
}