package org.artifactory.rest.resource.federation;


public class PermissionScopes {

    public static final String INTERNAL_SCOPE_VALIDATE_MIRROR = "internal:federated-repo/validate-mirror:x";
    public static final String INTERNAL_SCOPE_REPOSITORY_LIST = "internal:federated-repo/repository-list:x";
    public static final String INTERNAL_SCOPE_FED_REPOCONFIG_INFO = "internal:federated-repo/config-info:x";
    public static final String INTERNAL_SCOPE_FED_REPOCONFIG_INFO_READ = "internal:federated-repo/config-info:r";
    public static final String INTERNAL_SCOPE_FULL_SYNC = "internal:federated-repo/full-sync:x";
    public static final String INTERNAL_SCOPE_ARTIFACT_READ = "artifact:*:r";
    public static final String INTERNAL_SCOPE_CONFIG_SYNC = "internal:federated-repo/config-sync:x";
    public static final String INTERNAL_SCOPE_CONFIG_CONFLICTS = "internal:federated-repo/config-conflicts:x";
    public static final String INTERNAL_SCOPE_METADATA = "internal:federated-repo/metadata-info:x";
    public static final String INTERNAL_SCOPE_JPD_STATUS = "internal:federated-repo/jpd-status:x";
    public static final String INTERNAL_SCOPE_MIGRATION_STATUS = "internal:federated-repo/migration-status:x";
    public static final String INTERNAL_QUEUE_STATUS = "internal:federated-repo/internal-queue-status:x";
    public static final String INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO = "internal:federated-repo/full-sync-system-repo:w";
    public static final String INTERNAL_SCOPE_RTFS = "internal:federated-repo/rtfs:x";
    public static final String INTERNAL_SCOPE_RTFS_READ = "internal:federated-repo/rtfs:r";
    public static final String INTERNAL_SEND_BINARY_TASK_REQUEST = "internal:federated-repo/binary-task-request:x";
    public static final String INTERNAL_SCOPE_TENANT_MOVE_STATUS = "internal:federated-repo/tenant-move-status:x";
    public static final String INTERNAL_SCOPE_REPLACE_URL = "internal:federated-repo/replace-url:x";
    public static final String INTERNAL_SCOPE_GRID_TOPOLOGY_CHANGE = "internal:grid-topology/change:x";
    public static final String INTERNAL_SCOPE_GRID_REPOS_SYNC = "internal:grid-repos/sync:x";
    public static final String INTERNAL_SCOPE_GRID_REPOS_SNAPSHOT = "internal:grid-repos/snapshot:x";
    public static final String INTERNAL_SCOPE_REPO_EXISTS = "internal:federated-repo/repo-exists:x";
    public static final String INTERNAL_SCOPE_REPO_EXISTS_READ = "internal:federated-repo/repo-exists:r";
}
