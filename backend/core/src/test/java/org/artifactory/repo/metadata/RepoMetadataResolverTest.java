package org.artifactory.repo.metadata;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoMetadata;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.common.ResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the resolver semantics: the project key (Access default-project sentinel mapped to {@code null}), the
 * shared-projects flag, the replication flag (delegated to {@link org.artifactory.repo.RepoReplicationUtil}), and
 * the environments list (per-project entity, falling back to the default-project lookup).
 *
 * <p>Best-effort guarantees are exercised explicitly: a missing repo (AFTER_REPO_DELETE) and Access exceptions
 * collapse to the empty defaults, never propagate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepoMetadataResolverTest {

    @Mock
    private ProjectsService projectsService;
    @Mock
    private CentralConfigService centralConfigService;
    @InjectMocks
    private RepoMetadataResolver underTest;

    @Test
    void resolve_shouldReturnEmpty_whenRepoConfigIsNull() {
        RepoMetadata result = underTest.resolve(null);

        assertSameAsEmpty(result);
    }

    @Test
    void resolve_shouldReturnEmpty_whenRepoKeyIsNull() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(null);

        RepoMetadata result = underTest.resolve(repoConfig);

        assertSameAsEmpty(result);
    }

    @Test
    void resolve_shouldOmitProjectAndSkipResourceLookup_whenRepoIsInDefaultProject() {
        RepoConfig repoConfig = repoConfig("libs-release", RepoDetailsType.LOCAL);
        EnvironmentEntity dev = environment("DEV");
        EnvironmentEntity qa = environment("QA");
        CentralConfigDescriptor descriptor = emptyDescriptor();
        when(projectsService.getProjectKeyOfRepository("libs-release")).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(projectsService.getEnvironmentEntities("libs-release")).thenReturn(List.of(dev, qa));
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);

        RepoMetadata result = underTest.resolve(repoConfig);

        assertNull(result.project(), "DEFAULT_PROJECT must collapse to null on the wire");
        assertFalse(result.hasSharedProjects());
        assertFalse(result.hasReplication());
        assertEquals(List.of("DEV", "QA"), result.environments());
    }

    @Test
    void resolve_shouldPopulateProjectFields_whenRepoIsAssignedToProject() throws AccessClientException {
        RepoConfig repoConfig = repoConfig("libs-release", RepoDetailsType.LOCAL);
        ProjectResourceEntity entity = mock(ProjectResourceEntity.class);
        Set<String> envs = new HashSet<>(List.of("DEV", "PROD"));
        when(entity.getEnvironments()).thenReturn(envs);
        when(entity.getSharedWithProjects()).thenReturn(Set.of("other-proj"));
        CentralConfigDescriptor descriptor = emptyDescriptor();
        when(projectsService.getProjectKeyOfRepository("libs-release")).thenReturn("my-proj");
        when(projectsService.getProjectResourceEntitiesMap("my-proj", ResourceType.REPO, false))
                .thenReturn(Map.of("libs-release", entity));
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);

        RepoMetadata result = underTest.resolve(repoConfig);

        assertEquals("my-proj", result.project());
        assertTrue(result.hasSharedProjects());
        assertEquals(envs.size(), result.environments().size());
        assertTrue(result.environments().containsAll(envs));
    }

    @Test
    void resolve_shouldFallBackToEmpty_whenAccessLookupThrows() throws AccessClientException {
        RepoConfig repoConfig = repoConfig("gone-repo", RepoDetailsType.LOCAL);
        CentralConfigDescriptor descriptor = emptyDescriptor();
        when(projectsService.getProjectKeyOfRepository("gone-repo")).thenReturn("ghost-proj");
        when(projectsService.getProjectResourceEntitiesMap(eq("ghost-proj"), eq(ResourceType.REPO), eq(false)))
                .thenThrow(new AccessClientException("repo no longer exists"));
        when(projectsService.getEnvironmentEntities("gone-repo")).thenReturn(List.of());
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);

        RepoMetadata result = underTest.resolve(repoConfig);

        assertEquals("ghost-proj", result.project());
        assertFalse(result.hasSharedProjects());
        assertFalse(result.hasReplication());
        assertTrue(result.environments().isEmpty());
    }

    @Test
    void resolve_shouldReportReplication_whenLocalRepoHasEnabledLocalReplication() {
        RepoConfig repoConfig = repoConfig("libs-release", RepoDetailsType.LOCAL);
        LocalReplicationDescriptor replication = new LocalReplicationDescriptor();
        replication.setEnabled(true);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications("libs-release")).thenReturn(List.of(replication));
        when(projectsService.getProjectKeyOfRepository("libs-release")).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(projectsService.getEnvironmentEntities("libs-release")).thenReturn(List.of());

        RepoMetadata result = underTest.resolve(repoConfig);

        assertTrue(result.hasReplication());
    }

    @Test
    void resolve_shouldReportReplication_whenRemoteRepoHasEnabledReplication() {
        RepoConfig repoConfig = repoConfig("npm-remote", RepoDetailsType.REMOTE);
        RemoteReplicationDescriptor replication = new RemoteReplicationDescriptor();
        replication.setEnabled(true);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getRemoteReplication("npm-remote")).thenReturn(replication);
        when(projectsService.getProjectKeyOfRepository("npm-remote")).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(projectsService.getEnvironmentEntities("npm-remote")).thenReturn(List.of());

        RepoMetadata result = underTest.resolve(repoConfig);

        assertTrue(result.hasReplication());
    }

    private static RepoConfig repoConfig(String key, RepoDetailsType type) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(key);
        when(repoConfig.getRepoType()).thenReturn(type);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        return repoConfig;
    }

    private static CentralConfigDescriptor emptyDescriptor() {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(descriptor.getRemoteReplication(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        return descriptor;
    }

    private static EnvironmentEntity environment(String name) {
        EnvironmentEntity env = mock(EnvironmentEntity.class);
        when(env.getName()).thenReturn(name);
        return env;
    }

    private static void assertSameAsEmpty(RepoMetadata result) {
        assertNull(result.project());
        assertFalse(result.hasSharedProjects());
        assertFalse(result.hasReplication());
        assertTrue(result.environments().isEmpty());
    }
}
