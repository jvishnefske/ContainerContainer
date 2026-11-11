package org.artifactory.storage.db.repo.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodeBuilder;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.db.fs.service.RepositoryPersistingService;
import org.artifactory.storage.db.repo.RepositoryConfigStorageUtils;
import org.artifactory.storage.db.repo.dao.RepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Tamar Rosen
 */
@Service
public class RepositoryConfigStorageServiceImpl implements RepositoryConfigStorageService {
    private static final Logger log = LoggerFactory.getLogger(RepositoryConfigStorageServiceImpl.class);
    private static final String SYSTEM_USER = "system";
    private static final String CACHE_SUFFIX = "-cache";

    private final RepositoryConfigDao repositoryConfigDao;
    private final RepositoryPersistingService repositoryPersistingService;
    private final NodesDao nodesDao;
    private final DbService dbService;
    private long findAllReposLastTime = 0;

    @Autowired
    public RepositoryConfigStorageServiceImpl(RepositoryConfigDao repositoryConfigDao,
            RepositoryPersistingService repositoryPersistingService, NodesDao nodesDao, DbService dbService) {
        this.repositoryConfigDao = repositoryConfigDao;
        this.repositoryPersistingService = repositoryPersistingService;
        this.nodesDao = nodesDao;
        this.dbService = dbService;
    }

