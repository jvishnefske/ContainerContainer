/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.event.service;

import com.google.common.collect.Sets;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.mirror.DeleteEventHintType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.FederationClientService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.event.EventsService;
import org.artifactory.storage.event.ShiftEventsService;
import org.artifactory.storage.tx.SessionEventHandler;
import org.artifactory.storage.tx.SessionResource;
import org.artifactory.storage.tx.SessionResourceAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.artifactory.util.CollectionUtils.isNullOrEmpty;
import static org.artifactory.util.RepoPathUtils.isCache;
import static org.artifactory.util.RepoPathUtils.isSupportBundle;
import static org.artifactory.util.RepoPathUtils.isTrash;
import static org.artifactory.util.RepoPathUtils.removeDashCache;


/**
 * The events session resource is a {@link SessionResource} that receives low level storage events and stores them in
 * the events table once the transaction is committed.
 * Like any other session resource, it is attached to a single thread, lives and dies in the context of the current
 * database transaction.
 *
 * @author Yossi Shaul
 */
public class EventsSessionResource extends SessionResourceAdapter implements SessionEventHandler {
    private final Set<EventInfo> events = Sets.newLinkedHashSet();

    private final ShiftEventsService shiftEventsService;
    private final AddonsManager addonsManager;
    private boolean shouldTriggerShift = false;
    private final Set<String> consumedHintPaths = new HashSet<>();
    private final boolean shouldFilterEventsOfVirtualLocalRepo;
    FederationClientService federationClientService;
    InternalRepositoryConfigService repositoryConfigService;

    public EventsSessionResource() {
        this.shiftEventsService = ContextHelper.get().beanForType(ShiftEventsService.class);
        this.federationClientService = ContextHelper.get().beanForType(FederationClientService.class);
        this.repositoryConfigService = ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
        this.addonsManager = ContextHelper.get().beanForType(AddonsManager.class);

        this.shouldFilterEventsOfVirtualLocalRepo = ConstantValues.filterEventsOfVirtualLocalRepository.getBoolean();
    }

    @Override
    public void beforeCommit() {
        EventsService eventsService = ContextHelper.get().beanForType(EventsService.class);
        if (events.isEmpty()) {
            return;
        }
        LeadArtifactDetectorAddon leadArtifactDetector = addonsManager.addonByType(LeadArtifactDetectorAddon.class);
        List<EventInfo> filteredEvents = events.stream()
                .filter(item -> shouldIncludeEvents(item, leadArtifactDetector))
                // change timestamp to just now - make the gap between event and its being committed in db shorter.
                // add hints from thread local if exist
                .map(this::createEvent)
                .toList();
        if (!filteredEvents.isEmpty()) {
            eventsService.appendEvents(filteredEvents);
            shouldTriggerShift = true;
        }
    }

    private boolean shouldIncludeEvents(EventInfo item, LeadArtifactDetectorAddon leadArtifactDetector) {
        String repoKey = RepoPathFactory.create(item.getPath()).getRepoKey();
        if (isTrash(repoKey) || isSupportBundle(repoKey)) {
            return false;
        }
        if (isCache(repoKey)) {
            repoKey = removeDashCache(repoKey);
            RepoConfig repoConfig = repositoryConfigService.getRepoConfigByKey(repoKey, true);
            return repoConfig == null || (repoConfig.isReal() &&
                    leadArtifactDetector.isLeadArtifact(repoConfig.getPackageType(),
                            RepoPathFactory.create(item.getPath())));
        }
        RepoConfig repoConfig = repositoryConfigService.getRepoConfigByKey(repoKey);

        if (repoConfig == null) {
            return true;
        }

        if (shouldFilterEventsOfVirtualLocalRepo && !repoConfig.isReal()) {
            return false;
        }
        return !isUploadDirEvents(repoConfig, item.getPath());
    }

    private EventInfo createEvent(EventInfo eventInfo) {
        Set<String> allHints = new HashSet<>();

        Set<String> hints = EventLogHintManager.getHints();
        if (!isNullOrEmpty(hints)) {
            allHints = Sets.union(allHints, hints);
        }
        if (!isNullOrEmpty(eventInfo.getHints())) {
            allHints = Sets.union(allHints, eventInfo.getHints());
        }

        RepoPath repoPath = RepoPathFactory.create(eventInfo.getPath());
        Set<String> hintsByPath = EventLogHintManager.getHints(repoPath);
        if (!isNullOrEmpty(hintsByPath)) {
            allHints = Sets.union(allHints, hintsByPath);
            consumedHintPaths.add(repoPath.getCanonicalKey());
        }

        return new EventInfo(eventInfo.getType(), eventInfo.getPath(), allHints);
    }

    @Override
    public void afterConnectionReleased(boolean committed) {
        // If commit succeed and has events to shift => trigger shift
        if (committed && shouldTriggerShift) {
            //trigger shift the events to node_events with sequential ids
            shiftEventsService.triggerShift();
        }
        // Release path hints from the ThreadLocal only once their events are durably committed; on rollback they are
        // kept (a retry on this thread may still need them) and the request-end ArtifactoryFilter.clear() backstops.
        if (committed) {
            consumedHintPaths.forEach(EventLogHintManager::removePathHints);
        }
    }

    @Override
    public void itemCreated(VfsItem item) {
        events.add(new EventInfo(EventType.create, item.getRepoPath().toPath()));
    }

    @Override
    public void itemUpdated(VfsItem item) {
        events.add(new EventInfo(EventType.update, item.getRepoPath().toPath()));
    }

    @Override
    public void itemDeleted(VfsItem item) {
        events.add(new EventInfo(EventType.delete, item.getRepoPath().toPath(),
                Set.of(item.isFile()
                        ? DeleteEventHintType.File.getDescription()
                        : DeleteEventHintType.Folder.getDescription())));
    }

    @Override
    public void propertiesModified(VfsItem item) {
        events.add(new EventInfo(EventType.props, item.getRepoPath().toPath()));
    }

    /**
     * Check if the path is _uploads dir events.
     * It is relevant for docker and huggingface packages
     */
    private boolean isUploadDirEvents(RepoConfig repoConfig, String path) {
        boolean dockerSupport = repoConfig.getPackageType().isDockerGroup();
        boolean huggingFaceSupport = repoConfig.getPackageType().isHuggingFace();
        return (dockerSupport || huggingFaceSupport) && path.contains("/_uploads/");
    }
}
