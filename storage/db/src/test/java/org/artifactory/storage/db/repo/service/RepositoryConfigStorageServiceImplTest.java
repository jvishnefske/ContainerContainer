package org.artifactory.storage.db.repo.service;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.jfrog.storage.DbType;
import org.artifactory.storage.db.fs.service.RepositoryPersistingService;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.repo.dao.RepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryConfigStorageServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private MockitoSession mockitoSession;
    private RepositoryConfigStorageServiceImpl repConfigStorageServiceImpl;

    @Mock
    private RepositoryConfigDao repositoryConfigDao;
    @Mock
    private RepositoryPersistingService repositoryPersistingService;
    @Mock
    private NodesDao nodesDao;
    @Mock
    private DbService dbService;

    private AtomicLong nodeIdGenerator;

    @BeforeEach
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();

        nodeIdGenerator = new AtomicLong(1000);
        when(dbService.nextId()).thenAnswer(inv -> nodeIdGenerator.getAndIncrement());
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);

        repConfigStorageServiceImpl =
                spy(new RepositoryConfigStorageServiceImpl(repositoryConfigDao, repositoryPersistingService,
                        nodesDao, dbService));
    }

    @AfterEach
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void testFindRepositoryByKey() throws Exception {
        String repoKey = "find-me-repo";
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setType(LOCAL_REPO);
        dbRepositoryConfig.setPackageType(Maven.getType());

        when(repositoryConfigDao.findRepositoryByKey(repoKey)).thenReturn(Optional.of(dbRepositoryConfig));

        DbRepositoryConfig retrievedRepoConfig = repConfigStorageServiceImpl.findRepositoryByKey(repoKey);
        assertNotNull(retrievedRepoConfig);
        EqualsBuilder.reflectionEquals(retrievedRepoConfig, dbRepositoryConfig);
    }

    @Test
    void testFindRepositoryByKeyReleaseBundles() throws Exception {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setType(LOCAL_REPO);
        dbRepositoryConfig.setPackageType(ReleaseBundles.getType());

        when(repositoryConfigDao.findRepositoryByKey(DEFAULT_RBV2_REPO)).thenReturn(Optional.of(dbRepositoryConfig));

        DbRepositoryConfig retrievedRepoConfig = repConfigStorageServiceImpl.findRepositoryByKey(DEFAULT_RBV2_REPO);
        assertNotNull(retrievedRepoConfig);
        EqualsBuilder.reflectionEquals(retrievedRepoConfig, dbRepositoryConfig);
    }

    @Test
    void testGetAllRepositoriesWithReleaseBundles() throws Exception {
        List<DbRepositoryConfig> reposInStore = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("repo-" + i);
            dbRepositoryConfig.setType("local");
            dbRepositoryConfig.setPackageType(Maven.getType());
            reposInStore.add(dbRepositoryConfig);
        }
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey(DEFAULT_RBV2_REPO);
        dbRepositoryConfig.setType(RELEASE_BUNDLE_REPO);
        dbRepositoryConfig.setPackageType(ReleaseBundles.getType());
        reposInStore.add(dbRepositoryConfig);
        DbRepositoryConfig dbRepositoryConfig2 = new DbRepositoryConfig();
        dbRepositoryConfig2.setKey(DEFAULT_RBV2_REPO);
        dbRepositoryConfig2.setType(LOCAL_REPO);
        dbRepositoryConfig2.setPackageType(ReleaseBundles.getType());
        reposInStore.add(dbRepositoryConfig2);

        when(repositoryConfigDao.getAll()).thenReturn(reposInStore);

        List<DbRepositoryConfig> fetchedRepos = repConfigStorageServiceImpl.getAllRepositories();
        assertNotNull(fetchedRepos);
        assertEquals(5, fetchedRepos.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(fetchedRepos.get(i).getKey(), "repo-" + i);
        }
    }

    @Test
    void testFindRepositoriesByKeys() throws Exception {
        Set<DbRepositoryConfig> reposInStore = new HashSet<>();
        Set<String> repoKeys = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("repo-" + i);
            dbRepositoryConfig.setType(LOCAL_REPO);
            dbRepositoryConfig.setPackageType(Maven.getType());
            reposInStore.add(dbRepositoryConfig);
            repoKeys.add(dbRepositoryConfig.getKey());
        }
        repoKeys.add("non-existing-repo");
        when(repositoryConfigDao.findByKeysWithPagination(repoKeys)).thenReturn(reposInStore);

        Set<DbRepositoryConfig> repoConfigs =
                repConfigStorageServiceImpl.findRepositoryConfigsByKeys(repoKeys);

        assertNotNull(repoConfigs);
        assertTrue(repoConfigs.containsAll(reposInStore));
    }

    @Test
    void testFindRepositoriesByKeysReleaseBundles() throws Exception {
        Set<DbRepositoryConfig> reposInStore = new HashSet<>();
        Set<String> repoKeys = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("repo-" + i);
            dbRepositoryConfig.setType(LOCAL_REPO);
            dbRepositoryConfig.setPackageType(Maven.getType());
            reposInStore.add(dbRepositoryConfig);
            repoKeys.add(dbRepositoryConfig.getKey());
        }
        DbRepositoryConfig dbRepositoryConfig2 = new DbRepositoryConfig();
        dbRepositoryConfig2.setKey(DEFAULT_RBV2_REPO);
        dbRepositoryConfig2.setType(LOCAL_REPO);
        dbRepositoryConfig2.setPackageType(ReleaseBundles.getType());
        reposInStore.add(dbRepositoryConfig2);
        repoKeys.add("non-existing-repo");
        when(repositoryConfigDao.findByKeysWithPagination(repoKeys)).thenReturn(reposInStore);
        Set<DbRepositoryConfig> repoConfigs =
                repConfigStorageServiceImpl.findRepositoryConfigsByKeys(repoKeys);
        assertNotNull(repoConfigs);
        assertTrue(repoConfigs.containsAll(reposInStore));
    }

    @Test
    void testInsert() throws SQLException {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("insert-me");
        dbRepositoryConfig.setCreated(1L);
        dbRepositoryConfig.setModified(1L);
        List<DbRepositoryConfig> dbNaiveSimulator = new ArrayList<>();
        doAnswer(repo -> dbNaiveSimulator.add(repo.getArgument(0))).when(repositoryConfigDao)
                .insert(dbRepositoryConfig);

        repConfigStorageServiceImpl.insertRepositoryConfig(dbRepositoryConfig, false);

        assertTrue(dbNaiveSimulator.contains(dbRepositoryConfig));
        // Verify root folder existence was checked and node was created
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("insert-me", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        assertEquals("insert-me", nodeCaptor.getValue().getRepo());
    }

    @Test
    void testInsert_shouldCreateCacheRootFolder_whenCreateCacheRootFolderIsTrue() throws SQLException {
        // Arrange
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("remote-repo");
        dbRepositoryConfig.setCreated(1L);
        dbRepositoryConfig.setModified(1L);

        // Act - createCacheRootFolder=true creates cache root folder with "-cache" suffix
        repConfigStorageServiceImpl.insertRepositoryConfig(dbRepositoryConfig, true);

        // Assert - verify exists check and create call
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("remote-repo-cache", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        Node createdNode = nodeCaptor.getValue();
        assertEquals("remote-repo-cache", createdNode.getRepo());
        assertEquals("", createdNode.getPath());
        assertEquals("", createdNode.getName());
        assertFalse(createdNode.isFile());
    }

    @Test
    void testInsert_shouldCreateMainRootFolder_whenCreateCacheRootFolderIsFalse() throws SQLException {
        // Arrange
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("local-repo");
        dbRepositoryConfig.setCreated(1L);
        dbRepositoryConfig.setModified(1L);

        // Act - createCacheRootFolder=false creates main root folder with repo key
        repConfigStorageServiceImpl.insertRepositoryConfig(dbRepositoryConfig, false);

        // Assert - verify exists check and create call
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("local-repo", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        Node createdNode = nodeCaptor.getValue();
        assertEquals("local-repo", createdNode.getRepo());
        assertEquals("", createdNode.getPath());
        assertEquals("", createdNode.getName());
        assertFalse(createdNode.isFile());
    }

    @Test
    void testBulkInsert() throws SQLException {
        List<DbRepositoryConfig> reposToInsert = new ArrayList<>();
        for (int i = 3; i > 0; i--) {
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("repo-" + i);
            dbRepositoryConfig.setCreated(i);
            dbRepositoryConfig.setModified(i);
            reposToInsert.add(dbRepositoryConfig);
        }
        List<DbRepositoryConfig> dbNaiveSimulator = new ArrayList<>();
        doAnswer(repo -> dbNaiveSimulator.addAll(repo.getArgument(0))).when(repositoryConfigDao)
                .bulkInsert(reposToInsert.stream()
                        .sorted(Comparator.comparing(DbRepositoryConfig::getKey))
                        .collect(Collectors.toList()));

        repConfigStorageServiceImpl.bulkInsertRepositoryConfigs(new HashSet<>(reposToInsert), Set.of());

        assertTrue(dbNaiveSimulator.containsAll(reposToInsert));
    }

    @Test
    void testBulkInsert_shouldCreateRootFolders_whenRepoKeysProvided() throws SQLException {
        // Arrange
        Set<DbRepositoryConfig> reposToInsert = new HashSet<>();
        DbRepositoryConfig repo1 = new DbRepositoryConfig();
        repo1.setKey("bulk-repo-1");
        repo1.setCreated(1L);
        repo1.setModified(1L);
        reposToInsert.add(repo1);

        Set<String> rootFoldersToCreate = Set.of("bulk-repo-1", "bulk-repo-1-cache");

        // Act
        repConfigStorageServiceImpl.bulkInsertRepositoryConfigs(reposToInsert, rootFoldersToCreate);

        // Assert - exists and create called for each root folder
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao, Mockito.times(2)).exists(pathCaptor.capture());
        List<NodePath> checkedPaths = pathCaptor.getAllValues();
        assertTrue(checkedPaths.stream().anyMatch(p -> "bulk-repo-1".equals(p.getRepo())));
        assertTrue(checkedPaths.stream().anyMatch(p -> "bulk-repo-1-cache".equals(p.getRepo())));
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao, Mockito.times(2)).create(nodeCaptor.capture());
        List<Node> createdNodes = nodeCaptor.getAllValues();
        assertEquals(2, createdNodes.size());
        assertTrue(createdNodes.stream().anyMatch(n -> "bulk-repo-1".equals(n.getRepo())));
        assertTrue(createdNodes.stream().anyMatch(n -> "bulk-repo-1-cache".equals(n.getRepo())));
    }

    @Test
    void testUpdate() throws SQLException {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("update-me");
        dbRepositoryConfig.setModified(2L);
        dbRepositoryConfig.setRepositoryInfoBlob(new byte[] {1, 2, 3});
        Map<String, DbRepositoryConfig> dbNaiveSimulator = new HashMap<>();
        dbNaiveSimulator.put("update-me", dbRepositoryConfig);

        doAnswer(answer -> {
            DbRepositoryConfig updatedRepoConfig = answer.getArgument(0);
            dbNaiveSimulator.put("update-me", updatedRepoConfig);
            return null;
        }).when(repositoryConfigDao).update(any());

        DbRepositoryConfig updateAccordingToMe = new DbRepositoryConfig();
        updateAccordingToMe.setModified(2L);
        byte[] newInfoBlob = {4, 5, 6};
        updateAccordingToMe.setRepositoryInfoBlob(newInfoBlob);
        repConfigStorageServiceImpl.updateRepositoryConfig(updateAccordingToMe, false);

        DbRepositoryConfig updatedRepoConfig = dbNaiveSimulator.get("update-me");
        assertNotNull(updatedRepoConfig);
        assertNotNull(updatedRepoConfig.getRepositoryInfoBlob());
        assertEquals(updatedRepoConfig.getRepositoryInfoBlob(), newInfoBlob);
    }

    @Test
    void testBulkUpdate() throws SQLException {
        Map<String, DbRepositoryConfig> dbNaiveSimulator = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
            dbRepositoryConfig.setKey("update-me-" + i);
            dbRepositoryConfig.setModified(i);
            dbRepositoryConfig.setRepositoryInfoBlob(new byte[] {(byte) i});
            dbNaiveSimulator.put("update-me-" + i, dbRepositoryConfig);
        }

        doAnswer(answer -> {
            List<DbRepositoryConfig> updatedReposConfig = answer.getArgument(0);
            updatedReposConfig.forEach(repo -> dbNaiveSimulator.put(repo.getKey(), repo));
            return null;
        }).when(repositoryConfigDao).bulkUpdate(any());

        Set<DbRepositoryConfig> dbRepositoryConfigs = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig updateAccordingToMe = new DbRepositoryConfig();
            updateAccordingToMe.setKey("update-me-" + i);
            updateAccordingToMe.setModified(1L);
            byte[] newInfoBlob = {(byte) (i + 1)};
            updateAccordingToMe.setRepositoryInfoBlob(newInfoBlob);
            dbRepositoryConfigs.add(updateAccordingToMe);
        }

        repConfigStorageServiceImpl.bulkUpdateRepositoryConfigs(dbRepositoryConfigs, Set.of());

        assertEquals(dbNaiveSimulator.size(), 3);
        for (int i = 0; i < 3; i++) {
            DbRepositoryConfig updatedRepoConfig = dbNaiveSimulator.get("update-me-" + i);
            assertNotNull(updatedRepoConfig);
            assertNotNull(updatedRepoConfig.getRepositoryInfoBlob());
            assertArrayEquals(updatedRepoConfig.getRepositoryInfoBlob(), new byte[] {(byte) (i + 1)});
        }
    }

    @Test
    void testBulkUpdate_shouldCreateRootFolders_whenRepoKeysProvided() throws SQLException {
        // Arrange
        Set<DbRepositoryConfig> reposToUpdate = new HashSet<>();
        DbRepositoryConfig repo1 = new DbRepositoryConfig();
        repo1.setKey("update-repo");
        repo1.setModified(1L);
        reposToUpdate.add(repo1);

        Set<String> rootFoldersToCreate = Set.of("update-repo-cache");

        // Act
        repConfigStorageServiceImpl.bulkUpdateRepositoryConfigs(reposToUpdate, rootFoldersToCreate);

        // Assert - exists and create called for root folder
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("update-repo-cache", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        Node createdNode = nodeCaptor.getValue();
        assertEquals("update-repo-cache", createdNode.getRepo());
    }

    @Test
    void testDelete() throws SQLException {
        List<String> dbNaiveSimulator = new ArrayList<>();
        dbNaiveSimulator.add("delete-me");
        when(repositoryConfigDao.delete("delete-me"))
                .thenAnswer(repo -> {
                    dbNaiveSimulator.remove(repo.getArgument(0));
                    return 0;
                });

        repConfigStorageServiceImpl.delete("delete-me");

        assertFalse(dbNaiveSimulator.contains("delete-me"));
    }

    @Test
    void testBulkDelete() throws SQLException {
        List<String> dbNaiveSimulator = new ArrayList<>();
        List<String> reposToDelete = new ArrayList<>();
        for (int i = 3; i > 0; i--) {
            dbNaiveSimulator.add("delete-me-" + i);
            reposToDelete.add("delete-me-" + i);
        }
        doAnswer(repo -> {
            dbNaiveSimulator.removeAll(repo.getArgument(0));
            return null;
        }).when(repositoryConfigDao).bulkDelete(reposToDelete.stream().sorted(Comparator.comparing(Function.identity()))
                .collect(Collectors.toList()));

        repConfigStorageServiceImpl.bulkDeleteRepositoryConfigs(new HashSet<>(reposToDelete));
        reposToDelete.forEach(repoToDelete -> assertFalse(dbNaiveSimulator.contains(repoToDelete)));
    }

    @Test
    void testExists() throws SQLException {
        List<String> dbNaiveSimulator = new ArrayList<>();
        dbNaiveSimulator.add("exists");
        when(repositoryConfigDao.exists("exists"))
                .thenAnswer(repo -> dbNaiveSimulator.contains(repo.getArgument(0)));

        boolean exists = repConfigStorageServiceImpl.exists("exists");
        assertTrue(exists);
        exists = repConfigStorageServiceImpl.exists("does-not-exist");
        assertFalse(exists);
    }

    @Test
    void testCountAll() throws SQLException {
        when(repositoryConfigDao.countAll()).thenReturn(10);
        assertEquals(10, repConfigStorageServiceImpl.countAll());
    }

    @Test
    void testGetAllBasicRepoConfigs() throws SQLException {
        Set<DbRepositoryConfig> repositories = new HashSet();
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("repoKeyA");
        dbRepositoryConfig.setType("local");
        dbRepositoryConfig.setPackageType(Maven.getType());
        repositories.add(dbRepositoryConfig);

        DbRepositoryConfig dbRepositoryConfig2 = new DbRepositoryConfig();
        dbRepositoryConfig2.setKey(DEFAULT_RBV2_REPO);
        dbRepositoryConfig2.setType("local");
        dbRepositoryConfig2.setPackageType(ReleaseBundles.getType());
        repositories.add(dbRepositoryConfig2);

        when(repositoryConfigDao.getAllBasicRepoConfigs()).thenReturn(repositories);

        Set<DbRepositoryConfig> repoConfigs = repConfigStorageServiceImpl.getAllBasicRepoConfigs();
        assertEquals(2, repoConfigs.size());
    }

    @Test
    void testGetAllRepoKeys() throws SQLException {
        when(repositoryConfigDao.getAllRepoKeys()).thenReturn(Set.of("RBV2", "RBV1", "ANY-REPO"));
        Set<String> repoKeys = repConfigStorageServiceImpl.getAllRepoKeys();
        assertEquals(3, repoKeys.size());
    }

    @Test
    void testSortBeforePersist() throws SQLException {
        DbRepositoryConfig repoA = new DbRepositoryConfig();
        repoA.setKey("a");
        repoA.setCreated(1L);
        repoA.setModified(1L);
        DbRepositoryConfig repoB = new DbRepositoryConfig();
        repoB.setKey("b");
        repoB.setCreated(1L);
        repoB.setModified(1L);

        Set<DbRepositoryConfig> dbRepositoryConfigs = Set.of(repoB, repoA);

        repConfigStorageServiceImpl.bulkUpdateRepositoryConfigs(dbRepositoryConfigs, Set.of());

        // Repos were sorted
        verify(repositoryConfigDao).bulkUpdate(List.of(repoA, repoB));

        repConfigStorageServiceImpl.bulkInsertRepositoryConfigs(dbRepositoryConfigs, Set.of());
        verify(repositoryConfigDao).bulkInsert(List.of(repoA, repoB));

        repConfigStorageServiceImpl.bulkDeleteRepositoryConfigs(dbRepositoryConfigs.stream()
                .map(DbRepositoryConfig::getKey).collect(Collectors.toSet()));

        verify(repositoryConfigDao).bulkDelete(List.of(repoA.getKey(), repoB.getKey()));
    }

    @Test
    void testFindAllMissingRepoRootPaths() throws SQLException {
        // Arrange
        Set<String> missingRepos = Set.of("repo-1", "repo-2");
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0L)).thenReturn(missingRepos);

        // Act
        Set<String> result = repConfigStorageServiceImpl.findAllMissingRepoRootPaths();

        // Assert
        assertEquals(missingRepos, result);
        verify(repositoryConfigDao).findAllReposThatDoNotExistInNodes(0L);
    }

    @Test
    void testFindAllMissingRepoRootPaths_shouldUseTimeOptimization_whenCalledRecently() throws SQLException {
        // Arrange
        Set<String> missingRepos = Set.of("repo-1");
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(Mockito.anyLong())).thenReturn(missingRepos);

        // Act - first call
        repConfigStorageServiceImpl.findAllMissingRepoRootPaths();

        // Act - second call within interval should use time-based optimization
        repConfigStorageServiceImpl.findAllMissingRepoRootPaths();

        // Assert - verify calls with potentially different timestamps
        verify(repositoryConfigDao, Mockito.times(2)).findAllReposThatDoNotExistInNodes(Mockito.anyLong());
    }

    @Test
    void testInsert_shouldNotCreateRootFolder_whenRootFolderAlreadyExists() throws SQLException {
        // Arrange
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("existing-repo");
        dbRepositoryConfig.setCreated(1L);
        dbRepositoryConfig.setModified(1L);

        // Root folder already exists
        when(nodesDao.exists(any(NodePath.class))).thenReturn(true);

        // Act
        repConfigStorageServiceImpl.insertRepositoryConfig(dbRepositoryConfig, false);

        // Assert - exists was checked but create was NOT called
        verify(nodesDao).exists(any(NodePath.class));
        verify(nodesDao, Mockito.never()).create(any(Node.class));
    }

    @Test
    void testBulkInsert_shouldNotCreateRootFolders_whenRootFoldersAlreadyExist() throws SQLException {
        // Arrange
        Set<DbRepositoryConfig> reposToInsert = new HashSet<>();
        DbRepositoryConfig repo1 = new DbRepositoryConfig();
        repo1.setKey("existing-repo-1");
        repo1.setCreated(1L);
        repo1.setModified(1L);
        reposToInsert.add(repo1);

        Set<String> rootFoldersToCreate = Set.of("existing-repo-1");

        // Root folder already exists
        when(nodesDao.exists(any(NodePath.class))).thenReturn(true);

        // Act
        repConfigStorageServiceImpl.bulkInsertRepositoryConfigs(reposToInsert, rootFoldersToCreate);

        // Assert - exists was checked but create was NOT called
        verify(nodesDao).exists(any(NodePath.class));
        verify(nodesDao, Mockito.never()).create(any(Node.class));
    }

    @Test
    void testUpdate_shouldCreateCacheRootFolder_whenCreateCacheRootFolderIsTrue() throws SQLException {
        // Arrange
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("remote-repo");
        dbRepositoryConfig.setModified(1L);

        // Act - createCacheRootFolder=true
        repConfigStorageServiceImpl.updateRepositoryConfig(dbRepositoryConfig, true);

        // Assert - cache root folder was created
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("remote-repo-cache", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        assertEquals("remote-repo-cache", nodeCaptor.getValue().getRepo());
    }

    @Test
    void testUpdate_shouldNotCreateRootFolder_whenCreateCacheRootFolderIsFalse() throws SQLException {
        // Arrange
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey("local-repo");
        dbRepositoryConfig.setModified(1L);

        // Act - createCacheRootFolder=false
        repConfigStorageServiceImpl.updateRepositoryConfig(dbRepositoryConfig, false);

        // Assert - no root folder operations
        verify(nodesDao, Mockito.never()).exists(any(NodePath.class));
        verify(nodesDao, Mockito.never()).create(any(Node.class));
    }

    @Test
    void testFindAllReposDbRetrieveIntervalAndUpdateFlag_shouldReturnZero_whenFirstCall() {
        // Arrange - fresh service instance
        RepositoryConfigStorageServiceImpl freshService =
                new RepositoryConfigStorageServiceImpl(repositoryConfigDao, repositoryPersistingService,
                        nodesDao, dbService);

        // Act
        long result = freshService.findAllReposDbRetrieveIntervalAndUpdateFlag();

        // Assert - first call should return 0
        assertEquals(0, result);
    }

    @Test
    void createMissingRepoRootFolders_shouldReturnZero_whenNoMissingRepos() throws SQLException {
        // Arrange
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0)).thenReturn(Set.of());

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert
        assertEquals(0, result);
        verify(repositoryConfigDao).findAllReposThatDoNotExistInNodes(0);
        verify(nodesDao, Mockito.never()).exists(any(NodePath.class));
        verify(nodesDao, Mockito.never()).create(any(Node.class));
    }

    @Test
    void createMissingRepoRootFolders_shouldCreateNode_whenSingleRepoMissing() throws SQLException {
        // Arrange
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0)).thenReturn(Set.of("missing-repo"));
        when(nodesDao.exists(any(NodePath.class))).thenReturn(false);

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert
        assertEquals(1, result);
        ArgumentCaptor<NodePath> pathCaptor = ArgumentCaptor.forClass(NodePath.class);
        verify(nodesDao).exists(pathCaptor.capture());
        assertEquals("missing-repo", pathCaptor.getValue().getRepo());
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao).create(nodeCaptor.capture());
        Node createdNode = nodeCaptor.getValue();
        assertEquals("missing-repo", createdNode.getRepo());
        assertEquals("", createdNode.getPath());
        assertEquals("", createdNode.getName());
        assertFalse(createdNode.isFile());
    }

    @Test
    void createMissingRepoRootFolders_shouldCreateMultipleNodes_whenMultipleReposMissing() throws SQLException {
        // Arrange
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0))
                .thenReturn(Set.of("repo-a", "repo-b", "repo-c"));
        when(nodesDao.exists(any(NodePath.class))).thenReturn(false);

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert
        assertEquals(3, result);
        verify(nodesDao, Mockito.times(3)).exists(any(NodePath.class));
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao, Mockito.times(3)).create(nodeCaptor.capture());
        List<Node> createdNodes = nodeCaptor.getAllValues();
        assertEquals(3, createdNodes.size());
        assertTrue(createdNodes.stream().anyMatch(n -> "repo-a".equals(n.getRepo())));
        assertTrue(createdNodes.stream().anyMatch(n -> "repo-b".equals(n.getRepo())));
        assertTrue(createdNodes.stream().anyMatch(n -> "repo-c".equals(n.getRepo())));
    }

    @Test
    void createMissingRepoRootFolders_shouldSkipExistingNodes_whenNodeAlreadyExists() throws SQLException {
        // Arrange
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0)).thenReturn(Set.of("existing-repo"));
        when(nodesDao.exists(any(NodePath.class))).thenReturn(true);

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert
        assertEquals(0, result);
        verify(nodesDao).exists(any(NodePath.class));
        verify(nodesDao, Mockito.never()).create(any(Node.class));
    }

    @Test
    void createMissingRepoRootFolders_shouldThrowStorageException_whenDaoThrowsSqlException() throws SQLException {
        // Arrange
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0))
                .thenThrow(new SQLException("Database error"));

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class,
                () -> repConfigStorageServiceImpl.createMissingRepoRootFolders());
        assertTrue(exception.getMessage().contains("Unable to create missing repository root folder nodes"));
    }

    @Test
    void createMissingRepoRootFolders_shouldContinueProcessing_whenSingleNodeCreationFails() throws SQLException {
        // Arrange - two repos missing, first one fails during create
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0))
                .thenReturn(Set.of("repo-fail", "repo-success"));
        when(nodesDao.exists(any(NodePath.class))).thenReturn(false);
        doAnswer(invocation -> {
            Node node = invocation.getArgument(0);
            if ("repo-fail".equals(node.getRepo())) {
                throw new SQLException("Failed to create node");
            }
            return 1;
        }).when(nodesDao).create(any(Node.class));

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert - only one node was successfully created
        assertEquals(1, result);
        verify(nodesDao, Mockito.times(2)).exists(any(NodePath.class));
        verify(nodesDao, Mockito.times(2)).create(any(Node.class));
    }

    @Test
    void createMissingRepoRootFolders_shouldOnlyCreateMissingNodes_whenSomeNodesAlreadyExist() throws SQLException {
        // Arrange - three repos missing according to DB query, but one already has a node (race condition)
        when(repositoryConfigDao.findAllReposThatDoNotExistInNodes(0))
                .thenReturn(Set.of("repo-new-1", "repo-exists", "repo-new-2"));
        doAnswer(invocation -> {
            NodePath path = invocation.getArgument(0);
            // "repo-exists" already has a node (simulates race condition)
            return "repo-exists".equals(path.getRepo());
        }).when(nodesDao).exists(any(NodePath.class));

        // Act
        int result = repConfigStorageServiceImpl.createMissingRepoRootFolders();

        // Assert - only two nodes should be created (repo-exists skipped)
        assertEquals(2, result);
        verify(nodesDao, Mockito.times(3)).exists(any(NodePath.class));
        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(nodesDao, Mockito.times(2)).create(nodeCaptor.capture());
        List<Node> createdNodes = nodeCaptor.getAllValues();
        assertEquals(2, createdNodes.size());
        assertTrue(createdNodes.stream().anyMatch(n -> "repo-new-1".equals(n.getRepo())));
        assertTrue(createdNodes.stream().anyMatch(n -> "repo-new-2".equals(n.getRepo())));
        assertTrue(createdNodes.stream().noneMatch(n -> "repo-exists".equals(n.getRepo())));
    }
}
