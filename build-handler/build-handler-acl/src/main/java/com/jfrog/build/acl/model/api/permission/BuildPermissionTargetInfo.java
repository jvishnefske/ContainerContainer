package com.jfrog.build.acl.model.api.permission;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * A single build permission target shown in the Effective Permissions tab, mirroring the legacy
 * {@code BuildPermissionTargetInfo} UI model.
 */
@Data
public class BuildPermissionTargetInfo {

    private String permissionName;
    private Set<String> groups;
    private Set<String> users;
    private List<String> includePatterns;
    private List<String> excludePatterns;
}
