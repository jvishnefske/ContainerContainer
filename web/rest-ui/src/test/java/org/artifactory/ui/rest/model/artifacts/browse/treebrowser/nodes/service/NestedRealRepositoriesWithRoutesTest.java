package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;


import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.NestedRealRepositoriesWithRoutes;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.virtual.VirtualRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class NestedRealRepositoriesWithRoutesTest {

    @Mock
    private VirtualRepo parent;
    private final NestedRealRepositoriesWithRoutes nestedRealRepositoriesWithRoutes =
            new NestedRealRepositoriesWithRoutes(parent);
    @Mock
    private RealRepo realRepo;
    @Mock
    private VirtualRepo virtualRepo;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testResolveNestedRealRepos() {
        LocalRepo realRepo1 = mock(LocalRepo.class);
        RemoteRepo realRepo2 = mock(RemoteRepo.class);
        when(realRepo1.getKey()).thenReturn("local-repo-1");
        when(realRepo2.getKey()).thenReturn("remote-repo-2");
        when(realRepo1.isReal()).thenReturn(true);
        when(realRepo1.isLocal()).thenReturn(true);
        when(realRepo2.isReal()).thenReturn(true);
        when(realRepo2.isLocal()).thenReturn(false);

        VirtualRepo virtualRepo1 = mock(VirtualRepo.class);
        VirtualRepo virtualRepo2 = mock(VirtualRepo.class);
        VirtualRepo virtualRepo3 = mock(VirtualRepo.class);

        when(virtualRepo1.getKey()).thenReturn("virtual-repo-1");
        when(virtualRepo2.getKey()).thenReturn("virtual-repo-2");
        when(virtualRepo3.getKey()).thenReturn("virtual-repo-3");

        when(virtualRepo1.isReal()).thenReturn(false);
        when(virtualRepo2.isReal()).thenReturn(false);
        when(virtualRepo3.isReal()).thenReturn(false);

        when(virtualRepo1.getLocalRepositories()).thenReturn(List.of(realRepo1));
        when(virtualRepo1.getRemoteRepositories()).thenReturn(List.of());
        when(virtualRepo1.getVirtualRepositories()).thenReturn(List.of(virtualRepo2));

        when(virtualRepo2.getLocalRepositories()).thenReturn(List.of(realRepo1));
        when(virtualRepo2.getRemoteRepositories()).thenReturn(List.of(realRepo2));
        when(virtualRepo2.getVirtualRepositories()).thenReturn(List.of(virtualRepo3));

        when(virtualRepo3.getLocalRepositories()).thenReturn(List.of(realRepo1));
        when(virtualRepo3.getRemoteRepositories()).thenReturn(List.of(realRepo2));
        when(virtualRepo3.getVirtualRepositories()).thenReturn(List.of());


        when(virtualRepo1.isPathPatternValid(any(), any())).thenReturn(true);
        when(virtualRepo2.isPathPatternValid(any(), any())).thenReturn(true);
        when(virtualRepo3.isPathPatternValid(any(), any())).thenReturn(true);

        NestedRealRepositoriesWithRoutes nested =
                NestedRealRepositoriesWithRoutes.resolveNestedRepositories(virtualRepo1, "",
                        new HashSet<>(), new HashMap<>());


        Map<RealRepo<?, ?>, List<List<VirtualRepo>>> paths = nested.getPaths();
        assertEquals(2, paths.size());
        assertTrue(paths.containsKey(realRepo1));
        assertTrue(paths.containsKey(realRepo2));
        assertEquals(1, nested.getLocalReposKeys().size());
        assertEquals(1, nested.getRemoteReposKeys().size());
        assertEquals(3, nested.getRoutesByRepoKey("local-repo-1").size());
        assertEquals(2, nested.getRoutesByRepoKey("remote-repo-2").size());
    }

    @Test
    void shouldNotAddVirtualRepoWhenNestedIsNull() {
        nestedRealRepositoriesWithRoutes.addVirtualRepo(virtualRepo, null);
        assertTrue(nestedRealRepositoriesWithRoutes.getPaths().isEmpty());
    }

    @Test
    void shouldAddVirtualRepoWhenNestedIsNotNull() {
        NestedRealRepositoriesWithRoutes nested = new NestedRealRepositoriesWithRoutes(virtualRepo);
        nested.addRealRepo(realRepo, singletonList(virtualRepo));
        nestedRealRepositoriesWithRoutes.addVirtualRepo(virtualRepo, nested);
        assertFalse(nestedRealRepositoriesWithRoutes.getPaths().isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoRoutesByRepoKey() {
        assertTrue(nestedRealRepositoriesWithRoutes.getRoutesByRepoKey("nonexistent").isEmpty());
    }

    @Test
    void shouldReturnRoutesByRepoKey() {
        List<VirtualRepo> route = singletonList(virtualRepo);
        nestedRealRepositoriesWithRoutes.addRealRepo(realRepo, route);
        when(realRepo.getKey()).thenReturn("repoKey");
        assertFalse(nestedRealRepositoriesWithRoutes.getRoutesByRepoKey("repoKey").isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoLocalReposKeys() {
        assertTrue(nestedRealRepositoriesWithRoutes.getLocalReposKeys().isEmpty());
    }

    @Test
    void shouldReturnLocalReposKeys() {
        when(realRepo.isLocal()).thenReturn(true);
        when(realRepo.getKey()).thenReturn("localRepoKey");
        nestedRealRepositoriesWithRoutes.addRealRepo(realRepo, singletonList(virtualRepo));
        assertEquals(Arrays.asList("localRepoKey"), nestedRealRepositoriesWithRoutes.getLocalReposKeys());
    }

    @Test
    void shouldReturnEmptyListWhenNoRemoteReposKeys() {
        assertTrue(nestedRealRepositoriesWithRoutes.getRemoteReposKeys().isEmpty());
    }

    @Test
    void shouldReturnRemoteReposKeys() {
        when(realRepo.isLocal()).thenReturn(false);
        when(realRepo.isReal()).thenReturn(true);
        when(realRepo.getKey()).thenReturn("remoteRepoKey");
        nestedRealRepositoriesWithRoutes.addRealRepo(realRepo, singletonList(virtualRepo));
        assertEquals(Arrays.asList("remoteRepoKey"), nestedRealRepositoriesWithRoutes.getRemoteReposKeys());
    }

    @Test
    void shouldResolveNestedRepositories() {
        when(parent.isPathPatternValid(Mockito.any(), Mockito.any())).thenReturn(true);
        when(parent.getLocalRepositories()).thenReturn(Collections.emptyList());
        when(parent.getRemoteRepositories()).thenReturn(Collections.emptyList());
        when(parent.getVirtualRepositories()).thenReturn(Collections.emptyList());
        when(parent.getKey()).thenReturn("cool-repo");

        NestedRealRepositoriesWithRoutes result =
                NestedRealRepositoriesWithRoutes.resolveNestedRepositories(parent, "forPath", new HashSet<>(),
                        new HashMap<>());

        assertNotNull(result);
        assertEquals(parent, result.getParent());
        assertTrue(result.getPaths().isEmpty());
    }

    @Test
    void shouldNotResolveNestedRepositoriesWhenCyclicDependency() {
        HashSet<VirtualRepo> inProgress = new HashSet<>();
        inProgress.add(parent);

        NestedRealRepositoriesWithRoutes result =
                NestedRealRepositoriesWithRoutes.resolveNestedRepositories(parent, "forPath", inProgress,
                        new HashMap<>());

        assertNull(result);
    }

    @Test
    void shouldNotResolveNestedRepositoriesWhenPathPatternInvalid() {
        when(parent.isPathPatternValid(Mockito.any(), Mockito.any())).thenReturn(false);
        when(parent.getKey()).thenReturn("cool-repo");
        NestedRealRepositoriesWithRoutes result =
                NestedRealRepositoriesWithRoutes.resolveNestedRepositories(parent, "forPath", new HashSet<>(),
                        new HashMap<>());

        assertNull(result);
    }

    @Test
    void testResolveNestedRepositoriesWithNullVirtualRepo() {
        HashSet<VirtualRepo> set = new HashSet<>();
        HashMap<String, NestedRealRepositoriesWithRoutes> map = new HashMap<>();
        ItemNotFoundRuntimeException ex = assertThrows(ItemNotFoundRuntimeException.class,
                () -> NestedRealRepositoriesWithRoutes.resolveNestedRepositories(null, "forPath", set, map));
        assertEquals("Parent virtual repository cannot be null", ex.getMessage());
    }
}