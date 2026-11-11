package org.artifactory.event.work;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.artifactory.util.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Mor Merhav
 */
@Slf4j
@Component
public class NodeEventsErrorServiceImpl implements NodeEventsErrorService {

    public static final int MAX_RETRIES = 5;
    private final NodeEventsErrorsStorageService errorsStorageService;

    @Autowired
    public NodeEventsErrorServiceImpl(NodeEventsErrorsStorageService errorsStorageService) {
        this.errorsStorageService = errorsStorageService;
    }

    @Override
    public void process(String eventCategory, PrioritizedNodeEvent event, Exception e) {
        Optional<NodeEventsErrorInfo> optionalErrorInfo = errorsStorageService.getError(eventCategory,
                event.getType(), event.getRepoPath().toPath(), event.getOperatorId());

        if (optionalErrorInfo.isPresent() && optionalErrorInfo.get().getErrorCount() + 1 > MAX_RETRIES) {
            errorsStorageService.delete(optionalErrorInfo.get());
            log.warn("Long-term retries of event with category {}, path {} and Type {} exhausted. The event will not " +
                    "be retried", eventCategory, event.getPath(), event.getType());
            return;
        }

        String errMsg = "Exception: " + e.getMessage() + ", Root cause: " + ExceptionUtils.getRootCause(e);
        optionalErrorInfo.ifPresentOrElse(error -> updateExistingError(error, errMsg),
                () -> queueNewError(eventCategory, event, errMsg));
    }

    @Override
    public List<NodeEventsErrorInfo> getErrors(String eventCategory, String operatorId,
            NodeEventsErrorsBackOffStrategy backOffStrategy, int batchSize) {
        long currentTime = System.currentTimeMillis();
        List<NodeEventsErrorInfo> allErrors = errorsStorageService.getErrors(eventCategory, operatorId, batchSize);
        List<NodeEventsErrorInfo> errorsToSkipDueToBackoff = allErrors.stream()
                .filter(errorInfo -> backOffStrategy.isBackoffDelayRequired(errorInfo, currentTime))
                .collect(Collectors.toList());
        if (!errorsToSkipDueToBackoff.isEmpty()) {
            log.debug("Skipping errors due to backoff: {}", errorsToSkipDueToBackoff);
            allErrors.removeAll(errorsToSkipDueToBackoff);
        }
        return allErrors;
    }

    @Override
    public void removeError(String eventCategory, PrioritizedNodeEvent event) {
        errorsStorageService.delete(eventCategory, event.getType(), event.getPath(), event.getOperatorId());
    }

    private void queueNewError(String eventCategory, PrioritizedNodeEvent event, String errMsg) {
        NodeEventsErrorInfo errorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .eventType(event.getType())
                .path(event.getRepoPath())
                .operationKey(event.getOperatorId())
                .eventTime(event.getTimestamp())
                .errorCount(0)
                .errorMessage(errMsg)
                .firstErrorTime(System.currentTimeMillis())
                .lastErrorTime(System.currentTimeMillis())
                .build();
        errorsStorageService.add(errorInfo);
    }

    private void updateExistingError(NodeEventsErrorInfo errorInfo, String errMsg) {
        NodeEventsErrorInfo updatedErrorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(errorInfo.getEventCategory())
                .eventType(errorInfo.getEventType())
                .eventTime(errorInfo.getEventTime())
                .eventId(errorInfo.getEventId())
                .path(errorInfo.getPath())
                .operationKey(errorInfo.getOperationKey())
                .errorMessage(errMsg)
                .firstErrorTime(errorInfo.getFirstErrorTime())
                .lastErrorTime(System.currentTimeMillis())
                .errorCount(errorInfo.getErrorCount() + 1)
                .build();

        errorsStorageService.update(updatedErrorInfo);
    }
}
