package org.artifactory.storage.db.event.service;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventHintType;
import org.artifactory.event.EventType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.FederationClientService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.event.Checksum;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.event.EventsService;
import org.artifactory.storage.event.ShiftEventsService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.filterEventsOfVirtualLocalRepository;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.artifactory.util.RepoPathUtils.removeDashCache;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author shaharz
 */
@Test
public class EventsSessionResourceTest {

    private ShiftEventsService shiftEventsService;
    private EventsService eventsService;

    private FederationClientService federationClientService;
    private InternalRepositoryConfigService repositoryConfigService;
    private LeadArtifactDetectorAddon leadArtifactDetectorAddon;

    @BeforeMethod
    private void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        shiftEventsService = mock(ShiftEventsService.class);
        eventsService = mock(EventsService.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        leadArtifactDetectorAddon = mock(LeadArtifactDetectorAddon.class);
        when(leadArtifactDetectorAddon.isLeadArtifact(any(), any())).thenReturn(true);
        when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        when(context.beanForType(ShiftEventsService.class)).thenReturn(shiftEventsService);
        when(context.beanForType(EventsService.class)).thenReturn(eventsService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        federationClientService = spy(FederationClientService.class);
        repositoryConfigService = mock(InternalRepositoryConfigService.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repositoryConfigService.getRepoConfigByKey(any())).thenReturn(repoConfig);
        when(repositoryConfigService.getRepoConfigByKey(any(), eq(true))).thenReturn(repoConfig);
        when(context.beanForType(FederationClientService.class)).thenReturn(federationClientService);
        when(context.beanForType(InternalRepositoryConfigService.class)).thenReturn(repositoryConfigService);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = Mockito.mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        when(properties.getBooleanProperty(filterEventsOfVirtualLocalRepository)).thenReturn(true);
        ArtifactoryHome.bind(artifactoryHome);

    }

    @AfterMethod
    private void destroy() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        EventLogHintManager.clear();
    }

    @Test
    public void testBeforeCommit() {
        EventLogHintManager.put(EventHintType.FederatedReplication, "bla");
        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("repo1", "/a/b"));
        session.itemCreated(item);
        session.beforeCommit();

        ArgumentCaptor<List<EventInfo>> events = ArgumentCaptor.forClass(List.class);
        Mockito.verify(eventsService, Mockito.times(1)).appendEvents(events.capture());

