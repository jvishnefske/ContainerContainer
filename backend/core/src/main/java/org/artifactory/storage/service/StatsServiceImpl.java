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

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.Request;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.storage.db.fs.service.StatsPersistingServiceImpl;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.service.constraints.NotSystemUserCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;

/**
 * Provides statistics persistence and delegation services
 *
 * @author Michael Pasternak
 */
@Slf4j
@Service
public class StatsServiceImpl implements StatsService {
    private static final String SKIP_UPDATE_STATS = "skipUpdateStats";

    private StatsPersistingServiceImpl statsPersistingService;
    private StatsDelegatingServiceImpl statsDelegatingService;
    private NotSystemUserCriteria notSystemUserCriteria;
    private CentralConfigService configService;
    private SecurityService securityService;
    private RepoStatsService repoStatsService;

    @Autowired
    public void setStatsPersistingService(StatsPersistingServiceImpl statsPersistingService) {
        this.statsPersistingService = statsPersistingService;
    }

    @Autowired
    public void setStatsDelegatingService(StatsDelegatingServiceImpl statsDelegatingService) {
        this.statsDelegatingService = statsDelegatingService;
    }

    @Autowired
    public void setNotSystemUserCriteria(NotSystemUserCriteria notSystemUserCriteria) {
        this.notSystemUserCriteria = notSystemUserCriteria;
    }

