package org.artifactory.api.events;

import org.artifactory.event.EventHintType;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.api.events.EventLogHintManager.getHints;
import static org.artifactory.event.EventHintType.FederatedReplication;
import static org.artifactory.event.EventHintType.PackageIndexExtraInfo;
import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author avishayh
 */
class EventLogHintManagerTest {

    // ThreadLocal is shared across tests running on the same thread - keep tests isolated.
    @AfterEach
    void cleanup() {
        EventLogHintManager.clear();
    }

    @Test
    void putAndRemoveHint() {
        assertNull(getHints(), "starting with null");
        EventLogHintManager.put(FederatedReplication, "val1");
        assertNotNull(getHints(), "hints not null anymore");
        assertEquals(1, getHints().size(), "added hint 1");
        assertTrue(getHints().contains(FederatedReplication.getPrefix() + "val1"));
        EventLogHintManager.put(RemoveRepo, "val2");
        assertEquals(2, getHints().size(), "added different hint 2");
        assertTrue(getHints().contains(RemoveRepo.getPrefix() + "val2"));
        EventLogHintManager.put(RemoveRepo, "val3");
        assertEquals(2, getHints().size(), "try to add same hint 2 a new value");
        assertTrue(getHints().contains(RemoveRepo.getPrefix() + "val3"));
        EventLogHintManager.remove(RemoveRepo);
        assertEquals(1, getHints().size(), "remove hint 2");
        EventLogHintManager.remove(RemoveRepo);
        assertEquals(1, getHints().size(), "try remove same hint 2");
        EventLogHintManager.remove(FederatedReplication);
        assertNull(getHints(), "last hint removed, everything is cleaned");
        EventLogHintManager.remove(FederatedReplication);
        assertNull(getHints(), "calling remove after null is not failing");
    }

    @Test
    void putPathHint() {
        RepoPath path = mock(RepoPath.class);
        when(path.getCanonicalKey()).thenReturn("repo/a/b/c");
        assertNull(getHints(path), "starting with null");

        EventLogHintManager.put(PackageIndexExtraInfo, path, "val1");
        assertNotNull(getHints(path), "hints not null anymore");
        assertEquals(1, getHints(path).size(), "added hint 1");
        assertTrue(getHints(path).contains(PackageIndexExtraInfo.getPrefix() + "val1"));
        EventLogHintManager.put(RemoveRepo, path, "val2");
        assertEquals(2, getHints(path).size(), "added different hint 2");
        assertTrue(getHints(path).contains(RemoveRepo.getPrefix() + "val2"));
        EventLogHintManager.put(RemoveRepo, path, "val3");
        assertEquals(2, getHints(path).size(), "try to add same hint 2 a new value");
        assertTrue(getHints(path).contains(RemoveRepo.getPrefix() + "val3"));
    }

    @Test
    void removeTypeClearsPathEntriesAndCleansUpThreadLocal() {
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.put(PackageIndexExtraInfo, "plainVal");
        EventLogHintManager.put(PackageIndexExtraInfo, path, "pathVal");
        assertEquals(1, getHints().size(), "plain hint present");
        assertEquals(1, getHints(path).size(), "path hint present");

        // remove(type) must clear BOTH the plain hint and the path-scoped entries for that type.
        EventLogHintManager.remove(PackageIndexExtraInfo);
        assertNull(getHints(), "both maps empty -> ThreadLocal removed");
        assertNull(getHints(path), "path hints gone, ThreadLocal removed");
    }

    @Test
    void removeTypeKeepsThreadLocalWhileOtherTypesRemain() {
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.put(FederatedReplication, "plainVal");
        EventLogHintManager.put(PackageIndexExtraInfo, path, "pathVal");

        EventLogHintManager.remove(PackageIndexExtraInfo);
        assertTrue(getHints(path).isEmpty(), "removed path-scoped type (manager still alive -> empty, not null)");
        assertNotNull(getHints(), "FederatedReplication plain hint still present");
        assertTrue(getHints().contains(FederatedReplication.getPrefix() + "plainVal"));
    }

