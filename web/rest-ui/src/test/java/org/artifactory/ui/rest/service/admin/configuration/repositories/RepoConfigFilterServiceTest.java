package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoProjectAssociation;
import org.artifactory.ui.rest.service.admin.configuration.repositories.mapper.RepoConfigMapper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoProjectAssociation.ASSIGNED;
import static org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoProjectAssociation.SHARED;
import static org.mockito.Mockito.when;

class RepoConfigFilterServiceTest {

    private ProjectRepoHelper projectRepoHelper;
    private RepoConfigMapper repoConfigMapper;
    private RepoConfigFilterService repoConfigFilterService;

    @BeforeEach
    public void setup() {
        projectRepoHelper = Mockito.mock(ProjectRepoHelper.class);
        repoConfigMapper = Mockito.mock(RepoConfigMapper.class);
        repoConfigFilterService = new RepoConfigFilterService(projectRepoHelper, repoConfigMapper);
    }

    @ParameterizedTest
    @MethodSource("provideFilterScenarios")
    public void matchTest(RepoConfigRequest request, String repoKey, RepoDetailsType repoType, RepoType packageType,
                 String url, boolean inactive, boolean replications, boolean expected, String message) {
        var repoConfig = mockRepoConfig(repoKey, repoType, packageType, url, inactive, replications);

        boolean result = repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        Assertions.assertEquals(expected, result, message);
    }

    @ParameterizedTest
    @MethodSource("provideFederatedFilterScenarios")
    public void matchFederatedTest(RepoConfigRequest request, boolean isFederated, boolean expected, String message) {
        RepoConfig repoConfig = mockRepoConfig("repo1", RepoDetailsType.LOCAL, RepoType.Maven,
                null, false, false);
        when(repoConfig.isFederated()).thenReturn(isFederated);

        boolean result = repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        Assertions.assertEquals(expected, result, message);
    }

    @ParameterizedTest
    @MethodSource("provideFederatedFilterScenarios")
    public void matchFederatedWithFedAttrOptInTest(RepoConfigRequest request, boolean isFederated, boolean expected,
            String message) {
        // Exercises the isFedAttrOptIn() branch of matchFederated (self member is added to a working copy of the
        // members list before the federated evaluation), which is skipped by the default (opt-out) scenarios.
        request.setFedAttrOptIn(true);
        RepoConfig repoConfig = mockRepoConfig("repo1", RepoDetailsType.LOCAL, RepoType.Maven,
                null, false, false);
        when(repoConfig.isFederated()).thenReturn(isFederated);

        boolean result = repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        Assertions.assertEquals(expected, result, message);
    }

    @Test
    public void matchFederated_optIn_addsSelfMemberToResponseWhenMissing() {
        // remote-only members + opt-in -> the repo's own (self) member is added so it appears in the response
        RepoConfig repoConfig = mockRepoConfigWithMembers("repo1", List.of(remoteMember()));
        RepoConfigRequest request = RepoConfigRequest.builder().federated(false).fedAttrOptIn(true).build();

        repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        List<FederatedMember> members = repoConfig.getBaseConfig().getFederationConfig().getMembers();
        Assertions.assertEquals(2, members.size(), "self member should be added to a remote-only repo");
        Assertions.assertTrue(members.stream().anyMatch(RepoConfigFilterServiceTest::isSelfMember),
                "response members must contain the self member");
    }

    @Test
    public void matchFederated_optIn_doesNotDuplicateExistingSelfMember() {
        RepoConfig repoConfig =
                mockRepoConfigWithMembers("repo1", List.of(selfMember("repo1"), remoteMember()));
        RepoConfigRequest request = RepoConfigRequest.builder().federated(true).fedAttrOptIn(true).build();

        repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        List<FederatedMember> members = repoConfig.getBaseConfig().getFederationConfig().getMembers();
        Assertions.assertEquals(2, members.size(), "existing self member must not be duplicated");
        Assertions.assertEquals(1, members.stream().filter(RepoConfigFilterServiceTest::isSelfMember).count(),
                "exactly one self member expected");
    }

