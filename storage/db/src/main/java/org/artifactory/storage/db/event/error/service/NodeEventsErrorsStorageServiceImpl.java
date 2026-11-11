package org.artifactory.storage.db.event.error.service;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.event.EventType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;
import org.jfrog.common.ArgUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.storage.db.event.error.service.ErrorInfoConverter.toDBO;
import static org.artifactory.storage.db.event.error.service.ErrorInfoConverter.toErrorInfo;


/**
 * Storage service layer for persisting and retrieving node events errors
 *
 * @author Mor Merhav
 * @author avishayh
 */
@Service
public class NodeEventsErrorsStorageServiceImpl implements NodeEventsErrorsStorageService {
    private static final Logger log = LoggerFactory.getLogger(NodeEventsErrorsStorageServiceImpl.class);

    private final NodeEventsErrorsDAO nodeEventsErrorsDao;
    private InternalDbService dbService;

    @Autowired
    public NodeEventsErrorsStorageServiceImpl(NodeEventsErrorsDAO nodeEventsErrorsDao) {
        this.nodeEventsErrorsDao = nodeEventsErrorsDao;
    }

    @Override
    public void add(NodeEventsErrorInfo errorInfo) {
        validateErrorInfo(errorInfo);
        NodeEventsErrorsDBO record = toDBO(dbService.nextId(), errorInfo);
        try {
            log.debug("adding node events error: {}", errorInfo);
            nodeEventsErrorsDao.insert(record);
        } catch (SQLException e) {
            throw new StorageException("Couldn't persist node events error:" + errorInfo, e);
        }
    }

    @Override
    public List<NodeEventsErrorInfo> getErrors(String eventCategory) {
        return getErrors(eventCategory, null);
    }

    @Override
    public List<NodeEventsErrorInfo> getErrors(String eventCategory, String operationKey, Integer batchSize) {
        requireNonBlank("eventCategory", eventCategory);
        try {
            if (StringUtils.isNotBlank(operationKey)) {
                if (batchSize != null) {
                    return sortAndConvert(nodeEventsErrorsDao.select(eventCategory, operationKey, batchSize));
                } else {
                    return sortAndConvert(nodeEventsErrorsDao.select(eventCategory, operationKey));
                }
            } else {
                return sortAndConvert(nodeEventsErrorsDao.selectAll(eventCategory));
            }
        } catch (SQLException e) {
            log.error("Couldn't retrieve node events errors");
            throw new StorageException("Couldn't retrieve node events errors", e);
        }
    }

    @Override
    @Nonnull
    public List<NodeEventsErrorInfo> getErrors(String eventCategory, String operationKey) {
        return getErrors(eventCategory, operationKey, null);
    }

    @Override
    public Optional<NodeEventsErrorInfo> getError(String eventCategory, EventType eventType, String eventPath) {
        return getError(eventCategory, eventType, eventPath, null);
    }

    @Override
    public Optional<NodeEventsErrorInfo> getError(String eventCategory, EventType eventType, String eventPath,
            String operationKey) {
        requireNonBlank("eventCategory", eventCategory);
        ArgUtils.requireNonNull(eventType, "eventType param must be non-null");
        requireNonBlank("eventPath", eventPath);
        try {
            NodeEventsErrorsDBO errorRecord = nodeEventsErrorsDao
                    .select(eventCategory, eventType, eventPath, operationKey);
            if (errorRecord == null) {
                return Optional.empty();
            }
            return Optional.of(toErrorInfo(errorRecord));
        } catch (SQLException e) {
            log.error("Couldn't retrieve node events errors", e);
            throw new StorageException("Couldn't retrieve node events error", e);
        }
    }

    @Override
    public boolean delete(long errorId) {
        requirePositive("errorId", errorId);
        try {
            log.debug("Attempting to clean node events error id {}", errorId);
            return nodeEventsErrorsDao.delete(errorId);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete node events error ", e);
        }
    }

    @Override
    public boolean delete(NodeEventsErrorInfo errorInfo) {
        return delete(errorInfo.getEventCategory(), errorInfo.getEventType(), errorInfo.getPath().toPath(),
                errorInfo.getOperationKey());
    }

    @Override
    public boolean delete(String eventCategory, EventType eventType, String eventPath) {
        return delete(eventCategory, eventType, eventPath, null);
    }

    @Override
    public boolean delete(String eventCategory, EventType eventType, String eventPath, String operationKey) {
        requireNonBlank("eventCategory", eventCategory);
        ArgUtils.requireNonNull(eventType, "eventType param must be non-null");
        requireNonBlank("eventPath", eventPath);

        String message =
                String.format("Category: %s, Type: %s, Path: %s, Operation Key:%s", eventCategory, eventType, eventPath,
                        operationKey);
        try {
            log.debug("Attempting to clean node events error record: [{}]", message);
            return nodeEventsErrorsDao.delete(eventCategory, eventType, eventPath, operationKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete node events error: [" + message + "]", e);
        }
    }

    @Override
    public int deleteAll(String eventCategory, String operationKey) {
        requireNonBlank("eventCategory", eventCategory);
        requireNonBlank("operationKey", operationKey);
        try {
            log.debug("Attempting to clean all node events error records in category: {} under operation key: {}",
                    eventCategory, operationKey);
            return nodeEventsErrorsDao.delete(eventCategory, operationKey);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to delete node events errors in category: " + eventCategory + " by operation key: " +
                            operationKey, e);
        }
    }

