package com.jfrog.build.acl.model.api.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrincipalEffectivePermissionsTest {

    @Test
    void defaultConstructor_shouldInitialiseEmptyTargetsAndPermission() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions();

        assertTrue(permissions.getPermissionTargets().isEmpty());
        assertNotNull(permissions.getPermission());
        assertEquals(0, permissions.getPermissionTargetsCount());
        assertFalse(permissions.isAdmin());
        assertFalse(permissions.isPermissionTargetsCap());
    }

    @Test
    void principalConstructor_shouldSetPrincipal() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions("alice");

        assertEquals("alice", permissions.getPrincipal());
        assertNotNull(permissions.getPermission());
    }

    @Test
    void setters_shouldUpdateFields() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions();
        EffectivePermission permission = new EffectivePermission();

        permissions.setPrincipal("bob");
        permissions.setAdmin(true);
        permissions.setPermissionTargets(List.of("pt-a", "pt-b"));
        permissions.setPermission(permission);
        permissions.setPermissionTargetsCount(7);

        assertEquals("bob", permissions.getPrincipal());
        assertTrue(permissions.isAdmin());
        assertEquals(List.of("pt-a", "pt-b"), permissions.getPermissionTargets());
        assertEquals(permission, permissions.getPermission());
        assertEquals(7, permissions.getPermissionTargetsCount());
    }

    @Test
    void advancePermissionTargetsCount_shouldIncrement() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions();

        permissions.advancePermissionTargetsCount();
        permissions.advancePermissionTargetsCount();

        assertEquals(2, permissions.getPermissionTargetsCount());
    }

    @Test
    void isPermissionTargetsCap_shouldBeFalse_belowCap() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions();
        permissions.getPermissionTargets().add("pt-1");
        permissions.getPermissionTargets().add("pt-2");

        assertFalse(permissions.isPermissionTargetsCap());
    }

    @Test
    void isPermissionTargetsCap_shouldBeTrue_whenExactlyAtCap() {
        PrincipalEffectivePermissions permissions = new PrincipalEffectivePermissions();
        // The cap is 5; fill the list to exactly that size.
        for (int i = 0; i < 5; i++) {
            permissions.getPermissionTargets().add("pt-" + i);
        }

        assertTrue(permissions.isPermissionTargetsCap());
    }
}
