package org.artifactory.sh.cleanup.events;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventHintType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.sh.cleanup.GCService;
import static org.artifactory.sh.cleanup.GarbageCollectorStrategy.EVENTS_GC;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.event.StorageEventInfo;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.jfrog.storage.events.DeletedBinariesEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author TamirH
 */
@Slf4j
public class StorageEventsQueueConfigurationRegistrar implements QueueConfigurationRegistrar, EventGcMetric {
    private final QueueConfiguration configuration;
    private final ApplicationEventPublisher eventPublisher;
    private final StorageEventsService storageEvents;
    private final LongAdder eventsCount;
    private final LongAdder deleteBinariesCount;
    private final LongAdder cleanedBytes;
    private final LongAdder deleteFailuresCount;
    private final LongAdder retryExhaustedCount;
    private final LongAdder dedupCount;
    private final LongAdder squashedCount;
    private final RepositoryService repositoryService;
    private final GCService gcService;
    private final int maxFailedGCEventsRetries;

    public StorageEventsQueueConfigurationRegistrar(GCService gcService,
            ApplicationEventPublisher eventPublisher, StorageEventsService storageEvents,
            RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        this.eventsCount = new LongAdder();
        this.deleteBinariesCount = new LongAdder();
        this.cleanedBytes = new LongAdder();
        this.deleteFailuresCount = new LongAdder();
        this.retryExhaustedCount = new LongAdder();
        this.dedupCount = new LongAdder();
        this.squashedCount = new LongAdder();
        this.gcService = gcService;
        this.eventPublisher = eventPublisher;
        this.storageEvents = storageEvents;
        this.maxFailedGCEventsRetries = ConstantValues.gcEventsMaxReties.getInt();
        this.configuration =
                QueueConfiguration.builder()
                        .name("gc")
                        .cursorType(QueueCursorType.GC)
                        .enableErrorSupport(false)
                        .reductionBehaviours(QueueReductionBehaviours.None)
                        .implementor((events, key) -> handleEvents(events))
                        .build();
    }

    @Override
    public boolean isRepoBaseRegistrar() {
        return false;
    }

    @Override
    public String getOverrideQueueName() {
        return StorageEventsTriggerServiceImpl.JFROG_GC_INTERNAL;
    }

    /**
     * Handles events for garbage collection.
     * It processes a list of QueueEvent instances, identifying and cleaning up binaries eligible for GC.
     * <p>
     * The method applies (optional) optimization on the input events list:<il>
     * <li>deduplicates the input list of events to remove any duplicate entries.</li>
     * <li>removes of previous entries from the 'storage_events' table for each SHA1
     * found in the list of queue events.</li>
     * </il>
     * <pre>
     * Example. Let's consider the following list of events:
     * >  A  B  B  A  C  D  B  <  B  C
     * 0--1--2--3--4--5--6--7--8--9--10-->
     * Our batch spans from timestamp 1 to 8 (events b[9] and C[10] are present in the storage_events table,
     * but they're out of scope of the current batch).
     * First, we get rid of duplicates withing the batch leaving only the latest:
     * > A  C  D  B  <  B  C
     * - 4--5--6--7--8--9--10-->
     * Then we get the latest available events for each SHA1 in our batch (e.g. for A, C, D and B):
     * > A  D  B  C
     * - 4--6--9--10-->
     * Next, we compare the deduplicated batch with the latest events:
     * A  C  D  B
     * 4--5--6--7
     * vs
     * A  D  B  C
     * 4--6--9--10-->
     * We see that our current batch contains most recent events A[4-4] and D[6-6], but not B[7-9] and not C[5-10].
     * Thus, we'll handle A and D, and remove previous entries of B[7] and C[5].
     * Finally, we'll end up with two unhandled events B[9] and C[10] that will be treated in the next batch.
     * </pre>
     *
     * @param events a list of QueueEvent instances to handle.
     */
    @VisibleForTesting
    void handleEvents(List<? extends QueueEvent> events) {
        long maxEventIdHandled = 0;
        List<GCCandidate> deletedBinaries = new ArrayList<>();
        List<StorageEventInfo> failedGCEvents = new ArrayList<>();
        GarbageCollectorInfo result = new GarbageCollectorInfo(EVENTS_GC.name(), 10);

        List<StorageEventInfo> eventsToHandle = convertAndDeduplicateEvents(events);
        result.addCandidatesForDeletion(eventsToHandle.size());
        dedupCount.add((long) events.size() - eventsToHandle.size());

        for (StorageEventInfo event : eventsToHandle) {
            eventsCount.increment();
            try {
                log.trace("Deletion candidate: {}", event.sha1());
                GCCandidate candidate =
                        new GCCandidate(null, event.sha1(), event.sha256(), "", event.size());
                if (!repositoryService.existsBySha1(candidate.sha1()) &&
                        gcService.executeBinaryCleaner(candidate, result)) {
                    log.trace("Deleted: {}", event.sha1());
                    deleteBinariesCount.increment();
                    cleanedBytes.add(candidate.length());
                    deletedBinaries.add(candidate);
                }
            } catch (Exception e) {
                handleFailedGCCandidateRemoval(e, event, failedGCEvents);
            } finally {
                maxEventIdHandled = Math.max(maxEventIdHandled, event.eventId());
            }
        }
        try {
            storageEvents.appendEvents(failedGCEvents);
            doPostCleanupActions(deletedBinaries, result);
        } catch (Exception e) {
            log.error("Failed to Handle after binaries deletions flow: {}", e.getMessage(), e);
        }
        cleanHandledEvents(eventsToHandle, maxEventIdHandled);
    }

