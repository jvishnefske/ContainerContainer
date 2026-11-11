package org.artifactory.ui.rest.resource.utils.repositories;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.service.utils.UtilsServiceFactory;
import org.artifactory.ui.rest.service.utils.repositories.AutoCompleteRepositoriesServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_CONTINUE_STATE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PACKAGE_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_REPO_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_SEARCH_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoCompleteRepositoriesDataResourceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private UtilsServiceFactory utilsServiceFactory;
    private AutoCompleteRepositoriesServiceImpl service;
    @Spy
    private AutoCompleteRepositoriesDataResource resource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resource.setUtilsServiceFactory(utilsServiceFactory);
        service = new AutoCompleteRepositoriesServiceImpl();
        doReturn(mock(Response.class)).when(resource).runService(any());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testGetAutoCompleteServiceRun() {
        resource.getAutoCompleteRepoData(
                "prefix", List.of(""), List.of(""), "", "", null, null);
        resource.getAutoCompleteRepoData(
                "prefix", List.of(""), List.of(""), "1", "1", "project", "dev");
        verify(resource, times(2)).runService(any());
    }

    @Test
    void testGetAutoCompleteRepoConfigs() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("localRepo_1", Generic),
                LocalRepoConfig.create("localRepo_2", Generic),
                LocalRepoConfig.create("localRepo_3", Generic),
                LocalRepoConfig.create("localRepo_4", Generic),
                LocalRepoConfig.create("localRepo_5", Generic),
                LocalRepoConfig.create("localRepo_6", Generic));

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);

        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getPathParamByKey(AUTO_COMPLETE_SEARCH_PARAM)).thenReturn("local");
        when(artifactoryRestRequest.getQueryParamListByKey(AUTO_COMPLETE_REPO_TYPE_PARAM)).thenReturn(List.of(""));
        when(artifactoryRestRequest.getQueryParamListByKey(AUTO_COMPLETE_PACKAGE_TYPE_PARAM)).thenReturn(List.of(""));
        when(artifactoryRestRequest.getQueryParamByKey(AUTO_COMPLETE_LIMIT_PARAM)).thenReturn("100");
        when(artifactoryRestRequest.getQueryParamByKey(AUTO_COMPLETE_CONTINUE_STATE_PARAM)).thenReturn("0");
        when(artifactoryRestRequest.getQueryParamByKey(AUTO_COMPLETE_PACKAGE_TYPE_PARAM)).thenReturn("");

        RestResponse restResponse = spy(ArtifactoryRestResponse.class);
        service.execute(artifactoryRestRequest, restResponse);
        assertEquals(HttpServletResponse.SC_OK, restResponse.getResponseCode());
    }
}
