package org.artifactory.system.load.monitor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import static org.artifactory.system.load.monitor.SystemLoadMonitorConditions.OUTAGE_SELF_PROBE_DISABLED_EXPRESSION;

/**
 * @author Amir Avidan
 */
@Slf4j
@Component
@ConditionalOnExpression(OUTAGE_SELF_PROBE_DISABLED_EXPRESSION)
public class DisabledOutageSelfProbe implements OutageSelfProbe {

    @Override
    public ProbeResult getLatestResult() {
        return ProbeResult.INITIAL;
    }

    @Override
    public void start() {
        log.debug("Outage self-diagnostic probe is disabled");
    }

    @Override
    public void stop() {
        // no-op
    }
}
