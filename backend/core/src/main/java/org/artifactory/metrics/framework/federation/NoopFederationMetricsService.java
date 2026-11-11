package org.artifactory.metrics.framework.federation;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.Reloadable;
import org.springframework.stereotype.Service;

/**
 * A no-operation implementation of the FederationMetricsService.
 * This class is used when FederationMetricsServiceImpl bean is not available.
 * For example in RT JCR
 *
 * @author Igor Usenko
 */
@Slf4j
@Service
@Reloadable(beanClass = FederationMetricsService.class, initAfter = TaskService.class)
public class NoopFederationMetricsService implements FederationMetricsService {

    @Override
    public String getOpenMetrics(boolean applicationMetricsOnly) {
        return "";
    }

}
