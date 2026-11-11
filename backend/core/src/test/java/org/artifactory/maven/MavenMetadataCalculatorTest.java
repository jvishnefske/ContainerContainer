package org.artifactory.maven;

import com.jfrog.ph.acl.metrics.PackageIndexMetrics;
import com.jfrog.ph.acl.metrics.PackageIndexMetrics.Trigger;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.maven.snapshot.TimestampSnapshotComparator;
import org.artifactory.maven.versioning.MavenMetadataVersionComparator;
import org.artifactory.maven.versioning.VersionNameMavenMetadataVersionComparator;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.created;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MavenMetadataCalculatorTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private FileService fileService;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    AqlService aqlService;
    private AutoCloseable autoCloseable;
    MavenMetadataCalculator mavenMetadataCalculator;
    RepoPath repoPath;
    @BeforeEach
    void setup() throws SQLException {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());

        repoPath = new RepoPathImpl("mvn-local", "mvn-local:org/example/stam", true);

        ItemInfo itemInfo = mock(FolderInfo.class);
        AqlLazyResult result = mock(AqlLazyResult.class);
        ResultSet resultSet = mock(ResultSet.class);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);

        when(ContextHelper.get().beanForType(FileService.class)).thenReturn(fileService);
        when(itemInfo.isFolder()).thenReturn(true);
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        when(fileService.loadItem(repoPath)).thenReturn(itemInfo);
        when(result.getResultSet()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(aqlService.executeQueryLazy(any(AqlBase.class))).thenReturn(result);
        when(ContextHelper.get().beanForType(AqlService.class)).thenReturn(aqlService);
        when(InternalContextHelper.get().getRepositoryService()).thenReturn(internalRepositoryService);

        mavenMetadataCalculator = spy(new MavenMetadataCalculator(repoPath, false));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset the snapshot-comparator override so tests that set it (custom-comparator cases)
        // don't leak into later tests running in the same JVM under unspecified ordering.
        setStringSystemProperty(ConstantValues.mvnMetadataSnapshotComparator, "");
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void calculate() {
        ArgumentCaptor<AqlApiItem> queryCaptor = ArgumentCaptor.forClass(AqlApiItem.class);
        AqlApiItem exceptedQuery = createWithEmptyResults().distinct(false).filter(
                and(
                        repo().equal("mvn-local"),
                        path().matches("mvn-local:org/example/stam/*"),
                        name().matches("*.pom"),
                        AqlApiItem.depth().equals(5)
                )
        ).include(path(), name(), created());
        mavenMetadataCalculator.calculate();
        verify(aqlService).executeQueryLazy(queryCaptor.capture());
        assertEquals(exceptedQuery.toNative(0), queryCaptor.getValue().toNative(0));
    }

    @Test
    void calculateWithItemNodeRootNull() {
        when(fileService.loadItem(any(RepoPath.class))).thenThrow(new VfsItemNotFoundException("test"));
        mavenMetadataCalculator.calculate();
        verify(mavenMetadataCalculator).removeMetadataIfExist(repoPath);
    }

    @ParameterizedTest
    @MethodSource("getMetadataContent")
    void removeMetadataIfExist(String content, String path) {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        when(InternalContextHelper.get().getRepositoryService()).thenReturn(internalRepositoryService);
        RepoPath repoPath = new RepoPathImpl("mvn-local", path, true);
        List<RepoPathImpl> mavenMetadataPaths = List.of(new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME),
                new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME_SHA512));
        mavenMetadataPaths.forEach(mavenMetadataPath -> {
            when(internalRepositoryService.exists(mavenMetadataPath)).thenReturn(true);
            when(internalRepositoryService.getStringContent(mavenMetadataPath)).thenReturn(content);
            when(internalRepositoryService.undeploy(mavenMetadataPath, false, false)).thenReturn(
                    new BasicStatusHolder());
        });
        mavenMetadataCalculator.removeMetadataIfExist(repoPath);
        verify(internalRepositoryService, times(2)).undeploy(any(), eq(false), eq(false));
    }

    @Test
    void removeMetadataIfExistSnapshot() {
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        when(InternalContextHelper.get().getRepositoryService()).thenReturn(internalRepositoryService);
        RepoPath repoPath = new RepoPathImpl("gradle-local", "gradle-local:org/example/1.0-SNAPSHOT/stam", true);
        List<RepoPathImpl> mavenMetadataPaths = List.of(new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME),
                new RepoPathImpl(repoPath, MavenNaming.MAVEN_METADATA_NAME_SHA512));
        String content =
                ResourceUtils.getResourceAsString("/org/artifactory/metadata/local-maven-metadata-snapshot.xml");
        mavenMetadataPaths.forEach(mavenMetadataPath -> {
            when(internalRepositoryService.exists(mavenMetadataPath)).thenReturn(true);
            when(internalRepositoryService.getStringContent(mavenMetadataPath)).thenReturn(content);
            when(internalRepositoryService.undeploy(mavenMetadataPath, false, false)).thenReturn(
                    new BasicStatusHolder());
        });
        mavenMetadataCalculator.removeMetadataIfExist(repoPath);
        verify(internalRepositoryService, times(0)).undeploy(any(), eq(false), eq(false));
    }

    public static Stream<Arguments> getMetadataContent() {
        return Stream.of(
                Arguments.of("", "mvn-local:org/example/stam"),
                Arguments.of("<>", "mvn-local:org/example/stam"),
                Arguments.of(ResourceUtils.getResourceAsString("/org/artifactory/metadata/local-maven-metadata.xml"),
                        "mvn-local:org/example/stam"),
                Arguments.of(ResourceUtils.getResourceAsString(
                                "/org/artifactory/metadata/local-maven-metadata-snapshot.xml"),
                        "mvn-local:org/example/stam/1.0-SNAPSHOT")
        );
    }

    @Test
    void calculateRecordsDurationTimerWithExpectedTagsForIndex() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MavenMetadataCalculator instrumented = new MavenMetadataCalculator(repoPath, false,
                PackageType.MAVEN, Trigger.INDEX, new PackageIndexMetrics(registry));

        instrumented.calculate();

        Timer timer = registry.get("index.package.duration")
                .tags("packageType", "maven", "repoType", "local", "trigger", "index", "result", "success")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        // Folder has no child versions and no poms, so neither version count nor index size summaries are emitted.
        assertNull(registry.find("index.package.versions.count").summary());
        assertNull(registry.find("index.package.size.bytes").summary());
    }

    @Test
    void calculateRecordsReindexTriggerForRestEndpointPath() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MavenMetadataCalculator instrumented = new MavenMetadataCalculator(repoPath, false,
                PackageType.GRADLE, Trigger.REINDEX, new PackageIndexMetrics(registry));

        instrumented.calculate();

        Timer timer = registry.get("index.package.duration")
                .tags("packageType", "gradle", "repoType", "local", "trigger", "reindex", "result", "success")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void calculateSkipsMetricsWhenPackageIndexMetricsNotProvided() {
        // Legacy 2-arg constructor must remain a no-op for metrics so existing tests / callers are unaffected.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MavenMetadataCalculator legacy = new MavenMetadataCalculator(repoPath, false);

        legacy.calculate();

        assertNull(registry.find("index.package.duration").timer());
    }

    // -------------------------------------------------------------------------
    // Behavior pins for the version-metadata pipeline (RTDEV-74034).
    // These tests stub getSubFoldersContainingPoms with synthetic rows so that
    // the produced maven-metadata.xml can be inspected directly via a saveMetadata
    // spy. They lock down the dedup, ordering, and latest/release semantics that
    // the streamer-fold refactor must preserve.
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withSingleVersion_writesMetadataWithThatVersionAsLatestAndRelease(boolean flagEnabled) {
        List<MavenMetaDataInfo> rows = List.of(pomRow("1.0"));
        Metadata saved = captureSavedMetadata(rows, flagEnabled);

        assertEquals(List.of("1.0"), saved.getVersioning().getVersions());
        assertEquals("1.0", saved.getVersioning().getLatest());
        assertEquals("1.0", saved.getVersioning().getRelease());
    }

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withMultipleVersionsInRandomOrder_writesMetadataSortedAscending(boolean flagEnabled) {
        List<MavenMetaDataInfo> rows = List.of(pomRow("2.0"), pomRow("1.0"), pomRow("3.0"));
        Metadata saved = captureSavedMetadata(rows, flagEnabled);

        assertEquals(List.of("1.0", "2.0", "3.0"), saved.getVersioning().getVersions());
        assertEquals("3.0", saved.getVersioning().getLatest());
        assertEquals("3.0", saved.getVersioning().getRelease());
    }

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withDuplicateVersionRows_dedupsAndWritesEachVersionOnce(boolean flagEnabled) {
        // The OOM/leak pin: many rows per version must collapse to one entry per version
        // (this is exactly the reduction that today's createVersionsMetadata performs after
        // the 400k-row list is built, and that the streamer-fold refactor must preserve).
        // Verified across both flag branches.
        List<MavenMetaDataInfo> rows = List.of(
                pomRow("1.0"),
                pomRow("1.0"),
                pomRow("2.0"),
                pomRow("1.0"),
                pomRow("2.0")
        );
        Metadata saved = captureSavedMetadata(rows, flagEnabled);

        assertEquals(List.of("1.0", "2.0"), saved.getVersioning().getVersions());
        assertEquals("2.0", saved.getVersioning().getLatest());
        assertEquals("2.0", saved.getVersioning().getRelease());
    }

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withMixedSnapshotAndReleaseVersions_setsLatestAndReleaseCorrectly(boolean flagEnabled) {
        List<MavenMetaDataInfo> rows = List.of(
                pomRow("1.0"),
                pomRow("1.1-SNAPSHOT"),
                pomRow("1.2"),
                pomRow("1.3-SNAPSHOT")
        );
        Metadata saved = captureSavedMetadata(rows, flagEnabled);

        assertEquals(List.of("1.0", "1.1-SNAPSHOT", "1.2", "1.3-SNAPSHOT"), saved.getVersioning().getVersions());
        assertEquals("1.3-SNAPSHOT", saved.getVersioning().getLatest());
        assertEquals("1.2", saved.getVersioning().getRelease());
    }

    private Metadata captureSavedMetadata(List<MavenMetaDataInfo> rows, boolean flagEnabled) {
        setStringSystemProperty(ConstantValues.mvnMetadataStreamingDedupEnabled, Boolean.toString(flagEnabled));
        if (flagEnabled) {
            // New path: getSubFoldersContainingPoms returns an already-deduplicated map. Mirror that
            // reduction here so a test feeding raw (possibly duplicate) rows exercises the same
            // observable end-to-end behaviour. Keep-first on same-version matches the default
            // VersionNameMavenMetadataVersionComparator (compare == 0 for equal versions).
            Map<String, MavenMetaDataInfo> distinctVersions = new LinkedHashMap<>();
            for (MavenMetaDataInfo row : rows) {
                distinctVersions.merge(row.getVersion(), row, (existing, incoming) -> existing);
            }
            doReturn(distinctVersions).when(mavenMetadataCalculator).getSubFoldersContainingPoms(any());
        } else {
            // Legacy path: getSubFoldersContainingPomsLegacy returns the raw row list and
            // createVersionsMetadataLegacy performs the dedup downstream.
            doReturn(rows).when(mavenMetadataCalculator).getSubFoldersContainingPomsLegacy(any());
        }
        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
        doReturn(0L).when(mavenMetadataCalculator).saveMetadata(any(RepoPath.class), metadataCaptor.capture());
        mavenMetadataCalculator.calculate();
        return metadataCaptor.getValue();
    }

    private static MavenMetaDataInfo pomRow(String version) {
        MavenMetaDataInfo row = new MavenMetaDataInfo();
        row.setRepo("mvn-local");
        row.setPath("org/example/stam/" + version);
        row.setName("stam-" + version + ".pom");
        row.setCreated(new Date());
        return row;
    }

    // -------------------------------------------------------------------------
    // Coverage for branches not exercised by the behavior-pin tests above.
    // -------------------------------------------------------------------------

    @Test
    void calculate_withSnapshotFolderPath_skipsAqlAndTakesSnapshotBranch() throws SQLException {
        // A *-SNAPSHOT version-folder routes through createSnapshotsMetadata, which walks
        // treeNode.getChildren() rather than running the AQL pom-discovery query. Asserting that
        // executeQueryLazy is never invoked is the cleanest signal that the snapshot branch fired.
        RepoPath snapshotPath = new RepoPathImpl("mvn-local", "mvn-local:org/example/stam/1.0-SNAPSHOT", true);
        ItemInfo snapshotItemInfo = mock(FolderInfo.class);
        when(snapshotItemInfo.isFolder()).thenReturn(true);
        when(snapshotItemInfo.getRepoPath()).thenReturn(snapshotPath);
        when(fileService.loadItem(snapshotPath)).thenReturn(snapshotItemInfo);
        MavenMetadataCalculator snapshotCalculator = spy(new MavenMetadataCalculator(snapshotPath, false));

        snapshotCalculator.calculate();

        verify(aqlService, never()).executeQueryLazy(any(AqlBase.class));
    }

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withInvalidSamplePom_doesNotWriteMavenMetadataXml(boolean flagEnabled) {
        // If the sample pom's path can't be parsed by MavenArtifactInfo (e.g. fewer than 3 path
        // elements for groupId/artifactId/version), createVersionsMetadata returns a
        // (containsMetadataInfo=true, -1, 0) result without writing the XML. Verified across
        // both flag branches.
        setStringSystemProperty(ConstantValues.mvnMetadataStreamingDedupEnabled, Boolean.toString(flagEnabled));
        MavenMetaDataInfo invalidRow = invalidPomRow();
        if (flagEnabled) {
            doReturn(Map.of(invalidRow.getVersion(), invalidRow))
                    .when(mavenMetadataCalculator).getSubFoldersContainingPoms(any());
        } else {
            doReturn(List.of(invalidRow))
                    .when(mavenMetadataCalculator).getSubFoldersContainingPomsLegacy(any());
        }

        mavenMetadataCalculator.calculate();

        verify(mavenMetadataCalculator, never()).saveMetadata(any(RepoPath.class), any());
    }

    @Test
    void getSubFoldersContainingPoms_nonFolderTreeNode_returnsEmptyMapWithoutRunningQuery() {
        ItemNode nonFolderNode = mock(ItemNode.class);
        when(nonFolderNode.isFolder()).thenReturn(false);
        when(nonFolderNode.getRepoPath()).thenReturn(repoPath);

        Map<String, MavenMetaDataInfo> result = mavenMetadataCalculator.getSubFoldersContainingPoms(nonFolderNode);

        assertEquals(Map.of(), result);
        verify(aqlService, never()).executeQueryLazy(any(AqlBase.class));
    }

    @Test
    void getSubFoldersContainingPomsLegacy_nonFolderTreeNode_returnsEmptyListWithoutRunningQuery() {
        ItemNode nonFolderNode = mock(ItemNode.class);
        when(nonFolderNode.isFolder()).thenReturn(false);
        when(nonFolderNode.getRepoPath()).thenReturn(repoPath);

        List<MavenMetaDataInfo> result =
                mavenMetadataCalculator.getSubFoldersContainingPomsLegacy(nonFolderNode);

        assertEquals(List.of(), result);
        verify(aqlService, never()).executeQueryLazy(any(AqlBase.class));
    }

    private static MavenMetaDataInfo invalidPomRow() {
        // Path with only one element, so MavenArtifactInfo.fromRepoPath bails with pathElements.size() < 3.
        MavenMetaDataInfo row = new MavenMetaDataInfo();
        row.setRepo("mvn-local");
        row.setPath("1.0");
        row.setName("stam-1.0.pom");
        row.setCreated(new Date());
        return row;
    }

    @Test
    void collectDistinctVersionsFromStreamer_dedupsByVersion_keepsFirstWhenComparatorReturnsZero() {
        // Default version comparator returns 0 for equal version strings, so the keep-first branch
        // of the if-condition (compare > 0 == false) is exercised by the duplicate "1.0" row below.
        @SuppressWarnings("unchecked")
        AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer = mock(AqlLazyObjectResultStreamer.class);
        MavenMetaDataInfo firstOneOh = pomRow("1.0");
        MavenMetaDataInfo twoOh = pomRow("2.0");
        MavenMetaDataInfo secondOneOh = pomRow("1.0");
        when(streamer.getRow()).thenReturn(firstOneOh, twoOh, secondOneOh, null);

        Map<String, MavenMetaDataInfo> result = mavenMetadataCalculator.collectDistinctVersionsFromStreamer(
                streamer, "mvn-local", VersionNameMavenMetadataVersionComparator.get());

        assertEquals(2, result.size());
        // First 1.0 wins because the default comparator returns 0 for equal versions.
        assertEquals(firstOneOh, result.get("1.0"));
        assertEquals(twoOh, result.get("2.0"));
    }

    @Test
    void collectPomsFromStreamer_collectsAllRowsInOrderAndStampsRepoKey() {
        // Covers the legacy streamer loop body: row.setRepo() and items.add() for every row.
        @SuppressWarnings("unchecked")
        AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer = mock(AqlLazyObjectResultStreamer.class);
        MavenMetaDataInfo r1 = pomRow("1.0");
        MavenMetaDataInfo r2 = pomRow("2.0");
        MavenMetaDataInfo r3 = pomRow("1.0");
        r1.setRepo(null);
        r2.setRepo(null);
        r3.setRepo(null);
        when(streamer.getRow()).thenReturn(r1, r2, r3, null);

        List<MavenMetaDataInfo> result = mavenMetadataCalculator.collectPomsFromStreamer(streamer, "mvn-local");

        assertEquals(List.of(r1, r2, r3), result);
        assertEquals("mvn-local", r1.getRepo());
        assertEquals("mvn-local", r2.getRepo());
        assertEquals("mvn-local", r3.getRepo());
    }

    @Test
    void collectDistinctVersionsFromStreamer_replacesEntryWhenComparatorReturnsPositive() {
        // Cover the second arm of the dedup if-condition: current != null && compare > 0.
        @SuppressWarnings("unchecked")
        AqlLazyObjectResultStreamer<MavenMetaDataInfo> streamer = mock(AqlLazyObjectResultStreamer.class);
        MavenMetaDataInfo first = pomRow("1.0");
        MavenMetaDataInfo second = pomRow("1.0");
        when(streamer.getRow()).thenReturn(first, second, null);

        MavenMetadataVersionComparator alwaysGreater = (a, b) -> 1;
        Map<String, MavenMetaDataInfo> result = mavenMetadataCalculator.collectDistinctVersionsFromStreamer(
                streamer, "mvn-local", alwaysGreater);

        assertEquals(1, result.size());
        assertEquals(second, result.get("1.0"));
    }

    @ParameterizedTest(name = "flag={0}")
    @ValueSource(booleans = {true, false})
    void calculate_withNoPomsInVersionFolder_doesNotWriteMetadataAndAllowsRemoval(boolean flagEnabled) {
        // Covers the empty-poms early-return arms of calculateVersionsMetadata for both flag branches.
        // Drives the real getSubFoldersContainingPoms / *Legacy end-to-end (no stub) so the method
        // bodies exercise their AQL+streamer path against the default empty-resultSet mock.
        setStringSystemProperty(ConstantValues.mvnMetadataStreamingDedupEnabled, Boolean.toString(flagEnabled));

        mavenMetadataCalculator.calculate();

        verify(mavenMetadataCalculator, never()).saveMetadata(any(RepoPath.class), any());
        // EMPTY.containsMetadataInfo() == false, so removeMetadataIfExist fires for the repo path.
        verify(mavenMetadataCalculator).removeMetadataIfExist(any(RepoPath.class));
    }

    // -------------------------------------------------------------------------
    // getLatestUniqueSnapshotPomName (RTDEV-90367).
    // The fast path (BuildNumberSnapshotComparator, the production default) parses each file
    // name exactly once via MavenNaming.tryParseUniqueSnapshot. The custom-comparator path
    // (mvn.metadataSnapshotComparatorFqn override) preserves pluggable ordering with an O(n)
    // max-by-comparator pass. These tests pin both branches end-to-end.
    // -------------------------------------------------------------------------

    @Test
    void getLatestUniqueSnapshotPomName_withEmptyInput_returnsNull() {
        assertNull(mavenMetadataCalculator.getLatestUniqueSnapshotPomName(Collections.emptyList()));
    }

    @Test
    void getLatestUniqueSnapshotPomName_withMixedChildren_returnsHighestBuildNumberPom() {
        // Default comparator (BuildNumberSnapshotComparator) is selected — fast path is exercised.
        // Mix of valid unique snapshots, a non-unique snapshot, and metadata file. The winner
        // is the unique-snapshot pom with the largest build number.
        ItemNode pom1 = uniqueSnapshotNode("artifact-1.0-20081214.090217-1.pom");
        ItemNode pom7 = uniqueSnapshotNode("artifact-1.0-20081214.110000-7.pom");
        ItemNode pom3 = uniqueSnapshotNode("artifact-1.0-20081214.100000-3.pom");
        ItemNode nonUnique = uniqueSnapshotNode("artifact-1.0-SNAPSHOT.pom");
        ItemNode metadata = uniqueSnapshotNode(MavenNaming.MAVEN_METADATA_NAME);

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(pom1, pom7, pom3, nonUnique, metadata));

        assertEquals("artifact-1.0-20081214.110000-7.pom", latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_withOnlyNonUniqueSnapshotChildren_returnsNull() {
        // No item matches MavenNaming.tryParseUniqueSnapshot — result must be null in both
        // branches (here, the default fast path).
        ItemNode nonUnique1 = uniqueSnapshotNode("artifact-1.0-SNAPSHOT.pom");
        ItemNode nonUnique2 = uniqueSnapshotNode("artifact-1.0-SNAPSHOT.jar");
        ItemNode metadata = uniqueSnapshotNode(MavenNaming.MAVEN_METADATA_NAME);

        assertNull(mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(nonUnique1, nonUnique2, metadata)));
    }

    @Test
    void getLatestUniqueSnapshotPomName_withNullItemInIterable_isTolerated() {
        // Defensive: the fast path must skip null entries instead of throwing — keeps existing
        // callers (createSnapshotsMetadata) safe against any upstream filter returning nulls.
        ItemNode pom = uniqueSnapshotNode("artifact-1.0-20081214.090217-2.pom");

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(null, pom, null));

        assertEquals("artifact-1.0-20081214.090217-2.pom", latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_withSingleUniqueSnapshot_returnsThatName() {
        ItemNode pom = uniqueSnapshotNode("artifact-1.0-20081214.090217-9.pom");

        assertEquals("artifact-1.0-20081214.090217-9.pom",
                mavenMetadataCalculator.getLatestUniqueSnapshotPomName(Collections.singletonList(pom)));
    }

    @Test
    void getLatestUniqueSnapshotPomName_withCustomTimestampComparator_picksTimestampNewest() {
        // Custom-comparator path: when mvn.metadataSnapshotComparatorFqn points to
        // TimestampSnapshotComparator, the pluggable comparator wins. Pin that the generic
        // max-by-comparator branch returns the timestamp-newest item even when the
        // build-number ordering would disagree.
        setStringSystemProperty(ConstantValues.mvnMetadataSnapshotComparator,
                TimestampSnapshotComparator.class.getName());

        // Larger build number BUT older timestamp.
        ItemNode olderTimestampHigherBuild =
                uniqueSnapshotNode("artifact-1.0-20081214.090000-99.pom");
        // Smaller build number BUT newer timestamp — TimestampSnapshotComparator picks this one.
        ItemNode newerTimestampLowerBuild =
                uniqueSnapshotNode("artifact-1.0-20081214.230000-2.pom");

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(olderTimestampHigherBuild, newerTimestampLowerBuild));

        assertEquals("artifact-1.0-20081214.230000-2.pom", latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_withCustomTimestampComparatorAndMetadataChild_skipsMetadata() {
        // Generic path's filter (MavenNaming.isUniqueSnapshotFileName) must drop the metadata
        // child so we don't pass a non-unique name to the custom comparator (which would NPE
        // when parsing the timestamp).
        setStringSystemProperty(ConstantValues.mvnMetadataSnapshotComparator,
                TimestampSnapshotComparator.class.getName());
        ItemNode pom = uniqueSnapshotNode("artifact-1.0-20081214.090217-2.pom");
        ItemNode metadata = uniqueSnapshotNode(MavenNaming.MAVEN_METADATA_NAME);

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(pom, metadata));

        assertEquals("artifact-1.0-20081214.090217-2.pom", latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_returnsTheActualItemNodesName_notTheParsedTimestampAndBuildNumber() {
        // Guard against future refactors that return a reconstructed string — the production
        // caller (createSnapshotsMetadata) feeds the returned name straight into
        // MavenNaming.getUniqueSnapshotVersionBuildNumber + getUniqueSnapshotVersionTimestamp,
        // so it MUST be the original file name verbatim.
        ItemNode pom = uniqueSnapshotNode("artifact-1.0-20081214.090217-4.pom");

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Collections.singletonList(pom));

        assertSame(pom.getName(), latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_withTiedBuildNumbers_returnsLastEncountered() {
        // On equal build numbers the previous stable-sort + get(size - 1) returned the LAST tied
        // item in iteration order. The single-pass fast path must preserve that last-wins semantic.
        ItemNode firstTie = uniqueSnapshotNode("artifact-1.0-20081214.090000-5.pom");
        ItemNode secondTie = uniqueSnapshotNode("artifact-1.0-20081214.100000-5.pom");

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(firstTie, secondTie));

        assertEquals("artifact-1.0-20081214.100000-5.pom", latest);
    }

    @Test
    void getLatestUniqueSnapshotPomName_withCustomComparatorTie_returnsLastEncountered() {
        // TimestampSnapshotComparator returns 0 for two poms sharing a timestamp (build number is
        // ignored). The generic max-by-comparator pass must keep last-wins semantics on a tie.
        setStringSystemProperty(ConstantValues.mvnMetadataSnapshotComparator,
                TimestampSnapshotComparator.class.getName());
        ItemNode firstTie = uniqueSnapshotNode("artifact-1.0-20081214.090217-1.pom");
        ItemNode secondTie = uniqueSnapshotNode("artifact-1.0-20081214.090217-2.pom");

        String latest = mavenMetadataCalculator.getLatestUniqueSnapshotPomName(
                Arrays.asList(firstTie, secondTie));

        assertEquals("artifact-1.0-20081214.090217-2.pom", latest);
    }

    private static ItemNode uniqueSnapshotNode(String fileName) {
        ItemNode node = mock(ItemNode.class);
        when(node.getName()).thenReturn(fileName);
        return node;
    }
}