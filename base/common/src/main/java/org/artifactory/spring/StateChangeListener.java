package org.artifactory.spring;

import org.artifactory.state.ArtifactoryServerState;

/**
 * @author Dor Solomon
 */
public interface StateChangeListener {

    /**
     * Listens on {@link ArtifactoryServerState} changes. Called after the state has been changed.
     *
     * @param state the new and current artifactory server state
     */
    void onStateChange(ArtifactoryServerState state);
}
