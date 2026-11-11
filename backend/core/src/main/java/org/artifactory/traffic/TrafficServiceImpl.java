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

package org.artifactory.traffic;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.metrics.MetricsInfo;
import org.artifactory.metrics.MetricsThreadLocal;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemFolders;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.traffic.context.TrafficContext;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.traffic.entry.LegacyTrafficEntry;
import org.artifactory.traffic.entry.TransferEntry;
import org.artifactory.traffic.mbean.Traffic;
import org.artifactory.traffic.read.TrafficReader;
import org.artifactory.traffic.stats.RepoTrafficStatsService;
import org.artifactory.traffic.summary.TrafficSummaryService;
import org.artifactory.traffic.v2.TrafficLoggerV2;
import org.artifactory.traffic.v3.TrafficLoggerV3;
import org.artifactory.util.CollectionUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.repo.system.SystemFolders.SystemFolder.JFROG;

/**
 * Traffic service persists the traffic (download/upload) in Artifactory and can retrieve it by date range.
 *
 * @author Yoav Landman
 */
@Slf4j
@Service
@Reloadable(beanClass = InternalTrafficService.class, initAfter = {InternalRepositoryService.class, TaskService.class},
        listenOn = CentralConfigKey.none)
public class TrafficServiceImpl implements InternalTrafficService, ReloadableBean {

    private ArtifactoryServersCommonService serversService;
    private RepositoryService repositoryService;
    private ProjectsService projectsService;
    private SecurityService securityService;
    private TrafficSummaryService trafficSummaryService;
    private RepoTrafficStatsService repoTrafficStatsService;

    @Autowired
    public void setServersService(ArtifactoryServersCommonService serversService) {
        this.serversService = serversService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setTrafficSummaryService(TrafficSummaryService trafficSummaryService) {
        this.trafficSummaryService = trafficSummaryService;
    }

    @Autowired
    public void setRepoTrafficStatsService(RepoTrafficStatsService repoTrafficStatsService) {
        this.repoTrafficStatsService = repoTrafficStatsService;
    }

    @Override
    public void init() {
        //Register a mbean
        InternalArtifactoryContext context = InternalContextHelper.get();
        Traffic traffic = new Traffic(context.beanForType(InternalTrafficService.class));
        ContextHelper.get().beanForType(MBeanRegistrationServiceImpl.class).register(traffic);
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        //nop
    }

    @Override
    public void destroy() {
        //nop
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        //When conversion is needed, remove all old stats
    }

    @Override
    public void handleTrafficEntry(LegacyTrafficEntry entry) {
        handleTrafficEntry(entry, null);
    }

    @Override
    public void handleTrafficEntry(LegacyTrafficEntry trafficEntry, TrafficEntry.TrafficActor actor) {
        if (trafficEntry instanceof TransferEntry transferEntry) {
            RepoPath repoPath = InternalRepoPathFactory.createRepoPath(transferEntry.getRepoPath());
            String repoName = repoPath.getRepoKey();
            RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoName);
            handleMetrics(repoConfig, transferEntry.getAction().name());
            if (isV1Active()) {
                logTransferEntry(transferEntry);
            }
        }
    }

    private boolean isV1Active() {
        return ConstantValues.trafficCollectionActive.getBoolean();
    }

    protected void logTransferEntry(TransferEntry transferEntry) {
        TrafficLogger.logTransferEntry(transferEntry);
    }

    private void setAdditionalDetailsToEntry(TrafficEntry trafficEntry) {
        RepoPath repoPath = InternalRepoPathFactory.createRepoPath(trafficEntry.getRepoPath());
        String repoKey = repoPath.getRepoKey();
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        handleMetrics(repoConfig, trafficEntry.getAction().name());
        setExtendedInfo(trafficEntry, repoConfig, repoKey);
    }

    @Override
    public void handleTrafficEntry(TrafficEntry trafficEntry) {
        setAdditionalDetailsToEntry(trafficEntry);
        if (isV2Active()) {
            logTrafficEntryV2(trafficEntry);
        }
        if (shouldLogToTrafficLogV3(trafficEntry)) {
            logTrafficEntryV3(trafficEntry);
        }
        trafficSummaryService.collectTrafficEntry(trafficEntry);
        repoTrafficStatsService.recordTrafficEntry(trafficEntry);
    }

