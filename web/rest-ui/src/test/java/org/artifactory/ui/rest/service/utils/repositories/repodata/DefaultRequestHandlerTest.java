package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class DefaultRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @MethodSource("projectAndLocalParamProvider")
    void testHandlePermissionRepoDataRequest(String projectKey, String local) {
        DefaultRequestHandler<T> defaultRequestHandler = createDefaultRequestHandler();
        mockRequestForProjectKey(projectKey, null);
        when(request.getQueryParamByKey("local")).thenReturn(local);
        defaultRequestHandler.handle(request, response);
        assertRepoKeysForDefaultRequest(response, projectKey, Boolean.parseBoolean(local));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequestWithLimit(String projectKey) {
        DefaultRequestHandler<T> defaultRequestHandler = createDefaultRequestHandler();
        mockRequestForProjectKey(projectKey, defaultRequestHandler.getHandledQueryParam());
        setLimitQueryParam(2);
        defaultRequestHandler.handle(request, response);
        assertLimit();
    }

    @ParameterizedTest
    @MethodSource("searchAndProjectProvider")
    void testHandleUserRepoDataRequestWithSearch(String keySearch, String projectKey) {
        DefaultRequestHandler<T> defaultRequestHandler = createDefaultRequestHandler();
        mockRequestForProjectKey(projectKey, defaultRequestHandler.getHandledQueryParam());
        setKeySearchQueryParam(keySearch);
        defaultRequestHandler.handle(request, response);
        assertKeySearch(keySearch);
    }

    private static Stream<Arguments> projectAndLocalParamProvider() {
        return Stream.of(
                Arguments.of(null, "true"),
                Arguments.of(null, "false"),
                Arguments.of("", "true"),
                Arguments.of("", "false"),
                Arguments.of(DEFAULT_PROJECT, "true"),
                Arguments.of(DEFAULT_PROJECT, "false"),
                Arguments.of(PROJECT_KEY, "true"),
                Arguments.of(PROJECT_KEY, "false")
        );
    }

    @Override
    protected void assertLimit() {
        var repoKeys = ((RepositoriesData) response.getIModel()).getRepoList();
        assertNotNull(repoKeys);
        assertEquals(Integer.valueOf(request.getQueryParamByKey("limit")), repoKeys.size());
    }


    @Override
    protected void assertKeySearch(String keySearch) {
        if (keySearch == null) {
            return;
        }
        var repoKeys = ((RepositoriesData) response.getIModel()).getRepoList();
        assertNotNull(repoKeys);
        if (keySearch.equals(NON_MATCH_REPO_KEY_SEARCH)) {
            assertTrue(repoKeys.isEmpty());
        } else {
            assertFalse(repoKeys.isEmpty());
            assertTrue(repoKeys.stream().allMatch(repoKeyType -> repoKeyType.contains(keySearch)));
        }
    }

    private void assertRepoKeysForDefaultRequest(ArtifactoryRestResponse response, String projectKey,
            boolean onlyLocal) {
        var repoKeys = ((RepositoriesData) response.getIModel()).getRepoList();
        var expectedRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> !PROJECT_KEY.equals(projectKey) || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> onlyLocal ? (repoConfig.getRepoType().equals(LOCAL) ||
                        repoConfig.getRepoType().equals(RepoDetailsType.FEDERATED)) : repoConfig.isReal())
                .toList();
        assertEquals(expectedRepoConfigs.size(), repoKeys.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeys.stream()
                .anyMatch(key -> key.equals(repoConfig.getKey()))));
    }
}