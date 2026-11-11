package org.artifactory.request.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.springframework.stereotype.Component;

import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HttpHeadersAuditServiceImpl implements HttpHeadersAuditService {

    private final TaskService taskService;
    private final Set<String> distinctHeaderNames = ConcurrentHashMap.newKeySet();

    public HttpHeadersAuditServiceImpl(TaskService taskService) {
        this.taskService = taskService;
        if (ConstantValues.httpHeadersAuditEnabled.getBoolean()) {
            setupAuditJob();
        } else {
            log.debug("HTTP headers audit feature flag is disabled; skipping cron job scheduling");
        }
    }

    @Override
    public void recordHeaderNames(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return;
        }
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name != null) {
                distinctHeaderNames.add(name.toLowerCase());
            }
        }
    }

    @Override
    public void flush() {
        if (distinctHeaderNames.isEmpty()) {
            log.info("HTTP headers audit: no headers observed in the last period");
            return;
        }
        log.info("HTTP headers audit: distinct header names observed: {}", distinctHeaderNames);
    }

    private void setupAuditJob() {
        String cronExpression = ConstantValues.httpHeadersAuditCron.getString();
        log.info("Scheduling HTTP headers audit job with cron expression: {}", cronExpression);
        Task task = TaskUtils.createCronTask(HttpHeadersAuditJob.class, cronExpression);
        taskService.startTask(task, false);
    }
}