    private boolean isV2Active() {
        return ConstantValues.trafficVersion2Enabled.getBoolean();
    }

    @VisibleForTesting
    protected void logTrafficEntryV2(TrafficEntry trafficEntryV2) {
        TrafficLoggerV2.logEntry(trafficEntryV2);
    }

    @Override
    public void handleTrafficEntryV3(TrafficEntry trafficEntryV3) {
        setAdditionalDetailsToEntry(trafficEntryV3);
        if (shouldLogToTrafficLogV3(trafficEntryV3)) {
            logTrafficEntryV3(trafficEntryV3);
            trafficSummaryService.collectTrafficEntry(trafficEntryV3);
        }
        repoTrafficStatsService.recordTrafficEntry(trafficEntryV3);
    }

    private boolean shouldLogToTrafficLogV3(TrafficEntry trafficEntry) {
        if (!ConstantValues.systemFolderContractEnabled.getBoolean()) {
            return isV3Active() &&
                    !trafficEntry.getRepoPath().contains(":" + JFROG.getFolderName());
        }
        return isV3Active() && isBilledTraffic(trafficEntry);
    }

    /**
     * Whether {@code trafficEntry}'s path counts toward billing, and therefore should be logged to
     * the V3 traffic log. {@code .jfrog} keeps its original exclusion unchanged and is never
     * governed by the contract; {@code .evidence} (and any other non-{@code .jfrog} system folder)
     * is governed by the system-folder contract ({@code SystemFolders.isBilled}); all other paths
     * are billed.
     */
    private boolean isBilledTraffic(TrafficEntry trafficEntry) {
        // .jfrog keeps its exact original exclusion
        if (trafficEntry.getRepoPath().contains(":" + JFROG.getFolderName())) {
            return false;
        }
        // Only .evidence will use isBilled
        RepoPath repoPath = InternalRepoPathFactory.createRepoPath(trafficEntry.getRepoPath());
        return SystemFolders.forPath(repoPath)
                .map(SystemFolders::isBilled)
                .orElse(true);
    }

    @Override
    public boolean isV3Active() {
        return ConstantValues.trafficVersion3Enabled.getBoolean();
    }

    @VisibleForTesting
    protected void logTrafficEntryV3(TrafficEntry trafficEntryV3) {
        TrafficLoggerV3.logEntry(trafficEntryV3);
    }

    @VisibleForTesting
    void setExtendedInfo(TrafficEntry trafficEntry, RepoConfig repoConfig, String repoKey) {
        if (repoConfig == null) {
            log.warn("repoConfig = null, some fields may not be filled");
        }
        String projectKey = projectsService.getProjectKeyOfRepository(repoKey);
        trafficEntry.setRepoKey(repoKey);
        trafficEntry.setProjectKey(projectKey);
        String username = TrafficContextThreadLocal.getUsername() != null ?
                TrafficContextThreadLocal.getUsername() : securityService.currentUsername();
        trafficEntry.setUsername(username);
        if (repoConfig != null) {
            trafficEntry.setRepoType(repoConfig.getRepoType().typeNameLowercase());
            trafficEntry.setPackageType(repoConfig.getPackageType().getType());
        }
        setOriginInfo(trafficEntry, repoConfig, repoKey, projectKey);
    }

    private void setOriginInfo(TrafficEntry trafficEntry, RepoConfig defaultRepoConfig, String defaultRepoKey,
            String defaultProjectKey) {
        String originRepoKey = resolveOriginRepoKey(defaultRepoKey);
        trafficEntry.setOriginRepoKey(originRepoKey);

        RepoConfig originRepoConfig;
        String originProjectKey;
        if (originRepoKey.equals(defaultRepoKey)) {
            originRepoConfig = defaultRepoConfig;
            originProjectKey = defaultProjectKey;
        } else {
            originRepoConfig = repositoryService.getRepoConfigByKey(originRepoKey);
            originProjectKey = projectsService.getProjectKeyOfRepository(originRepoKey);
        }

        trafficEntry.setOriginProjectKey(originProjectKey);
        if (originRepoConfig != null) {
            trafficEntry.setOriginRepoType(originRepoConfig.getRepoType().typeNameLowercase());
            trafficEntry.setOriginPackageType(originRepoConfig.getPackageType().getType());
        } else if (defaultRepoConfig != null) {
            trafficEntry.setOriginRepoType(defaultRepoConfig.getRepoType().typeNameLowercase());
            trafficEntry.setOriginPackageType(defaultRepoConfig.getPackageType().getType());
        }
    }

