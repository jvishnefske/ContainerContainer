package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.common.ConstantValues;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PermissionRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePermissionRepoDataRequest(String projectKey) {
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        permissionRequestHandler.handle(request, response);
        assertRepoKeyTypesForPermissions(response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequestWithLimit(String projectKey) {
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setLimitQueryParam(2);
        permissionRequestHandler.handle(request, response);
        assertLimit();
    }

    @ParameterizedTest
    @MethodSource("searchAndProjectProvider")
    void testHandleUserRepoDataRequestWithSearch(String searchKey, String projectKey) {
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setKeySearchQueryParam(searchKey);
        permissionRequestHandler.handle(request, response);
        assertKeySearch(searchKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePermissionRepoDataRequest_defaultState_federatedReposLabelledAsFederated(String projectKey) {
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setFedAttrOptInParam(false);
        permissionRequestHandler.handle(request, response);

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
    void testHandlePermissionRepoDataRequest_fedAttrOptIn_federatedReposShownAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setFedAttrOptInParam(true);
        permissionRequestHandler.handle(request, response);

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
                .forEach(rkt -> assertEquals(LOCAL.typeNameLowercase(), rkt.getType(),
                        "Federated repo must be labelled as local when fedAttrOptIn is true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandlePermissionRepoDataRequest_fedAttrFlagEnabled_paramAbsent_federatedReposRetainFederatedLabel(
            String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setFedAttrOptInParam(false);
        permissionRequestHandler.handle(request, response);

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
    void testHandlePermissionRepoDataRequest_fedAttrFlagDisabled_paramPresent_federatedReposRetainFederatedLabel(
            String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "false");
        PermissionRequestHandler<T> permissionRequestHandler = createPermissionRequestHandler();
        mockRequestForProjectKey(projectKey, permissionRequestHandler.getHandledQueryParam());
        setFedAttrOptInParam(true);
        permissionRequestHandler.handle(request, response);

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

    private void assertRepoKeyTypesForPermissions(ArtifactoryRestResponse response, String projectKey) {
        var repoKeyTypes = ((RepositoriesData) response.getIModel()).getRepoTypesList();
        boolean isDefaultProject = ProjectsUtils.isDefaultProject(projectKey);
        assertNotNull(repoKeyTypes);
        var expectedRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> repoConfig.isReal() &&
                        !repoConfig.getRepoType().equals(RepoDetailsType.RELEASE_BUNDLE))
                .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> !infoRepoConfigs.contains(repoConfig))
                .filter(repoConfig -> !hiddenRepoConfigs.contains(repoConfig))
                .filter(repoConfig -> !repoConfig.getPackageType().equals(ReleaseBundles))
                .filter(repoConfig -> !repoConfig.getKey().equals("unauthorized-read-repo")).toList();
        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeyTypes.stream()
                .anyMatch(repoKeyType -> repoKeyType.getRepoKey().equals(repoConfig.getKey()) &&
                        repoKeyType.getType().equals(repoConfig.getRepoType().typeNameLowercase()))));
    }
}