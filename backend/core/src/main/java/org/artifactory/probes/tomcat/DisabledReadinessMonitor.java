package org.artifactory.probes.tomcat;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.artifactory.probes.tomcat.ServerReadinessMonitor.DISABLED_READINESS_MONITOR_EXPRESSION;

/**
 * @author Igor Usenko
 */
@Slf4j
@Component
@ConditionalOnExpression(DISABLED_READINESS_MONITOR_EXPRESSION)
public class DisabledReadinessMonitor implements ServerReadinessMonitor {

    private final String property;

    public DisabledReadinessMonitor(@Value("${artifactory.tomcat.monitor.mode}") String property) {
        this.property = property;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @PostConstruct
    public void postConstruct() {
        log.warn("Server readiness monitor disabled. Property value: {}", property);
    }
}