        Assert.assertEquals(events.getValue().size(), 1);
        Assert.assertEquals(events.getValue().get(0).getPath(), "repo1/a/b");
        Assert.assertEquals(events.getValue().get(0).getType(), EventType.create);
        Assert.assertEquals(events.getValue().get(0).getHints().size(), 1);
    }

    @Test
    public void testBeforeCommitExcludeTrashEvents() {
        RepoConfig dockerRepoConfig = mock(RepoConfig.class);
        when(dockerRepoConfig.getPackageType()).thenReturn(RepoType.Docker);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem trashEvent = getVfsItem(TRASH_KEY, "/a/b", dockerRepoConfig);
        session.itemCreated(trashEvent);
        session.beforeCommit();

        Mockito.verify(eventsService, never()).appendEvents(any());
    }

    @Test
    public void testBeforeCommitExcludeVirtualLocalRepoEvents() {
        RepoConfig virtualRepoConfig = mock(RepoConfig.class);
        when(virtualRepoConfig.isReal()).thenReturn(false);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem event = getVfsItem("repo-virtual", "/a/b", virtualRepoConfig);
        session.itemCreated(event);
        session.beforeCommit();

        Mockito.verify(eventsService, never()).appendEvents(any());
    }


    @Test
    public void testBeforeCommitExcludeRemoteEvents() {
        RepoConfig dockerRepoConfig = mock(RemoteRepoConfig.class);
        when(dockerRepoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(dockerRepoConfig.isReal()).thenReturn(true);

        EventsSessionResource session = new EventsSessionResource();
        when(leadArtifactDetectorAddon.isLeadArtifact(any(), any())).thenReturn(true);
        VfsItem remoteCacheEvent = getVfsItem("remote-cache", "/a/lead", dockerRepoConfig);
        session.itemCreated(remoteCacheEvent);
        session.beforeCommit();
        Mockito.verify(eventsService).appendEvents(any());

        Mockito.reset(eventsService);
        when(leadArtifactDetectorAddon.isLeadArtifact(any(), any())).thenReturn(false);
        remoteCacheEvent = getVfsItem("remote-cache", "/a/not-lead", dockerRepoConfig);
        session.itemCreated(remoteCacheEvent);
        session.beforeCommit();
        Mockito.verify(eventsService, never()).appendEvents(any());

        when(dockerRepoConfig.isReal()).thenReturn(false);
        when(leadArtifactDetectorAddon.isLeadArtifact(any(), any())).thenReturn(true);
        remoteCacheEvent = getVfsItem("virtual-cache", "/a/lead", dockerRepoConfig);
        session.itemCreated(remoteCacheEvent);
        session.beforeCommit();
        Mockito.verify(eventsService, never()).appendEvents(any());
    }

    @Test
    public void testBeforeCommitExcludeDockerUploadEvents() {
        RepoConfig dockerRepoConfig = mock(RepoConfig.class);
        when(dockerRepoConfig.getPackageType()).thenReturn(RepoType.Docker);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem dockerUploadEvent = getVfsItem("docker-local", "/a/b/_uploads/manifest.json", dockerRepoConfig);
        session.itemCreated(dockerUploadEvent);
        session.beforeCommit();

        Mockito.verify(eventsService, never()).appendEvents(any());
    }

    @Test
    public void testBeforeCommitIncludeDockerCacheIndexEvents() {
        RepoConfig dockerRepoConfig = mock(RepoConfig.class);
        when(dockerRepoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(dockerRepoConfig.isReal()).thenReturn(true);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem dockerCacheIndexEvent =
                getVfsItem("docker-remote-cache", "/a/b/c/list.manifest.json", dockerRepoConfig);
        session.itemCreated(dockerCacheIndexEvent);
        session.beforeCommit();

        Mockito.verify(eventsService).appendEvents(any());
    }

    @Test
    public void testBeforeCommitExcludeHuggingFaceUploadEvents() {
        RepoConfig hfRepoConfig = mock(RepoConfig.class);
        when(hfRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem huggingFaceUploadEvent = getVfsItem("hf-local", "/a/b/_uploads/model-file.py", hfRepoConfig);
        session.itemCreated(huggingFaceUploadEvent);
        session.beforeCommit();

        Mockito.verify(eventsService, never()).appendEvents(any());
    }

    @Test
    public void testBeforeCommitIncludeHuggingFaceCacheEvents() {
        RepoConfig hfRepoConfig = mock(RepoConfig.class);
        when(hfRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);
        when(hfRepoConfig.isReal()).thenReturn(true);

        EventsSessionResource session = new EventsSessionResource();

        VfsItem hfCacheIndexEvent =
                getVfsItem("hf-local", "/a/b/c/model-file.py", hfRepoConfig);
        session.itemCreated(hfCacheIndexEvent);
        session.beforeCommit();

        Mockito.verify(eventsService).appendEvents(any());
    }

    @Test
    public void testBeforeCommitIncludeDeleteRepoEvents() {
        EventsSessionResource session = new EventsSessionResource();

        VfsItem deletedRepoEvent = getVfsItem("generic-local", "", null);
        session.itemCreated(deletedRepoEvent);
        session.beforeCommit();

        Mockito.verify(eventsService).appendEvents(any());
    }

    @Nonnull
    private VfsItem getVfsItem(String repoKey, String path, RepoConfig repoConfig) {
        VfsItem eventItem = mock(VfsItem.class);
        when(eventItem.getRepoPath()).thenReturn(new RepoPathImpl(repoKey, path));
        when(repositoryConfigService.getRepoConfigByKey(removeDashCache(repoKey))).thenReturn(repoConfig);
        when(repositoryConfigService.getRepoConfigByKey(removeDashCache(repoKey), true)).thenReturn(repoConfig);
        return eventItem;
    }

    @Test
    public void testCreateEvent() {
        RepoPath path = new RepoPathImpl("repo1", "/a/b");
        RepoPath path2 = new RepoPathImpl("repo1", "/c/d");
        EventLogHintManager.put(EventHintType.FederatedReplication, "bla");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "hint1");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path2, "hint2");

        EventsSessionResource session = new EventsSessionResource();
        Stream.of(path, path2).forEach(itemPath -> {
            VfsItem item = mock(VfsItem.class);
            when(item.getRepoPath()).thenReturn(itemPath);
            session.itemCreated(item);
        });
        session.beforeCommit();

        ArgumentCaptor<List<EventInfo>> events = ArgumentCaptor.forClass(List.class);
        Mockito.verify(eventsService, Mockito.times(1)).appendEvents(events.capture());

        Assert.assertEquals(events.getValue().size(), 2);
        Assert.assertEquals(events.getValue().get(0).getPath(), "repo1/a/b");
        Assert.assertEquals(events.getValue().get(0).getType(), EventType.create);
        Assert.assertEquals(events.getValue().get(0).getHints().size(), 2);
        Assert.assertEquals(events.getValue().get(1).getHints().size(), 2);
        Assert.assertEquals(events.getValue().get(0).getHints().toArray()[1], "pie.hint1");
        Assert.assertEquals(events.getValue().get(1).getHints().toArray()[1], "pie.hint2");
    }

    @Test
    public void testSamePathMultipleEventTypesAllReceiveHintThenRemoved() {
        // A single path can emit multiple events (create + props) in one transaction -> distinct EventInfo since
        // equals includes type. Every one must carry the path hint; removal must happen only after all are built.
        RepoPath path = new RepoPathImpl("repo1", "a/b");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "hint1");

        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(path);
        session.itemCreated(item);
        session.propertiesModified(item);
        session.beforeCommit();

        ArgumentCaptor<List<EventInfo>> events = ArgumentCaptor.forClass(List.class);
        Mockito.verify(eventsService, Mockito.times(1)).appendEvents(events.capture());

        Assert.assertEquals(events.getValue().size(), 2, "create + props events for the same path");
        String expectedHint = EventHintType.PackageIndexExtraInfo.getPrefix() + "hint1";
        for (EventInfo event : events.getValue()) {
            Assert.assertTrue(event.getHints().contains(expectedHint),
                    "every same-path event must carry the path hint, got: " + event.getHints());
        }
        session.afterConnectionReleased(true);
        Assert.assertNull(EventLogHintManager.getHints(path), "path hints drained after commit");
    }

    @Test
    public void testMultiEventTransactionPathHintsRemovedPerEvent() {
        RepoPath pathA = new RepoPathImpl("repo1", "a/b");
        RepoPath pathB = new RepoPathImpl("repo1", "c/d");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, pathA, "hintA");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, pathB, "hintB");

        EventsSessionResource session = new EventsSessionResource();
        VfsItem itemA = mock(VfsItem.class);
        VfsItem itemB = mock(VfsItem.class);
        when(itemA.getRepoPath()).thenReturn(pathA);
        when(itemB.getRepoPath()).thenReturn(pathB);
        session.itemCreated(itemA);
        session.itemCreated(itemB);

        session.beforeCommit();
        session.afterConnectionReleased(true);

        // Each distinct path is drained independently after commit.
        Assert.assertNull(EventLogHintManager.getHints(pathA), "path A hints removed after commit");
        Assert.assertNull(EventLogHintManager.getHints(pathB), "path B hints removed after commit");
    }

    @Test
    public void testGlobalHintsUnchangedWhenPathHintsConsumed() {
        RepoPath path = new RepoPathImpl("repo1", "a/b");
        EventLogHintManager.put(EventHintType.FederatedReplication, "global");
        EventLogHintManager.put(EventHintType.ReplicationAllowed, "global2");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "path");

        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(path);
        session.itemCreated(item);
        session.beforeCommit();
        session.afterConnectionReleased(true);

        // Path hint consumed, both global hints must remain intact (manager alive -> empty set, not null).
        Assert.assertTrue(EventLogHintManager.getHints(path).isEmpty(), "path hint removed");
        Assert.assertNotNull(EventLogHintManager.getHints(), "global hints intact");
        Assert.assertEquals(EventLogHintManager.getHints().size(), 2, "both global hints still present");
    }

    @Test
    public void testBeforeCommitIfRtfsReturnFederatedArtifactInfo() {
        EventLogHintManager.put(EventHintType.FederatedReplication, "bla");
        HashMap<String, Collection<String>> properties = new HashMap<>();
        Collection<String> propertiesValue = new HashSet<>();
        propertiesValue.add("valu1");
        propertiesValue.add("valu2");
        String key = "key";
        properties.put(key, propertiesValue);
        String repoKey = "repo1";
        long length = 4324L;
        Checksum checksum = new Checksum("dsa", "4f32", "3fd4");
        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("repo1", "/a/b"));
        session.itemCreated(item);
        session.beforeCommit();
    }


    @Test(dataProvider = "AfterConnectionReleasedProvider")
    public void testAfterConnectionReleasedWithEvents(boolean committed, boolean hasEvents, int expectedShifts) {
        EventLogHintManager.put(EventHintType.FederatedReplication, "bla");
        EventsSessionResource session = new EventsSessionResource();

        if (hasEvents) {
            VfsItem item = mock(VfsItem.class);
            when(item.getRepoPath()).thenReturn(new RepoPathImpl("repo1", "/a/b"));
            session.itemCreated(item);
            session.beforeCommit();
        }
        session.afterConnectionReleased(committed);
        Mockito.verify(shiftEventsService, Mockito.times(expectedShifts)).triggerShift();
    }

    @DataProvider(name = "AfterConnectionReleasedProvider")
    private Object[][] testAfterConnectionReleasedProvider() {
        return new Object[][] {
                {false, false, 0},
                {false, true, 0},
                {true, false, 0},
                {true, true, 1}
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Parameterized coverage for consume-and-remove at commit
    // ---------------------------------------------------------------------------------------------------------------

    @DataProvider(name = "commitOutcome")
    private Object[][] commitOutcome() {
        return new Object[][] {
                {true, true},    // committed -> path hints drained
                {false, false},  // rolled back -> path hints kept (retry may need them)
        };
    }

    @Test(dataProvider = "commitOutcome")
    public void pathHintsDrainedOnlyOnCommit(boolean committed, boolean expectRemoved) {
        RepoPath path = new RepoPathImpl("repo1", "a/b");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "hint");

        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(path);
        session.itemCreated(item);
        session.beforeCommit();
        session.afterConnectionReleased(committed);

        if (expectRemoved) {
            Assert.assertNull(EventLogHintManager.getHints(path), "path hints drained after commit");
        } else {
            Assert.assertNotNull(EventLogHintManager.getHints(path), "path hints kept after rollback");
            Assert.assertEquals(EventLogHintManager.getHints(path).size(), 1, "hint intact after rollback");
        }
    }

    @DataProvider(name = "eventTypes")
    private Object[][] eventTypes() {
        return new Object[][] {
                {EventType.create},
                {EventType.update},
                {EventType.props},
                {EventType.delete},
        };
    }

    @Test(dataProvider = "eventTypes")
    public void pathHintDrainedForAnyEventType(EventType type) {
        RepoPath path = new RepoPathImpl("repo1", "a/b");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "hint");

        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(path);
        when(item.isFile()).thenReturn(true);
        switch (type) {
            case create -> session.itemCreated(item);
            case update -> session.itemUpdated(item);
            case props -> session.propertiesModified(item);
            case delete -> session.itemDeleted(item);
            default -> Assert.fail("unhandled event type " + type);
        }
        session.beforeCommit();
        session.afterConnectionReleased(true);

        Assert.assertNull(EventLogHintManager.getHints(path),
                "path hint drained after commit for event type " + type);
    }

    @DataProvider(name = "eventPathCounts")
    private Object[][] eventPathCounts() {
        return new Object[][] {{1}, {2}, {5}};
    }

    @Test(dataProvider = "eventPathCounts")
    public void onlyConsumedEventPathsDrainedAfterCommit(int pathCount) {
        EventsSessionResource session = new EventsSessionResource();
        List<RepoPath> eventPaths = new ArrayList<>();
        for (int i = 0; i < pathCount; i++) {
            RepoPath p = new RepoPathImpl("repo1", "p" + i + "/file");
            eventPaths.add(p);
            EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, p, "h" + i);
            VfsItem item = mock(VfsItem.class);
            when(item.getRepoPath()).thenReturn(p);
            session.itemCreated(item);
        }
        // A path with a hint but no event in this transaction must NOT be drained (it was never consumed).
        RepoPath orphan = new RepoPathImpl("repo1", "orphan/file");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, orphan, "orphan");

        session.beforeCommit();
        session.afterConnectionReleased(true);

        for (RepoPath p : eventPaths) {
            Assert.assertTrue(EventLogHintManager.getHints(p).isEmpty(),
                    "event path drained after commit: " + p.toPath());
        }
        Assert.assertNotNull(EventLogHintManager.getHints(orphan), "non-event path hint preserved");
        Assert.assertEquals(EventLogHintManager.getHints(orphan).size(), 1, "orphan hint intact");
    }

    @Test
    public void rollbackKeepsHintThenRetryCommitDrainsIt() {
        // Why draining is deferred to after-commit: a rolled-back transaction must keep its hints so a retry on the
        // same thread can still consume them; the retry's commit then drains.
        RepoPath path = new RepoPathImpl("repo1", "a/b");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, path, "hint");

        // Transaction 1 rolls back -> hint kept.
        runTransaction(path, false);
        Assert.assertEquals(EventLogHintManager.getHints(path).size(), 1, "hint kept after rollback for retry");

        // Transaction 2 (retry on the same thread) commits -> hint drained.
        runTransaction(path, true);
        Assert.assertNull(EventLogHintManager.getHints(path), "hint drained after the retry commit");
    }

    @Test
    public void commitRollbackCommitSequenceDrainsPerTransaction() {
        // Three transactions on the same thread share one EventLogHintManager ThreadLocal. Each drains only its own
        // committed path; the rolled-back transaction leaves its path hint intact.
        RepoPath pathA = new RepoPathImpl("repo1", "a");
        RepoPath pathB = new RepoPathImpl("repo1", "b");
        RepoPath pathC = new RepoPathImpl("repo1", "c");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, pathA, "ha");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, pathB, "hb");
        EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, pathC, "hc");

        runTransaction(pathA, true);   // commit   -> A drained
        runTransaction(pathB, false);  // rollback -> B kept
        runTransaction(pathC, true);   // commit   -> C drained

        // Manager stays alive throughout because B's hint survives, so drained paths read back as empty (not null).
        Assert.assertTrue(EventLogHintManager.getHints(pathA).isEmpty(), "A drained on its commit");
        Assert.assertEquals(EventLogHintManager.getHints(pathB).size(), 1, "B kept (rolled back)");
        Assert.assertTrue(EventLogHintManager.getHints(pathC).isEmpty(), "C drained on its commit");
    }

    /**
     * Runs one transaction (fresh session, single create event for {@code path}) ending in commit or rollback.
     */
    private void runTransaction(RepoPath path, boolean committed) {
        EventsSessionResource session = new EventsSessionResource();
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(path);
        session.itemCreated(item);
        session.beforeCommit();
        session.afterConnectionReleased(committed);
    }
}