    /**
     * Resolves the origin repo key using MDC > TrafficContextThreadLocal > default.
     * MDC (CurrentActivity.REPO) holds the virtual repo key set by RequestFilter and
     * survives past TrafficContextThreadLocal.unbind(), which matters for Jersey response
     * callbacks that fire after unbind.
     */
    @VisibleForTesting
    String resolveOriginRepoKey(String defaultRepoKey) {
        if (ConstantValues.trafficOriginFromMdcEnabled.getBoolean()) {
            String mdcRepoKey = CurrentActivity.REPO.get();
            if (isNotBlank(mdcRepoKey)) {
                return mdcRepoKey;
            }
        }
        TrafficContext trafficContext = TrafficContextThreadLocal.getTrafficContext();
        if (trafficContext != null && isNotBlank(trafficContext.getOriginRepoKey())) {
            return trafficContext.getOriginRepoKey();
        }
        return defaultRepoKey;
    }

    @VisibleForTesting
    protected void handleMetrics(RepoConfig repoConfig, String name) {
        if (repoConfig == null) {
            log.warn("metrics unavailable due to repoConfig = null");
            return;
        }
        MetricsInfo metricsInfo = MetricsInfo.builder()
                .repoClass(getRepoClassByRepoConfig(repoConfig))
                .repoType(repoConfig.getPackageType().getType())
                .action(name)
                .build();
        MetricsThreadLocal.setMetricsInfoList(Collections.singletonList(metricsInfo));
    }

    @VisibleForTesting
    String getRepoClassByRepoConfig(RepoConfig repoConfig) {
        String repoClass = repoConfig.getRepoType().typeNameLowercase();
        return repoClass.equals("remote") || repoClass.equals("virtual") ? repoClass : "local";
    }

    @Override
    public void validateDateRange(Calendar startDate, Calendar endDate) {
        if (startDate.after(endDate)) {
            throw new IllegalArgumentException("The start date cannot be later than the end date");
        }
    }

    /**
     * Returns traffic entries
     *
     * @param from Traffic start time
     * @param to   Traffic end time
     * @return {@link List< LegacyTrafficEntry >} taken from the traffic log files or the database
     */
    @Override
    public List<LegacyTrafficEntry> getEntryList(Calendar from, Calendar to) {
        return getTrafficReader().getEntries(from, to);
    }

    /**
     * Returns Xray traffic entries
     *
     * @param from Traffic start time
     * @param to   Traffic end time
     * @return {@link List< LegacyTrafficEntry >} taken from the traffic log files or the database
     */
    @Override
    public List<LegacyTrafficEntry> getXrayEntryList(Calendar from, Calendar to) {
        return getTrafficReader().getXrayEntries(from, to);
    }

    private TrafficReader getTrafficReader() {
        return new TrafficReader(getTrafficLogDir());
    }

    /**
     * Returns transfer usage
     *
     * @param startTime  Traffic start time in long
     * @param endTime    Traffic end time in long
     * @param ipToFilter filter the traffic by list of ip
     * @return TransferUsage taken from the traffic log files or the database
     */
    @Override
    public TransferUsage getTrafficUsageWithFilterCurrentNode(long startTime, long endTime,
            List<String> ipToFilter) {
        Calendar from = Calendar.getInstance();
        from.setTimeInMillis(startTime);
        Calendar to = Calendar.getInstance();
        to.setTimeInMillis(endTime);
        validateDateRange(from, to);
        List<LegacyTrafficEntry> allTrafficEntries = getEntryList(from, to);
        List<LegacyTrafficEntry> xrayTrafficEntries = getXrayEntryList(from, to);
        TransferUsage transferUsage = new TransferUsage();
        setAllTrafficUsage(allTrafficEntries, ipToFilter, transferUsage);
        setXrayTrafficUsage(xrayTrafficEntries, ipToFilter, transferUsage);
        return transferUsage;
    }

