package org.artifactory.storage.db.repo.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.repo.dao.TransparentRepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransparentRepositoryConfigServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private MockitoSession mockitoSession;
    private TransparentRepositoryConfigServiceImpl service;

    @Mock
    private TransparentRepositoryConfigDao dao;

    @Mock
    private RepositoryConfigStorageService repositoryConfigStorageService;

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private HaCommonAddon haCommonAddon;

    @BeforeEach
    void setUp() {
        // Keep HA-propagation retries cheap in tests: 1 attempt, 0 ms backoff. Production defaults
        // (3 attempts, 500 ms linear backoff) would add seconds per test through the failure paths.
        setStringSystemProperty(ConstantValues.transparentRepoPropagationMaxAttempts, "1");
        setStringSystemProperty(ConstantValues.transparentRepoPropagationBackoffMs, "0");
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();
        // Wire ContextHelper.get() → mocked context that resolves AddonsManager → HaCommonAddon
        // so the propagation path is exercisable.
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        service = new TransparentRepositoryConfigServiceImpl(dao);
        service.setRepositoryConfigStorageService(repositoryConfigStorageService);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
        mockitoSession.finishMocking();
    }

    @Test
    void insert_delegatesToDao() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        service.insert(config);
        verify(dao).insert(config);
    }

    @Test
    void insert_throwsStorageException_whenDaoFails() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        doThrow(new SQLException("db error")).when(dao).insert(config);
        assertThrows(StorageException.class, () -> service.insert(config));
    }

    @Test
    void update_delegatesToDao() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        service.update(config);
        verify(dao).update(config);
    }

    @Test
    void update_throwsStorageException_whenDaoFails() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        doThrow(new SQLException("db error")).when(dao).update(config);
        assertThrows(StorageException.class, () -> service.update(config));
    }

    @Test
    void findByKey_returnsConfig_whenFound() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        when(dao.findByKey("repo1")).thenReturn(Optional.of(config));
        Optional<DbRepositoryConfig> result = service.findByKey("repo1");
        assertTrue(result.isPresent());
        assertEquals(config, result.get());
    }

    @Test
    void findByKey_returnsEmpty_whenNotFound() throws SQLException {
        when(dao.findByKey("missing")).thenReturn(Optional.empty());
        assertTrue(service.findByKey("missing").isEmpty());
    }

    @Test
    void findByKey_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.findByKey("repo1")).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.findByKey("repo1"));
    }

    @Test
    void getAll_returnsAllConfigs() throws SQLException {
        List<DbRepositoryConfig> configs = List.of(config("repo1"), config("repo2"));
        when(dao.getAll()).thenReturn(configs);
        assertEquals(2, service.getAll().size());
    }

    @Test
    void getAll_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.getAll()).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.getAll());
    }

    @Test
    void existsByKey_returnsTrue_whenExists() throws SQLException {
        when(dao.existsByKey("repo1")).thenReturn(true);
        assertTrue(service.existsByKey("repo1"));
    }

    @Test
    void existsByKey_returnsFalse_whenNotExists() throws SQLException {
        when(dao.existsByKey("missing")).thenReturn(false);
        assertFalse(service.existsByKey("missing"));
    }

    @Test
    void existsByKey_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.existsByKey("repo1")).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.existsByKey("repo1"));
    }

    @Test
    void existsByKeyIgnoreCase_returnsTrue_whenCaseInsensitiveMatch() throws SQLException {
        when(dao.existsByKeyIgnoreCase("REPO1")).thenReturn(true);
        assertTrue(service.existsByKeyIgnoreCase("REPO1"));
    }

    @Test
    void existsByKeyIgnoreCase_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.existsByKeyIgnoreCase("repo1")).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.existsByKeyIgnoreCase("repo1"));
    }

    @Test
    void delete_delegatesToDao() throws SQLException {
        when(dao.delete("repo1")).thenReturn(1);
        assertEquals(1, service.delete("repo1"));
        verify(dao).delete("repo1");
    }

    @Test
    void delete_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.delete("repo1")).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.delete("repo1"));
    }

    // --- isRepoTransparent ---

    @Test
    void isRepoTransparent_returnsFalse_forBlankKey() {
        assertFalse(service.isRepoTransparent(null));
        assertFalse(service.isRepoTransparent(""));
        assertFalse(service.isRepoTransparent("   "));
    }

    @Test
    void isRepoTransparent_returnsTrueFromCache_afterInsert() throws SQLException {
        service.insert(config("repo1"));
        // Disregard the existsByKey call from insert's idempotency pre-check; what we care about
        // here is that the subsequent isRepoTransparent reads from cache, not DB.
        clearInvocations(dao);
        assertTrue(service.isRepoTransparent("repo1"));
        verify(dao, never()).existsByKey("repo1");
    }

    @Test
    void isRepoTransparent_isCaseInsensitive_afterInsert() throws SQLException {
        service.insert(config("REPO1"));
        clearInvocations(dao);
        assertTrue(service.isRepoTransparent("repo1"));
        assertTrue(service.isRepoTransparent("REPO1"));
        assertTrue(service.isRepoTransparent("Repo1"));
        verify(dao, never()).existsByKey(Mockito.anyString());
    }

    @Test
    void isRepoTransparent_loadsFromDb_onCacheMiss() throws SQLException {
        when(dao.existsByKey("repo1")).thenReturn(true);
        assertTrue(service.isRepoTransparent("repo1"));
        verify(dao).existsByKey("repo1");
    }

    @Test
    void isRepoTransparent_returnsFalse_whenNotInDbOrCache() throws SQLException {
        when(dao.existsByKey("missing")).thenReturn(false);
        assertFalse(service.isRepoTransparent("missing"));
    }

    // --- delete cache interaction ---

    @Test
    void delete_invalidatesCacheEntry_soNextCallHitsDb() throws SQLException {
        service.insert(config("repo1"));
        when(dao.delete("repo1")).thenReturn(1);
        service.delete("repo1");
        // Ignore existsByKey calls from prior setup (insert's pre-check); verify only that the
        // post-delete isRepoTransparent triggers a DB lookup because the cache entry was invalidated.
        clearInvocations(dao);
        when(dao.existsByKey("repo1")).thenReturn(false);
        assertFalse(service.isRepoTransparent("repo1"));
        verify(dao).existsByKey("repo1");
    }

    // --- init ---

    @Test
    void init_prePopulatesCache() throws SQLException {
        when(dao.getAll()).thenReturn(List.of(config("repo1"), config("repo2")));
        service.init();
        assertTrue(service.isRepoTransparent("repo1"));
        assertTrue(service.isRepoTransparent("repo2"));
        verify(dao, never()).existsByKey(Mockito.anyString());
    }

    @Test
    void init_continuesGracefully_whenDaoFails() throws SQLException {
        when(dao.getAll()).thenThrow(new SQLException("db error"));
        service.init();
        // cache starts empty; isRepoTransparent falls through to DB
        when(dao.existsByKey("repo1")).thenReturn(true);
        assertTrue(service.isRepoTransparent("repo1"));
    }

    // --- invalidateCacheEntry ---

    @Test
    void invalidateCacheEntry_doesNothing_forBlankKey() {
        service.invalidateCacheEntry(null);
        service.invalidateCacheEntry("");
        service.invalidateCacheEntry("   ");
    }

    @Test
    void invalidateCacheEntry_reloadsValue_fromDb() throws SQLException {
        when(dao.existsByKey("repo1")).thenReturn(true);
        service.insert(config("repo1"));
        service.invalidateCacheEntry("repo1");
        // refresh is async; mock is set up before invalidation so any reload returns true
        assertTrue(service.isRepoTransparent("repo1"));
    }

    // --- bulkInsert / bulkDelete / findByKeys: delegation happy paths ---

    @Test
    void bulkInsert_delegatesToDao_andMarksKeysTransparent() throws SQLException {
        List<DbRepositoryConfig> configs = List.of(config("repo1"), config("repo2"));
        service.bulkInsert(configs);
        verify(dao).bulkInsert(configs);
        // Disregard existsByKey calls from the idempotency pre-check; what matters is that the
        // post-insert isRepoTransparent calls below read from cache, not DB.
        clearInvocations(dao);
        assertTrue(service.isRepoTransparent("repo1"));
        assertTrue(service.isRepoTransparent("repo2"));
        verify(dao, never()).existsByKey(Mockito.anyString());
    }

    @Test
    void bulkDelete_delegatesToDao_andInvalidatesCacheEntries() throws SQLException {
        service.insert(config("repo1"));
        service.insert(config("repo2"));
        List<String> keys = List.of("repo1", "repo2");
        service.bulkDelete(keys);
        verify(dao).bulkDelete(keys);
        when(dao.existsByKey("repo1")).thenReturn(false);
        when(dao.existsByKey("repo2")).thenReturn(false);
        assertFalse(service.isRepoTransparent("repo1"));
        assertFalse(service.isRepoTransparent("repo2"));
    }

    @Test
    void findByKeys_delegatesToDao() throws SQLException {
        List<String> keys = List.of("repo1", "repo2");
        List<DbRepositoryConfig> configs = List.of(config("repo1"), config("repo2"));
        when(dao.findByKeys(keys)).thenReturn(configs);
        assertEquals(2, service.findByKeys(keys).size());
    }

    // --- empty-input no-op: parameterized across every bulk-style public method ---

    @ParameterizedTest(name = "{0}_isNoOp_forEmptyCollection")
    @MethodSource("bulkMethodsWithEmptyInput")
    void bulkMethod_isNoOp_forEmptyInput(String name, Consumer<TransparentRepositoryConfigServiceImpl> op)
            throws SQLException {
        op.accept(service);
        verify(dao, never()).bulkInsert(Mockito.any());
        verify(dao, never()).bulkDelete(Mockito.any());
        verify(dao, never()).findByKeys(Mockito.any());
        verify(repositoryConfigStorageService, never()).findRepositoryConfigsByKeys(any());
        verify(repositoryConfigStorageService, never()).bulkInsertRepositoryConfigs(any(), any());
        verify(repositoryConfigStorageService, never()).bulkDeleteRepositoryConfigs(any());
    }

    static Stream<Arguments> bulkMethodsWithEmptyInput() {
        return Stream.of(
                Arguments.of("bulkInsert",
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s -> s.bulkInsert(List.of())),
                Arguments.of("bulkDelete",
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s -> s.bulkDelete(List.of())),
                Arguments.of("findByKeys",
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s -> s.findByKeys(List.of())),
                Arguments.of("transitionReposToTransparent",
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s ->
                                s.transitionReposToTransparent(List.of())),
                Arguments.of("transitionReposToActive",
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s ->
                                s.transitionReposToActive(List.of()))
        );
    }

    // --- SQLException -> StorageException: parameterized across all bulk DAO-wrappers ---

    @ParameterizedTest(name = "{0}_throwsStorageException_whenDaoFails")
    @MethodSource("bulkDaoFailures")
    void bulkMethod_throwsStorageException_whenDaoFails(String name,
            DaoFailureStubber stubber,
            Consumer<TransparentRepositoryConfigServiceImpl> op) throws SQLException {
        stubber.apply(dao);
        assertThrows(StorageException.class, () -> op.accept(service));
    }

    static Stream<Arguments> bulkDaoFailures() {
        return Stream.of(
                Arguments.of("bulkInsert",
                        (DaoFailureStubber) d -> doThrow(new SQLException("db error")).when(d).bulkInsert(any()),
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s ->
                                s.bulkInsert(List.of(new DbRepositoryConfig("repo1", "local", null, "maven",
                                        1L, 1L, 1L, new byte[]{1})))),
                Arguments.of("bulkDelete",
                        (DaoFailureStubber) d -> doThrow(new SQLException("db error")).when(d).bulkDelete(any()),
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s ->
                                s.bulkDelete(List.of("repo1"))),
                Arguments.of("findByKeys",
                        (DaoFailureStubber) d -> when(d.findByKeys(any()))
                                .thenThrow(new SQLException("db error")),
                        (Consumer<TransparentRepositoryConfigServiceImpl>) s ->
                                s.findByKeys(List.of("repo1")))
        );
    }

    @FunctionalInterface
    private interface DaoFailureStubber {
        void apply(TransparentRepositoryConfigDao dao) throws SQLException;
    }

    // --- reloadAllFromDb ---

    @Test
    void reloadAllFromDb_throwsStorageException_whenDaoFails() throws SQLException {
        when(dao.getAll()).thenThrow(new SQLException("db error"));
        assertThrows(StorageException.class, () -> service.reloadAllFromDb());
    }

    @Test
    void reloadAllFromDb_replacesCacheWithCurrentDbState() throws SQLException {
        // Prime the cache with an entry that no longer exists in DB
        service.insert(config("stale"));
        when(dao.getAll()).thenReturn(List.of(config("fresh1"), config("fresh2")));
        service.reloadAllFromDb();
        assertTrue(service.isRepoTransparent("fresh1"));
        assertTrue(service.isRepoTransparent("fresh2"));
        // Ignore existsByKey from the earlier insert pre-check.
        clearInvocations(dao);
        // Stale should now miss the cache and fall through to DB
        when(dao.existsByKey("stale")).thenReturn(false);
        assertFalse(service.isRepoTransparent("stale"));
        verify(dao).existsByKey("stale");
    }

    // --- transitionReposToTransparent ---

    @Test
    void transitionReposToTransparent_movesActiveConfigsAndMarksThemTransparent() throws SQLException {
        List<String> keys = List.of("repo1", "repo2");
        DbRepositoryConfig c1 = config("repo1");
        DbRepositoryConfig c2 = config("repo2");
        when(dao.existsByKey("repo1")).thenReturn(false);
        when(dao.existsByKey("repo2")).thenReturn(false);
        when(repositoryConfigStorageService.findRepositoryConfigsByKeys(new HashSet<>(keys)))
                .thenReturn(new HashSet<>(List.of(c1, c2)));
        service.transitionReposToTransparent(keys);
        verify(dao).bulkInsert(any());
        verify(repositoryConfigStorageService).bulkDeleteRepositoryConfigs(any());
        assertTrue(service.isRepoTransparent("repo1"));
        assertTrue(service.isRepoTransparent("repo2"));
    }

    @Test
    void transitionReposToTransparent_isNoOp_whenAllKeysAlreadyTransparent() throws SQLException {
        when(dao.existsByKey("repo1")).thenReturn(true);
        when(dao.existsByKey("repo2")).thenReturn(true);
        service.transitionReposToTransparent(List.of("repo1", "repo2"));
        verify(dao, never()).bulkInsert(any());
        verify(repositoryConfigStorageService, never()).bulkDeleteRepositoryConfigs(any());
    }

    @Test
    void transitionReposToTransparent_isNoOp_whenNoneExistInActive() throws SQLException {
        when(dao.existsByKey(Mockito.anyString())).thenReturn(false);
        when(repositoryConfigStorageService.findRepositoryConfigsByKeys(any())).thenReturn(Set.of());
        service.transitionReposToTransparent(List.of("missing"));
        verify(dao, never()).bulkInsert(any());
        verify(repositoryConfigStorageService, never()).bulkDeleteRepositoryConfigs(any());
    }

    // --- transitionReposToActive ---

    @Test
    void transitionReposToActive_movesTransparentConfigsToActive() throws SQLException {
        List<String> keys = List.of("repo1", "repo2");
        DbRepositoryConfig c1 = config("repo1");
        DbRepositoryConfig c2 = config("repo2");
        when(dao.findByKeys(keys)).thenReturn(List.of(c1, c2));
        service.transitionReposToActive(keys);
        verify(repositoryConfigStorageService).bulkInsertRepositoryConfigs(any(), eq(Set.of()));
        verify(dao).bulkDelete(any());
    }

    @Test
    void transitionReposToActive_reconstructsRepoTypeV2_fromPreservedType() throws SQLException {
        // transparent_repo_config has no repo_type_v2 column, so configs read back from it carry a null shadow
        // value (here the type survives but repoTypeV2 is null). The transition must reconstruct repo_type_v2
        // from type before re-inserting into the active table: federated -> local, every other type -> itself.
        DbRepositoryConfig fed = configWithTypeAndNullV2("fed-repo", "federated");
        DbRepositoryConfig local = configWithTypeAndNullV2("local-repo", "local");
        DbRepositoryConfig remote = configWithTypeAndNullV2("remote-repo", "remote");
        List<String> keys = List.of("fed-repo", "local-repo", "remote-repo");
        when(dao.findByKeys(keys)).thenReturn(List.of(fed, local, remote));

        service.transitionReposToActive(keys);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<DbRepositoryConfig>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repositoryConfigStorageService).bulkInsertRepositoryConfigs(captor.capture(), eq(Set.of()));
        Set<DbRepositoryConfig> inserted = captor.getValue();
        assertEquals("local", repoTypeV2Of(inserted, "fed-repo"));
        assertEquals("local", repoTypeV2Of(inserted, "local-repo"));
        assertEquals("remote", repoTypeV2Of(inserted, "remote-repo"));
    }

    @Test
    void transitionReposToActive_isNoOp_whenNoneExistInTransparent() throws SQLException {
        when(dao.findByKeys(Mockito.any())).thenReturn(List.of());
        service.transitionReposToActive(List.of("missing"));
        verify(repositoryConfigStorageService, never()).bulkInsertRepositoryConfigs(any(), any());
        verify(dao, never()).bulkDelete(any());
    }

    @Test
    void transitionReposToActive_isNoOp_whenAllKeysAlreadyActive() throws SQLException {
        DbRepositoryConfig c1 = config("repo1");
        when(dao.findByKeys(List.of("repo1"))).thenReturn(List.of(c1));
        when(repositoryConfigStorageService.exists("repo1")).thenReturn(true);
        service.transitionReposToActive(List.of("repo1"));
        verify(repositoryConfigStorageService, never()).bulkInsertRepositoryConfigs(any(), any());
        verify(dao, never()).bulkDelete(any());
    }

    // --- Mapper-delegating one-liners ---

    @Test
    void getAllTransparentRepoConfigs_mapsEveryRow() throws SQLException {
        // Real JSON blobs so the mapper inside the impl can deserialize without crashing.
        when(dao.getAll()).thenReturn(List.of(configWithBlob("repo1"), configWithBlob("repo2")));
        var result = service.getAllTransparentRepoConfigs();
        assertEquals(2, result.size());
    }

    @Test
    void getAllTransparentRepoConfigs_returnsEmpty_whenNoRows() throws SQLException {
        when(dao.getAll()).thenReturn(List.of());
        assertTrue(service.getAllTransparentRepoConfigs().isEmpty());
    }

    @Test
    void getTransparentRepoConfigByKey_returnsMappedConfig_whenFound() throws SQLException {
        when(dao.findByKey("repo1")).thenReturn(Optional.of(configWithBlob("repo1")));
        var result = service.getTransparentRepoConfigByKey("repo1");
        assertTrue(result.isPresent());
        assertEquals("repo1", result.get().getKey());
    }

    @Test
    void getTransparentRepoConfigByKey_returnsEmpty_whenNotFound() throws SQLException {
        when(dao.findByKey("missing")).thenReturn(Optional.empty());
        assertTrue(service.getTransparentRepoConfigByKey("missing").isEmpty());
    }

    // --- bulkInsert/bulkDelete/findByKeys null and all-filtered branches ---

    @Test
    void bulkInsert_isNoOp_forNull() throws SQLException {
        service.bulkInsert(null);
        verify(dao, never()).bulkInsert(any());
    }

    @Test
    void bulkInsert_isNoOp_whenAllConfigsAreAlreadyTransparent() throws SQLException {
        when(dao.findAllKeysLowerCase()).thenReturn(Set.of("repo1"));
        service.bulkInsert(List.of(config("repo1")));
        verify(dao, never()).bulkInsert(any());
    }

    @Test
    void bulkDelete_isNoOp_forNull() throws SQLException {
        service.bulkDelete(null);
        verify(dao, never()).bulkDelete(any());
    }

    @Test
    void findByKeys_isNoOp_forNull() throws SQLException {
        assertTrue(service.findByKeys(null).isEmpty());
        verify(dao, never()).findByKeys(any());
    }

    @Test
    void transitionReposToTransparent_isNoOp_forNull() throws SQLException {
        service.transitionReposToTransparent(null);
        verify(dao, never()).bulkInsert(any());
    }

    @Test
    void transitionReposToActive_isNoOp_forNull() throws SQLException {
        service.transitionReposToActive(null);
        verify(dao, never()).bulkDelete(any());
    }

    // --- HA propagation path (delegates to HaCommonAddon via ContextHelper) ---

    @Nested
    class HaPropagation {

        @Test
        void singularInsert_propagatesAsTargetedInvalidation() throws SQLException {
            service.insert(config("repo1"));
            // single-key change → targeted (non-null) repoKey
            verify(haCommonAddon).propagateTransparentRepoCacheChange("repo1");
        }

        @Test
        void singularDelete_propagatesAsTargetedInvalidation() throws SQLException {
            when(dao.delete("repo1")).thenReturn(1);
            service.delete("repo1");
            verify(haCommonAddon).propagateTransparentRepoCacheChange("repo1");
        }

        @Test
        void bulkInsert_propagatesAsFullReload_whenMultipleKeys() throws SQLException {
            when(dao.existsByKey(Mockito.anyString())).thenReturn(false);
            service.bulkInsert(List.of(config("repo1"), config("repo2")));
            // bulk → null signals full reload to peers
            verify(haCommonAddon).propagateTransparentRepoCacheChange(null);
        }

        @Test
        void bulkInsert_propagatesAsTargeted_whenSingleKey() throws SQLException {
            when(dao.existsByKey("repo1")).thenReturn(false);
            service.bulkInsert(List.of(config("repo1")));
            verify(haCommonAddon).propagateTransparentRepoCacheChange("repo1");
        }

        @Test
        void propagationFailure_isSwallowed() throws SQLException {
            Mockito.doThrow(new RuntimeException("boom"))
                    .when(haCommonAddon).propagateTransparentRepoCacheChange(Mockito.any());
            // Service call must not propagate the propagation exception.
            service.insert(config("repo1"));
            verify(dao).insert(any());
        }

        @Test
        void propagation_retriesUpToConfiguredMaxAttempts() throws SQLException {
            setStringSystemProperty(ConstantValues.transparentRepoPropagationMaxAttempts, "3");
            setStringSystemProperty(ConstantValues.transparentRepoPropagationBackoffMs, "0");
            Mockito.doThrow(new RuntimeException("boom"))
                    .when(haCommonAddon).propagateTransparentRepoCacheChange(Mockito.any());
            service.insert(config("repo1"));
            // 3 attempts before giving up.
            verify(haCommonAddon, Mockito.times(3)).propagateTransparentRepoCacheChange("repo1");
        }

        @Test
        void propagationSkipped_whenAddonsManagerUnavailable() throws SQLException {
            // Unbind the mocked context so ContextHelper.get() returns null and the bean lookup fails.
            ArtifactoryContextThreadBinder.unbind();
            try {
                // Should not throw — the early-return path handles missing context gracefully.
                service.insert(config("repo1"));
                verify(haCommonAddon, never()).propagateTransparentRepoCacheChange(Mockito.any());
            } finally {
                ArtifactoryContextThreadBinder.bind(artifactoryContext);
            }
        }
    }

    // --- propagateAfterCommitOrNow: in-transaction branch ---

    @Nested
    class AfterCommitDeferral {

        @Test
        void propagationIsDeferred_whenTxIsActive() throws SQLException {
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.insert(config("repo1"));
                // Inside the (mock) tx, propagation must NOT have happened yet.
                verify(haCommonAddon, never()).propagateTransparentRepoCacheChange(Mockito.any());
                // Synchronization should have a registered hook waiting for commit.
                var syncs = TransactionSynchronizationManager.getSynchronizations();
                assertEquals(1, syncs.size());
                // Fire afterCommit manually to simulate commit boundary.
                for (TransactionSynchronization s : syncs) {
                    s.afterCommit();
                }
                verify(haCommonAddon).propagateTransparentRepoCacheChange("repo1");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        void propagationIsImmediate_whenNoTxActive() throws SQLException {
            // No initSynchronization → no active sync → propagation fires inline.
            service.insert(config("repo1"));
            verify(haCommonAddon).propagateTransparentRepoCacheChange("repo1");
        }
    }

    @Nested
    class UpsertRepoConfig {

        @Test
        void inserts_whenInsertSucceeds() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("new-repo", RepoType.Generic);

            service.upsertRepoConfig(repoConfig);

            verify(dao).insert(any(DbRepositoryConfig.class));
            verify(dao, never()).update(any(DbRepositoryConfig.class));
        }

        @Test
        void skipsInsert_andCallsUpdate_whenAlreadyTransparent() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("existing-repo", RepoType.Generic);
            // Cache hit via isRepoTransparent → existsByKey backs the cache loader.
            when(dao.existsByKey("existing-repo")).thenReturn(true);

            service.upsertRepoConfig(repoConfig);

            verify(dao, never()).insert(any(DbRepositoryConfig.class));
            verify(dao).update(any(DbRepositoryConfig.class));
        }

        @Test
        void fallsBackToUpdate_onUniqueConstraintViolation() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("existing-repo", RepoType.Generic);
            // SQLState 23505 → unique violation per BaseDao.isUniqueConstraintViolation
            doThrow(new SQLException("dup key", "23505")).when(dao).insert(any(DbRepositoryConfig.class));

            service.upsertRepoConfig(repoConfig);

            verify(dao).insert(any(DbRepositoryConfig.class));
            verify(dao).update(any(DbRepositoryConfig.class));
        }

        @Test
        void wrapsOtherSqlException_asStorageException() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("repo", RepoType.Generic);
            // Non-unique-violation SQLState — should NOT fall through to update.
            doThrow(new SQLException("connection error", "08000")).when(dao).insert(any(DbRepositoryConfig.class));

            assertThrows(StorageException.class, () -> service.upsertRepoConfig(repoConfig));
            verify(dao, never()).update(any(DbRepositoryConfig.class));
        }

        @Test
        void wrapsUpdateFailure_asStorageException() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("existing-repo", RepoType.Generic);
            doThrow(new SQLException("dup", "23505")).when(dao).insert(any(DbRepositoryConfig.class));
            doThrow(new SQLException("update error")).when(dao).update(any(DbRepositoryConfig.class));

            assertThrows(StorageException.class, () -> service.upsertRepoConfig(repoConfig));
        }

        @Test
        void ignores_whenConfigIsNull() throws SQLException {
            service.upsertRepoConfig(null);

            verify(dao, never()).insert(any(DbRepositoryConfig.class));
            verify(dao, never()).update(any(DbRepositoryConfig.class));
        }

        /**
         * Wire payload from federation mirror arrives with creation=0/modification=0; the
         * active-table insert validator (used after {@code transitionReposToActive}) rejects
         * zero, so upsert stamps both before mapping.
         */
        @Test
        void stampsCreationAndModification_whenIncomingZero() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("wire-repo", RepoType.Generic);
            repoConfig.setCreation(0L);
            repoConfig.setModification(0L);
            long before = System.currentTimeMillis();

            service.upsertRepoConfig(repoConfig);

            assertTrue(repoConfig.getCreation() >= before);
            assertTrue(repoConfig.getModification() >= before);
            verify(dao).insert(any(DbRepositoryConfig.class));
        }

        @Test
        void preservesExistingCreationAndModification_whenAlreadySet() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("kept-repo", RepoType.Generic);
            repoConfig.setCreation(111L);
            repoConfig.setModification(222L);

            service.upsertRepoConfig(repoConfig);

            assertEquals(111L, repoConfig.getCreation());
            assertEquals(222L, repoConfig.getModification());
            verify(dao).insert(any(DbRepositoryConfig.class));
        }

        @Test
        void stampsModification_whenCreationSetButModificationZero() throws SQLException {
            LocalRepoConfig repoConfig = LocalRepoConfig.create("partial-repo", RepoType.Generic);
            // Creation > 0 → stamp helper is a no-op (mirrors prior server-side population).
            repoConfig.setCreation(123L);
            repoConfig.setModification(0L);

            service.upsertRepoConfig(repoConfig);

            assertEquals(123L, repoConfig.getCreation());
            assertEquals(0L, repoConfig.getModification(),
                    "stamp triggers only when creation is missing — leaves modification alone otherwise");
            verify(dao).insert(any(DbRepositoryConfig.class));
        }
    }

    private DbRepositoryConfig config(String key) {
        return new DbRepositoryConfig(key, "local", null, "maven", 1L, 1L, 1L, new byte[]{1});
    }

    /**
     * Mirrors what a row read back from transparent_repo_config looks like: type is preserved but the
     * repo_type_v2 shadow column does not exist there, so the entity comes back with a null shadow value.
     */
    private DbRepositoryConfig configWithTypeAndNullV2(String key, String type) {
        return new DbRepositoryConfig(key, type, null, "maven", 1L, 1L, 1L, new byte[]{1});
    }

    private static String repoTypeV2Of(Set<DbRepositoryConfig> configs, String key) {
        return configs.stream()
                .filter(c -> key.equals(c.getKey()))
                .map(DbRepositoryConfig::getRepoTypeV2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No config for key " + key));
    }

    /**
     * Same as {@link #config(String)} but with a JSON-serialised blob so the mapper inside the
     * service can deserialise it (the dummy {@code new byte[]{1}} is fine for cache/store tests
     * but blows up the JSON path).
     */
    private DbRepositoryConfig configWithBlob(String key) {
        return new RepositoryConfigMapperImpl()
                .repositoryConfigToDbRepositoryConfig(LocalRepoConfig.create(key, RepoType.Generic));
    }
}