    @Override
    public int deleteAll(String eventCategory, EventType eventType, String operationKey) {
        requireNonBlank("eventCategory", eventCategory);
        try {
            log.debug(
                    "Attempting to clean all node events error records in category: {} of type: {} under operation " +
                            "key: {}",
                    eventCategory,
                    eventType,
                    operationKey);
            return nodeEventsErrorsDao.delete(eventCategory, operationKey, eventType);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to delete node events errors of type: " + eventType + " and operation key: " + operationKey,
                    e);
        }
    }

    @Override
    public int update(NodeEventsErrorInfo errorInfo) {
        validateErrorInfo(errorInfo);
        NodeEventsErrorsDBO record = toDBO(dbService.nextId(), errorInfo);
        try {
            log.debug("Updating node events error: {}", errorInfo);
            return nodeEventsErrorsDao.update(record);
        } catch (SQLException e) {
            throw new StorageException("Couldn't update node events error", e);
        }
    }

    @Override
    public int updateById(NodeEventsErrorInfo errorInfo, long errorId) {
        requirePositive("errorId", errorId);
        validateErrorInfo(errorInfo);
        NodeEventsErrorsDBO record = toDBO(errorId, errorInfo);
        try {
            log.debug("Updating node events error: {}", errorInfo);
            return nodeEventsErrorsDao.updateById(record);
        } catch (SQLException e) {
            throw new StorageException("Couldn't update node events error", e);
        }
    }

    @Override
    public int deleteBeforeWithEventId(long endExclusive) {
        try {
            log.debug("Deleting errors before: {}", endExclusive);
            return nodeEventsErrorsDao.deleteBeforeWithEventId(endExclusive);
        } catch (Exception e) {
            throw new StorageException("Failed to delete errors before: " + endExclusive, e);
        }
    }

    @Override
    public long getFirstEventErrorTimestampWithEventId() {
        try {
            log.debug("get first event error timestamp");
            return nodeEventsErrorsDao.getFirstEventErrorTimestampWithEventId();
        } catch (Exception e) {
            throw new StorageException("Failed to get first event error timestamp", e);
        }
    }

    @Autowired
    public void setDbService(InternalDbService dbService) {
        this.dbService = dbService;
    }

    @Override
    @Nonnull
    public Set<RepoAndCategory> getErroneousRepos() {
        try {
            return this.nodeEventsErrorsDao.getErroneousRepos();
        } catch (SQLException e) {
            throw new StorageException("Failed to get erroneous repos", e);
        }
    }

    @Nonnull
    private List<NodeEventsErrorInfo> sortAndConvert(List<NodeEventsErrorsDBO> allErrors) {
        List<NodeEventsErrorsDBO> allErrorsCopy = Lists.newArrayList(allErrors);
        allErrorsCopy.sort(Comparator.comparing(NodeEventsErrorsDBO::getEventTime, Comparator.naturalOrder())
                .thenComparing(NodeEventsErrorsDBO::getErrorId));
        return allErrorsCopy.stream().map(ErrorInfoConverter::toErrorInfo).collect(Collectors.toList());
    }

    private void validateErrorInfo(NodeEventsErrorInfo errorInfo) {
        requireNonBlank("eventCategory", errorInfo.getEventCategory());
        requireNonBlank("errorMessage", errorInfo.getErrorMessage());
        requireNullOrNonNegative("eventId", errorInfo.getEventId());
        requireNonNegative("evenTime", errorInfo.getEventTime());
        requireNonNegative("errorCount", errorInfo.getErrorCount());
        requireNonNegative("firstErrorTime", errorInfo.getFirstErrorTime());
        requireNonNegative("lastErrorTime", errorInfo.getLastErrorTime());
    }

    private void requireNonBlank(String fieldName, String fieldValue) {
        ArgUtils.requireNonBlank(fieldValue, fieldName + " param must be non-blank");
    }

    private void requirePositive(String fieldName, Long fieldValue) {
        ArgUtils.requirePositive(fieldValue, fieldName + " param must be positive");
    }

    private void requireNonNegative(String fieldName, Long fieldValue) {
        ArgUtils.requireNonNegative(fieldValue, fieldName + " param must be non-negative");
    }

    private void requireNonNegative(String fieldName, Integer fieldValue) {
        ArgUtils.requireNonNegative(fieldValue, fieldName + " param must be non-negative");
    }

    private void requireNullOrNonNegative(String fieldName, Long fieldValue) {
        ArgUtils.requireNullOrNonNegative(fieldValue, fieldName + " param must be non-negative");
    }
}
