package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.ui.utils.RequestUtils.FED_ATTR_OPT_IN_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeployRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @CsvSource({"_intransit,false", "release-bundles-v2-jfds,false",
            "project-release-bundles-v2-jfds,false", "not_Hidden_repo,true"})
    void isValidRepoForDeploy_ValidRepo(String repoKey, boolean expected) {
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.isReal()).thenReturn(true);

        assertEquals(expected, deployRequestHandler.isValidRepoForDeploy(repoConfig));
    }

    @ParameterizedTest
    @MethodSource("projectAndLimitProvider")
    void testHandleUserRepoDataRequestWithLimit(String projectKey, long limit) {
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        mockRequestForProjectKey(projectKey, deployRequestHandler.getHandledQueryParam());
        setLimitQueryParam(limit);
        deployRequestHandler.handle(request, response);
        assertLimit();
    }

    private static Stream<Arguments> projectAndLimitProvider() {
        return Stream.of(
                Arguments.of(null, 2),
                Arguments.of("", 2),
                Arguments.of(DEFAULT_PROJECT, 2),
                Arguments.of(PROJECT_KEY, 2),
                Arguments.of(null, 0),
                Arguments.of("", 0),
                Arguments.of(DEFAULT_PROJECT, 0),
                Arguments.of(PROJECT_KEY, 0));
    }

    @ParameterizedTest
    @MethodSource("searchAndProjectProvider")
    void testHandleUserRepoDataRequestWithSearch(String searchKey, String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        setKeySearchQueryParam(searchKey);
        userRequestHandler.handle(request, response);
        assertKeySearch(searchKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePermissionRepoDataRequest(String projectKey) {
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        mockRequestForProjectKey(projectKey, deployRequestHandler.getHandledQueryParam());
        deployRequestHandler.handle(request, response);
        assertRepoKeyTypesForDeploy(deployRequestHandler, response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleDeployRepoDataRequest_fedAttrOptIn_includesFederatedReposAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        mockRequestForProjectKey(projectKey, deployRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        deployRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must still appear in the response when fedAttrOptIn is enabled (shown as local)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleDeployRepoDataRequest_fedAttrFlagEnabled_paramAbsent_includesFederatedRepos(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        mockRequestForProjectKey(projectKey, deployRequestHandler.getHandledQueryParam());
        deployRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear when the opt-in param is absent, even if the feature flag is on");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleDeployRepoDataRequest_fedAttrFlagDisabled_paramPresent_includesFederatedRepos(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "false");
        DeployRequestHandler<T> deployRequestHandler = createDeployRequestHandler();
        mockRequestForProjectKey(projectKey, deployRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        deployRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear when the feature flag is off, even if the client sends the opt-in param");
    }

    private void assertRepoKeyTypesForDeploy(DeployRequestHandler<T> deployRequestHandler,
            ArtifactoryRestResponse response, String projectKey) {
        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        assertEquals(100, repositoriesData.getFileUploadMaxSizeMb());

        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        List<RepoConfig> expectedRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> !PROJECT_KEY.equals(projectKey) || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> {
                    RepoDetailsType repoDetailsType = repoConfig.getRepoType();
                    return repoDetailsType.equals(LOCAL) ||
                            repoDetailsType.equals(RepoDetailsType.FEDERATED) ||
                            repoDetailsType.equals(RepoDetailsType.VIRTUAL);
                })
                .filter(deployRequestHandler::isValidRepoForDeploy)
                .filter(repoConfig -> !repoConfig.getKey().equals("unauthorized-deploy-repo"))
                .toList();

        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());

        expectedRepoConfigs.forEach(repoConfig -> {
            String repoKey = repoConfig.getKey();
            RepoKeyType matchingRepoKeyType = repoKeyTypes.stream()
                    .filter(repoKeyType -> repoKeyType.getRepoKey().equals(repoKey) &&
                            repoKeyType.getRepoType().equals(repoConfig.getPackageType()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(matchingRepoKeyType);

            if (StringUtils.isNotBlank(repoConfig.getBaseConfig().getRepoLayoutRef())) {
                assertNotNull(matchingRepoKeyType.getLayoutPattern());
                assertNotNull(matchingRepoKeyType.getLayoutFileItegRevRegex());
                assertNotNull(matchingRepoKeyType.getLayoutFolderItegRevRegex());
            } else {
                assertNull(matchingRepoKeyType.getLayoutPattern());
                assertNull(matchingRepoKeyType.getLayoutFileItegRevRegex());
                assertNull(matchingRepoKeyType.getLayoutFolderItegRevRegex());
            }
        });
    }

}