package org.artifactory.rest.resource.system;

import org.artifactory.aol.AolService;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ImportServiceImpl;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Alexei Vainshtein
 */
class ImportResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AolService aolService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = openMocks(this);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(securityService.isAdmin()).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testActivateImport(boolean shouldActOnMissionControl) {
        ImportSettingsConfigurationImpl settings = new ImportSettingsConfigurationImpl();
        settings.importMissionControl = shouldActOnMissionControl;
        settings.importPath = "path";
        ImportResource importResource = new ImportResource();
        importResource.importService = new ImportServiceImpl();
        doAnswer(invocationOnMock -> {
            ImportSettingsImpl argument = invocationOnMock.getArgument(0, ImportSettingsImpl.class);
            assertEquals(argument.shouldActOnMissionControl(), shouldActOnMissionControl);
            return null;
        }).when(context).importFrom(any(ImportSettings.class));
        importResource.httpResponse = mock(HttpServletResponse.class);
        try (Response response = importResource.activateImport(settings)) {
            assertEquals(SC_OK, response.getStatus());
            verify(context, times(1)).importFrom(any(ImportSettings.class));
        }
    }

    @Test
    void testImportRepositories() throws IOException {
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ImportResource importResource = new ImportResource();
        importResource.repositoryService = repositoryService;
        importResource.securityService = securityService;
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        when(httpServletResponse.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        importResource.httpResponse = httpServletResponse;
        importResource.importRepositories(System.getProperty("java.io.tmpdir"), "", "", "");
        verify(repositoryService, times(1)).importAll(any(ImportSettingsImpl.class));
    }

    @Test
    void testActivateImportHomePath() {
        ImportSettingsConfigurationImpl settings = new ImportSettingsConfigurationImpl();
        settings.importPath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath();
        ImportResource importResource = new ImportResource();
        try (Response response = importResource.activateImport(settings)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }

    @Test
    void testImportRepositoriesHomePath() {
        String path = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath();
        ImportResource importResource = new ImportResource();
        assertThrows(BadRequestException.class, () -> importResource.importRepositories(path, "", "", ""));
    }
}
