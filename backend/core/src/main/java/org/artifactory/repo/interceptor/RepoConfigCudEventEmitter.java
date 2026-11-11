package org.artifactory.repo.interceptor;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoMetadata;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.interceptor.repocud.RepoConfigEventDataBuilder;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.springframework.stereotype.Component;

/**
 * Applies repo CUD emission policy (feature flags, system-repo exclusion, event-type resolution) and builds the
 * canonical {@link RepoConfigEventData} payload before handing off to {@link UnifiedEventService}.
 * Shared by {@link EventRepoConfigChangeInterceptor} and other config-change call sites (e.g. partial-delete AFTER).
 */
@Slf4j
@Component
public class RepoConfigCudEventEmitter {

    private final RepoConfigEventDataBuilder eventDataBuilder;
    private final UnifiedEventService unifiedEventService;

    public RepoConfigCudEventEmitter(RepoConfigEventDataBuilder eventDataBuilder,
            UnifiedEventService unifiedEventService) {
        this.eventDataBuilder = eventDataBuilder;
        this.unifiedEventService = unifiedEventService;
    }

    public void emitBefore(RepoConfigChange change) {
        if (!shouldEmitBefore(change)) {
            return;
        }
        SupportedUnifiedEvents eventType = resolveBeforeEventType(change);
        if (eventType == null) {
            log.debug("No BEFORE repo event for action: {}", change.getConfigAction());
            return;
        }
        unifiedEventService.sendRepoConfigEvent(eventType, buildEventData(change, eventType, null),
                change.getRepoKey());
    }

    public void emitAfter(RepoConfigChange change) {
        emitAfter(change, null);
    }

    public void emitAfter(RepoConfigChange change, @Nullable Integer statusCode) {
        if (!shouldEmitAfter(change)) {
            return;
        }
        SupportedUnifiedEvents eventType = resolveAfterEventType(change);
        if (eventType == null) {
            log.debug("No AFTER repo event for action: {}", change.getConfigAction());
            return;
        }
        unifiedEventService.sendRepoConfigEvent(eventType, buildEventData(change, eventType, statusCode),
                change.getRepoKey());
    }

    private RepoConfigEventData buildEventData(RepoConfigChange change, SupportedUnifiedEvents eventType,
            @Nullable Integer statusCode) {
        RepoMetadata metadata = change.getRepoMetadata() != null ? change.getRepoMetadata() : RepoMetadata.empty();
        return eventDataBuilder.build(change, eventType, statusCode, metadata);
    }

    private static boolean shouldEmitBefore(RepoConfigChange change) {
        return change != null
                && isUserManagedRepo(change)
                && ConstantValues.repoCudWorkerEventsEnabled.getBoolean();
    }

    private static boolean shouldEmitAfter(RepoConfigChange change) {
        return change != null
                && isUserManagedRepo(change)
                && (ConstantValues.repoCudWorkerEventsEnabled.getBoolean()
                || ConstantValues.repoCudJfbusEventsEnabled.getBoolean());
    }

    private static boolean isUserManagedRepo(RepoConfigChange change) {
        return !SystemRepos.SystemRepoConfig.isSystemRepo(change.getRepoKey());
    }

    @Nullable
    private static SupportedUnifiedEvents resolveBeforeEventType(RepoConfigChange change) {
        return switch (change.getConfigAction()) {
            case REPO_CREATE -> SupportedUnifiedEvents.BEFORE_REPO_CREATE;
            case REPO_UPDATE -> SupportedUnifiedEvents.BEFORE_REPO_UPDATE;
            case REPO_DELETE -> SupportedUnifiedEvents.BEFORE_REPO_DELETE;
            default -> null;
        };
    }

    @Nullable
    private static SupportedUnifiedEvents resolveAfterEventType(RepoConfigChange change) {
        return switch (change.getConfigAction()) {
            case REPO_CREATE -> SupportedUnifiedEvents.AFTER_REPO_CREATE;
            case REPO_UPDATE -> SupportedUnifiedEvents.AFTER_REPO_UPDATE;
            case REPO_DELETE -> SupportedUnifiedEvents.AFTER_REPO_DELETE;
            default -> null;
        };
    }
}
