package org.artifactory.unifiedevent.dispatchers.outbound.consumerclient;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component("workerClient")
public class WorkerClientOutboundDispatcherImpl implements ClientOutboundDispatcher {

    protected AddonsManager addonsManager;

    @Override
    public <T> DispatcherResponse sendEvent(EventEnvelope<T> unifiedEvent) {
        DispatcherResponse.DispatcherResponseBuilder responseBuilder = DispatcherResponse.builder().shouldProceed(true);
        try {
            WorkersAddon workersAddon = addonsManager.addonByType(WorkersAddon.class);
            log.debug("Dispatching event: '{}' To WorkerClientOutboundDispatcher: '{}'", unifiedEvent, workersAddon);
            workersAddon.run(unifiedEvent.type(), unifiedEvent, responseBuilder);
            return responseBuilder.build();
        } catch (WorkerStopException e) {
            log.debug("WorkerClientOutboundDispatcher received stop exception for event: {}", unifiedEvent, e);
            return responseBuilder.shouldProceed(false).message(e.getMessage()).build();
        } catch (Exception e) {
            log.warn("WorkerClientOutboundDispatcher failed to send event '{}' due to exception: {}",
                    unifiedEvent, e.getMessage(), e);
            return responseBuilder.shouldProceed(true).message(e.getMessage()).build();
        }
    }

}
