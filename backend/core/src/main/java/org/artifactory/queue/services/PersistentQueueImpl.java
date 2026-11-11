package org.artifactory.queue.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEventAggregatorConfig;
import org.artifactory.event.queue.QueueEventFilter;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.event.queue.QueueStatus;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.event.work.NodeEventsErrorsBackOffStrategy;
import org.artifactory.queue.QueueEventImpl;
import org.artifactory.queue.behaviors.QueueReductionBehavioursImpl;
import org.artifactory.queue.error.PersistentQueueBackoffStrategy;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.model.PersistentQueueGroupContext;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.queue.model.PersistentQueueRunningResult;
import org.artifactory.queue.monitor.PersistentQueueMonitor;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.event.EventServiceBase;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.artifactory.queue.error.PersistentQueueErrorServiceImpl.selfManageErrors;

/**
 * The PersistentQueueImpl is the main class where all the work happens.
 *
 * Those are the main steps when triggering a queue.
 * 1. get a lock (DB) handled by workQueue
 * 2. get the current marker (DB)
 * 3. fetch the next events batch + fetch errors (DB)
 * 4. filter out events based on deduplication rules and applicative code (APP)
 * 5. aggregate into groups (APP)
 * 6. call the applicative implementation logic for each group in a different thread (APP)
 * 7. handles errors and update the marker (DB)
 * 8. release the lock (DB) handled by workQueue
 *
 * @author Avishay Halpren
 */
public class PersistentQueueImpl implements PersistentQueue {

    private static final Logger log = LoggerFactory.getLogger(PersistentQueueImpl.class);
    protected final String cursorName;
    protected final String repoKey;
    protected final QueueConfiguration queueConfiguration;
    private final ArtifactoryExecutor sharedJobsExecutor;
    protected final EventServiceBase<?> eventsService;
    protected final PersistentQueueCursorService persistentQueueCursorService;

    protected final PersistentQueueErrorService persistentQueueErrorService;
    protected final QueueTriggeringService triggeringService;
    private boolean shutdown;
    private boolean paused;
    private final NodeEventsErrorsBackOffStrategy backoffStrategy;
    private final Collection<PersistentQueueMonitor> persistentQueueMonitors;

    public PersistentQueueImpl(QueueConfiguration queueConfiguration, String repoKey) {
        this.queueConfiguration = queueConfiguration;
        this.repoKey = repoKey;

        if (queueConfiguration.getCursorType() == QueueCursorType.GC) {
            this.eventsService = ContextHelper.get().beanForType(StorageEventsService.class);
        } else {
            this.eventsService = ContextHelper.get().beanForType(EventsService.class);
        }
        this.persistentQueueErrorService = ContextHelper.get().beanForType(PersistentQueueErrorService.class);

        this.persistentQueueCursorService = ContextHelper.get().beanForType(PersistentQueueCursorService.class);
        this.triggeringService = ContextHelper.get().beanForType(QueueTriggeringService.class);
        this.backoffStrategy =
                new PersistentQueueBackoffStrategy(queueConfiguration.getBackoffSupplier().getFactor(),
                        queueConfiguration.getBackoffSupplier().getMaxWaitHours());
        this.persistentQueueMonitors = ContextHelper.get().beansForType(PersistentQueueMonitor.class).values();
        //set cursor name
        this.cursorName = new PersistentQueueCursorInfo(repoKey,
                queueConfiguration.getCursorType(),
                queueConfiguration.getVariant()
        ).getCursorName();
        if (queueConfiguration.getAggregatorConfig() != null) {
            this.sharedJobsExecutor = ArtifactoryExecutorFactory.newArtifactoryExecutor(
                    queueConfiguration.getAggregatorConfig().getExecutorService(), ContextHelper.get(), false);
        } else {
            this.sharedJobsExecutor = null;
        }
    }

    /**
     * Create cursor marker if not exist.
     */
    public boolean createMarkerIfNotExists() {
        // Create cursor for marker if not exists
        return this.persistentQueueCursorService.createMarkerIfNotExists(this.repoKey, this.cursorName,
                this.queueConfiguration.getCursorType());
    }