    @Override
    public boolean isActive() {
        return isV1Active() || isV2Active() || isV3Active();
    }

    @Override
    public TransferUsage getTrafficUsageWithFilter(long startTime, long endTime, List<String> ipsToFilter) {
        List<TransferUsage> transferUsageList = Lists.newArrayList();
        TransferUsage currentNodeUsage = getTrafficUsageWithFilterCurrentNode(startTime, endTime, ipsToFilter);
        log.debug("usage from current node - upload: {}, download: {}, xrayUpload: {}, xrayDownload: {}",
                currentNodeUsage.getUpload(), currentNodeUsage.getDownload(), currentNodeUsage.getXrayUpload(),
                currentNodeUsage.getXrayDownload());
        transferUsageList.add(currentNodeUsage);

        HaCommonAddon haAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaCommonAddon.class);
        if (haAddon.isHaEnabled() && haAddon.isHaConfigured()) {
            List<ArtifactoryServer> allOtherActiveHaServers = serversService.getOtherActiveMembers();
            List<TransferUsage> responseList = haAddon.propagateTrafficCollector(startTime, endTime, ipsToFilter,
                    allOtherActiveHaServers, TransferUsage.class);
            if (CollectionUtils.isNullOrEmpty(responseList)) {
                return currentNodeUsage;
            }
            transferUsageList.addAll(responseList);
        }
        return aggregateUsage(transferUsageList);
    }

    private TransferUsage aggregateUsage(List<TransferUsage> transferUsageList) {
        TransferUsage finalTransferUsage = new TransferUsage();
        for (TransferUsage transferUsage : transferUsageList) {
            finalTransferUsage.setDownload(finalTransferUsage.getDownload() + transferUsage.getDownload());
            finalTransferUsage.setXrayDownload(finalTransferUsage.getXrayDownload() + transferUsage.getXrayDownload());
            finalTransferUsage.setUpload(finalTransferUsage.getUpload() + transferUsage.getUpload());
            finalTransferUsage.setXrayUpload(finalTransferUsage.getXrayUpload() + transferUsage.getXrayUpload());
            finalTransferUsage.setRedirect(finalTransferUsage.getRedirect() + transferUsage.getRedirect());
            finalTransferUsage.setExcludedDownload(
                    finalTransferUsage.getExcludedDownload() + transferUsage.getExcludedDownload());
            finalTransferUsage.setExcludedXrayDownload(
                    finalTransferUsage.getExcludedXrayDownload() + transferUsage.getExcludedXrayDownload());
            finalTransferUsage.setExcludedUpload(
                    finalTransferUsage.getExcludedUpload() + transferUsage.getExcludedUpload());
            finalTransferUsage.setExcludedXrayUpload(
                    finalTransferUsage.getExcludedXrayUpload() + transferUsage.getExcludedXrayUpload());
            finalTransferUsage.setExcludedRedirect(
                    finalTransferUsage.getExcludedRedirect() + transferUsage.getExcludedRedirect());
        }
        return finalTransferUsage;
    }

    private void setAllTrafficUsage(List<LegacyTrafficEntry> allTrafficEntries,
            List<String> ipToFilter, TransferUsage transferUsage) {
        List<LegacyTrafficEntry> trafficEntriesUsage = Lists.newArrayList();
        List<LegacyTrafficEntry> trafficEntriesExcludedUsage = Lists.newArrayList();
        setEntriesByFilter(trafficEntriesUsage, trafficEntriesExcludedUsage, allTrafficEntries, ipToFilter);

        fillUsage(trafficEntriesExcludedUsage, transferUsage, true);
        fillUsage(trafficEntriesUsage, transferUsage, false);
    }

    private void setXrayTrafficUsage(List<LegacyTrafficEntry> xrayTrafficEntries,
            List<String> ipToFilter, TransferUsage transferUsage) {
        List<LegacyTrafficEntry> xrayTrafficEntriesUsage = Lists.newArrayList();
        List<LegacyTrafficEntry> xrayTrafficEntriesExcludedUsage = Lists.newArrayList();
        setEntriesByFilter(xrayTrafficEntriesUsage, xrayTrafficEntriesExcludedUsage, xrayTrafficEntries, ipToFilter);

        fillXrayUsage(xrayTrafficEntriesExcludedUsage, transferUsage, true);
        fillXrayUsage(xrayTrafficEntriesUsage, transferUsage, false);
    }

    private void setEntriesByFilter(List<LegacyTrafficEntry> entries, List<LegacyTrafficEntry> excludedEntries,
            List<LegacyTrafficEntry> allEntries, List<String> ipToFilter) {
        if (ipToFilter == null || ipToFilter.isEmpty()) {
            entries.addAll(allEntries);
        } else {
            for (LegacyTrafficEntry trafficEntry : allEntries) {
                if (isExcludedTraffic(trafficEntry, ipToFilter)) {
                    excludedEntries.add(trafficEntry);
                } else {
                    entries.add(trafficEntry);
                }
            }
        }
    }

    private boolean isExcludedTraffic(LegacyTrafficEntry trafficEntry, List<String> ipsToFilter) {
        TransferEntry transferEntry = ((TransferEntry) trafficEntry);
        return ipsToFilter.stream()
                .anyMatch(ipToFilter -> ipToFilter.equals(transferEntry.getUserAddress()));
    }

    private void fillUsage(List<LegacyTrafficEntry> trafficEntriesUsage, TransferUsage transferUsage,
            boolean isExcludedUsage) {
        long uploadTransferUsage = 0;
        long downloadTransferUsage = 0;
        long redirectTransferUsage = 0;

        for (LegacyTrafficEntry trafficEntry : trafficEntriesUsage) {
            long contentLength = ((TransferEntry) trafficEntry).getContentLength();
            switch (trafficEntry.getAction()) {
                case UPLOAD:
                    uploadTransferUsage += contentLength;
                    break;
                case DOWNLOAD:
                    downloadTransferUsage += contentLength;
                    break;
                case REDIRECT:
                    redirectTransferUsage += contentLength;
                    break;
                default:
                    log.info("Traffic entity type {} is not supported", trafficEntry.getAction());
            }
        }
        if (isExcludedUsage) {
            transferUsage.setExcludedUpload(uploadTransferUsage);
            transferUsage.setExcludedDownload(downloadTransferUsage);
            transferUsage.setExcludedRedirect(redirectTransferUsage);
        } else {
            transferUsage.setUpload(uploadTransferUsage);
            transferUsage.setDownload(downloadTransferUsage);
            transferUsage.setRedirect(redirectTransferUsage);
        }
    }

    private void fillXrayUsage(List<LegacyTrafficEntry> trafficEntriesUsage, TransferUsage transferUsage,
            boolean isExcludedUsage) {
        long uploadTransferUsage = 0;
        long downloadTransferUsage = 0;

        for (LegacyTrafficEntry trafficEntry : trafficEntriesUsage) {
            long contentLength = ((TransferEntry) trafficEntry).getContentLength();
            switch (trafficEntry.getAction()) {
                case UPLOAD:
                    uploadTransferUsage += contentLength;
                    break;
                case DOWNLOAD:
                    downloadTransferUsage += contentLength;
                    break;
                default:
                    log.info("Traffic entity type {} is not supported", trafficEntry.getAction());
            }
        }
        if (isExcludedUsage) {
            transferUsage.setExcludedXrayUpload(uploadTransferUsage);
            transferUsage.setExcludedXrayDownload(downloadTransferUsage);
        } else {
            transferUsage.setXrayUpload(uploadTransferUsage);
            transferUsage.setXrayDownload(downloadTransferUsage);
        }
    }

    File getTrafficLogDir() {
        if (ConstantValues.trafficLogsDirectory.isSet()) {
            String trafficLogDirPath = ConstantValues.trafficLogsDirectory.getString();
            if (isNotBlank(trafficLogDirPath) && new File(trafficLogDirPath).exists()) {
                return new File(trafficLogDirPath);
            } else {
                log.error("Directory: \"{}\" not exist/valid, using default directory", trafficLogDirPath);
            }
        }
        return getDefaultTrafficLogDir();
    }

    File getDefaultTrafficLogDir() {
        return ContextHelper.get().getArtifactoryHome().getLogDir();
    }
}
