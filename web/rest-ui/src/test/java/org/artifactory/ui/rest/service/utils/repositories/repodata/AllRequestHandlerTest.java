package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.common.ConstantValues;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.ui.utils.RequestUtils.FED_ATTR_OPT_IN_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class AllRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleSearchRepoDataRequest(String projectKey) {
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        allRequestHandler.handle(request, response);
        assertRepoKeyTypesForAllRequest(response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequestWithLimit(String projectKey) {
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        setLimitQueryParam(2);
        allRequestHandler.handle(request, response);
        assertLimit();
    }

    @ParameterizedTest
    @MethodSource("searchAndProjectProvider")
    void testHandleUserRepoDataRequestWithSearch(String keySearch, String projectKey) {
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        setKeySearchQueryParam(keySearch);
        allRequestHandler.handle(request, response);
        assertKeySearch(keySearch);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleAllRepoDataRequest_defaultState_federatedReposAbsent(String projectKey) {
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        allRequestHandler.handle(request, response);

        List<RepoKeyType> repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().noneMatch(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must not appear in the default state (no gate active)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleAllRepoDataRequest_fedAttrOptIn_federatedReposShownAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        allRequestHandler.handle(request, response);

        List<RepoKeyType> repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear in the response when fedAttrOptIn is enabled (shown as local)");
        repoKeyTypes.stream()
                .filter(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED)))
                .forEach(rkt -> assertEquals(LOCAL.typeNameLowercase(), rkt.getType(),
                        "Federated repo must be labelled as local (package type) when fedAttrOptIn is true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleAllRepoDataRequest_fedAttrFlagEnabled_paramAbsent_federatedReposAbsent(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        allRequestHandler.handle(request, response);

        List<RepoKeyType> repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().noneMatch(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must not appear when the opt-in param is absent, even if the feature flag is on");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleAllRepoDataRequest_fedAttrFlagDisabled_paramPresent_federatedReposAbsent(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "false");
        AllRequestHandler<T> allRequestHandler = createAllRequestHandler();
        mockRequestForProjectKey(projectKey, allRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        allRequestHandler.handle(request, response);

        List<RepoKeyType> repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().noneMatch(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must not appear when the server flag is off, even if client sends the opt-in param");
    }

    @SuppressWarnings("unchecked")
    private void assertRepoKeyTypesForAllRequest(ArtifactoryRestResponse response, String projectKey) {
        var repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        assertNotNull(repoKeyTypes);
        var expectedRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> !PROJECT_KEY.equals(projectKey) || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> repoConfig.isReal() &&
                        !repoConfig.getRepoType().equals(RepoDetailsType.FEDERATED))
                .filter(repoConfig -> !repoConfig.getKey().equals("unauthorized-read-repo"))
                .toList();
        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeyTypes.stream()
                .anyMatch(repoKeyType -> repoKeyType.getRepoKey().equals(repoConfig.getKey()))));
    }
}