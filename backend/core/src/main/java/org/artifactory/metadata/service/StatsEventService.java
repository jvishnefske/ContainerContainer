package org.artifactory.metadata.service;

import com.google.common.collect.Maps;
import com.google.protobuf.util.JsonFormat;
import com.jfrog.artifactory.eventapi.stats.StatsEntryOuterClass;
import com.jfrog.artifactory.eventapi.stats.StatsEventOuterClass;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.repo.RepoPath;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.jfrog.client.util.PathUtils;
import org.jfrog.eventing.facade.TopicId;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.jfbus.JfbusConstants.HEADER_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.HEADER_PACKAGE_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SUBJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.STATS_EVENT_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.STATS_UPDATED_EVENT_TYPE;
import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * Publishes versioned stats batches to the {@code artifactory-stats-events} jfbus topic.
 */
@Slf4j
@Service
public class StatsEventService implements ContextCreationListener {

    private static final int STATS_EVENTS_TOPIC_PARTITIONS = 15;
    private final TopicId statsTopicId = new TopicId("artifactory", "artifactory-stats-events");

    private final JfbusEventingService jfbusEventingService;
    private final LeadArtifactFacade leadArtifactFacade;
    private final RepositoryService repositoryService;

    public StatsEventService(JfbusEventingService jfbusEventingService,
            LeadArtifactFacade leadArtifactFacade,
            RepositoryService repositoryService) {
        this.jfbusEventingService = jfbusEventingService;
        this.leadArtifactFacade = leadArtifactFacade;
        this.repositoryService = repositoryService;
    }

    @Override
    public void onContextCreated() {
        try {
            jfbusEventingService.createTopic(statsTopicId, STATS_EVENTS_TOPIC_PARTITIONS, true);
        } catch (Exception e) {
            log.error("Could not create JFBus topic for stats events: {}", statsTopicId, e);
        }
    }

    public void publishStats(Map<RepoPath, MetadataStats> pathToStats) throws Exception {
        List<String> excludedPackageTypes = ConstantValues.jfbusExcludedPackageTypes.getList();
        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                pathToStats, leadArtifactFacade, repositoryService, excludedPackageTypes);
        for (StatsEventOuterClass.StatsEvent event : events) {
            if (event.getEntriesCount() == 0) {
                continue;
            }
            String repoName = event.getEntries(0).getRepoName();
            try {
                sendStatsEvent(repoName, event, new HashMap<>());
            } catch (Exception e) {
                // Swallow per-chunk failures so one bad repo does not prevent subsequent chunks.
                log.error("Failed to dispatch stats event for repo '{}' (event_id={}): {}",
                        repoName, event.getEventId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Sends a stats event to JFBus.
     *
     * @param key        partition key (repo name) for per-repo ordering
     * @param statsEvent the stats event payload
     * @param headers    additional headers to include
     */
    public void sendStatsEvent(String key, @NonNull StatsEventOuterClass.StatsEvent statsEvent,
            Map<String, String> headers) throws Exception {
        Map<String, String> allHeaders = Maps.newHashMap(headers);
        populateStatsHeaders(allHeaders, statsEvent);
        printEventForTracing(statsEvent);
        if (log.isDebugEnabled()) {
            log.debug("Publishing stats event id={} repo={} entries={}",
                    statsEvent.getEventId(), key, statsEvent.getEntriesCount());
        }
        jfbusEventingService.sendEvent(statsTopicId, key, statsEvent.toByteArray(), allHeaders);
    }

    static void printEventForTracing(StatsEventOuterClass.StatsEvent event) {
        if (log.isTraceEnabled()) {
            try {
                log.trace(JsonFormat.printer().preservingProtoFieldNames().print(event));
            } catch (Exception e) {
                debugOrWarn(log, e.getMessage(), e);
            }
        }
    }

    private void populateStatsHeaders(Map<String, String> headers, StatsEventOuterClass.StatsEvent statsEvent) {
        headers.put(HEADER_TYPE, STATS_UPDATED_EVENT_TYPE);
        headers.put(HEADER_DATA_SCHEMA, STATS_EVENT_DATA_SCHEMA);
        if (statsEvent.getEntriesCount() == 0) {
            return;
        }
        StatsEntryOuterClass.StatsEntry firstEntry = statsEvent.getEntries(0);
        String leadFileName = PathUtils.getFileName(firstEntry.getLeadFilePath());
        if (StringUtils.isNotBlank(leadFileName)) {
            headers.put(HEADER_SUBJECT, leadFileName);
        }
        if (StringUtils.isNotBlank(firstEntry.getRepoPackageType())) {
            headers.put(HEADER_PACKAGE_TYPE, firstEntry.getRepoPackageType());
        }
    }
}
