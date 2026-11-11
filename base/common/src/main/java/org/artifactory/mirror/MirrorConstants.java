package org.artifactory.mirror;

/**
 * Used to add marker to the event log to avoid circular calls between the federation nodes.
 */
public class MirrorConstants {
    public static final String MIRROR_MARKER = "mirror.marker";
    public static final String ASYNC_THREAD = "async.thread";
    public static final String DELETE_BUILD_ARTIFACT = "delete.build.artifact";
    public static final String COPY_MOVE_ARTIFACT = "copy.move.artifact";
    public static final String REPLICATION_ALLOWED = "replication.allowed";
}
