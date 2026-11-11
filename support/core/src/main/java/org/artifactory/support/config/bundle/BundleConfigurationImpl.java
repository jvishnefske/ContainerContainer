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

package org.artifactory.support.config.bundle;

import org.artifactory.addon.support.ArtifactorySupportBundleParameters;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.support.config.federation.FederationSnapshotConfiguration;
import org.artifactory.support.config.security.SecurityInfoConfiguration;
import org.artifactory.support.config.storage.StorageSummaryConfiguration;
import org.jfrog.support.common.config.ConfigFilesConfiguration;
import org.jfrog.support.common.config.SystemInfoConfiguration;
import org.jfrog.support.common.config.SystemLogsConfiguration;
import org.jfrog.support.common.config.ThreadDumpConfiguration;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

/**
 * Generic bundle configuration
 *
 * @author Michael Pasternak
 */
public class BundleConfigurationImpl implements BundleConfiguration {

    private static final boolean FORCE_COLLECT_SYS_LOGS = true;
    public static final int DEFAULT_BUNDLE_SIZE = 10; // in Mb

    private SystemLogsConfiguration systemLogsConfiguration;
    private SystemInfoConfiguration systemInfoConfiguration;
    private SecurityInfoConfiguration securityInfoConfiguration;
    private ConfigFilesConfiguration configFilesConfiguration;
    private StorageSummaryConfiguration storageSummaryConfiguration;
    private FederationSnapshotConfiguration federationSnapshotConfiguration;
    private ThreadDumpConfiguration threadDumpConfiguration;

    private int bundleSize = DEFAULT_BUNDLE_SIZE;

    /**
     * serialization .ctr
     */
    public BundleConfigurationImpl() {
        this.systemLogsConfiguration = new SystemLogsConfiguration();
        this.systemInfoConfiguration = new SystemInfoConfiguration();
        this.securityInfoConfiguration = new SecurityInfoConfiguration();
        this.configFilesConfiguration = new ConfigFilesConfiguration();
        this.storageSummaryConfiguration = new StorageSummaryConfiguration();
        this.federationSnapshotConfiguration = new FederationSnapshotConfiguration();
        this.threadDumpConfiguration = new ThreadDumpConfiguration();
    }

    /**
     * @param startDate          collect logs from
     * @param endDate            collect logs to
     * @param hideUserDetails    whether user details should be hidden (default true)
     * @param threadDumpCount    amount of dumps to take
     * @param threadDumpInterval interval between dumps (millis)
     */
    public BundleConfigurationImpl(Date startDate, Date endDate, boolean collectSystemInfo,
            boolean collectSecurityConfig, Optional<Boolean> hideUserDetails,
            boolean collectConfigurationFiles, boolean collectThreadDump, int threadDumpCount, long threadDumpInterval,
            boolean collectStorageSummary, boolean collectFederation, Optional<Integer> bundleSize) {

        this.systemLogsConfiguration = new SystemLogsConfiguration(FORCE_COLLECT_SYS_LOGS, startDate, endDate);
        this.systemInfoConfiguration = new SystemInfoConfiguration(collectSystemInfo);
        this.securityInfoConfiguration = new SecurityInfoConfiguration(collectSecurityConfig, hideUserDetails);
        this.configFilesConfiguration = new ConfigFilesConfiguration(collectConfigurationFiles, hideUserDetails);
        this.storageSummaryConfiguration = new StorageSummaryConfiguration(collectStorageSummary);
        this.federationSnapshotConfiguration = new FederationSnapshotConfiguration(collectFederation);
        this.threadDumpConfiguration =
                new ThreadDumpConfiguration(collectThreadDump, threadDumpCount, threadDumpInterval);
        bundleSize.ifPresent(v -> this.bundleSize = v);
    }

    /**
     * @param daysCount          amount of days eligible for logs collection
     * @param hideUserDetails    whether user details should be hidden (default true)
     * @param threadDumpCount    amount of dumps to take
     * @param threadDumpInterval interval between dumps (millis)
     */
    public BundleConfigurationImpl(Integer daysCount, boolean collectSystemInfo, boolean collectSecurityConfig,
            Optional<Boolean> hideUserDetails, boolean collectConfigurationFiles,
            boolean collectThreadDump, int threadDumpCount, long threadDumpInterval, boolean collectStorageSummary,
            boolean collectFederation, Optional<Integer> bundleSize) {

        this.systemLogsConfiguration = new SystemLogsConfiguration(FORCE_COLLECT_SYS_LOGS, daysCount);
        this.systemInfoConfiguration = new SystemInfoConfiguration(collectSystemInfo);
        this.securityInfoConfiguration = new SecurityInfoConfiguration(collectSecurityConfig, hideUserDetails);
        this.configFilesConfiguration = new ConfigFilesConfiguration(collectConfigurationFiles, hideUserDetails);
        this.storageSummaryConfiguration = new StorageSummaryConfiguration(collectStorageSummary);
        this.federationSnapshotConfiguration = new FederationSnapshotConfiguration(collectFederation);
        this.threadDumpConfiguration =
                new ThreadDumpConfiguration(collectThreadDump, threadDumpCount, threadDumpInterval);
        bundleSize.ifPresent(v -> this.bundleSize = v);
    }