    /**
     * Creating a copy of the old cursor in the DB
     * in order to copy cursor we get the cursor of the old cursor from the DB
     * then we are creating new cursor in the DB with the same properties
     *
     * @param oldVariantCursor the old variant cursor
     */
    @Override
    public void copyCursor(String oldVariantCursor, QueueCursorType queueCursorType) {
        // get old variant cursor
        NodeEventCursor oldNodeEventCursor =
                this.persistentQueueCursorService.getCursorAndCreateIfNeeded(oldVariantCursor,
                        queueCursorType);
        // creating new cursor in the DB with the same properties
        this.persistentQueueCursorService.copyCursor(this.repoKey, this.cursorName, oldNodeEventCursor);
    }

    @Override
    public void updateStatus(NodeEventCursorStatus status) {
        persistentQueueCursorService.updateCursorStatus(cursorName, status);
    }

    @Override
    public void updateFromStatusToStatus(NodeEventCursorStatus sourceStatus, NodeEventCursorStatus targetStatus) {
        persistentQueueCursorService.updateFromStatusToStatus(cursorName, sourceStatus, targetStatus);
    }

    @Override
    public boolean shouldAlwaysTrigger() {
        return false;
    }

    @Override
    public String getName() {
        return cursorName;
    }

    @Override
    public String getRepoKey() {
        return this.repoKey;
    }

    @Override
    public QueueConfiguration getQueueConfiguration() {
        return queueConfiguration;
    }

    @Override
    public TriggerResult trigger() {
        if (shutdown || isPaused()) {
            return TriggerResult.NOT_AVAILABLE;
        }

        TriggerResult triggerResult = TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
        try {
            reportToMonitors(persistentQueueMonitor -> persistentQueueMonitor.start(this));
            triggerResult = innerExecute();
        } finally {
            final TriggerResult finalTriggerResult = triggerResult;
            reportToMonitors(persistentQueueMonitor -> persistentQueueMonitor.end(this.getName(), finalTriggerResult));
        }
        return triggerResult;
    }

    protected TriggerResult innerExecute() {
        PersistentQueueRunningContext runningContext = new PersistentQueueRunningContext();
        NodeEventCursor nodeEventCursor = getCurrentMarkerCursorForQueue();
        log.debug("Current event cursor '{}' set to -> {}", cursorName,
                nodeEventCursor.getEventMarker());
        runningContext.setStartingMarker(nodeEventCursor.getEventMarker());
        runningContext.setCursorsLastUpdated(nodeEventCursor.getLastUpdated());
        long lastEvent = persistentQueueCursorService.getCachedLatestEventId();
        //find all relevant DB events
        List<QueueEventImpl> loadedEvents = loadNextEvents(runningContext);
        //no events - nothing to do
        if (loadedEvents.isEmpty()) {
            if (nodeEventCursor.getEventMarker() < lastEvent &&
                    queueConfiguration.getCursorType() != QueueCursorType.GC) {
                log.debug("Next events cursor '{}' is empty resetting marker to -> {}", cursorName, lastEvent);
                updateMarker(lastEvent);
            }
            return TriggerResult.NOTHING_TO_DO;
        }
        //filter out identical records
        filterOutEventsBehaviours(loadedEvents, runningContext);

        Collection<PersistentQueueGroupContext> jobs = createJobGroups(runningContext);
        spreadTheWork(jobs);
        return handleResult(runningContext, jobs);
    }

    /**
     * get marker from cursor table by queueName (create if needed for 1st time)
     */
    @Override
    public NodeEventCursor getCurrentMarkerCursorForQueue() {
        NodeEventCursor nodeEventCursor =
                persistentQueueCursorService.getCursorAndCreateIfNeeded(
                        cursorName,
                        queueConfiguration.getCursorType());
        reportToMonitors(
                persistentQueueMonitor -> persistentQueueMonitor.startMarker(this.getName(),
                        nodeEventCursor.getEventMarker()));
        return nodeEventCursor;
    }