    @Autowired
    public void setConfigService(CentralConfigService configService) {
        this.configService = configService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setRepoStatsService(RepoStatsService repoStatsService) {
        this.repoStatsService = repoStatsService;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public StatsInfo getStats(FileInfo fileInfo) {
        return statsPersistingService.getStats(fileInfo);
    }

    /**
     * Collects stats from both DB and queue of events
     *
     * @param repoPath The file repo path to get stats on
     * @return {@link StatsInfo}
     */
    @Nullable
    @Override
    public StatsInfo getStats(RepoPath repoPath) {
        return statsPersistingService.getStats(repoPath);
    }

    /**
     * Triggered on local download event
     *
     * Event queued for local stats update and potential delegation
     *
     * @param repoPath               The file repo path to set/update stats
     * @param downloadedBy           User who downloaded the file
     * @param downloadedTime         Time the file was downloaded
     * @param fromAnotherArtifactory specifying whether request comes fromAnotherArtifactory
     *                               (happens on first transitive download when artifact not in cache yet)
     */
    @Override
    public void fileDownloaded(RepoPath repoPath, String downloadedBy, long downloadedTime,
            boolean fromAnotherArtifactory) {
        log.debug("Resource '{}' was downloaded by '{}' at '{}', fromAnotherArtifactory: '{}'",
                repoPath, downloadedBy, downloadedTime, fromAnotherArtifactory);

        if (notSystemUserCriteria.meet(downloadedBy)) {
            statsPersistingService.fileDownloaded(repoPath, downloadedBy, downloadedTime, fromAnotherArtifactory);
            if (!fromAnotherArtifactory) {
                // we'd like to skip trigger comes fromAnotherArtifactory
                // as it anyway will be delegated via remoteDownload event
                statsDelegatingService.fileDownloaded(repoPath, downloadedBy, downloadedTime);
            }
        } else {
            log.debug("The user: '{}' is not answering desired criteria, ignoring ...", downloadedBy);
        }
    }

    @Override
    public void updateDownloadStatsIfNeeded(RepoPath repoPath, Request request, boolean isRealRepo) {
        boolean skipStatsUpdate = request == null
                || isSkippedByQueryParam(request)
                || request.isHeadOnly()
                || !isRealRepo
                || !ConstantValues.downloadStatsEnabled.getBoolean()
                || isXrayRequest()
                || (request instanceof InternalArtifactoryRequest &&
                ((InternalArtifactoryRequest) request).isSkipStatsUpdate());
        if (!skipStatsUpdate) {
            fileDownloaded(repoPath, securityService.currentUsername(), System.currentTimeMillis(),
                    isFromAnotherArtifactory(request));
            if (ConstantValues.repoStatsEnabled.getBoolean()) {
                String originalRepoKey = request.getRepoPath() != null ? request.getRepoPath().getRepoKey() : null;
                repoStatsService.update(repoPath.getRepoKey(),
                        RepoStatDto.RepoStatUpdateType.DOWNLOAD, originalRepoKey);
            }
        }
    }

    private boolean isSkippedByQueryParam(Request request) {
        Map<String, String[]> parameters = request.getParameters();
        if (parameters.isEmpty()) {
            return false;
        }
        String[] paramValues = parameters.get(SKIP_UPDATE_STATS);
        if (paramValues == null) {
            return false;
        } else {
            return Boolean.parseBoolean(Arrays.stream(paramValues).findFirst().orElse("false"));
        }
    }

    /**
     * Used to avoid download counter increment when Xray is the one downloading the artifact for indexing purposes
     */
    private boolean isXrayRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserName = securityService.currentUsername();
        XrayDescriptor xrayConfig = configService.getDescriptor().getXrayConfig();
        return xrayConfig != null && xrayConfig.isEnabled() &&
                AuthenticationUtils.isXrayAuthenticationUser(currentUserName, authentication);
    }

    /**
     * Checks if original request came from another origin
     *
     * @return boolean
     */
    private boolean isFromAnotherArtifactory(Request request) {
        if (request.isFromAnotherArtifactory()) {
            return true;
        }
        if (request instanceof InternalArtifactoryRequest) {
            Enumeration origins = request.getHeaders(ArtifactoryRequest.ARTIFACTORY_ORIGINATED);
            if (origins == null) {
                origins = request.getHeaders(ArtifactoryRequest.ORIGIN_ARTIFACTORY);
            }
            return origins != null && origins.hasMoreElements();
        }
        return false;
    }

    /**
     * Triggered on remote download event
     *
     * Event queued for local stats update and potential delegation
     *
     * @param origin         The remote host the download was triggered by
     * @param path           The round trip of download request
     * @param repoPath       The file repo path to set/update stats
     * @param downloadedBy   User who downloaded the file
     * @param downloadedTime Time the file was downloaded
     * @param count          Amount of performed downloads
     */
    @Override
    public void fileDownloadedRemotely(String origin, String path, RepoPath repoPath, String downloadedBy,
            long downloadedTime, long count) {
        log.debug("Resource '{}' was downloaded remotely by '{}', at '{}', from {}, count: '{}'",
                repoPath, downloadedBy, downloadedTime, origin, count);

        if (notSystemUserCriteria.meet(downloadedBy)) {
            statsPersistingService.fileDownloadedRemotely(origin, path, repoPath, downloadedBy, downloadedTime, count);
            statsDelegatingService.fileDownloaded(origin, path, repoPath, downloadedBy, downloadedTime, count);
        } else {
            log.debug("The user: '{}' is not answering desired criteria, ignoring ...", downloadedBy);
        }
    }

    @Override
    public boolean setStats(RepoPath repoPath, StatsInfo statsInfo) {
        return statsPersistingService.setStats(repoPath, statsInfo);
    }

    @Override
    public int setStats(long nodeId, StatsInfo statsInfo) {
        return statsPersistingService.setStats(nodeId, statsInfo);
    }

    @Override
    public boolean deleteStats(long nodeId) {
        return statsPersistingService.deleteStats(nodeId);
    }

    /**
     * Checks if local stats available
     *
     * @param repoPath The repo path to check
     */
    @Override
    public boolean hasStats(RepoPath repoPath) {
        return statsPersistingService.hasStats(repoPath);
    }

    /**
     * Performs all queues flash
     */
    @Override
    public void flushStats() {
        statsPersistingService.flushStats();
        statsDelegatingService.flushStats();
    }
}
