package org.artifactory.model.xstream.security;

import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

/**
 * @author Volodymyr Shulga
 */
@Test
public class RepoPermissionTargetImplTest {

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = ".*marked non-null.*")
    public void shouldForbidNullKeys() {
        MutableRepoPermissionTarget permissionTarget = new RepoPermissionTargetImpl();
        permissionTarget.setRepoKeys(null);
    }

    public void shouldProcessNonNullEntries() {
        MutableRepoPermissionTarget permissionTarget = new RepoPermissionTargetImpl();
        permissionTarget.setRepoKeys(Set.of("one", "two", "three"));
        // ordered
        assertEquals(permissionTarget.getRepoKeys(), List.of("one", "three", "two"));
    }

    public void shouldProcessNullEntries() {
        MutableRepoPermissionTarget permissionTarget = new RepoPermissionTargetImpl();
        Set<String> repoKeys = new HashSet<>();
        repoKeys.add("one");
        repoKeys.add(null);
        repoKeys.add("two");
        repoKeys.add(null);
        repoKeys.add("three");

        permissionTarget.setRepoKeys(repoKeys);
        // ordered with filtered out nulls.
        assertEquals(permissionTarget.getRepoKeys(), List.of("one", "three", "two"));
    }

}