    @VisibleForTesting
    List<StorageEventInfo> convertAndDeduplicateEvents(List<? extends QueueEvent> events) {
        List<StorageEventInfo> eventsToHandle = toStorageEventInfo(events);
        if (ConstantValues.gcEventsCleanupEventsDedupOptimizationEnabled.getBoolean()) {
            // Deduplicate the list of events to remove any duplicate entries
            List<StorageEventInfo> eventsWithoutDuplicates = retainLastElements(eventsToHandle);
            int dedupEventsSize = eventsWithoutDuplicates.size();
            // Retrieve the latest events for the given SHA1 checksums
            Map<String, StorageEventInfo> latestEventsFromDbBySha1 = getLatestEventsFromDB(eventsWithoutDuplicates);
            // Keep the latest events only
            eventsWithoutDuplicates.removeIf(event -> {
                StorageEventInfo eventFromDb = latestEventsFromDbBySha1.get(event.sha1());
                if (eventFromDb != null) {
                    boolean shouldRemovePreviousEvents = eventFromDb.eventId() > event.eventId();
                    if (shouldRemovePreviousEvents) {
                        log.trace("Previous DB record of SHA1 {} older than {} will be removed",
                                event.sha1(), event.eventId());
                        int numberOfRemovedEntries = storageEvents.deleteAllPreviousBySha1(event.sha1());
                        if(numberOfRemovedEntries > 0) {
                            squashedCount.add(numberOfRemovedEntries);
                        }
                        log.trace("{} DB records of SHA1 {} were removed", numberOfRemovedEntries, event.sha1());
                    }
                    return shouldRemovePreviousEvents;
                } else {
                    //
                    log.trace("There are no {} events newer than {}. This event will be processed in this chunk",
                            event.sha1(), event.eventId());
                    return false;
                }
            });
            eventsToHandle = eventsWithoutDuplicates;
            log.debug("Running optimized GC events batch. Original batch size is {}, " +
                            "deduplicated batch size is {}, batch to handle size is {}",
                    events.size(), dedupEventsSize, eventsToHandle.size());
        } else {
            log.debug("Running GC events batch. Batch size is {}", events.size());
        }
        return eventsToHandle;
    }