    @Override
    public DbRepositoryConfig findRepositoryByKey(String repoKey) {
        try {
            Optional<DbRepositoryConfig> dbRepositoryConfigOptional = repositoryConfigDao.findRepositoryByKey(repoKey);
            if (dbRepositoryConfigOptional.isPresent()) {
                DbRepositoryConfig dbRepositoryConfig = dbRepositoryConfigOptional.get();
                return dbRepositoryConfig;
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to find repository : " + repoKey);
        }
        log.info("Unable to find repository : '{}'", repoKey);
        return null;
    }

    @Override
    public List<DbRepositoryConfig> getAllRepositories() {
        try {
            return repositoryConfigDao.getAll();
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch all repositories", e);
        }
    }

    @Override
    public Set<DbRepositoryConfig> findRepositoryConfigsByKeys(Set<String> repoKeys) {
        try {
            return repositoryConfigDao.findByKeysWithPagination(repoKeys);
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch repositories", e);
        }
    }

    @Override
    public void insertRepositoryConfig(DbRepositoryConfig dbRepositoryConfig, boolean createCacheRootFolder) {
        String repoKey = dbRepositoryConfig.getKey();
        try {
            log.info("About to insert new repository configuration '{}'", repoKey);
            RepositoryConfigStorageUtils.assertNonZeroCreatedDates(Set.of(dbRepositoryConfig));
            RepositoryConfigStorageUtils.assertNonZeroModificationDates(Set.of(dbRepositoryConfig));

            if (isRepoRootAtomicCreationEnabled()) {
                String rootFolderKey = createCacheRootFolder
                        ? repoKey + CACHE_SUFFIX
                        : repoKey;
                createRootFolderIfMissing(rootFolderKey);
            }

            repositoryConfigDao.insert(dbRepositoryConfig);
            repositoryPersistingService.markDirty();
        } catch (SQLException e) {
            try {
                if (exists(repoKey)) {
                    log.warn("Repository with key '{}' already exists.", repoKey);
                    return;
                }
            } catch (Exception ex) {
                log.warn("Repository: '{}' existence check failed.", repoKey, ex);
            }
            throw new StorageException(
                    "Unable to insert new configuration for repository : " + repoKey);
        }
    }

    @Override
    public void bulkInsertRepositoryConfigs(Set<DbRepositoryConfig> dbRepositoryConfigs,
            Set<String> repoKeysToCreateRootFolders) {
        try {
            if (!CollectionUtils.isEmpty(dbRepositoryConfigs)) {
                RepositoryConfigStorageUtils.assertNonZeroCreatedDates(dbRepositoryConfigs);
                RepositoryConfigStorageUtils.assertNonZeroModificationDates(dbRepositoryConfigs);
                log.info("About to insert {} new repository configurations", dbRepositoryConfigs.size());

                if (isRepoRootAtomicCreationEnabled()) {
                    createRootFoldersIfMissing(repoKeysToCreateRootFolders);
                }

                repositoryConfigDao.bulkInsert(dbRepositoryConfigs.stream()
                        .sorted(Comparator.comparing(DbRepositoryConfig::getKey))
                        .collect(Collectors.toList()));
                repositoryPersistingService.markDirty();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert new repository configurations", e);
        }
    }

    @Override
    public void bulkDeleteRepositoryConfigs(Set<String> repoKeys) {
        try {
            if (!CollectionUtils.isEmpty(repoKeys)) {
                log.debug("About to delete configuration blob for repository: '{}'", repoKeys.size());
                repositoryConfigDao.bulkDelete(repoKeys.stream().sorted(Comparator.comparing(Function.identity()))
                        .collect(Collectors.toList()));
                repositoryPersistingService.markDirty();
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to delete configuration for repository: ", e);
        }
    }

    @Override
    public void updateRepositoryConfig(DbRepositoryConfig dbRepositoryConfig, boolean createCacheRootFolder) {
        try {
            log.debug("About to update configuration for repository: '{}'", dbRepositoryConfig.getKey());
            RepositoryConfigStorageUtils.assertNonZeroModificationDates(Set.of(dbRepositoryConfig));

            if (isRepoRootAtomicCreationEnabled() && createCacheRootFolder) {
                createRootFolderIfMissing(dbRepositoryConfig.getKey() + CACHE_SUFFIX);
            }

            repositoryConfigDao.update(dbRepositoryConfig);
            repositoryPersistingService.markDirty();
        } catch (SQLException e) {
            throw new StorageException(
                    "Unable to update configuration for repository: " + dbRepositoryConfig.getKey(), e);
        }
    }

    @Override
    public void bulkUpdateRepositoryConfigs(Set<DbRepositoryConfig> dbRepositoryConfigs,
            Set<String> repoKeysToCreateRootFolders) {
        try {
            if (!CollectionUtils.isEmpty(dbRepositoryConfigs)) {
                RepositoryConfigStorageUtils.assertNonZeroModificationDates(dbRepositoryConfigs);
                log.info("About to update {} repository configurations", dbRepositoryConfigs.size());

                if (isRepoRootAtomicCreationEnabled()) {
                    createRootFoldersIfMissing(repoKeysToCreateRootFolders);
                }

                repositoryConfigDao.bulkUpdate(
                        dbRepositoryConfigs.stream().sorted(Comparator.comparing(DbRepositoryConfig::getKey))
                                .collect(Collectors.toList()));
                repositoryPersistingService.markDirty();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update repository configurations", e);
        }
    }

    @Override
    public void delete(String repoKey) {
        try {
            if (repositoryConfigDao.delete(repoKey) > 0) {
                log.debug("Repository '{}' was deleted.", repoKey);
                repositoryPersistingService.markDirty();
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to delete repository " + repoKey, e);
        }
    }

    @Override
    public boolean exists(String repoKey) {
        try {
            return repositoryConfigDao.exists(repoKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to validate repository's " + repoKey + "existence", e);
        }
    }

    @Override
    public int countAll() {
        try {
            return repositoryConfigDao.countAll();
        } catch (SQLException e) {
            throw new StorageException("Unable to count all repositories", e);
        }
    }

    @Override
    public int deleteAll() {
        try {
            int numDeleted = repositoryConfigDao.deleteAll();
            repositoryPersistingService.markDirty();
            return numDeleted;
        } catch (SQLException e) {
            throw new StorageException("Unable to delete all repositories", e);
        }
    }

    @Override
    public void flushBulkChanges(Set<DbRepositoryConfig> repositoriesToCreate,
            Set<DbRepositoryConfig> repositoriesToUpdate, Set<String> repositoriesToDelete,
            Set<String> rootFoldersToCreateForInsert, Set<String> rootFoldersToCreateForUpdate) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new StorageException("Active transaction is not allowed here");
        }
        RepositoryConfigStorageService repositoryConfigStorageService = getAdvisedMe();
        repositoryConfigStorageService.bulkInsertRepositoryConfigs(repositoriesToCreate, rootFoldersToCreateForInsert);
        repositoryConfigStorageService.bulkUpdateRepositoryConfigs(repositoriesToUpdate, rootFoldersToCreateForUpdate);
        repositoryConfigStorageService.bulkDeleteRepositoryConfigs(repositoriesToDelete);
        repositoryPersistingService.markDirty();
    }

    @Override
    public Collection<DbRepositoryConfig> findRepositoriesByDetailsType(String repoDetailsType) {
        try {
            return repositoryConfigDao.findRepositoriesByDetailsType(repoDetailsType);
        } catch (SQLException e) {
            throw new StorageException("Unable to fetch repositories", e);
        }
    }

    @Override
    public Set<String> findKeysFromSet(Set<String> repoKeys) {
        try {
            return repositoryConfigDao.findKeysFromSet(repoKeys);
        } catch (SQLException e) {
            throw new StorageException("Unable to check existing repositories", e);
        }
    }

    private RepositoryConfigStorageService getAdvisedMe() {
        return ContextHelper.get().beanForType(RepositoryConfigStorageService.class);
    }

    @Override
    public Set<DbRepositoryConfig> getAllBasicRepoConfigs() {
        try {
            return repositoryConfigDao.getAllBasicRepoConfigs();
        } catch (SQLException e) {
            throw new StorageException("Unable to get all repositories keys", e);
        }
    }

    @Override
    public Set<String> getAllRepoKeys() {
        try {
            return repositoryConfigDao.getAllRepoKeys();
        } catch (SQLException e) {
            throw new StorageException("Unable to get all repositories keys", e);
        }
    }

    @VisibleForTesting
    long findAllReposDbRetrieveIntervalAndUpdateFlag() {
        long now = System.currentTimeMillis();
        long intervalToReturn = findAllReposLastTime +
                ConstantValues.repositoryDbResultValidInterval.getLong() >= now ?
                now - ConstantValues.repositoryDbRetrieveDefaultInterval.getLong() : 0;
        if (intervalToReturn == 0) {
            findAllReposLastTime = now;
        }
        return intervalToReturn;
    }

    @NonNull
    @Override
    public Set<String> findAllMissingRepoRootPaths() {
        try {
            long modifiedInRange = findAllReposDbRetrieveIntervalAndUpdateFlag();
            return repositoryConfigDao.findAllReposThatDoNotExistInNodes(modifiedInRange);
        } catch (SQLException e) {
            throw new StorageException("Unable to find all repositories that do not exist in the nodes table", e);
        }
    }

    @Override
    public int createMissingRepoRootFolders() {
        try {
            // Use modifiedInRange=0 to check ALL repos (not just recently modified)
            Set<String> missingRepoKeys = repositoryConfigDao.findAllReposThatDoNotExistInNodes(0);
            if (missingRepoKeys.isEmpty()) {
                log.debug("No repositories missing root folder nodes");
                return 0;
            }
            log.info("Found {} repositories missing root folder nodes: {}", missingRepoKeys.size(), missingRepoKeys);
            long timestamp = System.currentTimeMillis();
            int sumCreated = 0;
            for (String repoKey : missingRepoKeys) {
                sumCreated += createNode(repoKey, timestamp);
            }
            log.info("Created {} root folder nodes", sumCreated);
            return sumCreated;
        } catch (SQLException e) {
            throw new StorageException("Unable to create missing repository root folder nodes", e);
        }
    }

    /**
     * Creates a root folder node for the specified repository if it doesn't exist.
     * @param repoKey the repository key
     * @param timestamp the timestamp to use for created/modified times
     * @return 1 if a node was created, 0 otherwise
     */
    private int createNode(String repoKey, long timestamp) {
        try {
            NodePath nodePath = new NodePath(repoKey, "", "", false);
            if (!nodesDao.exists(nodePath)) {
                log.debug("Creating root folder node for repository: {}", repoKey);
                Node rootFolderNode = buildRootFolderNode(repoKey, timestamp);
                nodesDao.create(rootFolderNode);
                return 1;
            }
        } catch (SQLException e) {
            log.warn("Failed to create root folder node for repository '{}': {}", repoKey, e.getMessage());
        }
        return 0;
    }

    /**
     * Creates root folder nodes for the specified repository keys if they don't exist.
     * Locking is handled at the service layer (RepositoryConfigServiceImpl) to ensure atomicity.
     *
     * @param repoKeys set of repository keys to create root folders for
     */
    private void createRootFoldersIfMissing(Set<String> repoKeys) throws SQLException {
        if (CollectionUtils.isEmpty(repoKeys)) {
            return;
        }

        log.debug("Creating root folder nodes for {} repositories: {}", repoKeys.size(), repoKeys);
        long timestamp = System.currentTimeMillis();

        for (String repoKey : repoKeys) {
            createRootFolderIfMissing(repoKey, timestamp);
        }
    }

    /**
     * Creates a single root folder node for the specified repository key if it doesn't exist.
     * Locking is handled at the service layer (RepositoryConfigServiceImpl) to ensure atomicity.
     *
     * @param repoKey the repository key to create root folder for
     */
    private void createRootFolderIfMissing(String repoKey) throws SQLException {
        createRootFolderIfMissing(repoKey, System.currentTimeMillis());
    }

    private void createRootFolderIfMissing(String repoKey, long timestamp) throws SQLException {
        NodePath nodePath = new NodePath(repoKey, "", "", false);
        if (!nodesDao.exists(nodePath)) {
            log.debug("Creating root folder node for repository: {}", repoKey);
            Node rootFolderNode = buildRootFolderNode(repoKey, timestamp);
            nodesDao.create(rootFolderNode);
        }
    }

    /**
     * Builds a root folder node for the specified repository.
     *
     * @param repoKey   the repository key
     * @param timestamp the timestamp to use for created/modified times
     * @return the root folder node
     */
    private Node buildRootFolderNode(String repoKey, long timestamp) {
        return new NodeBuilder()
                .nodeId(dbService.nextId())
                .file(false)
                .repo(repoKey)
                .path("")
                .name("")
                .created(timestamp)
                .createdBy(SYSTEM_USER)
                .modified(timestamp)
                .modifiedBy(SYSTEM_USER)
                .updated(timestamp)
                .build();
    }

    /**
     If feature is enabled, create root folder nodes atomically. Otherwise, they are created on:
     RepositoryServiceImpl.RepositoriesCacheBuilder.rebuildRepositories by using
     findAllMissingRepoRootPaths to check existence and pass a false existence flag all the way down
     to: DbStoringRepoMixin.init
    */
    private static boolean isRepoRootAtomicCreationEnabled() {
        return ConstantValues.repoRootAtomicCreation.getBoolean();
    }
}
