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

package org.artifactory.storage.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.fs.StatsImpl;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.db.fs.entity.Stat;
import org.artifactory.storage.db.fs.service.AbstractStatsService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.concurrency.ConflictGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_ROOT;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_STATISTICS;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.util.RepoPathUtils.removeDashCache;

/**
 * Delegates statistics events to registered members
 *
 * @author Michael Pasternak
 */
@Service
@Slf4j
public class StatsDelegatingServiceImpl extends AbstractStatsService implements StatsDelegatingService {

    //queue interpretation: destination rr | source rr | content to send
    private volatile ConcurrentMap<String, Map<String, Collection<StatsEvent>>> delegationQueue = Maps
            .newConcurrentMap();

    private static final String PATH_SEPARATOR = "/";
    private static final String ARTIFACTORY_APP_PATH = PATH_SEPARATOR + "artifactory";
    private static final String API_PATH = PATH_SEPARATOR + "api" + PATH_SEPARATOR;
    private static final String REMOTE_STATS_ARTIFACTORY_API_PATH = ARTIFACTORY_APP_PATH + API_PATH +
            PATH_ROOT + PATH_SEPARATOR + PATH_STATISTICS + PATH_SEPARATOR;
    private static final String REMOTE_STATS_API_PATH = API_PATH + PATH_ROOT + PATH_SEPARATOR + PATH_STATISTICS +
            PATH_SEPARATOR;

    private ConflictGuard executionGuard;

    @Autowired
    private InternalRepositoryService repoService;

    /**
     * Queueing event for delegation,
     * (triggered on artifact local download event)
     *
     * @param repoPath       The file repo path to set/update stats
     * @param downloadedBy   User who downloaded the file
     * @param downloadedTime Time the file was downloaded
     */
    public synchronized void fileDownloaded(RepoPath repoPath, String downloadedBy, long downloadedTime) {
        log.debug("Queuing downloaded delegation for resource '{}' by '{}' at '{}'", repoPath, downloadedBy,
                downloadedTime);

        StatsEvent statsEvent = getStatsEvents().get(repoPath);
        if (statsEvent == null) {
            getStatsEvents().put(repoPath, statsEvent = new StatsEvent(repoPath, null));
        }
        statsEvent.update(downloadedBy, null, null, downloadedTime, 1);
    }

    /**
     * Queueing event for delegation,
     * (triggered on artifact remote download event)
     *
     * @param origin         The remote host the download was triggered by
     * @param path           The round trip of download request
     * @param repoPath       The file repo path to set/update stats
     * @param downloadedBy   User who downloaded the file
     * @param downloadedTime Time the file was downloaded
     * @param count          Amount of performed downloads
     */
    @Override
    public synchronized void fileDownloaded(String origin, String path, RepoPath repoPath,
            String downloadedBy, long downloadedTime, long count) {

        log.debug(
                "Queuing download delegation for resource '{}' downloaded remotely by '{}', at '{}', from {}, count: " +
                        "'{}'",
                repoPath, downloadedBy, downloadedTime, origin, count);

        StatsEvent statsEvent = getStatsEvents().get(repoPath);
        if (statsEvent == null) {
            getStatsEvents().put(repoPath, statsEvent = new StatsEvent(repoPath, origin));
        }
        statsEvent.update(downloadedBy, origin, path, downloadedTime, count);
    }