    /**
     * load the events from the lastMarker (merged with previous errors)
     */
    private List<QueueEventImpl> loadNextEvents(PersistentQueueRunningContext context) {
        //fetch events from main table
        String fileSuffix = queueConfiguration.getFileSuffix();
        int maxBulkSize = queueConfiguration.getMaxBulkSize();
        List<EventInfo> eventInfos = getEventsAfter(context, fileSuffix, maxBulkSize);
        List<QueueEventImpl> events = eventInfos.stream().map(QueueEventImpl::new).toList();
        traceEvents("Next loaded events...", events, cursorName, context.getStartingMarker());
        //fetch and separate errors into 2 groups: include and excluded
        List<EventAndErrorInfo> errors = persistentQueueErrorService.getErrors(this);
        markOverFlowLimitErrorsIfNeeded(errors);
        Map<Long, EventAndErrorInfo> included = new LinkedHashMap<>();
        Set<Long> excluded = new HashSet<>();

        long currentTime = ClockUtils.epochMillis();
        for (EventAndErrorInfo error : errors) {
            if (!selfManageErrors(queueConfiguration.getCursorType()) &&
                    backoffStrategy.isBackoffDelayRequired(error, currentTime)) {
                //we still need the excluded list so we can filter out events from the main query
                excluded.add(error.getEventInfo().getEventId());
            } else {
                //add error event to result
                included.put(error.getEventInfo().getEventId(), error);
            }
        }
        //merging result:
        //result = included + (events - included - excludes)
        List<QueueEventImpl> result = included.values().stream()
                .map(EventAndErrorInfo::getEventInfo)
                .map(QueueEventImpl::new)
                .collect(Collectors.toList());
        events.stream()
                //filter if exist in included (already inside result)
                .filter(e -> !included.containsKey(e.getEventId()))
                //filter if exist in excluded (should not be handled due to time)
                .filter(e -> !excluded.contains(e.getEventId()))
                .forEach(result::add);

        if (!included.isEmpty()) {
            //sort again because included was merged to events
            result.sort(Comparator.comparingLong(QueueEventImpl::getEventId));
        }
        //save to context and return events
        reportToMonitors(persistentQueueMonitor -> persistentQueueMonitor.loadedEvents(this.getName(), result));
        reportToMonitors(
                persistentQueueMonitor -> persistentQueueMonitor.includedErrors(this.getName(), included.keySet()));
        context.setExistingErrors(included);

        traceEvents("Next events after sorting and filtering...", result, cursorName,
                context.getStartingMarker());
        return result;
    }

    @NonNull
    protected List<EventInfo> getEventsAfter(
            @NonNull PersistentQueueRunningContext context,
            @Nullable String fileSuffix,
            int maxBulkSize) {
        long eventId = context.getStartingMarker();
        String cursorType = queueConfiguration.getCursorType().name();
        return eventsService.getEventsAfter(eventId, repoKey, fileSuffix, maxBulkSize, cursorType);
    }

    private void filterOutEventsBehaviours(List<QueueEventImpl> events, PersistentQueueRunningContext context) {
        //get the last event marker before starting to filter out.
        long lastMarker = events.get(events.size() - 1).getEventId();
        //apply applicative filter if exist

        QueueEventFilter filter = queueConfiguration.getFilter();
        List<QueueEventImpl> filteredEvents = filter == null ? events : events.stream()
                .filter(filter::isRelevant)
                .toList();
        traceEvents("Next events after filtering out irrelevant entries...", filteredEvents,
                cursorName, lastMarker);

        //apply all reduction behaviors
        List<QueueEventImpl> dedupedEvents = QueueReductionBehavioursImpl
                .get(queueConfiguration.getReductionBehaviours())
                .applyChain(filteredEvents);

        traceEvents("Next events after applying reduction behaviours...", dedupedEvents, cursorName, lastMarker);

        //report
        log.debug("Events before {} and events after filter chain {}", events.size(), dedupedEvents.size());
        reportToMonitors(
                persistentQueueMonitor -> persistentQueueMonitor.addFilterEvents(this.getName(), filteredEvents,
                        dedupedEvents));
        //save state to context
        context.setEvents(dedupedEvents);
        context.setLastMarker(lastMarker);
    }

    @Nonnull
    private Collection<PersistentQueueGroupContext> createJobGroups(PersistentQueueRunningContext context) {
        QueueEventAggregatorConfig aggregator = queueConfiguration.getAggregatorConfig();
        LoadingCache<String, PersistentQueueGroupContext> groups = CacheBuilder.newBuilder().build(
                new CacheLoader<>() {
                    @Override
                    public PersistentQueueGroupContext load(@Nonnull String key) {
                        return new PersistentQueueGroupContext(key);
                    }
                });
        for (QueueEventImpl event : context.getEvents()) {
            Set<String> keys = new HashSet<>();
            if (aggregator != null) {
                aggregator.getAggregator().setKeys(event, keys);
            } else {
                //if no aggregator supplied only one group will be created with the repoKey name
                keys.add(repoKey);
            }
            for (String key : keys) {
                PersistentQueueGroupContext persistentQueueJob = groups.getUnchecked(key);
                //adding event to the relevant job
                persistentQueueJob.add(event);
            }
        }
        return groups.asMap().values();
    }