    @Test
    public void matchFederated_withoutOptIn_leavesMembersUntouched() {
        RepoConfig repoConfig = mockRepoConfigWithMembers("repo1", List.of(remoteMember()));
        RepoConfigRequest request = RepoConfigRequest.builder().federated(false).build(); // no opt-in

        repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        List<FederatedMember> members = repoConfig.getBaseConfig().getFederationConfig().getMembers();
        Assertions.assertEquals(1, members.size(), "members must be untouched without opt-in");
        Assertions.assertFalse(members.stream().anyMatch(RepoConfigFilterServiceTest::isSelfMember),
                "no self member should be added without opt-in");
    }

    @ParameterizedTest
    @MethodSource("provideCombinedRepoTypeAndFederatedFilterScenarios")
    public void matchRepoTypeAndFederatedTest(RepoConfigRequest request, RepoDetailsType internalRepoType,
                                              boolean isFederated, boolean expected, String message) {
        RepoConfig repoConfig = mockRepoConfig("repo1", internalRepoType, RepoType.Maven,
                null, false, false);
        when(repoConfig.isFederated()).thenReturn(isFederated);

        boolean result = repoConfigFilterService.match(repoConfig, request, Map.of(), Set.of());

        Assertions.assertEquals(expected, result, message);
    }

    @ParameterizedTest
    @MethodSource("provideProjectAndEnvironmentFilterScenarios")
    public void matchProjectAndEnvironmentTest(RepoConfigRequest request, String repoKey,
                                               Map<String, List<ProjectRepo>> allProjectRepos,
                                               boolean hasAssigned, boolean hasShared, Set<String> environments,
                                               boolean expected, String message) {
        var repoConfig = mockRepoConfig(repoKey, RepoDetailsType.LOCAL, RepoType.Maven, null,
                false, false);
        when(projectRepoHelper.hasAssignedStatus(Mockito.anyList())).thenReturn(hasAssigned);
        when(projectRepoHelper.hasSharedStatus(Mockito.anyList())).thenReturn(hasShared);
        when(projectRepoHelper.getEnvironments(Mockito.eq(repoConfig), Mockito.anyList())).thenReturn(environments);

        boolean result = repoConfigFilterService.match(repoConfig, request, allProjectRepos, Set.of());

        Assertions.assertEquals(expected, result, message);
    }

    private RepoConfig mockRepoConfig(String repoKey, RepoDetailsType repoType, RepoType packageType,
                                String url, boolean inactive, boolean replications) {
        RepoConfig repoConfig = Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getUrl()).thenReturn(url);
        when(repoConfig.getRepoType()).thenReturn(repoType);
        when(repoConfig.getPackageType()).thenReturn(packageType);
        when(repoConfig.getBaseConfig()).thenReturn(new BaseConfig());
        when(repoConfigMapper.hasReplication(repoConfig)).thenReturn(replications);

