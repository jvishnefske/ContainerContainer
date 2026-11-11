package org.artifactory.rest.common.service.admin.reverse.proxies;

import org.apache.http.HttpStatus;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.WebServerType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class UpdateReverseProxyServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalArtifactoryContext contextMock;
    @Mock
    private AolService aolService;
    @Mock
    private MutableCentralConfigDescriptor descriptor;
    @Mock
    private CentralConfigService centralConfigService;

    private UpdateReverseProxyService service;

    @BeforeEach
    public void setup() {
        ArtifactoryContextThreadBinder.bind(contextMock);

        when(descriptor.getReverseProxy(any())).thenReturn(new ReverseProxyDescriptor());

        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);

        ConfigValidationService configValidationService = new ConfigValidationService();
        service = new UpdateReverseProxyService(centralConfigService, configValidationService);

        when(contextMock.beanForType(AolService.class)).thenReturn(aolService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void createReverseProxyHappyFlow() {
        ReverseProxyDescriptor proxyDescriptor = createValidReverseProxyDescriptor();
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(proxyDescriptor);
        RestResponse response = new ArtifactoryRestResponse();

        service.execute(request, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
    }

    private static Stream<Parameters> invalidReverseProxyDescriptorModels() {
        return Stream.of(
                new Parameters(m -> m.setKey(null), "key"),
                new Parameters(m -> m.setWebServerType(null), "webServerType"),
                new Parameters(m -> m.setPublicAppContext(null), "publicAppContext"),
                new Parameters(m -> m.setServerName(null), "serverName"),
                new Parameters(m -> m.setArtifactoryServerName(null), "artifactoryServerName"),
                new Parameters(m -> m.setArtifactoryPort(0), "artifactoryPort"),
                new Parameters(m -> m.setDockerReverseProxyMethod(null), "dockerReverseProxyMethod"),
                new Parameters(m -> m.setSslPort(0), "sslPort"),
                new Parameters(m -> m.setHttpPort(0), "httpPort")
        );
    }

    @MethodSource("invalidReverseProxyDescriptorModels")
    @ParameterizedTest
    void whenValidationEnabled(Parameters parameters) {
        ReverseProxyDescriptor proxyDescriptor = createValidReverseProxyDescriptor();
        parameters.transform.accept(proxyDescriptor);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(proxyDescriptor);
        RestResponse response = new ArtifactoryRestResponse();

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> service.execute(request, response)
        );

        assertTrue(thrown.getMessage().contains(parameters.invalidMember));
    }

    @MethodSource("invalidReverseProxyDescriptorModels")
    @ParameterizedTest
    void whenValidationDisabled(Parameters parameters) {
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.centralConfigValidation.getPropertyName(), "false");

        ReverseProxyDescriptor proxyDescriptor = createValidReverseProxyDescriptor();
        parameters.transform.accept(proxyDescriptor);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(proxyDescriptor);
        RestResponse response = new ArtifactoryRestResponse();

        service.execute(request, response);

        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
    }

    private static ReverseProxyDescriptor createValidReverseProxyDescriptor() {
        ReverseProxyDescriptor model = new ReverseProxyDescriptor();
        model.setKey("key");
        model.setWebServerType(WebServerType.NGINX);
        model.setArtifactoryAppContext("artifactoryAppContext");
        model.setPublicAppContext("publicAppContext");
        model.setServerName("serverName");
        model.setServerNameExpression("serverNameExpression");
        model.setArtifactoryServerName("artifactoryServerName");
        model.setUpStreamName("upStreamName");
        model.setArtifactoryPort(8081);
        model.setSslCertificate("sslCertificate");
        model.setSslKey("sslKey");
        model.setDockerReverseProxyMethod(ReverseProxyMethod.SUBDOMAIN);
        model.setUseHttps(true);
        model.setUseHttp(true);
        model.setSslPort(443);
        model.setHttpPort(80);
        return model;
    }

    private record Parameters(Consumer<ReverseProxyDescriptor> transform, String invalidMember) {
    }

}