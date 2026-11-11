package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.model.api.ModuleDependency;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.getProjectKeyFromBuildInfoRepoKey;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.jfrog.access.common.ResourceType.REPO;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDependencyServiceTest {

    private BuildDependencyService dependencyService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    private BuildDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        databaseService = mock(BuildDatabaseService.class);
        dependencyService = new BuildDependencyService(
                bridgeToArtifactory,
                databaseService,
                mock(BuildPermissionService.class)
        );
    }

    @Test
    void populateDependenciesRepoPathData_Success() {
        String sha1 = "some-sha1";
        ModuleDependency dependency = mock(ModuleDependency.class);
        when(dependency.getSha1()).thenReturn(sha1);

        BuildNode node = mock(BuildNode.class);
        when(node.getName()).thenReturn("some-name");
        when(databaseService.selectDependencyNodesBySha1(any())).thenReturn(Map.of(sha1, List.of(node)));

        assertDoesNotThrow(() -> dependencyService.populateDependenciesRepoPathData(
                List.of(dependency), empty(), BUILDINFO_REPO_KEY));
        verify(databaseService).selectDependencyNodesBySha1(any());
    }

    @Test
    void populateDependenciesRepoPathData_Skip() {
        List<ModuleDependency> dependencies = List.of();
        assertDoesNotThrow(() -> dependencyService.populateDependenciesRepoPathData(dependencies, empty(), EMPTY));
        verify(databaseService, never()).selectDependencyNodesBySha1(any());
    }

    @ParameterizedTest
    @MethodSource("findPreferredDependencyNode_Success_Provider")
    void findPreferredDependencyNode_Success(
            @NonNull ModuleDependency dependency,
            @NonNull String buildRepoKey,
            @NonNull String promotionRepoKey,
            @NonNull List<BuildNode> candidateNodes,
            @NonNull BuildNode expectedNode) {
        String projectKey = getProjectKeyFromBuildInfoRepoKey(buildRepoKey);
        when(bridgeToArtifactory.getProjectForResource(eq(expectedNode.getRepo()), eq(REPO))).thenReturn(projectKey);
        assertEquals(expectedNode,
                dependencyService.findPreferredDependencyNode(
                        candidateNodes,
                        dependency,
                        of(promotionRepoKey),
                        buildRepoKey
                )
        );
    }

    @NonNull
    static Stream<Arguments> findPreferredDependencyNode_Success_Provider() {
        ModuleDependency dependency = mock(ModuleDependency.class);
        when(dependency.getName()).thenReturn("some-name-A");
        return Stream.of(
                Arguments.of(
                        dependency,
                        "artifactory-build-info",
                        "some-promotion-repo-key",
                        List.of(
                                buildNode("some-node-repo", "some-name-A"),
                                buildNode("some-promotion-repo-key", "some-name-1"),
                                buildNode("some-node-repo", "some-name-X")
                        ),
                        buildNode("some-promotion-repo-key", "some-name-1")
                ),
                Arguments.of(
                        dependency,
                        "phoenix-build-info",
                        "some-promotion-repo-key",
                        List.of(
                                buildNode("some-node-repo", "some-name-1"),
                                buildNode("phoenix-node-repo", "some-name-X"),
                                buildNode("some-node-repo", "some-name-A")
                        ),
                        buildNode("phoenix-node-repo", "some-name-X")
                ),
                Arguments.of(
                        dependency,
                        "artifactory-build-info",
                        "some-promotion-repo-key",
                        List.of(
                                buildNode("some-node-repo", "some-name-1"),
                                buildNode("some-node-repo", "some-name-A"),
                                buildNode("some-node-repo", "some-name-X")
                        ),
                        buildNode("some-node-repo", "some-name-A")
                )
        );
    }

    @NonNull
    private static BuildNode buildNode(
            @NonNull String repo,
            @NonNull String name) {
        return BuildNode.builder()
                .repo(repo)
                .path("some-path")
                .name(name)
                .build();
    }
}