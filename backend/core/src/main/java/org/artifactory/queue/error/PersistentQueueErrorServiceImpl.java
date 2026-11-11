package org.artifactory.queue.error;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.queue.QueueCursorTypeMapper;
import org.artifactory.queue.QueueEventImpl;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.queue.model.PersistentQueueRunningResult;
import org.artifactory.queue.register.RepoAndCursor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.error.dao.EventAndErrorInfoDao;
import org.artifactory.storage.db.event.error.factory.NodeEventsErrorInfoFactory;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.jfrog.common.ClockUtils;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author avishayh
 */
@AllArgsConstructor
@Service
@Slf4j
public class PersistentQueueErrorServiceImpl implements PersistentQueueErrorService {

    private final NodeEventsErrorsStorageService errorsStorageService;
    private final EventAndErrorInfoDao errorInfoDao;

    @Override
    public int getErrorCount(PersistentQueue queue) {
        QueueConfiguration queueConfiguration = queue.getQueueConfiguration();
        if (!queueConfiguration.isEnableErrorSupport()) {
            return 0;
        }
        String operationKey = queue.getName();
        String eventCategory = QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName();
        try {
            return errorInfoDao.getErrorCount(eventCategory, operationKey);
        } catch (SQLException exception) {
            throw new StorageException("Failed to retrieve error count", exception);
        }
    }

    @Override
    @Nonnull
    public List<EventAndErrorInfo> getErrors(PersistentQueue queue) {
        QueueConfiguration queueConfiguration = queue.getQueueConfiguration();
        if (!queueConfiguration.isEnableErrorSupport()) {
            //nothing to return if disabled
            return Collections.emptyList();
        }
        String operationKey = queue.getName();
        String eventCategory = QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName();
        try {
            return errorInfoDao.loadErrorEvents(eventCategory, operationKey, -1);
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve errors", e);
        }
    }

    @Override
    public void processErrors(PersistentQueue queue, PersistentQueueRunningContext context) {
        if (!queue.getQueueConfiguration().isEnableErrorSupport()) {
            return;
        }
        Map<Long, EventAndErrorInfo> existingErrors = context.getExistingErrors();
        Map<Long, Boolean> failed2OmitError = context.getFailedResults().stream()
                .filter(result -> result.getFailedMarker() > -1)
                .collect(Collectors.toMap(
                        PersistentQueueRunningResult::getFailedMarker,
                        PersistentQueueRunningResult::isOmitError));

        long now = ClockUtils.epochMillis();

        //merge to original failed markers and the new failed markers
        Set<Long> markers = new HashSet<>();
        markers.addAll(existingErrors.keySet());
        markers.addAll(failed2OmitError.keySet());
        for (long marker : markers) {
            boolean existedBefore = existingErrors.containsKey(marker);
            if (existedBefore) {
                updateOrDeleteError(marker, queue, context);
            } else {
                //Do not save error if omitted.
                if (!failed2OmitError.getOrDefault(marker, false)) {
                    createNewError(marker, queue, context, now);
                }
            }
        }
    }

    @Override
    public void deleteErrors(PersistentQueue queue) {
        QueueConfiguration queueConfiguration = queue.getQueueConfiguration();
        if (!queueConfiguration.isEnableErrorSupport()) {
            return;
        }
        String operationKey = queue.getName();
        String eventCategory = QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName();
        try {
            errorsStorageService.deleteAll(eventCategory, operationKey);
        } catch (Exception exception) {
            log.error("Failed cleanup errors for queue: {}, error: {}", queue.getName(), exception);
        }
    }

    @Override
    public void deleteErrors(@Nonnull Collection<Long> errors) {
        errors.forEach(error -> errorsStorageService.delete(error));
    }

    @Nonnull
    @Override
    public Set<RepoAndCursor> getErroneousQueues() {
        return errorsStorageService.getErroneousRepos().stream()
                .map(e -> new RepoAndCursor(e.getRepoKey(),
                        QueueCursorTypeMapper.get(NodeEventCursorType.valueOf(e.getEventCategory()))))
                .collect(Collectors.toSet());
    }

    @Override
    public void triggerRecovery(PersistentQueue queue) {
        List<EventAndErrorInfo> errors = getErrors(queue);
        errors.forEach(error -> {
            EventAndErrorInfo updatedError = NodeEventsErrorInfoFactory.resetExisting(error);
            errorsStorageService.updateById(toErrorInfo(updatedError, queue,
                            "Recovering error: " + error.getErrorId() + "|" + error),
                    updatedError.getErrorId());
        });
    }

