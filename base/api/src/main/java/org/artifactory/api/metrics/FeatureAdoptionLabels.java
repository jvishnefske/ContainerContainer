package org.artifactory.api.metrics;

import java.util.Map;

/**
 * This is a common interface for all enums that hold predefined sets of "dynamic labels" (both keys and values)
 * to be used as part of Feature Adoption tracking mechanism.
 *
 * @author Oleksandr Legenkiy
 */
public interface FeatureAdoptionLabels {

    Map<String, String> labels();
}
