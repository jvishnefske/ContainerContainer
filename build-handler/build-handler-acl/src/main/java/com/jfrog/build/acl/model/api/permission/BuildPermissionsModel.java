package com.jfrog.build.acl.model.api.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Effective permissions of a build: per-user and per-group aggregated permissions plus the build's
 * permission targets. Mirrors the legacy {@code BuildPermissionsModel} UI response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildPermissionsModel {

    private List<PrincipalEffectivePermissions> userEffectivePermissions;
    private List<PrincipalEffectivePermissions> groupEffectivePermissions;
    private List<BuildPermissionTargetInfo> permissionTargets;
}