    /**
     * Invoke the contexts in multi threads executor
     * or in the same thread in case of one context
     *
     * Each contexts is set with the Future result.
     */
    private void spreadTheWork(Collection<PersistentQueueGroupContext> contexts) {
        if (contexts.isEmpty()) {
            log.debug("No events to handle for cursor {}", getName());
            return;
        }
        QueueEventImplementor queueImplementor = queueConfiguration.getImplementor();
        if (contexts.size() == 1 || sharedJobsExecutor == null) {
            PersistentQueueGroupContext context = contexts.iterator().next();
            context.setFutureException(CompletableFuture.completedFuture(execute(queueImplementor, context)));
        } else {
            //for each job - start calculating all items , save all futures
            for (PersistentQueueGroupContext context : contexts) {
                context.setFutureException(sharedJobsExecutor.submit(() -> execute(queueImplementor, context)));
            }
        }
    }

    /**
     * Execute context
     *
     * @return optional exception
     */
    @Nonnull
    private Optional<Exception> execute(QueueEventImplementor queueImplementor, PersistentQueueGroupContext context) {
        Exception raisedException = null;
        try {
            reportToMonitors(
                    persistentQueueMonitor -> persistentQueueMonitor.beforeImplementor(this.getName(),
                            context.getUniqKey(), context.getEvents().size()));
            queueImplementor.execute(context.getEvents(), context.getUniqKey());
            return Optional.empty();
        } catch (Exception e) {
            //need to log the error ?
            log.debug("Error while executing implementor for queue {} with key {}", getName(), context.getUniqKey(), e);
            raisedException = e;
            return Optional.of(e);
        } finally {
            Exception finalRaisedException = raisedException;
            reportToMonitors(
                    persistentQueueMonitor -> persistentQueueMonitor.afterImplementor(this.getName(),
                            context.getUniqKey(),
                            finalRaisedException));
        }
    }

    /**
     * sync all jobs, handle failures if exist, update the marker
     */
    @Nonnull
    private TriggerResult handleResult(PersistentQueueRunningContext context,
            Collection<PersistentQueueGroupContext> jobs) {

        Set<PersistentQueueRunningResult> failedResults = jobs.stream()
                //syncing on all failedResults here.
                .map(PersistentQueueGroupContext::getResult)
                //keep only failed result
                .filter(PersistentQueueRunningResult::isFailed)
                .collect(Collectors.toSet());

        context.setFailedResults(failedResults);
        persistentQueueErrorService.processErrors(this, context);

        Set<PersistentQueueRunningResult> finalFailedResults;
        if (selfManageErrors(queueConfiguration.getCursorType())) {
            finalFailedResults = failedResults;
        } else {
            //Filter out the exhausted errors from the failed result.
            finalFailedResults = failedResults.stream()
                    .filter(failedResult -> !context.getExhaustedErrors().contains(failedResult.getFailedMarker()))
                    .collect(Collectors.toSet());
        }

        if (finalFailedResults.isEmpty()) {
            // No errors found we can update to the lastEventMarker - yeh !!
            // Update the marker only if was set (When we loaded events from the db) and > starting marker
            if (context.getLastMarker() > context.getStartingMarker()) {
                updateMarker(context.getLastMarker());
            }
            return TriggerResult.FINISHED_ALL;
        }

        long smallestFailureMarkerFound = Long.MAX_VALUE;
        for (PersistentQueueRunningResult failedResult : finalFailedResults) {
            long failedMarker = failedResult.getFailedMarker();
            if (failedMarker == -1) {
                //in case marker was set to -1, it means we came across unhandled error (e.g NPE...)
                //in this case we do not advance the marker
                return TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
            }
            smallestFailureMarkerFound = Math.min(smallestFailureMarkerFound, failedMarker);
        }

        //calculate last success, using naive approach
        long lastSuccessfulMarker = smallestFailureMarkerFound;
        if (lastSuccessfulMarker > context.getStartingMarker()) {
            //update marker to the last successful job
            updateMarker(lastSuccessfulMarker);
        } else {
            log.debug("marker {} is the same as we started on queue {}", lastSuccessfulMarker, getName());
        }

        if (NodeEventCursorStatus.EXHAUSTED == getCurrentMarkerCursorForQueue().getStatus()) {
            return TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
        }
        //in-case error support is disabled we will return an UNEXPECTED_ERROR instead of ERROR
        //this will prevent the retry loop to go into infinite loop cycles
        return queueConfiguration.isEnableErrorSupport() ?
                TriggerResult.FINISHED_WITH_HANDLED_ERROR :
                TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
    }

