package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.client.config.RequestConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.bearer.TestRemoteRepositoryUrlBearerScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class RemoteRepositoryProviderTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenAuthentication(boolean enabled) {
        ArtifactWebstartAddon webstartAddon = mock(ArtifactWebstartAddon.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(webstartAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        DenyListCacheHolderService denyListCacheHolderService = new DenyListCacheHolderService();
        when(context.beanForType(DenyListCacheHolderService.class)).thenReturn(denyListCacheHolderService);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(context.getCentralConfig()).thenReturn(centralConfigService);

        RemoteRepositoryConfigModel repositoryModel = getRemoteRepositoryConfigModel(enabled);

        HttpClientConfigurator configurator =
                RemoteRepositoryProvider.getHttpClientConfigurator("https://entplus.jfrog.io/ui/pipelines/",
                        repositoryModel, true);

        RequestConfig requestConfig = configurator.getConfig().build();
        Collection<String> authSchemes = requestConfig.getTargetPreferredAuthSchemes();
        if (enabled) {
            assertTrue(authSchemes.contains("Bearer"));
        } else {
            assertNull(authSchemes);
        }
    }

    private static RemoteRepositoryConfigModel getRemoteRepositoryConfigModel(boolean enabled) {
        RemoteRepositoryConfigModel repositoryModel = new RemoteRepositoryConfigModel();
        GeneralRepositoryConfigModel generalRepositoryConfigModel = new GeneralRepositoryConfigModel();
        generalRepositoryConfigModel.setRepoKey("repoKey");
        repositoryModel.setGeneral(generalRepositoryConfigModel);
        GenericTypeSpecificConfigModel typeSpecificConfigModel = new GenericTypeSpecificConfigModel();
        typeSpecificConfigModel.setEnableTokenAuthentication(enabled);
        repositoryModel.setTypeSpecific(typeSpecificConfigModel);
        RemoteNetworkRepositoryConfigModel remoteNetworkRepositoryConfigModel =
                new RemoteNetworkRepositoryConfigModel();
        remoteNetworkRepositoryConfigModel.setSelectedInstalledCertificate("certificate");
        RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel =
                new RemoteAdvancedRepositoryConfigModel();
        remoteAdvancedRepositoryConfigModel.setNetwork(remoteNetworkRepositoryConfigModel);
        repositoryModel.setAdvanced(remoteAdvancedRepositoryConfigModel);
        return repositoryModel;
    }

    @Test
    void testBearerScheme() {
        TestRemoteRepositoryUrlBearerScheme scheme = new TestRemoteRepositoryUrlBearerScheme("token");
        assertEquals("token", scheme.getToken(null, null, null));
    }
}