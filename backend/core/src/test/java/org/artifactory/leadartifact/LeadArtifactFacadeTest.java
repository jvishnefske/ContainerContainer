package org.artifactory.leadartifact;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.leadartifact.detector.BundleLeadArtifactDetector;
import org.artifactory.leadartifact.detector.LeadArtifactDetector;
import org.artifactory.metadata.service.provider.DockerMetadataProvider;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadArtifactFacadeTest {

    private static final String DOCKER_REPO_KEY = "docker-federated";
    private static final String GENERIC_REPO_KEY = "generic-federated";
    private static final String NPM_REPO_KEY = "npm-federated";

    @Mock
    private RepositoryService repoService;

    private LeadArtifactFacade leadArtifactFacade;
    private AutoCloseable closable;

    @BeforeEach
    void setUp() {
        closable = MockitoAnnotations.openMocks(this);
        prepareRepoDescriptor(ReleaseBundles, DEFAULT_RBV2_REPO);
        prepareRepoDescriptor(Docker, DOCKER_REPO_KEY);
        prepareRepoDescriptor(Generic, GENERIC_REPO_KEY);

        List<LeadArtifactDetector> detectors = List.of(
                new DockerMetadataProvider(),
                new BundleLeadArtifactDetector());
        this.leadArtifactFacade = new LeadArtifactFacade(repoService, detectors);
    }

    @AfterEach
    void tearDown() throws Exception {
        closable.close();
    }

    @ParameterizedTest
    @MethodSource("isLeadProvider")
    void isLeadArtifact(RepoPath repoPath, int repoServiceTimes, boolean expectedResult) {
        boolean actualResult = leadArtifactFacade.isLeadArtifact(repoPath);
        assertEquals(actualResult, expectedResult);

        verify(repoService, times(repoServiceTimes))
                .localFederatedOrCachedRepoDescriptorByKey(repoPath.getRepoKey());

    }

    @ParameterizedTest
    @MethodSource("isLeadProvider")
    void isPackageConsumptionLead(RepoPath repoPath, int repoServiceTimes, boolean expectedResult) {
        boolean actualResult = leadArtifactFacade.isPackageConsumptionLead(repoPath);
        assertEquals(actualResult, expectedResult);

        verify(repoService, times(repoServiceTimes))
                .localFederatedOrCachedRepoDescriptorByKey(repoPath.getRepoKey());

    }

    @ParameterizedTest
    @MethodSource("getLeadArtifactExtensionsProvider")
    void getLeadArtifactExtensions(RepoPath repoPath, Set<String> expectedResult) {
        Set<String> actualResult = leadArtifactFacade.getLeadArtifactExtensions(repoPath);
        assertEquals(actualResult, expectedResult);
    }

    private static Stream<Arguments> isLeadProvider() {
        RepoPath rbv2Lead =
                new RepoPathImpl(DEFAULT_RBV2_REPO, "Commons-Bundle/1.0.0/release-bundle.json.evd");
        RepoPath dir =
                new RepoPathImpl(DEFAULT_RBV2_REPO, "Commons-Bundle/");
        RepoPath root = new RepoPathImpl(DEFAULT_RBV2_REPO, "/");
        RepoPath rbv2Artifact =
                new RepoPathImpl(DEFAULT_RBV2_REPO,
                        "Commons-Bundle/1.0.0/artifacts/generic/summaries/meeting-summary.txt");
        RepoPath rbv2PromotionLead =
                new RepoPathImpl(DEFAULT_RBV2_REPO,
                        "Commons-Bundle-1/1.0.1/extensions/promotion-1699288936812.json.evd");
        RepoPath dockerLead = new RepoPathImpl(DOCKER_REPO_KEY,
                "coyote/1.0.0/manifest.json");
        RepoPath dockerLayer = new RepoPathImpl(DOCKER_REPO_KEY,
                "coyote/1.0.0/sha256__b014eae0767694ac4e6d1ea12282e992f02fbaa07c12cea925edcffa3819fd10n");
        // no lead detector created for generic
        RepoPath generic = new RepoPathImpl(GENERIC_REPO_KEY, "summaries/meeting-summary.txt");
        RepoPath npmNotThere = new RepoPathImpl(NPM_REPO_KEY, "file.tgz");

        return Stream.of(
                // repo path, expected times repoService called, expected result
                Arguments.of(root, 0, false),
                Arguments.of(dir, 0, false),
                Arguments.of(rbv2Lead, 1, true),
                Arguments.of(rbv2Artifact, 1, false),
                Arguments.of(rbv2PromotionLead, 1, true),
                Arguments.of(dockerLead, 1, true),
                Arguments.of(dockerLayer, 1, false),
                Arguments.of(generic, 1, false),
                Arguments.of(npmNotThere, 1, false)
                );
    }

    public static Stream<Arguments> getLeadArtifactExtensionsProvider() {
        RepoPath rbv2 =
                new RepoPathImpl(DEFAULT_RBV2_REPO, "/");
        RepoPath docker = new RepoPathImpl(DOCKER_REPO_KEY, "/");
        RepoPath generic = new RepoPathImpl(GENERIC_REPO_KEY, "/");
        RepoPath npmNotThere = new RepoPathImpl(NPM_REPO_KEY, "/");

        return Stream.of(
                // repo path, expected result
                Arguments.of(rbv2, Set.of("evd")),
                Arguments.of(docker, Set.of("json")),
                Arguments.of(generic, Set.of()),
                Arguments.of(npmNotThere, Set.of())
        );
    }

    private void prepareRepoDescriptor(RepoType releaseBundles, String rbv2RepoKey) {
        LocalRepoDescriptor rbv2Desc = Mockito.mock(LocalRepoDescriptor.class);
        when(rbv2Desc.getType()).thenReturn(releaseBundles);
        when(this.repoService.localFederatedOrCachedRepoDescriptorByKey(rbv2RepoKey))
                .thenReturn(rbv2Desc);
    }
}