    private void updateOrDeleteError(long failedMarker, PersistentQueue queue, PersistentQueueRunningContext context) {
        QueueConfiguration queueConfiguration = queue.getQueueConfiguration();
        EventAndErrorInfo existingError = context.getExistingErrors().get(failedMarker);

        PersistentQueueRunningResult failedResult = context.getFailedResults().stream()
                .filter(r -> r.getFailedMarker() == failedMarker)
                .findFirst().orElse(null);

        boolean errorResolved = failedResult == null;
        boolean errorExhausted = existingError.getErrorCount() + 1 >= queueConfiguration.getMaxErrorRetires();
        if (errorResolved) {
            errorsStorageService.delete(existingError.getErrorId());
            return;
        }
        if (errorExhausted) {
            // Add the failedEventId to the context, so we could consider it when calculating
            // the TriggerResult
            context.getExhaustedErrors().add(failedMarker);
            log.warn("Too much retries ({}) for event {}. event is exhausted",
                    existingError.getErrorCount() + 1,
                    existingError.getEventInfo());
            if (selfManageErrors(queue.getQueueConfiguration().getCursorType())) {
                log.warn("Federated Queue is paused due to exhausted event {}", existingError.getEventInfo());
                queue.updateStatus(NodeEventCursorStatus.EXHAUSTED);
            } else {
                errorsStorageService.delete(existingError.getErrorId());
                return;
            }
        }
        EventAndErrorInfo updatedError = NodeEventsErrorInfoFactory.updateExisting(existingError);
        errorsStorageService.updateById(toErrorInfo(updatedError, queue, failedResult.getErrorMessage()),
                updatedError.getErrorId());
    }

    private void createNewError(long failedMarker, PersistentQueue queue, PersistentQueueRunningContext context,
            long now) {

        //failed result must exist now
        PersistentQueueRunningResult failedResult = context.getFailedResults().stream()
                .filter(r -> r.getFailedMarker() == failedMarker)
                .findFirst().orElseThrow(() -> new IllegalStateException("result must exist: " + failedMarker));

        //Find the event.
        //If the event is a deduplication event it will not exist in the contexts events.
        //The context contain the event with the higher event id of the deduplication group, but the content of the
        //event with the lower event id
        //It contains a hint with the lower event id of the deduplication group.
        //The error should be saved on the lower event id.
        EventInfo eventInfo =
                context.getEvents().stream()
                        .filter(event -> matchByFailedMarker(failedMarker, event))
                        .findFirst()
                        .map(event -> {

                            if (event.getOriginalEvent() != null) {
                                //Generate event that represent the event with the lower event id
                                //The current event contains the type and path of the event with the lower event id
                                //The timestamp is of the event with the higher event id, however we actually don't
                                //use anything except the event id in error, the errors are read by join of:
                                //node_events_error and node_events, the result are taken from the node_events.
                                //So actually, error with wrong timestamp is harmless.
                                return new EventInfo(failedMarker, event.getTimestamp(), event.getType(),
                                        event.getPath(), event.getHints());
                            } else {
                                return ((QueueEventImpl) event).getEvent();
                            }
                        }).orElseThrow(() -> new IllegalStateException("failed marker must exist: " + failedMarker));
        EventAndErrorInfo error = NodeEventsErrorInfoFactory.createNew(eventInfo, now);
        errorsStorageService.add(toErrorInfo(error, queue, failedResult.getErrorMessage()));
    }

    /**
     * Match event by the event id or the RollbackEventId hint
     */
    private boolean matchByFailedMarker(long failedMarker, QueueEventImpl event) {
        if (event.getEventId() == failedMarker) {
            return true;
        } else {
            if (!Objects.isNull(event.getOriginalEvent())) {
                return event.getOriginalEvent().getEventId() == failedMarker;
            }
            return false;
        }
    }

    private NodeEventsErrorInfo toErrorInfo(EventAndErrorInfo error, PersistentQueue queue, String errorMessage) {
        QueueConfiguration queueConfiguration = queue.getQueueConfiguration();
        String operationKey = queue.getName();
        String eventCategory = QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName();
        return NodeEventsErrorInfo.builder()
                .eventType(error.getEventInfo().getType())
                .path(RepoPathFactory.create(error.getEventInfo().getPath()))
                .eventId(error.getEventInfo().getEventId())
                .eventTime(error.getEventInfo().getTimestamp())
                .firstErrorTime(error.getFirstErrorTime())
                .errorCount(error.getErrorCount())
                .eventCategory(eventCategory)
                .operationKey(operationKey)
                .errorMessage(errorMessage)
                .build();
    }

    public static boolean selfManageErrors(QueueCursorType cursorType) {
        return cursorType == QueueCursorType.FEDERATED_REPLICATION ||
                QueueCursorType.FEDERATION_SERVICE.equals(cursorType);
    }
}