    /**
     * Queue statistics for delegation to remote Artifactory instance,
     * queueing grouping is based on RemoteRepo target. e.g. actual remote
     * repository
     *
     * @param event  stats event
     * @param nodeId id representing artifact location
     * @param stats  statistics
     */
    @Override
    protected void processStats(StatsEvent event, long nodeId, Stat stats) {
        log.debug("Processing delegation event '{}' for nodeId '{}', stats {}", event, nodeId, stats);

        String sourceRepoKey = event.getRepoPath().getRepoKey();
        RemoteRepoConfig remoteRepoConfig = repoService.getRemoteRepoConfigByKey(removeDashCache(sourceRepoKey));
        if (remoteRepoConfig != null && remoteRepoConfig.isSmart()) {
            String targetRepoKey = getRemoteRepoKey(remoteRepoConfig);
            if (!StringUtils.isBlank(targetRepoKey)) {
                log.debug("Event {} saved for delegation to repo {}", event, targetRepoKey);
                getQueuedStatsEvents(sourceRepoKey, targetRepoKey).add(event);
            } else {
                log.debug("Can't find targetRepoKey by URL {}", remoteRepoConfig.getRepoTypeConfig().getUrl());
            }
        } else {
            log.debug("Repository configuration not found or repository is not a smart repo: {}", sourceRepoKey);
        }
    }

    /**
     * Extracts actual remote repo key from remote URI
     *
     * @return remote repoKey
     */
    @VisibleForTesting
    String getRemoteRepoKey(RemoteRepoConfig remoteRepoConfig) {
        try {
            URI uri = URI.create(remoteRepoConfig.getRepoTypeConfig().getUrl());
            String[] pathItems = StringUtils.split(uri.getPath(), "/");
            if (pathItems.length > 0) {
                return pathItems[pathItems.length - 1];
            }
            log.warn("Failed to get remote repo key from URI. URI path is root: {}", uri);
        } catch (Exception ex) {
            log.warn("Error on extracting remote repo key from URI", ex);
        }
        return null;
    }

    @Override
    protected void onTraversingStart() {
        log.debug("Starting events grouping for processing");
    }

    /**
     * In remote context, this method triggers actual grouped
     * events delegation
     */
    @Override
    protected void onTraversingEnd() {
        log.debug("Finished events grouping, delegating ...");

        Iterator<Map.Entry<String, Map<String, Collection<StatsEvent>>>> iterator
                = delegationQueue.entrySet().iterator();

        while (iterator.hasNext()) {
            final Map.Entry<String, Map<String, Collection<StatsEvent>>> entry = iterator.next();
            final String repoKey = entry.getKey();
            final Map<String, Collection<StatsEvent>> events = entry.getValue();
            iterator.remove();
            delegateStats(repoKey, events);
        }
    }

    /**
     * Fetches collection of events targeted to targetRepoKey grouped by sourceRepoKey
     *
     * @param sourceRepoKey local RemoteRepo
     * @param targetRepoKey target RemoteRepo
     * @return a collection of events
     */
    private Collection<StatsEvent> getQueuedStatsEvents(String sourceRepoKey, String targetRepoKey) {
        Map<String, Collection<StatsEvent>> events = delegationQueue.get(targetRepoKey);
        if (events == null) {
            synchronized (delegationQueue) {
                events = delegationQueue.get(targetRepoKey);
                if (events == null) {
                    delegationQueue.put(targetRepoKey, new ConcurrentHashMap<>());
                    events = delegationQueue.get(targetRepoKey);
                    events.put(sourceRepoKey, new ConcurrentLinkedQueue<>());
                }
            }
        }
        if (events.get(sourceRepoKey) == null) {
            synchronized (delegationQueue) {
                if (events.get(sourceRepoKey) == null) {
                    events.put(sourceRepoKey, new ConcurrentLinkedQueue<>());
                }
            }
        }
        return events.get(sourceRepoKey);
    }

    /**
     * Performs statistics delegation to RemoteRepository
     *
     * @param events an events to delegated (grouped by source repo)
     */
    private void delegateStats(String repoKey, Map<String, Collection<StatsEvent>> events) {
        if (!filterEvents(events)) {
            return;
        }
        log.debug("Delegating event/s '{}' to remote repo \"{}\"", events, repoKey);
        doDelegate(repoKey, events);
    }

