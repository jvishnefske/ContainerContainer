package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.ui.utils.RequestUtils.FED_ATTR_OPT_IN_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class PackageSearchRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePermissionRepoDataRequest(String projectKey) {
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        packageSearchRequestHandler.handle(request, response);
        assertRepoKeyTypesForPackageSearch(response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequestWithLimit(String projectKey) {
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        setLimitQueryParam(2);
        packageSearchRequestHandler.handle(request, response);
        assertLimit();
    }

    @ParameterizedTest
    @MethodSource("searchAndProjectProvider")
    void testHandleUserRepoDataRequestWithSearch(String searchKey, String projectKey) {
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        setKeySearchQueryParam(searchKey);
        packageSearchRequestHandler.handle(request, response);
        assertKeySearch(searchKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePackageSearchRepoDataRequest_defaultState_federatedReposLabelledAsFederated(String projectKey) {
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        packageSearchRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        repoKeyTypes.stream()
                .filter(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED)))
                .forEach(rkt -> assertEquals(FEDERATED.typeNameLowercase(), rkt.getType(),
                        "Federated repo must be labelled as federated in the default state (no gate active)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePackageSearchRepoDataRequest_fedAttrOptIn_federatedReposShownAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        packageSearchRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must still appear in the response when fedAttrOptIn is enabled (shown as local)");
        repoKeyTypes.stream()
                .filter(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED)))
                .forEach(rkt -> assertEquals(RepoDetailsType.LOCAL.typeNameLowercase(), rkt.getType(),
                        "Federated repo must be labelled as local when fedAttrOptIn is true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePackageSearchRepoDataRequest_fedAttrFlagEnabled_paramAbsent_federatedReposRetainFederatedLabel(
            String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        packageSearchRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear when the opt-in param is absent, even if the feature flag is on");
        repoKeyTypes.stream()
                .filter(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED)))
                .forEach(rkt -> assertEquals(FEDERATED.typeNameLowercase(), rkt.getType(),
                        "Federated repo must retain federated label when opt-in param is absent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePackageSearchRepoDataRequest_fedAttrFlagDisabled_paramPresent_federatedReposRetainFederatedLabel(
            String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "false");
        PackageSearchRequestHandler<T> packageSearchRequestHandler = createPackageSearchRequestHandler();
        mockRequestForProjectKey(projectKey, packageSearchRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        packageSearchRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear when the feature flag is off, even if the client sends the opt-in param");
        repoKeyTypes.stream()
                .filter(rkt -> allRepoConfigs.stream()
                        .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED)))
                .forEach(rkt -> assertEquals(FEDERATED.typeNameLowercase(), rkt.getType(),
                        "Federated repo must retain federated label when the server flag is off"));
    }

    private void assertRepoKeyTypesForPackageSearch(ArtifactoryRestResponse response, String projectKey) {
        var repoKeyTypes = ((RepositoriesData) response.getIModel()).getRepoTypesList();
        assertNotNull(repoKeyTypes);
        boolean isDefaultProject = ProjectsUtils.isDefaultProject(projectKey);
        var expectedRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> repoConfig.isReal() && !repoConfig.getRepoType().equals(RepoDetailsType.REMOTE))
                .filter(repoConfig -> repoConfig.getPackageType().isMavenGroup() ||
                        repoTypePackageSearchTypeMap.containsKey(repoConfig.getPackageType()))
                .filter(repoConfig -> {
                    if (repoConfig instanceof LocalCacheRepoConfig) {
                        RepoType type = repoConfig.getPackageType();
                        return type.isMavenGroup() ?
                                PackageSearchCriteria.PackageSearchType.gavc.isRemoteCachesProps() :
                                repoTypePackageSearchTypeMap.get(type).isRemoteCachesProps();
                    }
                    return true;
                })
                .filter(repoConfig -> !hiddenRepoConfigs.contains(repoConfig))
                .filter(repoConfig -> !infoRepoConfigs.contains(repoConfig))
                .filter(repoConfig -> !repoConfig.getKey().equals("unauthorized-read-repo"))
                .toList();
        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeyTypes.stream()
                .anyMatch(repoKeyType -> repoKeyType.getRepoKey().equals(repoConfig.getKey()))));
    }
}