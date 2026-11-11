package org.artifactory.livelogs;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jfrog.sysconf.SysConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.missioncontrol.MissionControlService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.exception.http.TooManyRequestsException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.jfrog.common.system.logs.SystemLogDataModel;
import org.jfrog.common.util.LiveLogsParams;
import org.jfrog.common.util.SystemLogsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jfrog.common.util.SystemLogsUtil.DEFAULT_CONCURRENT_SESSIONS_PER_USER;
import static org.jfrog.common.util.SystemLogsUtil.DEFAULT_ENABLED;
import static org.jfrog.common.util.SystemLogsUtil.DEFAULT_READ_BLOCK_SIZE_KB;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
public class LiveLogsServiceImpl implements LiveLogsService {

    static final String ENABLED_PROP_NAME = "shared.logging.view.enabled";
    static final String FILES_PROP_NAME = "shared.logging.view.files";
    static final String REFRESH_RATE_PROP_NAME = "shared.logging.view.refreshRate";
    static final String CONCURRENT_SESSIONS_PER_USER_PROP_NAME = "shared.logging.view.concurrentSessionsPerUser";
    static final String READ_BLOCK_SIZE_KB_PROP_NAME = "shared.logging.view.readBlockSizeKB";
    static final String ID_IS_EMPTY_ERROR_MESSAGE = "The log ID is empty.";
    static final String TOO_MANY_REQUESTS_ERROR_MESSAGE = "Refresh rate exceeded.";
    static final String FEATURE_NOT_ENABLED_ERROR_MESSAGE = "Live log is not enabled for this instance.";
    static final String BAD_LICENSE_ERROR_MESSAGE = "Live log requires Enterprise license.";
    static final String LOG_NOT_FOUND_ERROR_MESSAGE = "Log was not found.";
    static final String FAILED_TO_CONVERT_PROPERTY_MESSAGE_TEMPLATE =
            "Failed to convert property {}, default value will be used.";
    static final List<String> LOGS_WHITELIST = new ArrayList<>(Arrays.asList(
            "console.log",
            "access-request.log",
            "artifactory-request.log",
            "event-request.log",
            "jfconnect-request.log",
            "jflink-request.log",
            "metadata-request.log",
            "observability-request.log",
            "router-request.log",
            "access-service.log",
            "artifactory-service.log",
            "event-service.log",
            "jfconnect-service.log",
            "jflink-service.log",
            "frontend-service.log",
            "metadata-service.log",
            "observability-service.log",
            "router-service.log",
            "artifactory-access.log",
            "access-audit.log",
            "access-security-audit.log",
            "artifactory-event.log",
            "router-traefik.log",
            "topology-service.log",
            "onemodel-service.log",
            "onemodel-request.log",
            "evidence-service.log",
            "evidence-request.log",
            "jfconfig-request.log",
            "jfconfig-service.log",
            "jfmelt-request.log",
            "jfmelt-service.log"
    ));
    static final List<String> SERVICE_NAMES = new ArrayList<>(Arrays.asList(
            "access",
            "artifactory",
            "event",
            "frontend",
            "jfconnect",
            "jflink",
            "metadata",
            "observability",
            "router",
            "topology",
            "onemodel",
            "evidence",
            "jfconfig",
            "jfmelt"
    ));

    private final ArtifactoryServersCommonService artifactoryServersCommonService;
    private final SecurityService securityService;
    private final AddonsManager addonsManager;
    private final File logDir;
    private final SysConfig sysConfig;
    private final boolean isEnabled;
    private final long refreshRate;
    private final int concurrentSessionsPerUser;
    private final int readBlockSizeKB;
    private final Cache<String, Long> userLastRequestTimestampsCache;
    private final List<String> logFilesToInclude;

