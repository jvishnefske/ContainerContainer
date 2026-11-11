package org.artifactory.throttling.lh.model;

/**
 * Represents the reactive blocking state of an activity within the Load Healer.
 * This is a runtime-only state — it is never configured externally.
 * <p>
 * {@link #IDLE} - Default state: the activity is running normally and is not blocking any requests.
 * {@link #REACTIVE} - Active state: the activity is blocking excess requests because a system outage
 * was detected and the activity was already over its threshold. Restored to {@link #IDLE} once the
 * reactive mode duration expires.
 *
 * @author Hussein Alshqerat
 */
// Doc: ai-knowledge-base/workspace/design/load-healer-reactive-mode.md
public enum ReactiveActivityState {
    IDLE,
    REACTIVE
}
