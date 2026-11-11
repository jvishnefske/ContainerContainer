package org.artifactory.storage.fs.service;

import org.jfrog.common.ArgUtils;

/**
 * Keys enum and conventions for using ConfigsService
 *
 * @author Avishay Halpren
 */
public enum ConfigsServiceKey {

    /**
     * naming conventions for keys:
     *  "domain-namespace.meaningful.key.name"
     *      domain-namespace can be: artifactory, storage, packages ,bundle ...
     *      e.g: artifactory.gc.iteration, packages.some.value
     *  for a key which represent a config object (translated to json) use .config suffix
     *      e.g: storage.checksum.replication.config will be translated to a specific Object (not primitive)
     *  for a primitive value no .suffix is required
     *      e.g: artifactory.gc.iteration will just an int representation
     */

    STORAGE_CHECKSUM_REPLICATION("storage.checksum.replication.config"),
    ARTIFACTORY_GC_ITERATION("artifactory.gc.iteration"),
    ARTIFACTORY_CONVERTER_DELETE_OLD_SSH_KEYS("artifactory.converter.delete.old.ssh.keys"),
    ARTIFACTORY_QUERY_RATE_LIMITER("artifactory.query.rate.limiter"),
    ARTIFACTORY_SERVICE_ID("artifactory.service_id"),
    ARTIFACTORY_REQUEST_RATE_LIMITER("artifactory.request.rate.limiter"),
    ARTIFACTORY_REQUEST_RATE_LIMITER_V2("artifactory.request.rate.limiter.v2"),
    STORAGE_PRUNE_REPORT("storage.prune.report"),
    STORAGE_PRUNE_STOP_MARKER("storage.prune.stop.marker"),
    RTFS_MIGRATION_STATUS("rtfs.migration.status"),
    RTFS_MIGRATION_VERBOSE_STATUS("rtfs.migration.verbose.status"),
    RTFS_ENABLED_STATUS("rtfs.enabled"),
    RTFS_BINARIES_TASKS_ENABLED("rtfs.binaries.tasks.enabled"),
    RTFS_DISABLED_BY_SYSTEM_REPOS("rtfs.disabled.by.system.repos"),
    RTFS_QUEUE_STATE_MD_NEGOTIATION("rtfs.queue.state.md.negotiation"),
    RTFS_REPOSITORIES_STATES_SNAPSHOT("rtfs.repositories.states.snapshot"),
    RTFS_TENANT_MOVE_STATUS("rtfs.tenant.move.status"),
    ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID("artifactory.empty.folder.scan.start.node.id"),
    ARTIFACTORY_LOAD_HEALER_MODE("artifactory.load.healer.mode.config"),
    PACKAGES_ALLOWED_REMOTE_URLS("packages.allowed.remote.urls.config"),
    REMOTE_REPO_OFFLINE_TRIGGERS("remote.repo.offline.triggers.config"),
    REMOTE_REPO_OFFLINE_MARKED_REPOS("remote.repo.offline.marked.repos.config"),
    INBOUND_REQUEST_GUARD_TRIGGERS("inbound.request.guard.triggers.config"),
    CLEANUP_RETENTION_MIGRATION_STATE("cleanup.retention.migration.state"),
    ;

    //
    private final String keyName;

    ConfigsServiceKey(String keyName) {
        this.keyName = ArgUtils.requireMaxLength(keyName, 256, "key max length must be lower than 256 characters");
    }

    public String getKeyName() {
        return keyName;
    }
}