    private void updateMarker(long newMarker) {
        log.debug("updating the marker to {} on queue {}", newMarker, getName());
        reportToMonitors(persistentQueueMonitor -> persistentQueueMonitor.endMarker(this.getName(), newMarker));
        persistentQueueCursorService.updateCursor(cursorName, newMarker);
    }

    @Override
    public void reset(boolean delete) {
        //removing existing errors for either delete or non delete
        persistentQueueErrorService.deleteErrors(this);
        if (delete) {
            persistentQueueCursorService.deleteMarker(cursorName);
        } else {
            persistentQueueCursorService.resetMarker(cursorName, -1);
        }
    }

    @Override
    public void shutdown() {
        //stop accepting new requests
        log.info("shutting down all running jobs for queue {}", cursorName);
        shutdown = true;
    }

    @Override
    public void pause() {
        paused = true;
        log.info("Queue paused, name: {}, repo: {}", getName(), repoKey);
    }

    @Override
    public void proceed() {
        if (shutdown) {
            log.warn("Should not call proceed on a queue {} which was shutdown ", cursorName);
        }
        if (paused) {
            paused = false;
            triggeringService.triggerQueues(repoKey, queueConfiguration.getCursorType());
            log.info("Queue resumed, name: {}, repo: {}", getName(), repoKey);
        }
    }

    @Override
    public void reset() {
        reset(false);
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public int getEventQueueSize() {
        long marker = getCurrentMarkerCursorForQueue().getEventMarker();
        String fileSuffix = queueConfiguration.getFileSuffix();
        return eventsService.countEventsAfter(marker, repoKey, fileSuffix);
    }

    @Override
    public QueueStatus getQueueStatus() {
        int overflowLimit = queueConfiguration.getErrorOverflowLimit();
        if (overflowLimit > 0) {
            int errorCount = persistentQueueErrorService.getErrorCount(this);
            if (errorCount >= overflowLimit) {
                return QueueStatus.ERROR_OVERFLOW;
            }
        }
        return persistentQueueCursorService.getStatus(repoKey, cursorName);
    }

    @Override
    public void resetStatus() {
        reset(false);
    }

    @Override
    public void reset(String repoKey, long id) {
        //removing existing errors for either delete or non delete
        persistentQueueErrorService.deleteErrors(this);
        persistentQueueCursorService.resetMarker(cursorName, id);
    }

    private void markOverFlowLimitErrorsIfNeeded(@Nonnull List<EventAndErrorInfo> error) {
        if (selfManageErrors(queueConfiguration.getCursorType())) {
            return;
        }
        if (error.size() > queueConfiguration.getErrorOverflowLimit()
                && queueConfiguration.getErrorOverflowLimit() > 0) {
            persistentQueueCursorService.updateCursorStatus(cursorName, NodeEventCursorStatus.OUT_OF_SYNC);
        }
    }

    private void traceEvents(String message, Collection<QueueEventImpl> events, String cursor, Long marker) {
        if (log.isTraceEnabled()) {
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add(message);
            joiner.add("cursor: " + cursor);
            joiner.add("marker -> " + marker);
            if (!events.isEmpty()) {
                joiner.add(events.size() + " events: [");
                events.forEach(e -> joiner.add("\t" + e.toString()));
                joiner.add("]");
            }
            log.trace(joiner + (events.isEmpty() ? " (no events)" : ""));
        }
    }

    private void reportToMonitors(Consumer<PersistentQueueMonitor> monitorConsumer) {
        for (PersistentQueueMonitor monitor : persistentQueueMonitors) {
            monitorConsumer.accept(monitor);
        }
    }
}
