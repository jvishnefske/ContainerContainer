package org.artifactory.repo;

/**
 * @author Alexei Vainshtein
 */
public enum ActionStatus {

    CREATE,
    UPDATE,
    SYNC,
    DELETE,
    // Used only when RTFS is enabled; in all other cases, the status changes as an update.
    STATUS_CHANGE
}
