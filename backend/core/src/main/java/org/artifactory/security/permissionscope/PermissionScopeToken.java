package org.artifactory.security.permissionscope;

import lombok.experimental.UtilityClass;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;

@UtilityClass
public class PermissionScopeToken {

    public static final String SUPPORT_BUNDLE_SCOPE_TARGET = "supportbundle";
    public static final String MAIL_SCOPE_TARGET = "mail";

    public static final PermissionScope INTERNAL_SUPPORT_BUNDLE_READ =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL, SUPPORT_BUNDLE_SCOPE_TARGET, Action.READ);
    public static final PermissionScope INTERNAL_SUPPORT_BUNDLE_WRITE =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL, SUPPORT_BUNDLE_SCOPE_TARGET, Action.WRITE);
    public static final PermissionScope INTERNAL_SUPPORT_BUNDLE_DELETE =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL, SUPPORT_BUNDLE_SCOPE_TARGET, Action.DELETE);
    public static final PermissionScope SEND_MAIL_EXECUTE =
            PermissionScope.create(PermissionScopeResourceType.INTERNAL, MAIL_SCOPE_TARGET, Action.EXECUTE);
    public final String SYSTEM_PLATFORM_CONFIG_READ = "system:platformconfig:r";
    public final String SYSTEM_INFO_READ = "system:info:r";
    public final String SYSTEM_LICENSE_READ = "system:info/licenses:r";
    public final String SYSTEM_STORAGE_READ = "system:info/storage:r";
    public final String INTERNAL_MISSION_CONTROL_READ = "internal:mc:r";
    public final String INTERNAL_MISSION_CONTROL_EXECUTE = "internal:mc:x";
    public final String REPO_REPLICATION_READ = "repo:replication:r";
    public final String REPO_REPLICATION_EXECUTE = "repo:replication:x";
    public final String ARTIFACT_ALL_READ = "artifact:*:r";
}
