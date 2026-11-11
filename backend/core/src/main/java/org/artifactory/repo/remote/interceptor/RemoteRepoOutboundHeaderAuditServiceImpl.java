package org.artifactory.repo.remote.interceptor;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RemoteRepoOutboundHeaderAuditServiceImpl implements RemoteRepoOutboundHeaderAuditService {

    private final TaskService taskService;
    private final Map<String, RepoEntry> byRepoKey = new ConcurrentHashMap<>();

    @Autowired
    public RemoteRepoOutboundHeaderAuditServiceImpl(TaskService taskService) {
        this(taskService, ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getBoolean());
    }

    @VisibleForTesting
    RemoteRepoOutboundHeaderAuditServiceImpl(TaskService taskService, boolean scheduleReportJob) {
        this.taskService = taskService;
        if (scheduleReportJob) {
            scheduleReportJobIfConfigured();
        }
    }

    private void scheduleReportJobIfConfigured() {
        String cron = ConstantValues.remoteRepoOutboundHeadersAuditCron.getString();
        if (StringUtils.isBlank(cron)) {
            log.debug("{} job disabled (blank {})",
                    JobName.REMOTE_REPO_OUTBOUND_HEADERS_AUDIT.asText(),
                    ConstantValues.remoteRepoOutboundHeadersAuditCron.getPropertyName());
            return;
        }
        log.info("Scheduling {} with cron: {}", JobName.REMOTE_REPO_OUTBOUND_HEADERS_AUDIT.asText(), cron);
        Task task = TaskUtils.createCronTask(RemoteRepoOutboundHeaderAuditJob.class, cron);
        taskService.startTask(task, false);
    }

    @Override
    public void record(String repoKey, String configuredRepoUrl, HttpResponse response) {
        if (StringUtils.isBlank(repoKey) || response == null) {
            return;
        }
        // compute() holds the bin lock for repoKey, which serializes record() and flush() per repo
        // so headers added here can't be dropped by a concurrent flush.
        byRepoKey.compute(repoKey, (k, existing) -> {
            RepoEntry entry = (existing != null) ? existing : new RepoEntry();
            entry.setRepoUrlIfEmpty(configuredRepoUrl);
            for (Header header : response.getAllHeaders()) {
                String name = header.getName();
                if (StringUtils.isNotBlank(name)) {
                    entry.headerNames.add(name);
                }
            }
            return entry;
        });
    }

    @Override
    public void flush() {
        if (byRepoKey.isEmpty()) {
            return;
        }
        for (String key : byRepoKey.keySet()) {
            byRepoKey.compute(key, (k, entry) -> {
                if (entry == null || entry.headerNames.isEmpty()) {
                    return null;
                }
                log.info("Remote outbound header audit summary:" +
                                " repoKey={} repoUrl={} distinctHeaderNames={} headers={}",
                        k, entry.repoUrlOrEmpty(), entry.headerNames.size(), entry.headerNames);
                return entry;
            });
        }
    }

    @Override
    public Set<String> getHeaderNames(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return Collections.emptySet();
        }
        RepoEntry entry = byRepoKey.get(repoKey);
        if (entry == null) {
            return Collections.emptySet();
        }
        return entry.headerNames;
    }

    @Override
    public void remove(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return;
        }
        byRepoKey.remove(repoKey);
    }

    private static final class RepoEntry {
        // All writes happen inside byRepoKey.compute(), which serializes access via the bin lock.
        private String repoUrl;
        private final Set<String> headerNames = ConcurrentHashMap.newKeySet();

        void setRepoUrlIfEmpty(String url) {
            if (StringUtils.isNotBlank(url) && repoUrl == null) {
                repoUrl = url;
            }
        }

        String repoUrlOrEmpty() {
            return repoUrl != null ? repoUrl : "";
        }
    }
}
