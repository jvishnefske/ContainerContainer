package org.artifactory.unifiedevent;

import jakarta.validation.constraints.NotNull;
import org.artifactory.addon.replication.ReplicationTargetInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;

import java.util.List;

/**
 * Service interface for sending unified events.
 *
 * @author harendrar
 */
public interface UnifiedEventService {

    /**
     * Sends a "before download" unified event for the specified request and repository path.
     *
     * @param request  the {@link ArtifactoryRequest} representing the download request; must not be {@code null}
     * @param repoPath the {@link RepoPath} representing the artifact location;
     * @return a {@link DispatcherResponse} indicating the result of the event dispatch
     */
    DispatcherResponse sendArtifactEvent(@NotNull SupportedUnifiedEvents supportedUnifiedEvent,
            @NotNull ArtifactoryRequest request, RepoPath repoPath);

    /**
     * Sends an artifact event using the given remote resource/save context (e.g. from curation flow).
     * Equivalent to {@link #sendArtifactEvent(SupportedUnifiedEvents, ArtifactoryRequest, RepoPath)}
     * with repo path taken from {@code context.getRepoResource().getRepoPath()}.
     */
    DispatcherResponse sendRemoteArtifactEvent(@NotNull SupportedUnifiedEvents supportedUnifiedEvent,
            ArtifactResourceContext resourceContext);

    DispatcherResponse sendStorageEvent(@NotNull SupportedUnifiedEvents supportedUnifiedEvent,
            @NotNull VfsItem<? extends ItemInfo> info, DeleteContext ctx, RepoPath targetRepoPath);

    DispatcherResponse sendPropertyEvent(@NotNull SupportedUnifiedEvents eventType,
            @NotNull VfsItem<? extends ItemInfo> info, @NotNull String propertyName, List<String> propertyValues);

    DispatcherResponse sendReplicationEvent(@NotNull SupportedUnifiedEvents supportedUnifiedEvents,
            @NotNull RepoPath repoPath, @NotNull ReplicationTargetInfo targetInfo);

    /**
     * Publishes a repo CUD (create/update/delete) event for a pre-built canonical payload. The payload is wrapped in a
     * CloudEvents envelope (type, subject = repo key, data-only body) and dispatched through the unified event handler,
     * which fans it out to the Worker outbound (async Worker scripts) and the JFBus async dispatcher.
     *
     * @param eventType the repo CUD event type (e.g. {@link SupportedUnifiedEvents#AFTER_REPO_CREATE})
     * @param eventData the canonical, already-masked repo CUD event payload
     * @param subject   the event subject, typically the repository key
     * @return a {@link DispatcherResponse} indicating the result of the event dispatch
     */
    DispatcherResponse sendRepoConfigEvent(@NotNull SupportedUnifiedEvents eventType, RepoConfigEventData eventData,
            String subject);
}