        return  repoConfig;
    }

    private RepoConfig mockRepoConfigWithMembers(String repoKey, List<FederatedMember> members) {
        RepoConfig repoConfig = mockRepoConfig(repoKey, RepoDetailsType.LOCAL, RepoType.Maven, null, false, false);
        BaseConfig baseConfig = new BaseConfig();
        baseConfig.getFederationConfig().setMembers(new ArrayList<>(members));
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        return repoConfig;
    }

    private static FederatedMember remoteMember() {
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate("https://other.jfrog.io/artifactory/repo1");
        return member;
    }

    private static FederatedMember selfMember(String repoKey) {
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate(FederatedMember.BASE_URL_PLACEHOLDER + "/" + repoKey);
        return member;
    }

    private static boolean isSelfMember(FederatedMember member) {
        return member.getUrlOrTemplate() != null
                && member.getUrlOrTemplate().startsWith(FederatedMember.BASE_URL_PLACEHOLDER);
    }

    private static Stream<Arguments> provideFilterScenarios() {
        return Stream.of(
                Arguments.of(RepoConfigRequest.builder().build(), "repo1", RepoDetailsType.LOCAL,
                        RepoType.Maven, null, false, false, true, "No filters, should match"),
                Arguments.of(RepoConfigRequest.builder().repoKeys(List.of()).build(),
                        "repo1", RepoDetailsType.LOCAL,
                        RepoType.Maven, null, false, false, false, "Empty repo keys should match nothing"),
                Arguments.of(RepoConfigRequest.builder().repoTypes(Set.of(RepoDetailsType.LOCAL)).build(),
                        "repo1", RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, true,
                        "Filter by repo type, should match"),
                Arguments.of(RepoConfigRequest.builder().repoTypes(Set.of(RepoDetailsType.REMOTE)).build(),
                        "repo1", RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, false,
                        "Filter by repo type, should not match"),
                Arguments.of(RepoConfigRequest.builder().packageTypes(Set.of(RepoType.Maven)).build(),
                        "repo1", RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, true,
                        "Filter by package type, should match"),
                Arguments.of(RepoConfigRequest.builder().packageTypes(Set.of(RepoType.Gradle)).build(),
                        "repo1", RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, false,
                        "Filter by package type, should not match"),
                Arguments.of(RepoConfigRequest.builder().url("localhost").build(), "repo-remote",
                        RepoDetailsType.REMOTE, RepoType.Generic, "http://localhost:8081", false, false, true,
                        "Filter by URL, should match"),
                Arguments.of(RepoConfigRequest.builder().url("remote-host").build(), "repo-remote",
                        RepoDetailsType.REMOTE, RepoType.Generic, "http://localhost:8081", false, false, false,
                        "Filter by URL, should not match"),
                Arguments.of(RepoConfigRequest.builder().inactive(true).build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, true, false, true,
                        "Filter by inactive status, should match"),
                Arguments.of(RepoConfigRequest.builder().replications(true).build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, false, true, true,
                        "Filter by replications, should match"),
                Arguments.of(RepoConfigRequest.builder().repoTypes(Set.of(RepoDetailsType.LOCAL))
                        .packageTypes(Set.of(RepoType.Maven)).build(), "repo1", RepoDetailsType.LOCAL,
                        RepoType.Maven, null, false, false, true, "Multiple filters, should match"),
                Arguments.of(RepoConfigRequest.builder().repoKeys(List.of("repo1")).build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, true,
                        "Filter by repoKeys, should match"),
                Arguments.of(RepoConfigRequest.builder().repoKeys(List.of("repo2")).build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, false,
                        "Filter by repoKeys, should not match"),
                Arguments.of(RepoConfigRequest.builder().repoKeyToSearch("repo").build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, true,
                        "Filter by repoKeyToSearch, should match"),
                Arguments.of(RepoConfigRequest.builder().repoKeyToSearch("test").build(), "repo1",
                        RepoDetailsType.LOCAL, RepoType.Maven, null, false, false, false,
                        "Filter by repoKeyToSearch, should not match"),
                Arguments.of(RepoConfigRequest.builder().build(), "repo1", RepoDetailsType.LOCAL,
                        RepoType.Maven, null, false, false, true, "Non-system repo, should match"),
                Arguments.of(RepoConfigRequest.builder().build(), "artifactory-build-info",
                        RepoDetailsType.LOCAL,
                        RepoType.BuildInfo, null, false, false, true,
                        "artifactory-build-info system repo, should match"),
                Arguments.of(RepoConfigRequest.builder().build(), "pipe-info-repo",
                        RepoDetailsType.LOCAL,
                        RepoType.PipeInfo, null, false, false, false, "Local PipeInfo repo, should not match"),
                Arguments.of(RepoConfigRequest.builder().build(), "build-info-repo",
                        RepoDetailsType.LOCAL,
                        RepoType.BuildInfo, null, false, false, true, "Local BuildInfo repo, should match"),
                Arguments.of(RepoConfigRequest.builder().build(), "pipe-info-repo",
                        RepoDetailsType.FEDERATED,
                        RepoType.PipeInfo, null, false, false, false, "Federated PipeInfo repo, should not match")
        );
    }

    private static Stream<Arguments> provideFederatedFilterScenarios() {
        return Stream.of(
                Arguments.of(RepoConfigRequest.builder().federated(true).build(), true, true,
                        "Filter federated=true, repo is federated, should match"),
                Arguments.of(RepoConfigRequest.builder().federated(true).build(), false, false,
                        "Filter federated=true, repo is not federated, should not match"),
                Arguments.of(RepoConfigRequest.builder().federated(false).build(), false, true,
                        "Filter federated=false, repo is not federated, should match"),
                Arguments.of(RepoConfigRequest.builder().federated(false).build(), true, false,
                        "Filter federated=false, repo is federated, should not match"),
                Arguments.of(RepoConfigRequest.builder().build(), true, true,
                        "No federated filter (BC), repo is federated, should match"),
                Arguments.of(RepoConfigRequest.builder().build(), false, true,
                        "No federated filter (BC), repo is not federated, should match")
        );
    }

    private static Stream<Arguments> provideCombinedRepoTypeAndFederatedFilterScenarios() {
        // Scenario D: { "repoTypes": ["LOCAL"], "federated": true } - "give me all locals that are federated".
        // The repoTypes filter operates on the INTERNAL RepoDetailsType (not on the JSON-facing repoType).
        RepoConfigRequest localAndFederated = RepoConfigRequest.builder()
                .repoTypes(Set.of(RepoDetailsType.LOCAL)).federated(true).build();
        return Stream.of(
                Arguments.of(localAndFederated, RepoDetailsType.LOCAL, true, true,
                        "repoTypes=[LOCAL] + federated=true, internal LOCAL+federated, should match"),
                Arguments.of(localAndFederated, RepoDetailsType.LOCAL, false, false,
                        "repoTypes=[LOCAL] + federated=true, internal LOCAL not federated, should not match"),
                Arguments.of(localAndFederated, RepoDetailsType.FEDERATED, true, false,
                        "repoTypes=[LOCAL] + federated=true, internal FEDERATED (legacy), should not match"),
                Arguments.of(localAndFederated, RepoDetailsType.REMOTE, true, false,
                        "repoTypes=[LOCAL] + federated=true, internal REMOTE+federated, should not match"),
                Arguments.of(localAndFederated, RepoDetailsType.VIRTUAL, true, false,
                        "repoTypes=[LOCAL] + federated=true, internal VIRTUAL+federated, should not match")
        );
    }

    private static Stream<Arguments> provideProjectAndEnvironmentFilterScenarios() {
        ProjectResourceEntity resourceEntity1 = Mockito.mock(ProjectResourceEntity.class);
        when(resourceEntity1.getResourceStatus()).thenReturn(ProjectResourceStatus.NONE);
        when(resourceEntity1.getEnvironments()).thenReturn(Set.of("DEV"));
        ProjectResourceEntity resourceEntity2 = Mockito.mock(ProjectResourceEntity.class);
        when(resourceEntity1.getResourceStatus()).thenReturn(ProjectResourceStatus.SHARED_TARGET);
        when(resourceEntity1.getEnvironments()).thenReturn(Set.of("PROD"));

        ProjectRepo assignedRepo = new ProjectRepo("repo1", "proj1",  resourceEntity1);
        ProjectRepo sharedRepo = new ProjectRepo("repo1", "proj2",  resourceEntity2);

        return Stream.of(
                Arguments.of(RepoConfigRequest.builder().projectAssociation(
                                RepoProjectAssociation.UN_ASSIGNED_AND_NOT_SHARED).build(),
                        "repo1", Map.of(), false, false, Set.of(), true,
                        "Filter by unassigned, repo is unassigned, should match"),
                Arguments.of(RepoConfigRequest.builder().projectAssociation(
                                RepoProjectAssociation.UN_ASSIGNED_AND_NOT_SHARED).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true,
                        false, Set.of("DEV"), false, "Filter by unassigned, repo is assigned, should not match"),
                Arguments.of(RepoConfigRequest.builder().projectKeys(Set.of("proj1")).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV"), true,
                        "Filter by project key, should match"),
                Arguments.of(RepoConfigRequest.builder().projectKeys(Set.of("proj-non-existent")).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV"), false,
                        "Filter by non-existent project key, should not match"),
                Arguments.of(RepoConfigRequest.builder().projectAssociation(ASSIGNED).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV"), true,
                        "Filter by assigned association, should match"),
                Arguments.of(RepoConfigRequest.builder().projectAssociation(SHARED).build(),
                        "repo1", Map.of("repo1", List.of(sharedRepo)), false, true, Set.of("PROD"), true,
                        "Filter by shared association, should match"),
                Arguments.of(RepoConfigRequest.builder().environments(Set.of("DEV")).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV"), true,
                        "Filter by environment, should match"),
                Arguments.of(RepoConfigRequest.builder().environments(Set.of("QA")).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV"), false,
                        "Filter by environment, should not match"),
                Arguments.of(RepoConfigRequest.builder().environments(Set.of("QA", "DEV")).build(),
                        "repo1", Map.of("repo1", List.of(assignedRepo)), true, false, Set.of("DEV", "PROD"), true,
                        "Filter by environment, should match")
        );
    }
}