    @Test
    void removeSinglePathLeavesOtherPaths() {
        RepoPath path1 = mockPath("repo", "a/b/c");
        RepoPath path2 = mockPath("repo", "x/y/z");
        EventLogHintManager.put(PackageIndexExtraInfo, path1, "val1");
        EventLogHintManager.put(PackageIndexExtraInfo, path2, "val2");

        EventLogHintManager.remove(PackageIndexExtraInfo, path1);
        assertTrue(getHints(path1).isEmpty(), "path1 hint removed");
        assertEquals(1, getHints(path2).size(), "path2 hint kept");
        assertTrue(getHints(path2).contains(PackageIndexExtraInfo.getPrefix() + "val2"));

        // Removing the last path entry empties both maps and cleans the ThreadLocal.
        EventLogHintManager.remove(PackageIndexExtraInfo, path2);
        assertNull(getHints(path2), "last path removed -> ThreadLocal cleaned");
    }

    @Test
    void removeAllClearsPlainAndPathHintsForType() {
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.put(PackageIndexExtraInfo, "plainVal");
        EventLogHintManager.put(PackageIndexExtraInfo, path, "pathVal");

        EventLogHintManager.removeAll(PackageIndexExtraInfo);
        assertNull(getHints(), "plain hints cleared and ThreadLocal removed");
        assertNull(getHints(path), "path hints cleared and ThreadLocal removed");
    }

    @Test
    void removePathHintsClearsAllTypesForPathLeavingGlobalAndOtherPaths() {
        RepoPath pathA = mockPath("repo", "a/b/c");
        RepoPath pathB = mockPath("repo", "x/y/z");
        EventLogHintManager.put(FederatedReplication, "global");
        EventLogHintManager.put(PackageIndexExtraInfo, pathA, "hintA1");
        EventLogHintManager.put(RemoveRepo, pathA, "hintA2");
        EventLogHintManager.put(PackageIndexExtraInfo, pathB, "hintB");

        EventLogHintManager.removePathHints(pathA.getCanonicalKey());

        // All hints for pathA removed across both types.
        assertTrue(getHints(pathA).isEmpty(), "all type hints for pathA removed");
        // pathB hint for the same type is unaffected.
        assertEquals(1, getHints(pathB).size(), "pathB hint untouched");
        assertTrue(getHints(pathB).contains(PackageIndexExtraInfo.getPrefix() + "hintB"));
        // Global (non-path) hints are untouched.
        assertNotNull(getHints(), "global hints survive");
        assertEquals(1, getHints().size());
        assertTrue(getHints().contains(FederatedReplication.getPrefix() + "global"));
    }

    @Test
    void removePathHintsOnNullManagerDoesNotFail() {
        assertNull(getHints(), "pre-condition: no manager");
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.removePathHints(path.getCanonicalKey());
        assertNull(getHints(), "still null");
    }

    @Test
    void removeWithoutAddDoesNotFail() {
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.remove(PackageIndexExtraInfo);
        EventLogHintManager.remove(PackageIndexExtraInfo, path);
        EventLogHintManager.removeAll(PackageIndexExtraInfo);
        assertNull(getHints(), "still null after no-op removes");
        assertNull(getHints(path), "still null after no-op removes");
    }

    @Test
    void pathKeyNormalizationMatchesDistinctInstances() {
        // Producer puts a hint with one RepoPath instance; consumer queries with a different instance representing
        // the same logical path (e.g. from RepoPathFactory.create). String keys must make them match.
        RepoPath producerPath = mockPath("repo", "a/b/c");
        RepoPath consumerPath = mockPath("repo", "a/b/c");
        assertNotSame(producerPath, consumerPath, "distinct instances");

        EventLogHintManager.put(PackageIndexExtraInfo, producerPath, "val1");
        Set<String> hints = getHints(consumerPath);
        assertNotNull(hints, "matched on canonical string key");
        assertEquals(1, hints.size());
        assertTrue(hints.contains(PackageIndexExtraInfo.getPrefix() + "val1"));
    }

