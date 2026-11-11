/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.metrics.framework;

import org.artifactory.spring.ReloadableBean;

import java.util.Map;

/**
 * A Service responsible for writing Artifactory Metrics logs (artifactory-metrics.log)
 *
 * @author leela
 */
public interface MetricsService extends ReloadableBean {

    /**
     * Allows to track adoption metrics in artifactory metrics log
     * <p>
     * NOTE: Should not be used for Feature Adoption (usage-metrics.log)
     */
    void trackAdoption(String featureName, String action, boolean success, Map<String, String> labels);

    boolean isMetricServiceRunning();

    String getOpenMetrics(boolean applicationMetricsOnly);
}
