package org.artifactory.ui.rest.service.admin.configuration.repositories;

import com.jfrog.build.acl.bridge.BuildFacade;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.pipelines.InternalPipelinesService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Slf4j
/**
 * @author Alexei Vainshtein
 */
class DeleteRepositoryConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    protected RepositoryService repositoryService;
    @Mock
    protected RepositoryConfigService repositoryConfigService;
    @Mock
    protected BuildFacade buildFacade;
    @Mock
    protected InternalPipelinesService pipelinesService;
    @Mock
    protected SecurityService securityService;
    protected DeleteRepositoryConfigService<?> deleteRepositoryConfigService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        deleteRepositoryConfigService = new DeleteRepositoryConfigService<>(repositoryService,
                repositoryConfigService, null, buildFacade, pipelinesService, securityService);
        disableDeleteRepoFlowV2();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    static Stream<Arguments> systemReposProvider() {
        return Stream.of(
                Arguments.of(USAGE_REPO_KEY),
                Arguments.of(LOG_SHIPPING_REPO_KEY),
                Arguments.of(BUILDINFO_REPO_KEY),
                Arguments.of(PIPEINFO_REPO_KEY),
                Arguments.of(TRASH_REPO_KEY),
                Arguments.of(SUPPORT_BUNDLE_REPO_KEY),
                Arguments.of(IN_TRANSIT_REPO_KEY),
                Arguments.of(EDGE_UPLOADS_REPO_KEY),
                Arguments.of(RELEASE_BUNDLE_REPO_KEY)
        );
    }

    @Test
    void testExecuteDeletePipeInfoRepo() {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("pipe-info");
        when(pipelinesService.isPipeInfoRepo("pipe-info")).thenReturn(true);
        ArtifactoryRestResponse<?> restResponse = mock(ArtifactoryRestResponse.class);
        doAnswer(invocationOnMock -> {
            assertEquals(1, invocationOnMock.getArguments().length);
            Object argument = invocationOnMock.getArguments()[0];
            assertInstanceOf(String.class, argument);
            assertEquals("Deleting a system repo 'pipe-info' is not allowed.", argument);
            return restResponse;
        }).when(restResponse).error(anyString());
        doAnswer(invocationOnMock -> {
            assertEquals(1, invocationOnMock.getArguments().length);
            int argument = (int) invocationOnMock.getArguments()[0];
            assertEquals(403, argument);
            return restResponse;
        }).when(restResponse).responseCode(anyInt());

        deleteRepositoryConfigService.execute(request, restResponse);
    }

    @ParameterizedTest
    @MethodSource("systemReposProvider")
    void testDeleteSystemRepo(String repoKey) {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn(repoKey);
        ArtifactoryRestResponse<?> restResponse = mock(ArtifactoryRestResponse.class);
        doAnswer(invocationOnMock -> {
            assertEquals(1, invocationOnMock.getArguments().length);
            Object argument = invocationOnMock.getArguments()[0];
            assertInstanceOf(String.class, argument);
            assertEquals(argument, "Deleting a system repo '" + repoKey + "' is not allowed.");
            return restResponse;
        }).when(restResponse).error(anyString());
        doAnswer(invocationOnMock -> {
            assertEquals(1, invocationOnMock.getArguments().length);
            int argument = (int) invocationOnMock.getArguments()[0];
            assertEquals(403, argument);
            return restResponse;
        }).when(restResponse).responseCode(anyInt());

        deleteRepositoryConfigService.execute(request, restResponse);
    }

    @Test
    public void testDeleteLastLocalRepo() {
        try (MockedStatic<AolUtils> mockedAolUtils = mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
            BuildFacade buildFacade = mock(BuildFacade.class);
            RepositoryService repositoryService = mock(RepositoryService.class);
            InternalPipelinesService pipelinesService = mock(InternalPipelinesService.class);
            LocalRepoConfig lastLocalConfig = mock(LocalRepoConfig.class);
            LocalConfig buildInfoConfig = mock(LocalConfig.class);
            SecurityService securityService = mock(SecurityService.class);
            DeleteRepositoryConfigService<?> deleteRepositoryConfigService =
                    new DeleteRepositoryConfigService<>(
                            repositoryService,
                            repositoryConfigService,
                            null,
                            buildFacade,
                            pipelinesService,
                            securityService
                    );
            ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
            when(request.getPathParamByKey("repoKey")).thenReturn("last-local-repo");
            when(pipelinesService.isPipeInfoRepo("last-local-repo")).thenReturn(false);
            when(repositoryConfigService.existsByRepoKey("last-local-repo")).thenReturn(true);
            when(repositoryConfigService.getLocalRepoConfigByKeyForDelete("last-local-repo"))
                    .thenReturn(lastLocalConfig);
            when(lastLocalConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                    List.of(lastLocalConfig, buildInfoConfig));
            when(buildInfoConfig.getKey()).thenReturn(BUILDINFO_REPO_KEY);

            ArtifactoryRestResponse<?> restResponse = mock(ArtifactoryRestResponse.class);
            doAnswer(invocationOnMock -> {
                Assertions.assertEquals(1, invocationOnMock.getArguments().length);
                Object argument = invocationOnMock.getArguments()[0];
                assertInstanceOf(String.class, argument);
                Assertions.assertEquals("Deleting the last local repository is not allowed", argument);
                return restResponse;
            }).when(restResponse).error(anyString());
            doAnswer(invocationOnMock -> {
                Assertions.assertEquals(1, invocationOnMock.getArguments().length);
                int argument = (int) invocationOnMock.getArguments()[0];
                Assertions.assertEquals(403, argument);
                return restResponse;
            }).when(restResponse).responseCode(anyInt());
            deleteRepositoryConfigService.execute(request, restResponse);
        }
    }

    @Test
    void testDeleteLastLocalRepoOnAol() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(true);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
            BuildFacade buildFacade = mock(BuildFacade.class);
            RepositoryService repositoryService = mock(RepositoryService.class);
            InternalPipelinesService pipelinesService = mock(InternalPipelinesService.class);
            LocalRepoConfig lastLocalConfig = mock(LocalRepoConfig.class);
            LocalConfig buildInfoConfig = mock(LocalConfig.class);
            SecurityService securityService = mock(SecurityService.class);
            DeleteRepositoryConfigService<?> deleteRepositoryConfigService =
                    new DeleteRepositoryConfigService<>(
                            repositoryService,
                            repositoryConfigService,
                            null,
                            buildFacade,
                            pipelinesService,
                            securityService
                    );
            ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
            BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
            when(request.getPathParamByKey("repoKey")).thenReturn("last-local-repo");
            when(pipelinesService.isPipeInfoRepo("last-local-repo")).thenReturn(false);
            when(repositoryConfigService.existsByRepoKey("last-local-repo")).thenReturn(true);
            when(repositoryConfigService.getLocalRepoConfigByKeyForDelete("last-local-repo")).thenReturn(
                    lastLocalConfig);
            doAnswer(invocationOnMock -> ((RepoPath) invocationOnMock.getArgument(0)).getRepoKey()
                    .equals("last-local-repo")).when(securityService)
                    .isProjectAdmin(any(RepoPath.class));
            when(lastLocalConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                    List.of(lastLocalConfig, buildInfoConfig));
            when(repositoryService.removeRepository(anyString())).thenReturn(statusHolder);
            when(buildInfoConfig.getKey()).thenReturn(BUILDINFO_REPO_KEY);

            ArtifactoryRestResponse<?> restResponse = mock(ArtifactoryRestResponse.class);
            doAnswer(invocationOnMock -> {
                Assertions.assertEquals(1, invocationOnMock.getArguments().length);
                Object argument = invocationOnMock.getArguments()[0];
                assertInstanceOf(String.class, argument);
                Assertions.assertEquals("Successfully deleted 'last-local-repo' repository", argument);
                return restResponse;
            }).when(restResponse).info(anyString());
            doAnswer(invocationOnMock -> {
                Assertions.assertEquals(1, invocationOnMock.getArguments().length);
                int argument = (int) invocationOnMock.getArguments()[0];
                Assertions.assertEquals(200, argument);
                return restResponse;
            }).when(restResponse).responseCode(anyInt());
            deleteRepositoryConfigService.execute(request, restResponse);
        }
    }

    @Test
    @Disabled
    void testDeleteFederatedRepo() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);

            BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
            when(statusHolder.hasErrors()).thenReturn(false);
            when(repositoryService.removeRepository(anyString())).thenReturn(statusHolder);
            FederatedRepoConfig federatedConfig = mock(FederatedRepoConfig.class);
            ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
            when(request.getPathParamByKey("repoKey")).thenReturn("federated-repo");
            when(pipelinesService.isPipeInfoRepo("federated-repo")).thenReturn(false);
            when(repositoryConfigService.existsByRepoKey("federated-repo")).thenReturn(true);
            when(repositoryConfigService.getLocalRepoConfigByKeyForDelete("federated-repo")).thenReturn(null);
            doAnswer(invocationOnMock -> ((RepoPath) invocationOnMock.getArgument(0)).getRepoKey()
                    .equals("federated-repo")).when(securityService)
                    .isProjectAdmin(any(RepoPath.class));
            when(federatedConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
            ArtifactoryRestResponse<?> restResponse = mock(ArtifactoryRestResponse.class);
            doAnswer(invocationOnMock -> {
                assertEquals(1, invocationOnMock.getArguments().length);
                Object argument = invocationOnMock.getArguments()[0];
                assertInstanceOf(String.class, argument);
                assertEquals("Successfully deleted 'federated-repo' repository", argument);
                return restResponse;
            }).when(restResponse).info(anyString());
            doAnswer(invocationOnMock -> {
                assertEquals(1, invocationOnMock.getArguments().length);
                int argument = (int) invocationOnMock.getArguments()[0];
                assertEquals(200, argument);
                return restResponse;
            }).when(restResponse).responseCode(anyInt());

            deleteRepositoryConfigService.execute(request, restResponse);
        }
    }

    private void disableDeleteRepoFlowV2() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.FALSE.toString());
    }
}