    /**
     * Filters out events not applicable for delegation
     *
     * @param events an events to delegated (grouped by source repo)
     * @return if delegation should occur (e.g. any events left)
     */
    @VisibleForTesting
    boolean filterEvents(Map<String, Collection<StatsEvent>> events) {
        boolean shouldRemove;
        Iterator<Map.Entry<String, Collection<StatsEvent>>> iterator = events.entrySet().iterator();
        while (iterator.hasNext()) {
            shouldRemove = false;
            final Map.Entry<String, Collection<StatsEvent>> event = iterator.next();
            String repoKey = removeDashCache(event.getKey());
            HttpRepo remoteRepo = (HttpRepo) repoService.remoteRepositoryByKey(repoKey);
            if (remoteRepo == null) {
                log.debug("Not performing content synchronization due to missing remote repository in context," +
                        " for instance, this may happen if current repository is LOCAL or repoKey is incorrect");
                shouldRemove = true;
            } else {
                if (remoteRepo.isOffline()) {
                    log.debug("Not performing content synchronization due to offline remote repository");
                    shouldRemove = true;
                } else if (isStaticsSynchronisationDisabled(remoteRepo)) {
                    log.debug("Not performing content synchronization due to disabled ContentSynchronisation");
                    shouldRemove = true;
                }
            }
            if (shouldRemove) {
                iterator.remove();
            }
        }
        return !events.isEmpty();
    }

    private static boolean isStaticsSynchronisationDisabled(HttpRepo remoteRepo) {
        return !remoteRepo.getRepoConfig().isSmart() || !remoteRepo.getRepoConfig().isStatsSyncEnabled();
    }

    /**
     * actual content delegation in groups targeted to specific
     * remoteRepo
     */
    private void doDelegate(String remoteRepoKey, Map<String, Collection<StatsEvent>> eventsCollection) {
        log.debug("Received content for delegation is: '{}', target: '{}'", eventsCollection, remoteRepoKey);
        for (Map.Entry<String, Collection<StatsEvent>> localRepo : eventsCollection.entrySet()) {
            Collection<StatsEvent> events = localRepo.getValue();
            if (events.isEmpty()) {
                continue;
            }
            String localRemoteRepoKey = removeDashCache(localRepo.getKey());
            HttpRepo remoteRepo = (HttpRepo) repoService.remoteRepositoryByKey(localRemoteRepoKey);
            if (remoteRepo != null) {
                handleRemoteRequest(remoteRepo, events);
            } else {
                log.warn("Can't find local remoteRepo by key {} from delegation targeted events", localRemoteRepoKey);
            }
        }
    }

    private void handleRemoteRequest(HttpRepo remoteRepo, Collection<StatsEvent> events) {
        CloseableHttpResponse response = null;
        try {
            HttpPut putMethod = buildHttpPutRequest(remoteRepo, events);
            response = request(putMethod, remoteRepo);
            isOkResponse(response);
        } catch (Exception ex) {
            log.error("Error during content delegation", ex);
        } finally {
            closeQuietly(response);
        }
    }

    private void isOkResponse(CloseableHttpResponse response) {
        if (response == null || response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            log.warn("Bad response \"{}\" from delegation request",
                    response != null ? response.getStatusLine()
                            : "response is null!");
        }
    }

    private HttpPut buildHttpPutRequest(HttpRepo remoteRepo, Collection<StatsEvent> events) throws IOException {
        URI uri = URI.create(remoteRepo.getUrl());
        String actualUrl = produceRequestUrl(uri);
        log.debug("Target URL: '{}'", actualUrl);
        HttpPut putMethod = new HttpPut(actualUrl);
        String serializedData = serialize(toStatsImpl(events));
        log.debug("Serialized content to send: '{}'", serializedData);
        putMethod.setEntity(new StringEntity(serializedData, ContentType.APPLICATION_JSON));
        putMethod.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        return putMethod;
    }

