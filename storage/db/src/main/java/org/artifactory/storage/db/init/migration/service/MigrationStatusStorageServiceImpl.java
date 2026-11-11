package org.artifactory.storage.db.init.migration.service;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.init.migration.model.MigrationInfoBlob;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.mapper.MigrationStatusMapper;
import org.artifactory.storage.db.init.migration.service.mapper.MigrationStatusMapperImpl;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.util.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Service
public class MigrationStatusStorageServiceImpl implements MigrationStatusStorageService {
    private static final Logger log = LoggerFactory.getLogger(MigrationStatusStorageServiceImpl.class);
    private MigrationStatusDao migrationStatusDao;

    private MigrationStatusMapper mapper;

    @Autowired
    public MigrationStatusStorageServiceImpl(MigrationStatusDao migrationStatusDao) {
        this.migrationStatusDao = migrationStatusDao;
        this.mapper = new MigrationStatusMapperImpl();
    }

    @Override
    public MigrationStatus findMigrationById(String migrationId) {
        try {
            Optional<DbMigrationStatus> migration = migrationStatusDao.findMigrationById(migrationId);
            if (migration.isPresent()) {
                return mapper.dbMigrationStatusToMigrationStatus(migration.get());
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to find migration by id: " + migrationId);
        }
        return null;
    }

    @Override
    public MigrationStatus findMigrationByIdWithInfoBlob(String migrationId,
            Class<? extends MigrationInfoBlob> infoBlobModel) {
        try {
            Optional<DbMigrationStatus> migration = migrationStatusDao.findMigrationById(migrationId);
            if (migration.isPresent()) {
                return dbMigrationStatusToMigrationStatusWithInfoBlob(infoBlobModel, migration.get());
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to find migration by id: " + migrationId, e);
        }
        return null;
    }

    @Override
    public List<MigrationStatus> findMigrationByIdPrefixWithInfoBlob(String migrationIdPrefix, String separator,
            Class<? extends MigrationInfoBlob> infoBlobModel) {
        return findMigrationByIdPrefixWithInfoBlobBetweenDates(migrationIdPrefix, separator, null, null, infoBlobModel);
    }

    @Override
    public List<MigrationStatus> findMigrationByIdPrefixWithInfoBlobBetweenDates(String migrationIdPrefix, Long from,
            Long to, Class<? extends MigrationInfoBlob> infoBlobModel) {
        return findMigrationByIdPrefixWithInfoBlobBetweenDates(migrationIdPrefix, null, from, to, infoBlobModel);
    }

    @Override
    public void insertMigration(MigrationStatus migrationStatus) {
        try {
            log.info("About to insert new migration status record '{}'", migrationStatus);
            migrationStatusDao.insertMigration(mapper.migrationStatusToDbMigrationStatus(migrationStatus));

        } catch (SQLException e) {
            if (findMigrationById(migrationStatus.getIdentifier()) != null) {
                log.info("Migration with ID '{}' already exists.", migrationStatus.getIdentifier());
                return;
            }
            throw new StorageException("Unable to insert new migration for: " + migrationStatus.getIdentifier());
        }
    }

    @Override
    public void updateMigration(MigrationStatus migrationStatus) {
        try {
            log.debug("About to update migration status record: '{}'", migrationStatus);
            migrationStatusDao.updateMigrationInfoBlob(mapper.migrationStatusToDbMigrationStatus(migrationStatus));
        } catch (SQLException e) {
            throw new StorageException("Unable to update migration for: " + migrationStatus.getIdentifier(), e);
        }
    }

    @Override
    public void insertMigrationWithInfoBlob(MigrationStatus migrationStatus) {
        try {
            log.info("Inserting new migration status record '{}'", migrationStatus);
            migrationStatusDao.insertMigration(mapper.migrationStatusToDbMigrationStatus(migrationStatus));
        } catch (SQLException e) {
            if (findMigrationById(migrationStatus.getIdentifier()) != null) {
                log.info("Migration with ID '{}' already exists.", migrationStatus.getIdentifier());
                return;
            }
            throw new StorageException("Unable to insert new migration for: " + migrationStatus.getIdentifier());
        }
    }

    @Override
    public void migrationDone(String migrationId) {
        log.info("Migration for '{}' has finished.", migrationId);
        try {
            migrationStatusDao.updateFinishTimeById(migrationId, ClockUtils.epochMillis());
        } catch (SQLException e) {
            throw new StorageException("Unable to mark migration as finished", e);
        }
    }

    @Override
    public List<MigrationStatus> findAllUnfinishedByPrefixWithInfoBlob(String prefix,
            Class<? extends MigrationInfoBlob> infoBlobModel) {
        if (StringUtils.isEmpty(prefix)) {
            return Collections.emptyList();
        }
        try {
            Collection<DbMigrationStatus> migrations = migrationStatusDao.findAllUnfinishedByPrefix(prefix);
            return StreamSupportUtils.stream(migrations)
                    .map(dbMigrationStatus -> dbMigrationStatusToMigrationStatusWithInfoBlob(infoBlobModel,
                            dbMigrationStatus))
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch migrations by prefix: " + prefix, e);
        }
    }

    @Override
    public List<MigrationStatus> findLastByTimeAndPrefix(String prefix, long afterEpochTime,
            Class<? extends MigrationInfoBlob> infoBlobModel) {
        if (StringUtils.isBlank(prefix)) {
            return Collections.emptyList();
        }
        try {
            Collection<DbMigrationStatus> migrations =
                    migrationStatusDao.findFinishedStartedAfterDateByPrefix(prefix, afterEpochTime);
            return StreamSupportUtils.stream(migrations)
                    .map(dbMigrationStatus -> dbMigrationStatusToMigrationStatusWithInfoBlob(infoBlobModel,
                            dbMigrationStatus)).collect(Collectors.toList());
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch migrations by prefix: " + prefix, e);
        }
    }

    @Override
    public List<MigrationStatus> findAllUnfinishedIn(List<String> migrationList) {
        if (CollectionUtils.isNullOrEmpty(migrationList)) {
            return Collections.emptyList();
        }
        try {
            Collection<DbMigrationStatus> migrations = migrationStatusDao.findAllUnfinishedIn(migrationList);
            return StreamSupportUtils.stream(migrations)
                    .map(mapper::dbMigrationStatusToMigrationStatus)
                    .collect(Collectors.toList());

        } catch (SQLException e) {
            throw new StorageException("Unable to fetch migrations", e);
        }
    }

    @Override
    public int countAllUnfinishedByPrefix(String prefix) {
        if (StringUtils.isEmpty(prefix)) {
            return 0;
        }
        try {
            return migrationStatusDao.countAllUnfinishedByPrefix(prefix);
        } catch (SQLException e) {
            throw new StorageException("Unable to count unfinished migrations", e);
        }
    }

    @Override
    public void delete(String identifierName) {
        try {
            if (migrationStatusDao.delete(identifierName) > 0) {
                log.debug("Migration with identifier '{}' was deleted.", identifierName);
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to delete migration", e);
        }
    }

    @Override
    public Collection<String> findStartedSinceContainsPrefix(String prefix, long startTime) {
        try {
            return migrationStatusDao.findStartedBeforeDateContainsPrefix(prefix, startTime);
        } catch (SQLException e) {
            throw new StorageException("Unable to find migrations by start date and prefix", e);
        }
    }

    private MigrationStatus dbMigrationStatusToMigrationStatusWithInfoBlob(
            Class<? extends MigrationInfoBlob> infoBlobModel,
            DbMigrationStatus migration) {
        MigrationStatus migrationStatus = mapper.dbMigrationStatusToMigrationStatus(migration);
        if (migration.getMigrationInfoBlob() != null &&
                migration.getMigrationInfoBlob().length != 0) {
            migrationStatus.setMigrationInfoBlob(
                    JsonUtils.getInstance().readValue(migration.getMigrationInfoBlob(), infoBlobModel));
        }
        return migrationStatus;
    }

    private List<MigrationStatus> findMigrationByIdPrefixWithInfoBlobBetweenDates(String migrationIdPrefix,
            String separator, Long from, Long to, Class<? extends MigrationInfoBlob> infoBlobModel) {
        if (StringUtils.isEmpty(migrationIdPrefix)) {
            return Collections.emptyList();
        }
        try {
            Collection<DbMigrationStatus> migrations =
                    migrationStatusDao.findMigrationsByIdPrefixBetweenDates(migrationIdPrefix, separator,
                            Direction.DESC, from, to);
            return StreamSupportUtils.stream(migrations)
                    .map(migration ->
                            dbMigrationStatusToMigrationStatusWithInfoBlob(infoBlobModel, migration))
                    .toList();
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch migrations", e);
        }
    }
}