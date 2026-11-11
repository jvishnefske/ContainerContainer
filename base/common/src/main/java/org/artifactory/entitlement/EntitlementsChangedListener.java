package org.artifactory.entitlement;

/**
 * Marks recipients of entitlements changes in entitlements service
 */
public interface EntitlementsChangedListener {
    void onEntitlementServiceChanged (boolean isInitialize);
}