    @Test
    void getValue() {
        Set<String> hints = new HashSet<>();
        hints.add(FederatedReplication.getPrefix() + "value1");
        assertEquals("value1", EventHintType.getValue(hints, FederatedReplication), "find value1");
        assertNull(EventHintType.getValue(null, FederatedReplication), "not failing for null hints");
        assertNull(EventHintType.getValue(hints, RemoveRepo), "cannot find wrong hint");
        hints.add(RemoveRepo.getPrefix() + "value2");
        assertEquals("value2", EventHintType.getValue(hints, RemoveRepo), "find value for different hint");
        //check wrong values doesn't fail method
        Set<String> wrongHintsValues = new HashSet<>();
        wrongHintsValues.add("value1");
        wrongHintsValues.add(null);
        wrongHintsValues.add("");
        assertNull(EventHintType.getValue(wrongHintsValues, FederatedReplication), "wrong values not failing");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Parameterized coverage for removePathHints
    // ---------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(EventHintType.class)
    void removePathHintsRemovesSingleTypePathHintForAnyType(EventHintType type) {
        RepoPath path = mockPath("repo", "a/b/c");
        EventLogHintManager.put(type, path, "val");
        assertEquals(1, getHints(path).size(), "path hint present for type " + type);

        EventLogHintManager.removePathHints(path.getCanonicalKey());
        assertNull(getHints(path), "only hint removed -> ThreadLocal cleaned for type " + type);
    }

    @ParameterizedTest
    @MethodSource("pathTypeCombinations")
    void removePathHintsRemovesAllTypesForPath(Set<EventHintType> types) {
        RepoPath path = mockPath("repo", "a/b/c");
        types.forEach(type -> EventLogHintManager.put(type, path, "v-" + type.name()));
        assertEquals(types.size(), getHints(path).size(), "all path hints present");

        EventLogHintManager.removePathHints(path.getCanonicalKey());
        assertNull(getHints(path), "every type for the path removed");
    }

    static Stream<Set<EventHintType>> pathTypeCombinations() {
        return Stream.of(
                EnumSet.of(PackageIndexExtraInfo),
                EnumSet.of(PackageIndexExtraInfo, RemoveRepo),
                EnumSet.of(PackageIndexExtraInfo, RemoveRepo, EventHintType.EvidenceExtraInfo),
                EnumSet.allOf(EventHintType.class));
    }

    @ParameterizedTest
    @MethodSource("globalHintTypeSets")
    void removePathHintsLeavesGlobalAndOtherPathHints(Set<EventHintType> globalTypes) {
        RepoPath target = mockPath("repo", "a/b/c");
        RepoPath other = mockPath("repo", "x/y/z");
        globalTypes.forEach(type -> EventLogHintManager.put(type, "g-" + type.name()));
        EventLogHintManager.put(PackageIndexExtraInfo, target, "t");
        EventLogHintManager.put(PackageIndexExtraInfo, other, "o");

        EventLogHintManager.removePathHints(target.getCanonicalKey());

        assertTrue(getHints(target).isEmpty(), "target path hints removed");
        assertEquals(1, getHints(other).size(), "other path hint preserved");
        assertEquals(globalTypes.size(), getHints().size(), "global hints untouched");
    }

    static Stream<Set<EventHintType>> globalHintTypeSets() {
        return Stream.of(
                EnumSet.noneOf(EventHintType.class),
                EnumSet.of(FederatedReplication),
                EnumSet.of(FederatedReplication, RemoveRepo));
    }

    @ParameterizedTest
    @CsvSource({
            "repo, a/b/c",
            "repo, single",
            "my-repo, deep/nested/path/file.txt",
            "r2, a",
    })
    void removePathHintsMatchesDistinctEquivalentInstances(String repoKey, String path) {
        RepoPath producer = mockPath(repoKey, path);
        RepoPath consumer = mockPath(repoKey, path);
        EventLogHintManager.put(PackageIndexExtraInfo, producer, "v");
        assertEquals(1, getHints(consumer).size(), "equivalent instance matches by canonical key");

        EventLogHintManager.removePathHints(consumer.getCanonicalKey());
        assertNull(getHints(producer), "removed via an equivalent (distinct) RepoPath instance");
    }

    private static RepoPath mockPath(String repoKey, String path) {
        RepoPath repoPath = mock(RepoPath.class);
        // The manager keys off getCanonicalKey(); a Mockito mock won't run the interface
        // default, so stub it.
        when(repoPath.getCanonicalKey()).thenReturn(repoKey + "/" + path);
        return repoPath;
    }
}
