package org.artifactory.ui.rest.service.admin.configuration.proxies;

import jakarta.annotation.Nonnull;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.configuration.proxy.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class GetProxiesServiceTest {

    @Mock
    private ArtifactoryRestRequest<Boolean> artifactoryRestRequest;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Spy
    private GetProxiesService getProxiesService;

    private AutoCloseable closable;

    @BeforeEach
    public void setUp() {
        closable = MockitoAnnotations.openMocks(this);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(mutableCentralConfigDescriptor.getProxies()).thenReturn(generateProxyDescriptorsWithPasswords());
        when(mutableCentralConfigDescriptor.getProxy(anyString())).thenReturn(getProxyDescriptor(1));
        getProxiesService.setCentralConfigService(centralConfigService);
    }

    private List<ProxyDescriptor> generateProxyDescriptorsWithPasswords() {
        return IntStream.range(0, 3).mapToObj(this::getProxyDescriptor).collect(Collectors.toList());
    }

    @Nonnull
    private ProxyDescriptor getProxyDescriptor(int i) {
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setUsername("username" + i);
        proxyDescriptor.setPassword("password" + i);
        return proxyDescriptor;
    }


    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closable.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void executeMultiProxiesGet(boolean withPassword) {
        RestResponse<Proxy> restResponse = new ArtifactoryRestResponse<>();
        when(artifactoryRestRequest.getImodel()).thenReturn(withPassword);
        getProxiesService.execute(artifactoryRestRequest, restResponse);
        Assertions.assertNull(restResponse.getIModel());
        Collection<Proxy> proxies = restResponse.getiModelList();
        for (Proxy proxy : proxies) {
            if (withPassword) {
                Assertions.assertNotNull(proxy.getPassword());
            } else {
                Assertions.assertNull(proxy.getPassword());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void executeSingleProxiesGet(boolean withPassword) {
        RestResponse<Proxy> restResponse = new ArtifactoryRestResponse<>();
        when(artifactoryRestRequest.getImodel()).thenReturn(withPassword);
        when(artifactoryRestRequest.getPathParamByKey("id")).thenReturn("proxy_id");
        getProxiesService.execute(artifactoryRestRequest, restResponse);
        Assertions.assertNull(restResponse.getiModelList());
        final Proxy proxy = restResponse.getIModel();
        if (withPassword) {
            Assertions.assertNotNull(proxy.getPassword());
        } else {
            Assertions.assertNull(proxy.getPassword());
        }
    }
}