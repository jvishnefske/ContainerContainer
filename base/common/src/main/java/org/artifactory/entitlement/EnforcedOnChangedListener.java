package org.artifactory.entitlement;

/**
 * Marks recipients of changes in the enforcement of the entitlements service
 */
public interface EnforcedOnChangedListener {
    void onEnforcedChanged (boolean isEnforced);
}