    @Autowired
    public LiveLogsServiceImpl(ArtifactoryServersCommonService artifactoryServersCommonService,
            SecurityService securityService, AddonsManager addonsManager) {
        this.artifactoryServersCommonService = artifactoryServersCommonService;
        this.securityService = securityService;
        this.addonsManager = addonsManager;

        logDir = ArtifactoryHome.get().getLogDir();
        sysConfig = ArtifactoryHome.get().getSysConfig();
        addMCIfNeeded();
        addArtifactoryFederationIfNeeded();
        isEnabled = isLoggingViewEnabled();
        logFilesToInclude =
                SystemLogsUtil.getLogFilesToInclude(LOGS_WHITELIST, SERVICE_NAMES, getFilesFromSystemYaml());
        refreshRate = getRefreshRateFromSystemYaml();
        concurrentSessionsPerUser = getConcurrentSessionsPerUserFromSystemYaml();
        readBlockSizeKB = getReadBlockSizeFromSystemYaml();
        userLastRequestTimestampsCache =
                CacheBuilder.newBuilder().expireAfterWrite(getWaitingTimeBeforeRefresh(), TimeUnit.MILLISECONDS)
                        .build();
    }

    void addArtifactoryFederationIfNeeded() {
        FederatedRepoService federatedRepoService =
                ContextHelper.get().beanForType(FederatedRepoService.class);
        if (federatedRepoService.shouldProcessEventsToRTFS()) {
            LOGS_WHITELIST.add("artifactory-federation-service.log");
            SERVICE_NAMES.add("rtfs");
        }
    }

    /**
     * Adds the Mission Control log names and service to the lists
     */
    void addMCIfNeeded() {
        MissionControlService missionControlService = ContextHelper.get().beanForType(MissionControlService.class);
        if (missionControlService.isMissionControlEnabled()) {
            LOGS_WHITELIST.add("mc-error.log");
            LOGS_WHITELIST.add("mc-request.log");
            LOGS_WHITELIST.add("mc-request-out.log");
            LOGS_WHITELIST.add("mc-service.log");
            SERVICE_NAMES.add("mc");
        }
    }

    public void assertIdIsNotEmpty(String selectedLog) {
        if (StringUtils.isBlank(selectedLog)) {
            throw new BadRequestException(ID_IS_EMPTY_ERROR_MESSAGE);
        }
    }

    public void assertUserCanRefreshLog(@NonNull String selectedLog) {
        if (!canRefreshLog(securityService.currentUsername(), selectedLog)) {
            throw new TooManyRequestsException(TOO_MANY_REQUESTS_ERROR_MESSAGE);
        }
    }

    public void assertLiveLogsFeatureIsEnabled() {
        if (!isEnabled) {
            throw new BadRequestException(FEATURE_NOT_ENABLED_ERROR_MESSAGE);
        }
        if (!addonsManager.isEntitled(EntitlementType.LIVE_LOGS)) {
            throw new BadRequestException(BAD_LICENSE_ERROR_MESSAGE);
        }
    }

    public void assertRequestedLogIsValid(@NonNull String selectedLog) {
        if (!logFilesToInclude.contains(selectedLog)) {
            throw new NotFoundException(LOG_NOT_FOUND_ERROR_MESSAGE);
        }
    }

    public void assertCanGetLogData(@NonNull String selectedLog) {
        assertIdIsNotEmpty(selectedLog);
        assertUserCanRefreshLog(selectedLog);
        assertLiveLogsFeatureIsEnabled();
        assertRequestedLogIsValid(selectedLog);
    }

    @NonNull
    public LiveLogsConfig getLiveLogsConfig() {
        return LiveLogsConfig.builder()
                .logs(logFilesToInclude)
                .nodes(getNodeIds())
                .refreshRateMillis(refreshRate)
                .build();
    }

