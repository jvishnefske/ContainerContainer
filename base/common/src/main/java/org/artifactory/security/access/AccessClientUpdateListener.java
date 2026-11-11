package org.artifactory.security.access;

/**
 * @author BarakH
 */
public interface AccessClientUpdateListener {

    /**
     * This method will be called each time the access client will be updated
     */
    void onClientUpdate();
}
