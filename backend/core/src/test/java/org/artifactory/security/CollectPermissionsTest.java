package org.artifactory.security;

import org.artifactory.model.security.PrincipalPermissionImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author igoru
 */
public class CollectPermissionsTest {

    private static final String PYPI = "pypi";
    private static final String PYPI_CACHE = "pypi-cache";

    @Test
    public void whenNoPermissionTargetsForRepoThenNullReturned() {
        LegacyAuthorizationServiceImpl service = new LegacyAuthorizationServiceImpl();

        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> permissionsMap = createPermissionsMap();

        Set<String> compatibleKeys = new HashSet<>();

        Set<PrincipalPermission<RepoPermissionTarget>> actual = service.collectPermissions("no-perm-repo",
                compatibleKeys, permissionsMap);
        assertNull(actual);
    }

    @Test
    public void whenNoCompatibleRepoThenOnlyCheckedReposPermsReturn() {
        LegacyAuthorizationServiceImpl service = new LegacyAuthorizationServiceImpl();

        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> permissionsMap = createPermissionsMap();

        Set<String> compatibleKeys = new HashSet<>();

        Set<PrincipalPermission<RepoPermissionTarget>> actual = service.collectPermissions(PYPI, compatibleKeys,
                permissionsMap);
        assertEquals(actual.size(), 1);
        assertEquals(actual.iterator().next().getPermissionTarget().getName(), PYPI);
    }

    @Test
    public void whenCompatibleRepoExistsThenItsPermsAddedToTheSet() {
        LegacyAuthorizationServiceImpl service = new LegacyAuthorizationServiceImpl();

        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> permissionsMap = createPermissionsMap();

        Set<String> compatibleKeys = new HashSet<>();
        compatibleKeys.add(PYPI_CACHE);

        Set<PrincipalPermission<RepoPermissionTarget>> actual = service.collectPermissions(PYPI, compatibleKeys,
                permissionsMap);
        assertEquals(actual.size(), 2);
        Iterator<PrincipalPermission<RepoPermissionTarget>> iterator = actual.iterator();
        Set<String> permNames = Set.of(
                iterator.next().getPermissionTarget().getName(),
                iterator.next().getPermissionTarget().getName()
        );
        assertTrue(permNames.contains(PYPI));
        assertTrue(permNames.contains(PYPI_CACHE));
    }

    private Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> createPermissionsMap() {
        RepoPermissionTargetImpl pypiTarget = new RepoPermissionTargetImpl(PYPI);
        PrincipalPermission<RepoPermissionTarget> pypiPermission = new PrincipalPermissionImpl<>(pypiTarget,
                null, "accessIdentifier");

        RepoPermissionTargetImpl pypiCacheTarget = new RepoPermissionTargetImpl(PYPI_CACHE);
        PrincipalPermission<RepoPermissionTarget> pypiCachePermission = new PrincipalPermissionImpl<>(pypiCacheTarget
                , null, "accessIdentifier");

        Set<PrincipalPermission<RepoPermissionTarget>> pypiPermissionsSet = new HashSet<>();
        pypiPermissionsSet.add(pypiPermission);

        Set<PrincipalPermission<RepoPermissionTarget>> pypiCachePermissionsSet = new HashSet<>();
        pypiCachePermissionsSet.add(pypiCachePermission);

        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> permissionsMap = new HashMap<>();
        permissionsMap.put(PYPI, pypiPermissionsSet);
        permissionsMap.put(PYPI_CACHE, pypiCachePermissionsSet);
        return permissionsMap;
    }
}
