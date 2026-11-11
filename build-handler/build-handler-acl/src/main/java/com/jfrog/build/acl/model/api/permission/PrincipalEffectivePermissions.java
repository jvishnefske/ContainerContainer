package com.jfrog.build.acl.model.api.permission;

import java.util.ArrayList;
import java.util.List;

/**
 * Effective permissions of a single principal (user or group) on a build, mirroring the legacy
 * {@code PrincipalEffectivePermissions} UI model. The permission-target list is capped for the main view;
 * once the cap is reached {@link #isPermissionTargetsCap()} flags the UI to show a "more" tooltip.
 */
public class PrincipalEffectivePermissions {

    private static final int PERMISSION_TARGET_CAP = 5;

    private String principal;
    private boolean admin;
    private List<String> permissionTargets;
    private EffectivePermission permission;
    private int permissionTargetsCount;

    public PrincipalEffectivePermissions() {
        this.permissionTargets = new ArrayList<>(PERMISSION_TARGET_CAP);
        this.permission = new EffectivePermission();
        this.permissionTargetsCount = 0;
    }

    public PrincipalEffectivePermissions(String principal) {
        this();
        this.principal = principal;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public List<String> getPermissionTargets() {
        return permissionTargets;
    }

    public void setPermissionTargets(List<String> permissionTargets) {
        this.permissionTargets = permissionTargets;
    }

    public EffectivePermission getPermission() {
        return permission;
    }

    public void setPermission(EffectivePermission permission) {
        this.permission = permission;
    }

    public int getPermissionTargetsCount() {
        return permissionTargetsCount;
    }

    public void setPermissionTargetsCount(int permissionTargetsCount) {
        this.permissionTargetsCount = permissionTargetsCount;
    }

    public void advancePermissionTargetsCount() {
        permissionTargetsCount++;
    }

    public boolean isPermissionTargetsCap() {
        return permissionTargets.size() == PERMISSION_TARGET_CAP;
    }
}