    /**
     * Converts StatsEvent to StatsImpl
     * (StatsEvent is not available in artifactory-api layer)
     *
     * @return {@link StatsImpl}
     */
    private StatsImpl[] toStatsImpl(Collection<StatsEvent> statsEvents) {
        StatsImpl[] stats = new StatsImpl[statsEvents.size()];
        int i = -1;
        for (StatsEvent statsEvent : statsEvents) {
            StatsImpl statsImpl = new StatsImpl();
            statsImpl.setDownloadCount(statsEvent.getLocalEventCount().get());
            statsImpl.setLastDownloaded(statsEvent.getLocalDownloadedTime());
            statsImpl.setLastDownloadedBy(statsEvent.getLocalDownloadedBy());

            statsImpl.setRemoteDownloadCount(statsEvent.getRemoteEventCount().get());
            statsImpl.setRemoteLastDownloaded(statsEvent.getRemoteDownloadedTime());
            statsImpl.setRemoteLastDownloadedBy(statsEvent.getRemoteDownloadedBy());

            statsImpl.setRepoPath(statsEvent.getRepoPath().getPath());
            statsImpl.setPath(statsEvent.getPath());
            stats[++i] = statsImpl;
        }
        return stats;
    }

    /**
     * Produces url to be used against target host
     *
     * @param uri original URI
     * @return url to be used
     */
    private String produceRequestUrl(URI uri) {
        String[] uriParts = uri.getPath().split("/");
        String remoteRepoKey = uriParts[uriParts.length - 1];
        return new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getHost())
                .append(uri.getPort() != -1 ?
                        ":" + uri.getPort()
                        :
                        ""
                )
                .append(uri.getPath().startsWith(ARTIFACTORY_APP_PATH) ?
                        REMOTE_STATS_ARTIFACTORY_API_PATH
                        :
                        getServiceName(uri) + REMOTE_STATS_API_PATH
                )
                .append(toCachedRepoKey(remoteRepoKey))
                .toString();
    }

    /**
     * Fetches service name from the original URI
     *
     * @return service name
     */
    private String getServiceName(URI uri) {
        if (uri.getPath() != null) {
            String[] parts = uri.getPath().split("/");
            if (parts.length >= 2) {
                if (!(parts.length == 2 && uri.getPath().startsWith("/") && uri.getPath().endsWith("/"))) {
                    return "/" + PathUtils.getFirstPathElement(uri.getPath()); // .../serviceName/repoName
                }
            }
        }
        return "";
    }

    /**
     * Converts repoKey to repoKey-cache
     *
     * @return remote repoKey
     */
    private String toCachedRepoKey(String remoteRepoKey) {
        boolean isLocalRepo = repoService
                .localOrFederatedRepositoryByKey(remoteRepoKey) != null;
        return (remoteRepoKey + (isLocalRepo ||
                remoteRepoKey.endsWith(REMOTE_CACHE_SUFFIX) ? "" : REMOTE_CACHE_SUFFIX));
    }

    protected ConflictGuard getConflictGuard() {
        if (executionGuard == null) {
            AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
            HaCommonAddon haCommonAddon = addonsManager.addonByType(HaCommonAddon.class);
            executionGuard = haCommonAddon.getConflictGuard(HaCommonAddon.STATS_REMOTE_SEMAPHORE_NAME);
        }
        return executionGuard;
    }

    /**
     * Serializes given item to json
     *
     * @param item an object to serialize
     * @return serialized json string
     */

    private <T> String serialize(T item) throws IOException {
        return getObjectMapper().writeValueAsString(item);
    }

    /**
     * Creates a Jackson object mapper
     *
     * @return {@link com.fasterxml.jackson.databind.ObjectMapper}
     */
    private ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Executes HttpMethod against RemoteRepo target
     *
     * @return {@link CloseableHttpResponse}
     */
    @VisibleForTesting
    CloseableHttpResponse request(HttpRequestBase method, @Nonnull HttpRepo httpRepo) {
        try {
            return httpRepo.executeMethod(method);
        } catch (IOException e) {
            log.warn("Executing remote statistics delegation to \"{}\" has failed due to:",
                    httpRepo.getUrl(), e);
        }
        return null;
    }
}