package org.artifactory.metrics.framework.adoption;

import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.api.metrics.FeatureAdoptionLabels;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.metrics.model.TrackingSchema;

/**
 * A service to perform Feature Adoption (aka Usage Metrics) tracking operations.
 *
 * @author Oleksandr Legenkiy
 */
public interface FeatureAdoptionService extends ReloadableBean {

    /**
     * This is a basic method that should be used to track usage of features as part of Feature Adoption.
     *
     * @param feature a feature that should be tracked
     * @param action  an action that should be tracked
     * @param success <code>true</code> if a tracked action was successful, otherwise <code>false</code>
     * @param labels  a class that holds a map of "dynamic labels". It should be used to provide additional details
     *                on the action that should be tracked
     */
    void trackAdoption(FeatureAdoptionFeature feature, FeatureAdoptionAction action, boolean success,
                       FeatureAdoptionLabels labels);

    void trackIncrease(TrackingSchema trackingSample);

    void trackDecrease(TrackingSchema trackingSample);

    void trackAverage(TrackingSchema trackingSample);

    void trackSet(TrackingSchema trackingSample);

    void trackUnset(TrackingSchema trackingSample);
}