    public static BundleConfigurationImpl fromSupportBundleConfig(ArtifactorySupportBundleParameters parameters) {
        ArtifactorySupportBundleParameters.Logs logs =
                Optional.of(parameters.getLogs()).orElse(new ArtifactorySupportBundleParameters.Logs());
        ArtifactorySupportBundleParameters.ThreadDump threadDump =
                Optional.of(parameters.getThreadDump()).orElse(new ArtifactorySupportBundleParameters.ThreadDump());

        return new BundleConfigurationImpl(
                logs.getStartDate(),
                logs.getEndDate(),
                parameters.isSystem(),
                parameters.isConfiguration(),
                Optional.of(true),  // hide user detail
                parameters.isConfiguration(),
                threadDump.getCount() > 0,
                threadDump.getCount(),
                threadDump.getInterval(),
                parameters.isSystem(),
                false,              // collectFederation — decided by license in Pro layer
                Optional.empty()    // limit size
        );
    }

    /**
     * @return {@link SystemLogsConfiguration}
     */
    @Override
    public SystemLogsConfiguration getSystemLogsConfiguration() {
        return systemLogsConfiguration;
    }

    /**
     * @return {@link SystemInfoConfiguration}
     */
    @Override
    public SystemInfoConfiguration getSystemInfoConfiguration() {
        return systemInfoConfiguration;
    }

    /**
     * @return {@link org.artifactory.support.config.security.SecurityInfoConfiguration}
     */
    @Override
    public SecurityInfoConfiguration getSecurityInfoConfiguration() {
        return securityInfoConfiguration;
    }

    /**
     * @return {@link ConfigFilesConfiguration}
     */
    @Override
    public ConfigFilesConfiguration getConfigFilesConfiguration() {
        return configFilesConfiguration;
    }

    /**
     * @return {@link org.artifactory.support.config.storage.StorageSummaryConfiguration}
     */
    @Override
    public StorageSummaryConfiguration getStorageSummaryConfiguration() {
        return storageSummaryConfiguration;
    }

    /**
     * @return {@link FederationSnapshotConfiguration}
     */
    @Override
    public FederationSnapshotConfiguration getFederationSnapshotConfiguration() {
        return federationSnapshotConfiguration;
    }

    /**
     * @return {@link ThreadDumpConfiguration}
     */
    @Override
    public ThreadDumpConfiguration getThreadDumpConfiguration() {
        return threadDumpConfiguration;
    }

    /**
     * @return a default size of compressed archives
     */
    @Override
    public int getBundleSize() {
        return bundleSize;
    }

    @Override
    public boolean isCollectSystemLogs() {
        return systemLogsConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectSystemInfo() {
        return systemInfoConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectSecurityConfig() {
        return securityInfoConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectConfigurationFiles() {
        return configFilesConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectThreadDump() {
        return threadDumpConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectStorageSummary() {
        return storageSummaryConfiguration.isEnabled();
    }

    @Override
    public boolean isCollectFederation() {
        return federationSnapshotConfiguration.isEnabled();
    }

    @Override
    public ArtifactorySupportBundleParameters toNewModel() {
        ArtifactorySupportBundleParameters artifactorySupportBundleParameters =
                new ArtifactorySupportBundleParameters();
        artifactorySupportBundleParameters.setConfiguration(
                this.isCollectConfigurationFiles() || this.isCollectSecurityConfig() || this.isCollectStorageSummary());
        artifactorySupportBundleParameters.setSystem(this.isCollectSystemInfo());

        ArtifactorySupportBundleParameters.ThreadDump td = new ArtifactorySupportBundleParameters.ThreadDump();
        td.setCount(this.threadDumpConfiguration.getCount());
        td.setInterval(this.threadDumpConfiguration.getInterval());
        artifactorySupportBundleParameters.setThreadDump(td);

        ArtifactorySupportBundleParameters.Logs logs = new ArtifactorySupportBundleParameters.Logs();
        logs.setInclude(this.isCollectSystemLogs());
        logs.setStartDate(this.systemLogsConfiguration.getStartDate());
        logs.setEndDate(this.systemLogsConfiguration.getEndDate());
        artifactorySupportBundleParameters.setLogs(logs);
        return artifactorySupportBundleParameters;
    }

    @Override
    public String toString() {
        try {
            return JacksonWriter.serialize(this, true);
        } catch (IOException e) {
            return ""; // should not happen
        }
    }
}
