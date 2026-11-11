package org.artifactory.ph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.model.FilteringProperty;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageSearchContext;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.db.DbService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.jfrog.metadata.client.GraphQLClient;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@Slf4j
class PackageSearchArtifactServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AqlService aqlService;
    @Mock
    private PackageRepoService repoService;
    @Mock
    private MetadataEventService metadataEventService;
    @Mock
    private GraphQLClient graphQLClient;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private DbService dbService;

    private PackageSearchArtifactServiceImpl packageSearchService;

    private final Date date = new Date(System.currentTimeMillis());
    private final String md5 = UUID.randomUUID().toString().replace("-", "");
    private final String sha = UUID.randomUUID().toString().replace("-", "");
    private static final String VALID_SHA2 =
            "20cfcf638366719a2978804a936d7eae3d1d199b1d17f810d1a9a8d25aabeefe";
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        when(authorizationService.canRead(any())).thenReturn(true);
        this.packageSearchService = Mockito.spy(new PackageSearchArtifactServiceImpl(
                aqlService, authorizationService, repoService, null, repositoryConfigService, binaryService,
                dbService));
        AqlEagerResult result = mock(AqlEagerResult.class);
        doAnswer(invocation -> List.of(aqlItem(date, md5, sha + "12345678", sha + sha))).when(result).getResults();
        ResultSet resultSet = mock(ResultSet.class);
        AqlLazyResult resultLazy = mock(AqlLazyResult.class);
        doAnswer(invocation -> resultSet).when(resultLazy).getResultSet();
        when(aqlService.executeQueryEager(any(AqlBase.class))).thenReturn(result);
        when(aqlService.executeQueryLazy(any(AqlBase.class))).thenReturn(resultLazy);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsByExtensionInputs")
    void testFindArtifactsByExtensionInput(String repo, String path, String extension, String expectedError) {
        try {
            packageSearchService.findArtifactsByExtension(repo, path, extension);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    void testFindArtifactsByExtensionAndPath() {
        List<PackageArtifact> arts = packageSearchService.findArtifactsByExtension("repo", "path", "zip", "tar.gz")
                .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"$or":[
                        {"path":{"$eq":"path"}},
                        {"path":{"$match":"path/*"}}
                      ]},
                      {"$or":[
                        {"name":{"$match":"*.zip"}},
                        {"name":{"$match":"*.tar.gz"}}
                      ]},
                      {"depth":{"$gte":2}},
                      {"type":{"$eq":"file"}}
                    ]
                  }"""), query);

    }

    @Test
    void testFindArtifactsByExtensionOnly() {
        List<PackageArtifact> arts = packageSearchService.findArtifactsByExtension("repo", null, "tar.gz")
                .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                  "$and":[
                    {"repo":{"$eq":"repo"}},
                    {"$or":[
                      {"name":{"$match":"*.tar.gz"}}
                    ]},
                    {"depth":{"$gte":1}},
                    {"type":{"$eq":"file"}}
                  ]
                }""".indent(2)), query);
    }

    @Test
    void testFindArtifactsChildren() {
        List<PackageArtifact> arts = packageSearchService.findArtifactsChildren("repo", "/path/aaa/bb")
                .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"$or":[
                        {"path":{"$eq":"/path/aaa/bb"}},
                        {"path":{"$match":"/path/aaa/bb/*"}}
                      ]},
                      {"depth":{"$gte":4}},
                      {"type":{"$eq":"file"}}
                    ]
                  }"""), query);
    }

    @Test
    void testFindArtifactsChildrenWithLastUpdated() {
        List<PackageArtifact> arts = packageSearchService.findArtifactsChildren("repo", "/path/aaa/bb", 0, -1, 1000L)
                .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"$or":[
                        {"path":{"$eq":"/path/aaa/bb"}},
                        {"path":{"$match":"/path/aaa/bb/*"}}
                      ]},
                      {"depth":{"$gte":4}},
                      {"updated":{"$lte":"1000"}},
                      {"type":{"$eq":"file"}}
                    ]
                  }"""), query);
    }

    @Test
    void testFindArtifactsChildrenIncludeFolders() {
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsChildren("repo", "/path/aaa/bb", 0, -1, 1000L, true)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("  {\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"$or\":[\n" +
                "        {\"path\":{\"$eq\":\"/path/aaa/bb\"}},\n" +
                "        {\"path\":{\"$match\":\"/path/aaa/bb/*\"}}\n" +
                "      ]},\n" +
                "      {\"depth\":{\"$gte\":4}},\n" +
                "      {\"updated\":{\"$lte\":\"1000\"}},\n" +
                "      {\"type\":{\"$eq\":\"any\"}}\n" +
                "    ]\n" +
                "  }\n"), query);
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsChildrenInputs")
    void testFindArtifactsChildrenInput(String repo, String path, String expectedError) {
        try {
            packageSearchService.findArtifactsChildren(repo, path, 0, -1, 0);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    void testFindArtifactsChildrenWithName() {
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsChildrenWithName("repo", "/path/aaa/bb", "package_", 0, -1)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                  "$and":[
                    {"repo":{"$eq":"repo"}},
                    {"$or":[
                      {"path":{"$eq":"/path/aaa/bb"}},
                      {"path":{"$match":"/path/aaa/bb/*"}}
                    ]},
                    {"name":{"$eq":"package_"}},
                    {"depth":{"$gte":4}},
                    {"type":{"$eq":"file"}}
                  ]
                }""".indent(2)), query);
    }

    @Test
    void testFindArtifactsChildrenWithNameAndAsterix() {
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsChildrenWithName("repo", "/path/aaa/bb", "package_*", 0, -1)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                  "$and":[
                    {"repo":{"$eq":"repo"}},
                    {"$or":[
                      {"path":{"$eq":"/path/aaa/bb"}},
                      {"path":{"$match":"/path/aaa/bb/*"}}
                    ]},
                    {"name":{"$match":"package_*"}},
                    {"depth":{"$gte":4}},
                    {"type":{"$eq":"file"}}
                  ]
                }""".indent(2)), query);
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsChildrenWithNameInputs")
    void testFindArtifactsChildrenWithNameInput(String repo, String path, String name, String expectedError) {
        try {
            packageSearchService.findArtifactsChildrenWithName(repo, path, name, 0, -1);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    void testFindArtifactsByExactName() {
        List<PackageArtifact> arts = packageSearchService.findArtifactsByExactName("repo", "PACKAGES")
                .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                  "$and":[
                    {"repo":{"$eq":"repo"}},
                    {"name":{"$eq":"PACKAGES"}},
                    {"depth":{"$gte":1}},
                    {"type":{"$eq":"file"}}
                  ]
                }""".indent(2)), query);
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsByExactNameInputs")
    void testFindArtifactsByExactNameInput(String repo, String name, String expectedError) {
        try {
            packageSearchService.findArtifactsByExactName(repo, name);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameQueryShape() {
        Optional<PackageArtifact> artifact =
                packageSearchService.findFirstReadableRetrievableArtifactByExactName("xorb_xorb-123_0-1024", null,
                        Set.of());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryLazy(aql.capture());
        // No rows in the mocked lazy result -> empty, but the query shape is what matters here.
        assertTrue(artifact.isEmpty());
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\"name\":{\"$eq\":\"xorb_xorb-123_0-1024\"}}"), query);
        assertTrue(query.contains("{\"type\":{\"$eq\":\"file\"}}"), query);
        // Package type / repo are filtered outside the query (in-stream), so no repo filter is part of it. "repo" only
        // appears in the include() projection, never as a {"repo":{...}} filter clause.
        assertFalse(query.contains("{\"repo\":{"), query);
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameReturnsEmptyWhenNoReposOfType() {
        when(repoService.getAllReposByType(eq(PackageType.HUGGINGFACEML), any()))
                .thenReturn(List.of());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_xorb-123_0-1024", PackageType.HUGGINGFACEML, Set.of());

        assertTrue(artifact.isEmpty());
        // No eligible repositories of the package type -> skip the query entirely.
        verify(aqlService, never()).executeQueryLazy(any(AqlBase.class));
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameResolvesRemoteCacheRepoKeys() {
        PackageRepo localRepo = mock(PackageRepo.class);
        when(localRepo.getKey()).thenReturn("hf-local");
        PackageRepo remoteRepo = mock(PackageRepo.class);
        when(remoteRepo.getKey()).thenReturn("hf-remote");
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.LOCAL)).thenReturn(List.of(localRepo));
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.REMOTE)).thenReturn(List.of(remoteRepo));
        when(repoService.getRemoteCacheRepoKey("hf-remote")).thenReturn("hf-remote-cache");

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_xorb-123_0-1024", PackageType.HUGGINGFACEML, Set.of());

        assertTrue(artifact.isEmpty());
        // Remote repos are resolved to their cache repos (the storage key AQL reports), local repos are used as-is.
        verify(repoService, times(1)).getRemoteCacheRepoKey("hf-remote");
        verify(aqlService, times(1)).executeQueryLazy(any(AqlBase.class));
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameExcludesPrefixedPaths() {
        // Only one row returned by AQL, and it falls under the excluded path prefix.
        // The excluded row should be skipped, resulting in an empty optional.
        ResultSet singleExcludedRowResultSet = mock(ResultSet.class);
        when(singleExcludedRowResultSet.next()).thenReturn(true, false);

        List<DomainSensitiveField> fields = List.of(
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items)));
        when(singleExcludedRowResultSet.getString("itemRepo")).thenReturn("hf-repo");
        when(singleExcludedRowResultSet.getString("itemPath")).thenReturn(".jfrog/.xet/cas_upload/session1");
        when(singleExcludedRowResultSet.getString("itemName")).thenReturn("xorb_abc");

        AqlLazyResult singleExcludedRowLazyResult = mock(AqlLazyResult.class);
        when(singleExcludedRowLazyResult.getResultSet()).thenReturn(singleExcludedRowResultSet);
        when(singleExcludedRowLazyResult.getFields()).thenReturn(fields);
        when(aqlService.executeQueryLazy(any(AqlBase.class))).thenReturn(singleExcludedRowLazyResult);

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of(".jfrog/.xet/cas_upload"));

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsRemoteRepoWithoutCache() {
        PackageRepo localRepo = mock(PackageRepo.class);
        when(localRepo.getKey()).thenReturn("hf-local");
        PackageRepo remoteRepo = mock(PackageRepo.class);
        when(remoteRepo.getKey()).thenReturn("hf-remote-no-cache");
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.LOCAL)).thenReturn(List.of(localRepo));
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.REMOTE)).thenReturn(List.of(remoteRepo));
        when(repoService.getRemoteCacheRepoKey("hf-remote-no-cache"))
                .thenThrow(new PackageNotFoundException("no cache"));
        mockExactNameLazyResult(LazySearchRow.builder().repo("other-repo").path("path").name("xorb_abc").build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", PackageType.HUGGINGFACEML, Set.of());

        assertTrue(artifact.isEmpty());
        verify(repoService, times(1)).getRemoteCacheRepoKey("hf-remote-no-cache");
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsRepoNotInAllowedSet() {
        PackageRepo localRepo = mock(PackageRepo.class);
        when(localRepo.getKey()).thenReturn("hf-local");
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.LOCAL)).thenReturn(List.of(localRepo));
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.REMOTE)).thenReturn(List.of());
        mockExactNameLazyResult(LazySearchRow.builder().repo("other-repo").path("path").name("xorb_abc").build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", PackageType.HUGGINGFACEML, Set.of());

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsUnreadableArtifact() {
        mockExactNameLazyResult(LazySearchRow.builder().repo("hf-repo").path("path").name("xorb_abc").build());
        when(authorizationService.canRead(any())).thenReturn(false);

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameReturnsFirstEligibleArtifact() {
        PackageRepo localRepo = mock(PackageRepo.class);
        when(localRepo.getKey()).thenReturn("hf-local");
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.LOCAL)).thenReturn(List.of(localRepo));
        when(repoService.getAllReposByType(PackageType.HUGGINGFACEML,
                org.artifactory.common.repo.RepoType.REMOTE)).thenReturn(List.of());
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("other-repo").path("skip").name("xorb_skip").build(),
                LazySearchRow.builder().repo("hf-local").path("path").name("xorb_abc").build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", PackageType.HUGGINGFACEML, Set.of());

        assertTrue(artifact.isPresent());
        assertEquals("xorb_abc", artifact.get().getName());
        assertEquals("hf-local", artifact.get().getRepoKey());
        // non-federated repos short-circuit isBinaryAvailable without touching binaryService
        verify(binaryService, never()).findBinary(any(), anyString());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameThrowsOnLazyQueryFailure() {
        RuntimeException cause = new RuntimeException("db error");
        when(aqlService.executeQueryLazy(any(AqlBase.class))).thenThrow(cause);

        PackageInternalServerError error = assertThrows(PackageInternalServerError.class,
                () -> packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                        "xorb_abc", null, Set.of()));

        assertSame(cause, error.getCause());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsFederatedArtifactWithBlankSha2() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        when(repositoryConfigService.getFederatedRepoConfigByKey("fed-repo")).thenReturn(federatedRepoConfig);
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("fed-repo").path("path").name("xorb_abc").sha2("").build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isEmpty());
        verify(binaryService, never()).findBinary(any(), anyString());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsFederatedArtifactWhenBinaryNotFound() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        when(repositoryConfigService.getFederatedRepoConfigByKey("fed-repo")).thenReturn(federatedRepoConfig);
        when(binaryService.findBinary(ChecksumType.sha256, VALID_SHA2)).thenReturn(null);
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("fed-repo").path("path").name("xorb_abc").sha2(VALID_SHA2).build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsFederatedArtifactWhenBinaryNotYetReplicated() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        BinaryElement binaryElement = mock(BinaryElement.class);
        when(repositoryConfigService.getFederatedRepoConfigByKey("fed-repo")).thenReturn(federatedRepoConfig);
        when(binaryService.findBinary(ChecksumType.sha256, VALID_SHA2)).thenReturn(binaryElement);
        when(binaryElement.getLength()).thenReturn(42L);
        when(binaryService.getBinary(binaryElement)).thenThrow(new BinaryNotFoundException("not yet available"));
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("fed-repo").path("path").name("xorb_abc").sha2(VALID_SHA2).build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameSkipsFederatedArtifactWhenBinaryVerificationFails() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        BinaryElement binaryElement = mock(BinaryElement.class);
        when(repositoryConfigService.getFederatedRepoConfigByKey("fed-repo")).thenReturn(federatedRepoConfig);
        when(binaryService.findBinary(ChecksumType.sha256, VALID_SHA2)).thenReturn(binaryElement);
        when(binaryElement.getLength()).thenReturn(42L);
        // getBinary declares only BinaryNotFoundException (a RuntimeException), so the catch (IOException) branch in
        // isBinaryAvailable is reachable solely through the try-with-resources close(). Fail close() to exercise it.
        InputStream failingStream = mock(InputStream.class);
        doThrow(new IOException("io error")).when(failingStream).close();
        when(binaryService.getBinary(binaryElement)).thenReturn(failingStream);
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("fed-repo").path("path").name("xorb_abc").sha2(VALID_SHA2).build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isEmpty());
    }

    @Test
    @SneakyThrows
    void testFindFirstReadableRetrievableArtifactByExactNameReturnsFederatedArtifactWhenBinaryAvailable() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        BinaryElement binaryElement = mock(BinaryElement.class);
        InputStream binaryStream = new ByteArrayInputStream(new byte[0]);
        when(repositoryConfigService.getFederatedRepoConfigByKey("fed-repo")).thenReturn(federatedRepoConfig);
        when(binaryService.findBinary(ChecksumType.sha256, VALID_SHA2)).thenReturn(binaryElement);
        when(binaryElement.getLength()).thenReturn(42L);
        when(binaryService.getBinary(binaryElement)).thenReturn(binaryStream);
        mockExactNameLazyResult(
                LazySearchRow.builder().repo("fed-repo").path("path").name("xorb_abc").sha2(VALID_SHA2).build());

        Optional<PackageArtifact> artifact = packageSearchService.findFirstReadableRetrievableArtifactByExactName(
                "xorb_abc", null, Set.of());

        assertTrue(artifact.isPresent());
        assertEquals("xorb_abc", artifact.get().getName());
        assertEquals("fed-repo", artifact.get().getRepoKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "name*"})
    void testFindFirstReadableRetrievableArtifactByExactNameRejectsInvalidNames(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> packageSearchService.findFirstReadableRetrievableArtifactByExactName(name, null, Set.of()));
    }

    @Test
    void testFindArtifactsByExactNameAndPath() {
        Optional<PackageArtifact> artifact =
                packageSearchService.findArtifactByExactPathAndName("repo", "path", "PACKAGES");
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertTrue(artifact.isPresent());
        assertArts(date, md5, sha, List.of(artifact.get()));
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"path\"}},\n" +
                "      {\"name\":{\"$eq\":\"PACKAGES\"}},\n" +
                "      {\"depth\":{\"$gte\":2}},\n" +
                "      {\"type\":{\"$eq\":\"file\"}}\n" +
                "    ]\n" +
                "  }"), query);
    }

    @Test
    void testFindArtifactsByExactNameAndPathIncludeFolders() {
        Optional<PackageArtifact> artifact =
                packageSearchService.findArtifactByExactPathAndName("repo", "path", "PACKAGES", true);
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertTrue(artifact.isPresent());
        assertArts(date, md5, sha, List.of(artifact.get()));
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"path\"}},\n" +
                "      {\"name\":{\"$eq\":\"PACKAGES\"}},\n" +
                "      {\"depth\":{\"$gte\":2}},\n" +
                "      {\"type\":{\"$eq\":\"any\"}}\n" +
                "    ]\n" +
                "  }"), query);
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsByExactNameAndPathInputs")
    void testFindArtifactsByExactNameAndPath(String repo, String path, String name, String expectedError) {
        try {
            packageSearchService.findArtifactByExactPathAndName(repo, path, name);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    void testFindArtifactsByPropKeys() {
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeys("repo", "/a/b/c", "deb.component", "deb.distribution")
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"path":{"$eq":"/a/b/c"}},
                      {"depth":{"$gte":4}},
                      {"property.key":{"$eq":"deb.component"}},
                      {"property.key":{"$eq":"deb.distribution"}},
                      {"type":{"$eq":"file"}}
                    ]
                  }"""), query);
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsByPropKeysInputInputs")
    void testFindArtifactsByPropKeysInput(String repo, String path, String propKey, String expectedError) {
        try {
            packageSearchService.findArtifactsByPropKeys(repo, path, propKey);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    @SneakyThrows
    void testFindArtifactsWithPropsByPropKeys() {
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsWithPropsByPropKey("repo", "/a/b/c", "pypi.yanked")
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryLazy(aql.capture());
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                 {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"$or":[
                        {"path":{"$eq":"/a/b/c"}},
                        {"path":{"$match":"/a/b/c/*"}}
                      ]},
                      {"depth":{"$gte":4}},
                      {"property.key":{"$eq":"pypi.yanked"}},
                      {"type":{"$eq":"file"}}
                    ]
                  }
                ).
                """), query);
        assertTrue(query.contains("include("));
        assertTrue(query.contains("\"property.key\",\"property.value\")"));
    }

    @Test
    void testFindArtifactsByPropKeyAndVal() {
        PackageSearchContext searchContext = new PackageSearchContext("repo", "/a/b/c");
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeyAndVal(searchContext, Map.of("deb.component", "a"))
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("""
                {
                    "$and":[
                      {"repo":{"$eq":"repo"}},
                      {"path":{"$eq":"/a/b/c"}},
                      {"depth":{"$gte":4}},
                      {"@deb.component":{"$eq":"a"}},
                      {"type":{"$eq":"file"}}
                    ]
                  }"""), query);
    }


    @Test
    void testFindArtifactsByPropKeyAndValWithLimitAndOffset() {
        PackageSearchContext searchContext =
                PackageSearchContext.builder().repoKey("repo").path("/a/b/c").offset(1).limit(2).build();
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeyAndVal(searchContext, Map.of("deb.component", "a"))
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"/a/b/c\"}},\n" +
                "      {\"depth\":{\"$gte\":4}},\n" +
                "      {\"@deb.component\":{\"$eq\":\"a\"}},\n" +
                "      {\"type\":{\"$eq\":\"file\"}}\n" +
                "    ]\n" +
                "  }"), query);
        assertTrue(query.contains("offset(1).\n" +
                "limit(2)"));
    }


    @ParameterizedTest
    @MethodSource("provideFindArtifactsByPropKeyAndValInputs")
    void testFindArtifactsByPropKeyAndValInput(String repo, String path, String propKey, String propVal,
            String expectedError) {
        PackageSearchContext searchContext = new PackageSearchContext(repo, path);
        try {
            Map<String, String> properties = Maps.newHashMap();
            if (propKey != null) {
                properties.put(propKey, propVal);
            }
            packageSearchService.findArtifactsByPropKeyAndVal(searchContext, properties);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @ParameterizedTest
    @MethodSource("provideFindArtifactsByPropKeyAndValInputs")
    void testFindArtifactsByPropKeyAndMultiValInput(String repo, String path, String propKey, String propVal,
            String expectedError) {
        PackageSearchContext searchContext = new PackageSearchContext(repo, path);
        try {
            Multimap<String, String> multiValues = HashMultimap.create();
            if (propKey != null ) {
                multiValues.put(propKey, propVal);
            }
            if (propKey != null && propKey.equals("MultiValAstrix")) {
                multiValues.put(propKey, propVal);
                multiValues.put(propKey, "**");
            }
            packageSearchService.findArtifactsByPropKeyAndMultiVal(searchContext, multiValues);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), expectedError);
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @Test
    void testFindArtifactsByPropKeyAndMultiValWithNullAsProperties() {
        PackageSearchContext searchContext = new PackageSearchContext("repo", "path");
        try {
            packageSearchService.findArtifactsByPropKeyAndMultiVal(searchContext, null);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("No properties maps have been provided", e.getMessage());
        } catch (Exception e) {
            fail("Expected IllegalArgumentException only");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testFindArtifactsByPropKeyAndValWithRemote(boolean withCache) throws PackageNotFoundException {
        String repo = "remote";
        String path = "path";
        String searchedRepo = withCache ? repo + "-cache" : repo;
        when(repoService.isRemote(repo)).thenReturn(true);
        doAnswer(invocationOnMock -> {
            if (withCache) {
                return repo + "-cache";
            }
            throw new PackageNotFoundException("error");
        }).when(repoService).getRemoteCacheRepoKey(repo);
        PackageSearchContext searchContext = new PackageSearchContext(repo, path);
        packageSearchService.findArtifactsByPropKeyAndVal(searchContext, Map.of("prop", "val"));
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\"repo\":{\"$eq\":\"" + searchedRepo + "\"}}"), query);
        verify(repoService, times(1)).isRemote(repo);
        verify(repoService, times(1)).getRemoteCacheRepoKey(repo);
        verifyNoMoreInteractions(repoService);
    }

    @Test
    void testFindArtifactsBySha2Checksum() throws PackageNotFoundException {
        packageSearchService.findArtifactsBySha2Checksum("repo",
                "20cfcf638366719a2978804a936d7eae3d1d199b1d17f810d1a9a8d25aabeefe");
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        String query = aql.getValue().toNative(0);
        System.out.println(query);
        assertTrue(query.contains("{\"repo\":{\"$eq\":\"repo\"}}"), query);
        assertTrue(query.contains(
                "{\"sha256\":{\"$eq\":\"20cfcf638366719a2978804a936d7eae3d1d199b1d17f810d1a9a8d25aabeefe\"}}"), query);
        assertTrue(query.contains("{\"type\":{\"$eq\":\"file\"}"), query);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testFindArtifactsWithPropsIncludeStats(boolean includeStats) throws PackageException {
        packageSearchService.findArtifactsWithProps("repo", null, null, List.of(), includeStats);
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryLazy(aql.capture());
        String query = aql.getValue().toNative(0);
        if (includeStats) {
            assertTrue(query.contains("\"stat.downloads\""), query);
        } else {
            assertFalse(query.contains("\"stat.downloads\""), query);
        }
    }

    @ParameterizedTest
    @MethodSource("provideDbTypesForLowerPropKeyIndexSeek")
    void testSupportsLowerPropKeyIndexSeek(DbType dbType, boolean expectedSupport) {
        when(dbService.getDatabaseType()).thenReturn(dbType);
        assertEquals(expectedSupport, packageSearchService.supportsLowerPropKeyIndexSeek());
    }

    private static Stream<Arguments> provideDbTypesForLowerPropKeyIndexSeek() {
        return Stream.of(
                // Postgres and Oracle keep an index that stays sargable with LOWER(prop_key).
                Arguments.of(DbType.POSTGRESQL, true),
                Arguments.of(DbType.ORACLE, true),
                // MySQL, MariaDB and MSSQL only index the exact prop_key, so it must stay unfolded.
                Arguments.of(DbType.MYSQL, false),
                Arguments.of(DbType.MARIADB, false),
                Arguments.of(DbType.MSSQL, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideMatchModeComparators")
    void testCreatePropCriteriaRendersComparatorPerMatchMode(FilteringProperty.MatchMode mode,
            String expectedComparator) throws PackageException {
        FilteringProperty property = new FilteringProperty("nuget.id", "smart-zip-pro", mode);
        packageSearchService.findArtifactsWithProps("repo", null, null, List.of(property), false);
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryLazy(aql.capture());
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("{\"@nuget.id\":{\"" + expectedComparator + "\":"), query);
    }

    private static Stream<Arguments> provideMatchModeComparators() {
        return Stream.of(
                // Exact match keeps both prop_key and prop_value case-sensitive.
                Arguments.of(FilteringProperty.MatchMode.CASE_SENSITIVE, "$eq"),
                // Fold both key and value.
                Arguments.of(FilteringProperty.MatchMode.CASE_INSENSITIVE, "$eqic"),
                // Keep prop_key exact (sargable), fold only prop_value.
                Arguments.of(FilteringProperty.MatchMode.VALUE_CASE_INSENSITIVE, "$eqvic")
        );
    }

    @Test
    void whenPassingNullAsPackageNameShouldReturnAll() throws PackageException {
        String repoKey = "repoKey";
        List<PackageArtifact> artifacts = List.of(
                PackageArtifact.builder().repoKey(repoKey + 1).name("package1A").build(),
                PackageArtifact.builder().repoKey(repoKey + 1).name("package1B").build(),
                PackageArtifact.builder().repoKey(repoKey + 1).name("package1C").build(),
                PackageArtifact.builder().repoKey(repoKey + 2).name("package2A").build(),
                PackageArtifact.builder().repoKey(repoKey + 2).name("package2B").build(),
                PackageArtifact.builder().repoKey(repoKey + 3).name("package3").build()
        );
        when(metadataEventService.getMetadataClient()).thenThrow(new RuntimeException("error"));
        FilteringProperty property = new FilteringProperty("nuget.id", "propValue");
        mockPopulateRowsToSearchResult(repoKey + 1, artifacts);

        List<PackageArtifact> result =
                packageSearchService.findArtifactsWithProps(repoKey + 1, null, null,
                        List.of(), false).toList();
        assertEquals(3, result.size());
        assertEquals(List.of("package1A", "package1B", "package1C"),
                result.stream().map(PackageArtifact::getName).toList());

        // verify populate was called just once
        verify(packageSearchService, times(1)).populateRowsToSearchResult(any(), anyString());
    }

    //===========================================================================
    //================ReposItemsThreadLocalCache Tests===========================
    //===========================================================================

    @Nested
    class ReposItemsThreadLocalCacheRelatedTests {
        private final String repoKey = "repoKey";
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        ArtifactoryContext artifactoryContext;

        @BeforeEach
        void setup() {
            RepoConfig repoConfig = mock(RepoConfig.class);
            repoService = mock(InternalRepositoryService.class);
            this.artifactoryContext = mock(ArtifactoryContext.class);
            ArtifactoryContextThreadBinder.bind(artifactoryContext);
            when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repoService);
            when(artifactoryContext.beanForType(MeterRegistry.class)).thenReturn(Metrics.globalRegistry);
            when(repoService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
            VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
            when(repoService.virtualRepoDescriptorByKey(repoKey)).thenReturn(virtualRepoDescriptor);
            when(virtualRepoDescriptor.getRepositories()).thenReturn(IntStream.rangeClosed(1, 20)
                    .mapToObj(i -> mock(LocalRepoDescriptor.class))
                    .collect(Collectors.toList()));
            when(repoConfig.getPackageType()).thenReturn(RepoType.Pypi);

            LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
            when(repoService.getLocalRepoConfigByKey(any())).thenReturn(localRepoConfig);
            when(repoService.getLocalOrFederatedRepoConfigByKey(any())).thenReturn(localRepoConfig);
            when(localRepoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(localRepoConfig.getPackageType()).thenReturn(RepoType.Pypi);

            when(repoConfig.getPackageType()).thenReturn(RepoType.Pypi);

            List<RepoDescriptor> repoDescriptors = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
                when(repoDescriptor.getKey()).thenReturn("local-repo-" + i);
                repoDescriptors.add(repoDescriptor);
            }
            when(repoService.getVirtualResolvedLocalFederatedAndCacheDescriptors(repoKey))
                    .thenReturn(repoDescriptors);
        }

        @AfterEach
        void cleanup() {
            ReposItemsThreadLocalCacheService.getVirtualRepoNumberOfLocalRepos().invalidateAll();
            ArtifactoryContextThreadBinder.unbind();
        }

        @Test
        void whenUsingLocalCacheShouldCallDBJustOnce() throws PackageException {
            List<PackageArtifact> artifacts = List.of(
                    PackageArtifact.builder().repoKey(repoKey + 1).name("package1").build(),
                    PackageArtifact.builder().repoKey(repoKey + 2).name("package2").build(),
                    PackageArtifact.builder().repoKey(repoKey + 3).name("package3").build()
            );
            when(metadataEventService.getMetadataClient()).thenThrow(new RuntimeException("error"));
            FilteringProperty property = new FilteringProperty("nuget.id", "propValue");
            ReposItemsThreadLocalCacheService.wrap(repoKey, () -> {
                mockWherePackageExists(List.of(AqlItemMock.builder().repo(repoKey + 1).build(),
                        AqlItemMock.builder().repo(repoKey + 2).build()));
                mockPopulateRowsToSearchResult(null, artifacts);

                List<PackageArtifact> result1 =
                        packageSearchService.findArtifactsWithProps(repoKey + 1, null, property,
                                List.of(), false).toList();
                List<PackageArtifact> result2 =
                        packageSearchService.findArtifactsWithProps(repoKey + 2, null, property,
                                List.of(), false).toList();
                assertEquals(1, result1.size());
                assertEquals("package1", result1.get(0).getName());
                assertEquals(1, result2.size());
                assertEquals("package2", result2.get(0).getName());

                // verify populate was called just once
                verify(packageSearchService, times(1)).populateRowsToSearchResult(any(), anyString());
                return null;
            });
        }

        @Test
        void whenNotUsingLocalCacheShouldCallDBMultipleTimes() throws PackageException {
            List<PackageArtifact> artifacts = List.of(
                    PackageArtifact.builder().repoKey(repoKey + 1).name("package1").build(),
                    PackageArtifact.builder().repoKey(repoKey + 2).name("package2").build(),
                    PackageArtifact.builder().repoKey(repoKey + 3).name("package3").build()
            );
            when(metadataEventService.getMetadataClient()).thenThrow(new RuntimeException("error"));
            FilteringProperty property = new FilteringProperty("nuget.id", "propValue");

            mockWherePackageExists(List.of(AqlItemMock.builder().repo(repoKey + 1).build(),
                    AqlItemMock.builder().repo(repoKey + 2).build()));

            for (int i = 1; i <= 3; i++) {
                mockPopulateRowsToSearchResult(repoKey + i, List.of(artifacts.get(i - 1)));
            }

            List<PackageArtifact> result1 =
                    packageSearchService.findArtifactsWithProps(repoKey + 1, null, property,
                            List.of(), false).toList();
            List<PackageArtifact> result2 =
                    packageSearchService.findArtifactsWithProps(repoKey + 2, null, property,
                            List.of(), false).toList();
            assertEquals(1, result1.size());
            assertEquals("package1", result1.get(0).getName());
            assertEquals(1, result2.size());
            assertEquals("package2", result2.get(0).getName());

            // verify populate was more than once
            verify(packageSearchService, times(2)).populateRowsToSearchResult(any(), anyString());
        }

        @Test
        void whenPackageIsNotInRepoShouldNotSearchThere() throws PackageException {
            List<PackageArtifact> artifacts = List.of(
                    PackageArtifact.builder().repoKey(repoKey + 1).name("package1").build(),
                    PackageArtifact.builder().repoKey(repoKey + 2).name("package2").build(),
                    PackageArtifact.builder().repoKey(repoKey + 3).name("package3").build()
            );
            when(metadataEventService.getMetadataClient()).thenThrow(new RuntimeException("error"));
            FilteringProperty property = new FilteringProperty("nuget.id", "propValue");
            ReposItemsThreadLocalCacheService.wrap(repoKey, () -> {
                mockWherePackageExists(List.of());
                mockPopulateRowsToSearchResult(null, artifacts);

                List<PackageArtifact> result1 =
                        packageSearchService.findArtifactsWithProps(repoKey + 1, null, property,
                                List.of(), false).toList();
                List<PackageArtifact> result2 =
                        packageSearchService.findArtifactsWithProps(repoKey + 2, null, property,
                                List.of(), false).toList();
                assertEquals(0, result1.size());
                assertEquals(0, result2.size());

                // verify populate was not called
                verify(packageSearchService, never()).populateRowsToSearchResult(any(), anyString());
                return null;
            });
        }

        private void mockPopulateRowsToSearchResult(String repoKey, Collection<PackageArtifact> artifacts)
                throws PackageException {
            doReturn(artifacts).when(packageSearchService)
                    .populateRowsToSearchResult(any(AqlApiItem.class), nonNull(repoKey) ? eq(repoKey) : anyString());
        }

        private void mockWherePackageExists(List<AqlItem> results)
                throws PackageException {
            AqlEagerResult result = mock(AqlEagerResult.class);
            doAnswer(invocation -> results).when(result).getResults();
            when(aqlService.executeQueryEager(any(AqlBase.class))).thenReturn(result);
        }

    }

    @Test
    void testFindArtifactsByQuery() {
        String query = "some-query";
        AqlBaseFullRowImpl item = mock(AqlBaseFullRowImpl.class);
        when(item.getNodeId()).thenReturn(1L);
        when(item.getRepo()).thenReturn("repo");
        when(item.getPath()).thenReturn("path/to/artifact");
        when(item.getName()).thenReturn("artifactName");
        when(item.getCreated()).thenReturn(new Date(987654321L));
        when(item.getUpdated()).thenReturn(new Date(987654321L));
        when(item.getModified()).thenReturn(new Date(987654321L));

        AqlEagerResult<AqlItem> result = mock(AqlEagerResult.class);
        when(result.getResults()).thenReturn(List.of(item));
        when(aqlService.executeQueryEager(anyString())).thenReturn(result);

        Stream<PackageArtifact> artifacts = packageSearchService.findArtifactsByQuery(query);
        PackageArtifact expectedArtifact = PackageArtifactMapper.artifact(item);

        assertEquals(expectedArtifact.getName(), artifacts.toList().getFirst().getName());
    }

    @Test
    void testFindArtifactsByPropKeyAndMultiValWithLimitAndOffset() {
        PackageSearchContext searchContext =
                PackageSearchContext.builder().repoKey("repo").path("/a/b/c").offset(1).limit(2).build();
        Multimap<String, String> multiValues = HashMultimap.create();
        multiValues.put("deb.component", "a");
        multiValues.put("deb.component", "b");
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeyAndMultiVal(searchContext, multiValues)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("  {\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"/a/b/c\"}},\n" +
                "      {\"depth\":{\"$gte\":4}},\n" +
                "      {\"$or\":[\n" +
                "        {\"@deb.component\":{\"$eq\":\"a\"}},\n" +
                "        {\"@deb.component\":{\"$eq\":\"b\"}}\n" +
                "      ]},\n" +
                "      {\"type\":{\"$eq\":\"file\"}}\n" +
                "    ]\n" +
                "  }"), query);
        assertTrue(query.contains("offset(1).\n" +
                "limit(2)"));
    }

    @Test
    void testFindArtifactsByPropKeyAndMultiVal() {
        PackageSearchContext searchContext = new PackageSearchContext("repo", "/a/b/c");
        Multimap<String, String> multiValues = HashMultimap.create();
        multiValues.put("deb.component", "a");
        multiValues.put("deb.component", "b");
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeyAndMultiVal(searchContext, multiValues)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("  {\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"/a/b/c\"}},\n" +
                "      {\"depth\":{\"$gte\":4}},\n" +
                "      {\"$or\":[\n" +
                "        {\"@deb.component\":{\"$eq\":\"a\"}},\n" +
                "        {\"@deb.component\":{\"$eq\":\"b\"}}\n" +
                "      ]},\n" +
                "      {\"type\":{\"$eq\":\"file\"}}\n" +
                "    ]\n" +
                "  }"), query);
    }

    @Test
    void testFindArtifactsByPropKeyAndMultiValWithCouplesMultiValsPerKey() {
        PackageSearchContext searchContext = new PackageSearchContext("repo", "/a/b/c");
        Multimap<String, String> multiValues = HashMultimap.create();
        multiValues.put("deb.component.partA", "a");
        multiValues.put("deb.component.partA", "b");
        multiValues.put("deb.component.partB", "c");
        multiValues.put("deb.component.partB", "d");
        List<PackageArtifact> arts =
                packageSearchService.findArtifactsByPropKeyAndMultiVal(searchContext, multiValues)
                        .collect(Collectors.toList());
        ArgumentCaptor<AqlBase> aql = ArgumentCaptor.forClass(AqlBase.class);
        verify(aqlService, times(1)).executeQueryEager(aql.capture());
        assertArts(date, md5, sha, arts);
        String query = aql.getValue().toNative(0);
        assertTrue(query.contains("  {\n" +
                "    \"$and\":[\n" +
                "      {\"repo\":{\"$eq\":\"repo\"}},\n" +
                "      {\"path\":{\"$eq\":\"/a/b/c\"}},\n" +
                "      {\"depth\":{\"$gte\":4}},\n" +
                "      {\"$or\":[\n" +
                "        {\"@deb.component.partA\":{\"$eq\":\"a\"}},\n" +
                "        {\"@deb.component.partA\":{\"$eq\":\"b\"}}\n" +
                "      ]},\n" +
                "      {\"$or\":[\n" +
                "        {\"@deb.component.partB\":{\"$eq\":\"d\"}},\n" +
                "        {\"@deb.component.partB\":{\"$eq\":\"c\"}}\n" +
                "      ]},\n" +
                "      {\"type\":{\"$eq\":\"file\"}}\n" +
                "    ]\n" +
                "  }"), query);
    }

    //===========================================================================
    //================Utils===========================
    //===========================================================================
    private AqlItem aqlItem(Date date, String md5, String sha1, String sha2) {
        return AqlItemMock.builder()
                .path("path")
                .name("name")
                .repo("repo")
                .size(Long.MAX_VALUE)
                .sha2(sha2)
                .actualSha1(sha1)
                .numericCountValue(Long.MAX_VALUE)
                .nodeId(Long.MAX_VALUE)
                .created(date)
                .modified(date)
                .updated(date)
                .createdBy("admin")
                .actualMd5(md5)
                .modifiedBy("admin")
                .originalSha1(sha1)
                .type(AqlItemTypeEnum.file)
                .originalMd5(md5)
                .depth(Integer.MAX_VALUE)
                .virtualRepos(new String[] {"v1", "v2"})
                .build();
    }

    private void assertArts(Date date, String md5, String sha, List<PackageArtifact> arts) {
        assertEquals(1, arts.size());
        PackageArtifact art = arts.iterator().next();
        assertEquals("repo", art.getRepoKey());
        assertEquals("path/name", art.getPath());
        assertEquals(date.getTime(), art.getCreated());
        assertEquals("admin", art.getCreatedBy());
        assertEquals(date.getTime(), art.getLastUpdated());
        assertEquals(date.getTime(), art.getLastModified());
        assertEquals(Long.MAX_VALUE, art.getId());
        assertEquals(Long.MAX_VALUE, art.getLength());
        assertEquals(md5, art.getMd5());
        assertEquals("admin", art.getModifiedBy());
        assertNull(art.getPackageType());
        assertEquals(sha + "12345678", art.getSha1());
        assertEquals(sha + sha, art.getSha2());
        assertEquals("name", art.getName());
    }

    static Stream<Arguments> provideFindArtifactsByExtensionInputs() {
        return Stream.of(
                Arguments.of("repo", "some/path*", "zip", "Parameter 'some/path*' cannot include '*'"),
                Arguments.of("repo", null, "*.zip", "Parameter '*.zip' cannot include '*'"),
                Arguments.of("repo", null, null, "Extension cannot be empty"),
                Arguments.of("repo", null, "", "Extension cannot be empty"),
                Arguments.of("", "", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsChildrenInputs() {
        return Stream.of(
                Arguments.of("repo", "some/path*", "Parameter 'some/path*' cannot include '*'"),
                Arguments.of("repo", "/*", "Parameter '/*' cannot include '*'"),
                Arguments.of("", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsChildrenWithNameInputs() {
        return Stream.of(
                Arguments.of("repo", "some/path*", "", "Parameter 'some/path*' cannot include '*'"),
                Arguments.of("repo", "/*", "", "Parameter '/*' cannot include '*'"),
                Arguments.of("repo", "/", "", "Input cannot be empty"),
                Arguments.of("", "", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsByExactNameInputs() {
        return Stream.of(
                Arguments.of("repo", "", "Input cannot be empty"),
                Arguments.of("repo", "*", "Parameter '*' cannot include '*'"),
                Arguments.of("repo", null, "Input cannot be empty"),
                Arguments.of("repo", "someFile.*", "Parameter 'someFile.*' cannot include '*'"),
                Arguments.of("", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsByExactNameAndPathInputs() {
        return Stream.of(
                Arguments.of("repo", "path", "", "Input cannot be empty"),
                Arguments.of("repo", "path", "*", "Parameter '*' cannot include '*'"),
                Arguments.of("repo", "path/*", "name", "Parameter 'path/*' cannot include '*'"),
                Arguments.of("repo", "", "someFile.*", "Parameter 'someFile.*' cannot include '*'"),
                Arguments.of("", "", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsByPropKeysInputInputs() {
        return Stream.of(
                Arguments.of("repo", "path", "", "PropKey cannot be empty"),
                Arguments.of("repo", "path", "*", "Parameter '*' cannot include '*'"),
                Arguments.of("repo", "path/*", "name", "Parameter 'path/*' cannot include '*'"),
                Arguments.of("repo", "", "deb.*", "Parameter 'deb.*' cannot include '*'"),
                Arguments.of("repo", "", null, "PropKey cannot be empty"),
                Arguments.of("", "", "", "RepoKey cannot be empty")
        );
    }

    static Stream<Arguments> provideFindArtifactsByPropKeyAndValInputs () {
        return Stream.of(
                Arguments.of("repo", "path", "", "", "PropKey cannot be empty"),
                Arguments.of("repo", "path", "*", "", "Parameter '*' cannot include '*'"),
                Arguments.of("repo", "path/*", "name", "", "Parameter 'path/*' cannot include '*'"),
                Arguments.of("repo", "", "deb.*", "", "Parameter 'deb.*' cannot include '*'"),
                Arguments.of("repo", "", "deb.arch", "*", "Parameter '*' is not a legal property value"),
                Arguments.of("", "", "", "", "RepoKey cannot be empty"),
                Arguments.of("repo", "path", null, null, "No properties maps have been provided"),
                Arguments.of("repo", "path", "MultiValAstrix", "*", "Parameter '*' is not a legal property value")
        );
    }




    private void mockPopulateRowsToSearchResult(String repoKey, Collection<PackageArtifact> artifacts)
            throws PackageException {
        ResultSet resultSet = mock(ResultSet.class);
        if (nonNull(repoKey)) {
            artifacts = artifacts.stream().filter(artifact -> artifact.getRepoKey().equals(repoKey)).toList();
        }
        doReturn(artifacts).when(packageSearchService)
                .populateRowsToSearchResult(any(AqlApiItem.class), nonNull(repoKey) ? eq(repoKey) : anyString());
    }

    private void mockWherePackageExists(List<AqlItem> results)
            throws PackageException {
        AqlEagerResult result = mock(AqlEagerResult.class);
        doAnswer(invocation -> results).when(result).getResults();
        when(aqlService.executeQueryEager(any(AqlBase.class))).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void mockExactNameLazyResult(LazySearchRow... rows) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        AtomicInteger rowIndex = new AtomicInteger(-1);
        when(resultSet.next()).thenAnswer(invocation -> rowIndex.incrementAndGet() < rows.length);
        when(resultSet.getString("itemRepo")).thenAnswer(invocation -> rows[rowIndex.get()].repo);
        when(resultSet.getString("itemPath")).thenAnswer(invocation -> rows[rowIndex.get()].path);
        when(resultSet.getString("itemName")).thenAnswer(invocation -> rows[rowIndex.get()].name);
        when(resultSet.getString("itemSha2")).thenAnswer(invocation -> rows[rowIndex.get()].sha2);

        AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        when(lazyResult.getFields()).thenReturn(exactNameSearchAqlFields());
        when(aqlService.executeQueryLazy(any(AqlBase.class))).thenReturn(lazyResult);
    }

    private List<DomainSensitiveField> exactNameSearchAqlFields() {
        return List.of(
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemSha2, List.of(AqlDomainEnum.items)));
    }

    @Getter
    @Builder
    private static class LazySearchRow {
        private String repo;
        private String path;
        private String name;
        @Builder.Default
        private String sha2 = null;
    }


    @Getter
    @Builder
    static class AqlItemMock implements AqlItem {
        private Date created;
        private Date modified;
        private Date updated;
        private String createdBy;
        private String modifiedBy;
        private AqlItemTypeEnum type;
        private String repo;
        private String path;
        private String name;
        private long size;
        private int depth;
        private Long nodeId;
        private String originalMd5;
        private String actualMd5;
        private String originalSha1;
        private String actualSha1;
        private String sha2;
        private String[] virtualRepos;
        private Long numericCountValue;
    }
}