    @NonNull
    public LiveLogsData getLiveLogsData(@NonNull String selectedLog, long lastPointer) {
        File systemLogFile = SystemLogsUtil.getSelectedLogIfFound(selectedLog, logDir);
        LiveLogsParams liveLogsParams = new LiveLogsParams(lastPointer, systemLogFile, readBlockSizeKB,
                getAdditionalSensitiveRegex());
        SystemLogDataModel systemLogDataModel =
                SystemLogsUtil.readLogFile(liveLogsParams);
        return LiveLogsData.builder()
                .lastUpdateModified(systemLogDataModel.getLastUpdateModified())
                .lastUpdateLabel(systemLogDataModel.getLastUpdateLabel())
                .logContent(systemLogDataModel.getLogContent())
                .fileSize(systemLogDataModel.getFileSize())
                .build();
    }

    @NonNull
    private List<String> getFilesFromSystemYaml() {
        if (sysConfig == null || sysConfig.get(FILES_PROP_NAME).isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(sysConfig.get(FILES_PROP_NAME).get().split(","));
    }

    @NonNull
    private List<String> getNodeIds() {
        return artifactoryServersCommonService.getActiveMembers()
                .stream()
                .map(ArtifactoryServer::getServerId)
                .collect(Collectors.toList());
    }

    private boolean isLoggingViewEnabled() {
        if (sysConfig == null) {
            return DEFAULT_ENABLED;
        }
        return sysConfig.getBoolean(ENABLED_PROP_NAME, DEFAULT_ENABLED);
    }

    private long getRefreshRateFromSystemYaml() {
        if (sysConfig == null) {
            return SystemLogsUtil.DEFAULT_REFRESH_RATE_MILLISECONDS;
        }
        Optional<String> value = sysConfig.get(REFRESH_RATE_PROP_NAME);
        if (value.isPresent()) {
            try {
                return SystemLogsUtil.getRefreshRateInMillis(value.get());
            } catch (Exception e) {
                log.warn(FAILED_TO_CONVERT_PROPERTY_MESSAGE_TEMPLATE, REFRESH_RATE_PROP_NAME);
            }
        }
        return SystemLogsUtil.DEFAULT_REFRESH_RATE_MILLISECONDS;
    }

    private int getConcurrentSessionsPerUserFromSystemYaml() {
        if (sysConfig == null) {
            return DEFAULT_CONCURRENT_SESSIONS_PER_USER;
        }
        int concurrentSessions = sysConfig
                .getInt(CONCURRENT_SESSIONS_PER_USER_PROP_NAME, DEFAULT_CONCURRENT_SESSIONS_PER_USER);
        return concurrentSessions == 0 ? DEFAULT_CONCURRENT_SESSIONS_PER_USER :
                concurrentSessions; // to avoid division by 0
    }

    private long getWaitingTimeBeforeRefresh() {
        // extra 10ms between requests, as expiry of cache might end a bit earlier
        return (refreshRate / concurrentSessionsPerUser) + 10;
    }

    private boolean canRefreshLog(@NonNull String username, @NonNull String selectedLog) {
        String key = username + selectedLog;
        if (userLastRequestTimestampsCache.getIfPresent(key) == null) {
            userLastRequestTimestampsCache.put(key, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    public List<String> getAdditionalSensitiveRegex() {
        String additionalSensitiveRegex = ConstantValues.systemLiveLogsAdditionalSensitiveRegex.getString();
        if (!StringUtils.isEmpty(additionalSensitiveRegex)) {
            return Arrays.asList(additionalSensitiveRegex.split(" , "));
        }
        return Collections.emptyList();
    }

    public int getReadBlockSizeFromSystemYaml() {
        if (sysConfig == null) {
            return DEFAULT_READ_BLOCK_SIZE_KB;
        }
        int readBlockSize = sysConfig.getInt(READ_BLOCK_SIZE_KB_PROP_NAME, DEFAULT_READ_BLOCK_SIZE_KB);
        if (readBlockSize <= 0) {
            log.warn(FAILED_TO_CONVERT_PROPERTY_MESSAGE_TEMPLATE, READ_BLOCK_SIZE_KB_PROP_NAME);
            return DEFAULT_READ_BLOCK_SIZE_KB;
        }
        return readBlockSize;
    }
}
