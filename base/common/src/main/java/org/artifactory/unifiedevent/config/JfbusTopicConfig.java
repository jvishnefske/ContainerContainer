package org.artifactory.unifiedevent.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.jfrog.eventing.facade.TopicId;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;

/**
 * Default JFBus topic registrations provisioned at context startup by the async unified-event dispatchers.
 */
@AllArgsConstructor
@Getter
@Slf4j
public enum JfbusTopicConfig {

    AFTER_REMOTE_DOWNLOAD(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD,
            new TopicId(ARTIFACTORY_SERVICE_NAME, SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD.getType()), 10,
            "artifact"),
    AFTER_REPO_CREATE(SupportedUnifiedEvents.AFTER_REPO_CREATE, RepositoryTopic.ID, 3, "repository"),
    AFTER_REPO_DELETE(SupportedUnifiedEvents.AFTER_REPO_DELETE, RepositoryTopic.ID, 3, "repository"),
    AFTER_REPO_UPDATE(SupportedUnifiedEvents.AFTER_REPO_UPDATE, RepositoryTopic.ID, 3, "repository");

    private final SupportedUnifiedEvents event;
    private final TopicId topicId;
    private final int partitionCount;
    /**
     * UEM v2 entity type for this event; drives the {@code artifactory.mb_<entityType>} topic used by
     * {@link org.artifactory.unifiedevent.dispatchers.UemV2EventDispatcher}. Only events with a mapping here are
     * published to UEM v2.
     */
    private final String entityType;

    /** Built once for O(1), allocation-free per-event lookups (the dispatch hot path). */
    private static final Map<SupportedUnifiedEvents, JfbusTopicConfig> BY_EVENT;
    /** Pre-built unmodifiable snapshot; {@link #entityTypePartitions()} returns this directly. */
    private static final Map<String, Integer> ENTITY_TYPE_PARTITIONS;

    static {
        Map<SupportedUnifiedEvents, JfbusTopicConfig> byEvent = new EnumMap<>(SupportedUnifiedEvents.class);
        Map<String, Integer> entityPartitions = new LinkedHashMap<>();
        for (JfbusTopicConfig config : values()) {
            byEvent.put(config.event, config);
            if (config.entityType != null) {
                entityPartitions.putIfAbsent(config.entityType, config.partitionCount);
            }
        }
        BY_EVENT = byEvent;
        ENTITY_TYPE_PARTITIONS = Collections.unmodifiableMap(entityPartitions);
    }

    public static List<JfbusTopicConfig> genericAsyncTopicConfigs() {
        return List.of(AFTER_REMOTE_DOWNLOAD);
    }

    public static List<JfbusTopicConfig> repoCudAfterTopicConfigs() {
        return List.of(AFTER_REPO_CREATE, AFTER_REPO_DELETE, AFTER_REPO_UPDATE);
    }

    @Nullable
    public static JfbusTopicConfig getGenericAsyncTopic(SupportedUnifiedEvents event) {
        JfbusTopicConfig config = BY_EVENT.get(event);
        return config != null && config == AFTER_REMOTE_DOWNLOAD ? config : null;
    }

    @Nullable
    public static JfbusTopicConfig getRepoCudAfterTopic(SupportedUnifiedEvents event) {
        JfbusTopicConfig config = BY_EVENT.get(event);
        return config != null && repoCudAfterTopicConfigs().contains(config) ? config : null;
    }

    @Nullable
    public static JfbusTopicConfig getByEvent(SupportedUnifiedEvents event) {
        return BY_EVENT.get(event);
    }

    @Nullable
    public static TopicId getTopicId(SupportedUnifiedEvents event) {
        JfbusTopicConfig config = BY_EVENT.get(event);
        return config != null ? config.topicId : null;
    }

    @Nullable
    public static String getEntityType(SupportedUnifiedEvents event) {
        JfbusTopicConfig config = BY_EVENT.get(event);
        return config != null ? config.entityType : null;
    }

    /**
     * Distinct UEM v2 {@code entityType -> partitionCount} for every event that declares an entity type.
     * Used by {@link org.artifactory.unifiedevent.dispatchers.UemV2EventDispatcher} to register the
     * {@code artifactory.mb_<entityType>} topics at startup. Events without an entity type are skipped.
     * The returned map is unmodifiable and built once at class-load time.
     */
    public static Map<String, Integer> entityTypePartitions() {
        return ENTITY_TYPE_PARTITIONS;
    }

    private static final class RepositoryTopic {
        private static final TopicId ID = new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository");
    }

}
