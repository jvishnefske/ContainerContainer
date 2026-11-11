package org.artifactory.throttling.lh.mode;

/**
 * Describes the outcome of a Load Healer mode-update request.
 *
 * @author Amir Avidan
 */
public enum ModeUpdateStatus {

    /** The mode was changed to a new value. */
    UPDATED,

    /** The requested mode was already active and not expired — no change was made. */
    ALREADY_ACTIVE,

    /** The mode was already active but the expiry was extended to a later time. */
    EXTENDED,

    /** Load Healer is disabled on this node — no change is possible. */
    UNCHANGED
}
