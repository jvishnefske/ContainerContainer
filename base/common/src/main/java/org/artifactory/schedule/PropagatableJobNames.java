package org.artifactory.schedule;

/**
 * @author igoru
 */
public interface PropagatableJobNames {
    String ARTIFACT_CLEANUP = "artifact-cleanup";
    String ARTIFACT_CLEANUP_UNEXCLUSIVE = "artifact-cleanup-unexclusive";
    String BACKUP = "backup";
    String BINARY_STORE_GARBAGE_COLLECTOR = "binary-store-garbage-collector";
    String BUILD_CLEANUP = "build-cleanup";
    String RELEASE_BUNDLE_V2_CLEANUP = "release-bundle-v2-cleanup";
    String MAVEN_INDEXER = "maven-indexer";
    String PACKAGE_ARCHIVE = "package-archive";
    String PACKAGE_CLEANUP = "package-cleanup";
    String PUSH_REPLICATION = "push-replication";
    String REMOTE_REPLICATION = "remote-replication";
    String ARTIFACT_RETENTION_ARCHIVE = "artifact-retention-archive";
    String SHA_256_MIGRATION = "sha-256-migration";
    String VIRTUAL_CACHE_CLEANUP = "virtual-cache-cleanup";
}
