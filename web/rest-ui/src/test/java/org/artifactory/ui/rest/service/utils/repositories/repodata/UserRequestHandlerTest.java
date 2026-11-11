package org.artifactory.ui.rest.service.utils.repositories.repodata;

import jakarta.annotation.Nonnull;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.system.suppliers.SupportBundleRepo;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.descriptor.repo.RepoType.Ivy;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.ui.utils.RequestUtils.FED_ATTR_OPT_IN_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class UserRequestHandlerTest<T> extends RepositoriesDataHandlerTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequest(String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        userRequestHandler.handle(request, response);
        assertRepoKeyTypesForUser(response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequestWithLimit(String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        setLimitQueryParam(2);
        userRequestHandler.handle(request, response);
        assertLimit();
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

    private void assertRepoKeyTypesForUser(ArtifactoryRestResponse response, String projectKey) {
        var repoKeyTypes = ((RepositoriesData) response.getIModel()).getRepoTypesList();
        assertNotNull(repoKeyTypes);
        var expectedRepoConfigs = getUserRepoConfigs(projectKey);
        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeyTypes.stream()
                .anyMatch(repoKeyType -> repoKeyType.getRepoKey().equals(repoConfig.getKey()) &&
                        repoKeyType.getRepoType().equals(repoConfig.getPackageType()))));
    }

    @Nonnull
    private List<RepoConfig> getUserRepoConfigs(String projectKey) {
        boolean isDefaultProject = ProjectsUtils.isDefaultProject(projectKey);
        var expectedUserRepoConfigs = allRepoConfigs.stream()
                .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(PROJECT_KEY))
                .filter(repoConfig -> repoConfig.getRepoType().equals(LOCAL) ||
                        repoConfig.getRepoType().equals(RepoDetailsType.FEDERATED))
                .filter(repoConfig -> !hiddenRepoConfigs.contains(repoConfig))
                .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> !repoConfig.getKey().equals("unauthorized-deploy-repo"))
                .collect(Collectors.toList());
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            expectedUserRepoConfigs.add(new SupportBundleRepo().buildRepoConfig(descriptor, SUPPORT_BUNDLE_REPO_KEY));
        }
        return expectedUserRepoConfigs;
    }

    @ParameterizedTest
    @MethodSource("SourceRepoAndActionAndProjectProvider")
    void testHandleUserRepoDataInvalidSourceRepoKeyOrActionRequest(String repoKey, String action, String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey("sourceRepoKey")).thenReturn(repoKey);
        when(request.getQueryParamByKey("action")).thenReturn(action);
        userRequestHandler.handle(request, response);
        assertRepoKeyTypesForUser(response, projectKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_PROJECT, PROJECT_KEY})
    @NullAndEmptySource
    void testHandleUserRepoDataRequestWithMoveActionAndBuildInfo(String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        RepoConfig buildInfoRepoConfig = allRepoConfigs.stream()
                .filter(r -> r.getPackageType() == RepoType.BuildInfo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("BuildInfo repo config not found"));
        mockRequestForSourceRepoKeyAndAction(buildInfoRepoConfig, "move");
        userRequestHandler.handle(request, response, projectKey);
        assertSourceRepoKeyAndAction(userRequestHandler, buildInfoRepoConfig, projectKey, "move");
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_PROJECT, PROJECT_KEY})
    @NullAndEmptySource
    void testHandleRequestThrowsExceptionForNonExistentSourceRepo(String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey("sourceRepoKey")).thenReturn("non-exists-repo");
        when(request.getQueryParamByKey("action")).thenReturn("copy");
        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> userRequestHandler.handle(request, response));
        assertEquals("Could not find source repo with key non-exists-repo", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("PackageActionAndProjectProvider")
    void testHandleUserRepoDataRequestWithSourceRepoKeyAndAction(RepoType packageType, String action,
            String projectKey) {
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        RepoConfig repoConfig =
                allRepoConfigs.stream().filter(r -> r.getPackageType() == packageType).findFirst().orElse(null);
        mockRequestForSourceRepoKeyAndAction(repoConfig, action);
        userRequestHandler.handle(request, response, projectKey);
        assertSourceRepoKeyAndAction(userRequestHandler, repoConfig, projectKey, action);
    }

    private static Stream<Arguments> SourceRepoAndActionAndProjectProvider() {
        return Stream.of(
                Arguments.of("", "", DEFAULT_PROJECT),
                Arguments.of("", "", PROJECT_KEY),
                Arguments.of("artifactory-build-info", "invalid-action", DEFAULT_PROJECT),
                Arguments.of("artifactory-build-info", "invalid-action", PROJECT_KEY)
        );
    }

    private static Stream<Arguments> PackageActionAndProjectProvider() {
        return Stream.of(
                Arguments.of(Maven, "copy", DEFAULT_PROJECT),
                Arguments.of(Gradle, "copy", DEFAULT_PROJECT),
                Arguments.of(Ivy, "copy", DEFAULT_PROJECT),
                Arguments.of(Docker, "copy", DEFAULT_PROJECT),
                Arguments.of(Generic, "copy", DEFAULT_PROJECT),
                Arguments.of(Maven, "move", DEFAULT_PROJECT),
                Arguments.of(Gradle, "move", DEFAULT_PROJECT),
                Arguments.of(Ivy, "move", DEFAULT_PROJECT),
                Arguments.of(Docker, "move", DEFAULT_PROJECT),
                Arguments.of(Generic, "move", DEFAULT_PROJECT),
                Arguments.of(Maven, "restore", DEFAULT_PROJECT),
                Arguments.of(Gradle, "restore", DEFAULT_PROJECT),
                Arguments.of(Ivy, "restore", DEFAULT_PROJECT),
                Arguments.of(Docker, "restore", DEFAULT_PROJECT),
                Arguments.of(Generic, "restore", DEFAULT_PROJECT),

                Arguments.of(Maven, "copy", PROJECT_KEY),
                Arguments.of(Gradle, "copy", PROJECT_KEY),
                Arguments.of(Ivy, "copy", PROJECT_KEY),
                Arguments.of(Docker, "copy", PROJECT_KEY),
                Arguments.of(Generic, "copy", PROJECT_KEY),
                Arguments.of(Maven, "move", PROJECT_KEY),
                Arguments.of(Gradle, "move", PROJECT_KEY),
                Arguments.of(Ivy, "move", PROJECT_KEY),
                Arguments.of(Docker, "move", PROJECT_KEY),
                Arguments.of(Generic, "move", PROJECT_KEY),
                Arguments.of(Maven, "restore", PROJECT_KEY),
                Arguments.of(Gradle, "restore", PROJECT_KEY),
                Arguments.of(Ivy, "restore", PROJECT_KEY),
                Arguments.of(Docker, "restore", PROJECT_KEY),
                Arguments.of(Generic, "restore", PROJECT_KEY)
        );
    }

    private void mockRequestForSourceRepoKeyAndAction(RepoConfig sourceRepoConfig, String action) {
        when(request.getQueryParamByKey("sourceRepoKey")).thenReturn(sourceRepoConfig.getKey());
        when(request.getQueryParamByKey("action")).thenReturn(action);
    }

    private void assertSourceRepoKeyAndAction(UserRequestHandler<T> userRequestHandler, RepoConfig sourceRepoConfig,
            String projectKey, String action) {
        assertNotNull(response.getIModel());
        UserRequestHandler.Action userAction = UserRequestHandler.Action.fromString(action);

        List<RepoConfig> expectedRepoConfigs =
                getExpectedRepoConfigs(userRequestHandler, sourceRepoConfig, projectKey, userAction);
        List<RepoKeyType> repoKeyTypes = ((RepositoriesData) response.getIModel()).getRepoTypesList();

        assertEquals(expectedRepoConfigs.size(), repoKeyTypes.size());
        expectedRepoConfigs.forEach(repoConfig -> assertTrue(repoKeyTypes.stream()
                .anyMatch(repoKeyType -> repoKeyType.getRepoKey().equals(repoConfig.getKey()) &&
                        repoKeyType.getRepoType() == repoConfig.getPackageType())));
    }

    private List<RepoConfig> getExpectedRepoConfigs(UserRequestHandler<T> userRequestHandler,
            RepoConfig sourceRepoConfig,
            String projectKey, UserRequestHandler.Action userAction) {
        if (userAction == UserRequestHandler.Action.RESTORE || userAction == null) {
            return getUserRepoConfigs(projectKey);
        } else {
            Set<RepoType> repoTypes = determineRepoTypes(userRequestHandler, sourceRepoConfig);
            return getUserRepoConfigs(projectKey).stream()
                    .filter(repoConfig -> repoTypes.contains(repoConfig.getPackageType()))
                    .toList();
        }
    }

    private Set<RepoType> determineRepoTypes(UserRequestHandler<T> userRequestHandler, RepoConfig sourceRepoConfig) {
        if (sourceRepoConfig.getPackageType() == BuildInfo) {
            return Set.of(BuildInfo);
        }
        return userRequestHandler.determineRepoTypes(sourceRepoConfig);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, PROJECT_KEY})
    @NullSource
    void testHandleUserRepoDataRequest_fedAttrOptIn_includesFederatedReposAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        userRequestHandler.handle(request, response);

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
    void testHandleUserRepoDataRequest_fedAttrFlagEnabled_paramAbsent_includesFederatedRepos(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        userRequestHandler.handle(request, response);

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
    void testHandleUserRepoDataRequest_fedAttrFlagDisabled_paramPresent_includesFederatedRepos(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "false");
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        userRequestHandler.handle(request, response);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must appear when the feature flag is off, even if the client sends the opt-in param");
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_PROJECT, PROJECT_KEY})
    @NullAndEmptySource
    void testHandleUserRepoDataRequest_fedAttrOptIn_moveWithBuildInfo_includesFederatedReposAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        RepoConfig buildInfoRepoConfig = allRepoConfigs.stream()
                .filter(r -> r.getPackageType() == BuildInfo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("BuildInfo repo config not found"));
        mockRequestForSourceRepoKeyAndAction(buildInfoRepoConfig, "move");
        userRequestHandler.handle(request, response, projectKey);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must still appear in move+BuildInfo path when fedAttrOptIn is enabled (shown as local)"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {DEFAULT_PROJECT, PROJECT_KEY})
    @NullAndEmptySource
    void testHandleUserRepoDataRequest_fedAttrOptIn_copyWithSourceRepo_includesFedAsLocal(String projectKey) {
        setStringSystemProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        UserRequestHandler<T> userRequestHandler = createUserRequestHandler();
        mockRequestForProjectKey(projectKey, userRequestHandler.getHandledQueryParam());
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn("true");
        RepoConfig dockerRepoConfig = allRepoConfigs.stream()
                .filter(r -> r.getPackageType() == Docker && r.getRepoType().equals(LOCAL))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Docker local repo config not found"));
        mockRequestForSourceRepoKeyAndAction(dockerRepoConfig, "copy");
        userRequestHandler.handle(request, response, projectKey);

        RepositoriesData repositoriesData = (RepositoriesData) response.getIModel();
        assertNotNull(repositoriesData);
        List<RepoKeyType> repoKeyTypes = repositoriesData.getRepoTypesList();
        assertNotNull(repoKeyTypes);
        assertTrue(repoKeyTypes.stream().anyMatch(rkt -> allRepoConfigs.stream()
                .anyMatch(rc -> rc.getKey().equals(rkt.getRepoKey()) && rc.getRepoType().equals(FEDERATED))),
                "Federated repos must still appear in copy+sourceRepo path when fedAttrOptIn is on (shown as local)"
        );
    }
}