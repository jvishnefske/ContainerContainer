package org.artifactory.search.gavc;

import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.search.gavc.GavcSearchControls;
import org.artifactory.sapi.search.VfsBoolType;
import org.artifactory.sapi.search.VfsDateFieldName;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.search.model.VfsQueryDbImpl;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.StringUtils;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.sapi.search.VfsComparatorType.CONTAINS;
import static org.artifactory.sapi.search.VfsComparatorType.EQUAL;
import static org.artifactory.sapi.search.VfsComparatorType.LOWER_THAN;
import static org.artifactory.sapi.search.VfsComparatorType.LOWER_THAN_EQUAL;
import static org.artifactory.sapi.search.VfsComparatorType.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GavcSearcherTest extends ArtifactoryHomeBoundJUnitTest {

    private GavcSearcher gavcSearcher;

    @Mock
    DbService dbService;

    @BeforeEach
    void setUp() {
        ArtifactoryStorageContext context = mock(ArtifactoryStorageContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        dbService = mock(DbService.class);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(DbType.ORACLE);
        gavcSearcher = spy(GavcSearcher.class);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void doCount() {
        VfsQuery query = mock(VfsQueryDbImpl.class);
        GavcSearchControls controls = mock(GavcSearchControls.class);
        when(controls.getSortDirection()).thenReturn(GavcSearchControls.Direction.NONE);
        when(query.count()).thenReturn(1L);
        doReturn(query).when(gavcSearcher).getBaseQuery(eq(controls), any(), any());
        long count = gavcSearcher.doCount(controls);
        assertEquals(1, count);
    }

    @Test
    void doCountWithSorting() {
        VfsQuery query = mock(VfsQueryDbImpl.class);
        GavcSearchControls controls = mock(GavcSearchControls.class);
        when(controls.getSortDirection()).thenReturn(GavcSearchControls.Direction.ASC);
        when(query.count()).thenReturn(1L);
        doReturn(query).when(gavcSearcher).getBaseQuery(any(), any(), any());
        long count = gavcSearcher.doCount(controls);
        assertEquals(1, count);
    }

    @Test
    void doCountForNotLocal() {
        String errMessage =
                "count not supporting specific gavc flow for remote and virtual repositories, specific should be " +
                        "false";
        GavcSearchControls controls = mock(GavcSearchControls.class);
        when(controls.isSpecific()).thenReturn(true);
        Exception e = assertThrows(IllegalArgumentException.class, () -> gavcSearcher.doCount(controls));
        assertEquals(e.getMessage(), errMessage);
    }

    @ParameterizedTest
    @MethodSource("provideValues")
    void getBaseQuery(Set<String> includedFolderPaths) {
        GavcSearchControls controls = new GavcSearchControls();
        controls.setSelectedRepoForSearch(List.of("maven-local"));
        controls.setIncludedProperty(Pair.of("propKey", "propValue"));
        controls.setIncludedFolderPaths(includedFolderPaths);
        VfsQueryService service = mock(VfsQueryService.class);
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        when(service.createQuery()).thenReturn(vfsQueryDb);
        doReturn(service).when(gavcSearcher).getVfsQueryService();
        gavcSearcher.getBaseQuery(controls, null, "virtual");
        verify(service, times(1)).createQuery();
    }

    @ParameterizedTest
    @MethodSource("getGAVCControls")
    void addNodeNameFilter(String nodeName, GavcSearchControls gavcSearchControls) {
        VfsQuery query = spy(new VfsQueryDbImpl());
        gavcSearcher.addNodeNameFilter(query, gavcSearchControls);
        if (StringUtils.isNotBlank(nodeName)) {
            verify(query, times(1)).name(nodeName);
        } else {
            verify(query, never()).name(any());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void addDatesFilter(long date) {
        VfsQueryDbImpl query = spy(new VfsQueryDbImpl());
        GavcSearchControls gavcSearchControls = new GavcSearchControls();
        gavcSearchControls.setLastModifiedBefore(date);
        gavcSearchControls.setCreateBefore(date);
        gavcSearchControls.setLastDownloadedBefore(date);
        gavcSearchControls.setIncludeRemotes(true);
        gavcSearcher.addDatesFilter(query, gavcSearchControls);

        if (date > 0) {
            verify(query, times(6)).prop(any());
            verify(query, times(4)).comp(LOWER_THAN_EQUAL);
            verify(query, times(2)).nextBool(VfsBoolType.OR);
        } else {
            verify(query, never()).prop(any());
        }

        // Divide non-strict and strict tests
        clearInvocations(query);

        GavcSearcher strictGavcSearcher = new GavcSearcher(true);

        strictGavcSearcher.addDatesFilter(query, gavcSearchControls);

        if (date > 0) {
            verify(query, times(6)).prop(any());
            verify(query, times(4)).comp(LOWER_THAN);
            verify(query, times(2)).nextBool(VfsBoolType.OR);
        } else {
            verify(query, never()).prop(any());
        }
    }

    @Test
    void addSortingTest() {
        VfsQueryDbImpl query = spy(new VfsQueryDbImpl());
        GavcSearchControls gavcSearchControls = new GavcSearchControls();

        // Let's test the case if the property name is blank
        gavcSearcher.addSorting(query, gavcSearchControls, null);
        verify(query, times(0)).orderByDescending(VfsDateFieldName.CREATED.getPropName());
        verify(query, times(0)).orderByAscending(VfsDateFieldName.CREATED.getPropName());

        // Let's test the case where both asc and desc are false
        gavcSearcher.addSorting(query, gavcSearchControls, VfsDateFieldName.CREATED.getPropName());
        verify(query, times(0)).orderByDescending(VfsDateFieldName.CREATED.getPropName());
        verify(query, times(0)).orderByAscending(VfsDateFieldName.CREATED.getPropName());

        // Now let's test the case where asc is true and desc is false
        gavcSearchControls.setSortDirection(GavcSearchControls.Direction.ASC);
        gavcSearcher.addSorting(query, gavcSearchControls, VfsDateFieldName.CREATED.getPropName());
        verify(query, times(1)).orderByAscending(VfsDateFieldName.CREATED.getPropName());

        // Now let's test the case where asc is false and desc is true
        gavcSearchControls.setSortDirection(GavcSearchControls.Direction.DESC);
        gavcSearcher.addSorting(query, gavcSearchControls, VfsDateFieldName.CREATED.getPropName());
        verify(query, times(1)).orderByDescending(VfsDateFieldName.CREATED.getPropName());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithCreatedAndLastDownloaded(boolean includeCreatedBefore) {
        VfsQueryDbImpl query = spy(new VfsQueryDbImpl());
        GavcSearchControls gavcSearchControls = new GavcSearchControls();
        gavcSearchControls.setLastModifiedBefore(0);
        gavcSearchControls.setLastDownloadedBefore(System.currentTimeMillis());
        gavcSearchControls.setIncludeRemotes(true);
        if (includeCreatedBefore) {
            gavcSearchControls.setCreateBefore(System.currentTimeMillis());
        } else {
            gavcSearchControls.setCreateBefore(0);
        }
        gavcSearcher.addDatesFilter(query, gavcSearchControls);

        if (includeCreatedBefore) {
            // CreateBefore ? AND LastDownloadedBefore ? OR LastDownloadedBefore is NULL
            verify(query, times(5)).prop(any());
            verify(query, times(2)).nextBool(VfsBoolType.OR);
            verify(query, times(3)).startGroup();
            verify(query, times(3)).endGroup();
        } else {
            // LastDownloadedBefore ?
            verify(query, times(9)).prop(any());
            verify(query, times(3)).endGroup(VfsBoolType.OR);
            verify(query, times(5)).startGroup();
            verify(query, times(2)).endGroup();
            verify(query, times(2)).endGroup(null);
        }
    }

    @Test
    void testWithoutCreatedAndWithLastDownloadedRemotesExcluded() {
        VfsQuery query = spy(new VfsQueryDbImpl());
        GavcSearchControls controls = new GavcSearchControls();
        controls.setLastDownloadedBefore(System.currentTimeMillis());
        controls.setIncludeRemotes(false);
        gavcSearcher.addDatesFilter(query, controls);

        verify(query, times(2)).prop(VfsDateFieldName.LAST_DOWNLOADED.getPropName());
        verify(query, times(1)).prop(VfsDateFieldName.CREATED.getPropName());
        verify(query, times(2)).comp(LOWER_THAN_EQUAL);
        verify(query, times(1)).comp(NONE);
        verify(query, times(2)).val(controls.getLastDownloadedBefore());
        verify(query, times(1)).nextBool(VfsBoolType.OR);
        verify(query, times(2)).startGroup();
        verify(query, times(2)).endGroup();
    }

    private static Stream<Arguments> getGAVCControls() {
        GavcSearchControls gavcSearchControlsExt = new GavcSearchControls();
        GavcSearchControls gavcSearchControlsClassifier = new GavcSearchControls();
        GavcSearchControls gavcSearchControlsBoth = new GavcSearchControls();
        gavcSearchControlsExt.setExtensions(List.of("ext"));
        gavcSearchControlsClassifier.setClassifier("classifier");
        gavcSearchControlsBoth.setClassifier("classifier");
        gavcSearchControlsBoth.setExtensions(List.of("ext"));
        return Stream.of(
                Arguments.of("", new GavcSearchControls()),
                Arguments.of("*.ext", gavcSearchControlsExt),
                Arguments.of("*-classifier*", gavcSearchControlsClassifier),
                Arguments.of("*-classifier*.ext", gavcSearchControlsBoth)
        );
    }

    @Test
    void testPropertyFilterUsesLikeComparatorForWildcardValue() {
        GavcSearchControls controls = new GavcSearchControls();
        controls.setSelectedRepoForSearch(List.of("maven-local"));
        controls.setIncludedProperty(Pair.of("migration.date", "2026-03-*"));
        VfsQueryService service = mock(VfsQueryService.class);
        VfsQueryDbImpl vfsQueryDb = spy(new VfsQueryDbImpl());
        when(service.createQuery()).thenReturn(vfsQueryDb);
        doReturn(service).when(gavcSearcher).getVfsQueryService();
        gavcSearcher.getBaseQuery(controls, null, null);
        verify(vfsQueryDb, times(1)).comp(CONTAINS);
    }

    @Test
    void testPropertyFilterUsesEqualComparatorForExactValue() {
        GavcSearchControls controls = new GavcSearchControls();
        controls.setSelectedRepoForSearch(List.of("maven-local"));
        controls.setIncludedProperty(Pair.of("shouldBeDeleted", "false"));
        VfsQueryService service = mock(VfsQueryService.class);
        VfsQueryDbImpl vfsQueryDb = spy(new VfsQueryDbImpl());
        when(service.createQuery()).thenReturn(vfsQueryDb);
        doReturn(service).when(gavcSearcher).getVfsQueryService();
        gavcSearcher.getBaseQuery(controls, null, null);
        verify(vfsQueryDb, times(1)).comp(EQUAL);
    }

    private static Stream<Arguments> provideValues() {
        return Stream.of(Arguments.of(Set.of("com/*","org/*","demo")),
                Arguments.of(Set.of()),
                Arguments.of(Set.of("**")));
    }
}