package org.artifactory.ph;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageEventService;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueService;

@Slf4j
public class PackageEventServiceImpl implements PackageEventService {
    private final QueueService queueService;

    public PackageEventServiceImpl(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void resetQueueIfExist(String repoKey) throws PackageException {
        try {
            queueService.resetQueueIfExist(repoKey, QueueCursorType.INCREMENTAL_INDEX, StringUtils.EMPTY);
        } catch (Exception e) {
            log.debug("", e);
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage());
        }
    }
}