    /**
     * Retrieves the last event from each group of events with the same SHA1 checksum.
     * The last event is determined by the last event within each group.
     *
     * @param events a list of queue events
     * @return a list containing the last event from each group of events with the same SHA1 checksum
     */
    @VisibleForTesting
    @NonNull
    static List<StorageEventInfo> retainLastElements(@NonNull List<StorageEventInfo> events) {
        return events.stream()
                // Group events by SHA1, i.e. Map(sha1, List<events>)
                .collect(Collectors.groupingBy(event -> Objects.requireNonNullElse(event.sha1(), ""),
                        Collectors.toList()))
                // For every group above take its values, i.e. List<events>
                .values().stream()
                // Keep last event only. BTW, this event has the biggest eventID within its SHA1 group
                .map(eventsWithSameSHA1 -> eventsWithSameSHA1.get(eventsWithSameSHA1.size() - 1))
                // Pack the result to a list
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Retrieves the latest events for the given SHA1 checksums.
     *
     * @param dedupedEvents a list of deduplicated events
     * @return a map containing the latest events for the given SHA1 checksums
     */
    @VisibleForTesting
    @NonNull
    Map<String, StorageEventInfo> getLatestEventsFromDB(@NonNull List<StorageEventInfo> dedupedEvents) {
        // get newest for dedup([4 a], [7 b], [5 c], [6 d]) -> newest([4 a], [9 b], [10 c], [6 d])
        List<String> sha1s = dedupedEvents.stream()
                .map(StorageEventInfo::sha1)
                .filter(Objects::nonNull)
                .toList();
        return storageEvents.getLatestEventsBySHA1(sha1s).stream()
                .collect(Collectors.toMap(StorageEventInfo::sha1, event -> event));
    }

    @VisibleForTesting
    List<StorageEventInfo> toStorageEventInfo(List<? extends QueueEvent> events) {
        return events.stream()
                .map(queueEventToStorageEventInfo)
                .toList();
    }

    /**
     * Function to convert a QueueEvent to a StorageEventInfo object.
     */
    private final Function<QueueEvent, StorageEventInfo> queueEventToStorageEventInfo = event -> {
        Set<String> hints = event.getHints();
        String sha1 = EventHintType.getValue(hints, EventHintType.Sha1Checksum);
        String sha256 = EventHintType.getValue(hints, EventHintType.Sha256Checksum);
        long size = getSize(hints, sha1, sha256);
        return new StorageEventInfo(event.getEventId(), event.getTimestamp(), event.getType(),
                sha1, sha256, size, StorageEventInfo.Data.getEventsGcData(hints));
    };

    private void handleFailedGCCandidateRemoval(Exception e, StorageEventInfo event,
            List<StorageEventInfo> failedGCEvents) {
        ExceptionLogUtils.debugOrError(log,
                "Failed to delete binary with sha1:%s, sha256:%s".formatted(event.sha1(), event.sha256()), e);
        int retryCount = event.getRetryCount();
        if (retryCount < maxFailedGCEventsRetries) {
            StorageEventInfo retryGCEvent = event.copyAndIncrementRetryCount();
            failedGCEvents.add(retryGCEvent);
            log.info("The binary {} removal will be retried (attempt {} out of {})",
                    event.sha1(), retryCount, maxFailedGCEventsRetries);
            deleteFailuresCount.increment();
        } else {
            retryExhaustedCount.increment();
            log.warn("Could not remove binary {} in {} attempts", event.sha1(), retryCount);
        }
    }

    private long getSize(Set<String> hints, String sha1, String sha256) {
        String sizeHint = EventHintType.getValue(hints, EventHintType.BinLength);
        try {
            return Long.parseLong(Objects.requireNonNull(sizeHint));
        } catch (Exception e) {
            ExceptionLogUtils.debugOrWarn(log, "Hint %s cannot be parsed as a number in event sha1:%s, sha256:%s"
                    .formatted(sizeHint, sha1, sha256), e);
            return 0L;
        }
    }

    private void cleanHandledEvents(Collection<?> events, long maxEventIdHandled) {
        if (!events.isEmpty()) {
            storageEvents.deleteByCursor(maxEventIdHandled);
        }
    }

    private void doPostCleanupActions(List<GCCandidate> deletedBinaries, GarbageCollectorInfo result) {
        if (!deletedBinaries.isEmpty()) {
            eventPublisher.publishEvent(
                    new DeletedBinariesEvent(this, deletedBinaries.stream().map(GCCandidate::sha2).toList()));
        }
        result.setGcEndTime(System.currentTimeMillis());
        if (result.getBinariesCleaned().get() > 0) {
            if (log.isTraceEnabled()) {
                result.printCollectionInfo();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(result.generateGcLogReport());
                }
            }
        }
    }

    @Override
    public List<QueueConfiguration> getConfigurations(String repoKey) {
        return List.of(configuration);
    }

    @Override
    public long getCleanedBytes() {
        return this.cleanedBytes.longValue();
    }

    @Override
    public long getEventsCount() {
        return this.eventsCount.longValue();
    }

    @Override
    public long getDeleteBinariesCount() {
        return this.deleteBinariesCount.longValue();
    }

    @Override
    public long getDeleteFailuresCount() {
        return this.deleteFailuresCount.longValue();
    }

    @Override
    public long getRetryExhaustedCount() {
        return this.retryExhaustedCount.longValue();
    }

    @Override
    public long getDedupCount() {
        return this.dedupCount.longValue();
    }
    @Override
    public long getSquashedCount() {
        return this.squashedCount.longValue();
    }
}
