package org.artifactory.sh.cleanup.events;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.event.StorageEventInfo;
import org.jfrog.storage.binstore.common.ZeroBytesBinaryElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.stream.Stream;

import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class StorageEventsSessionResourceTest {
    @BeforeAll
    static void setUp() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(ConstantValues.gcEventsCleanupEnabled)).thenReturn(true);
        when(home.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(home);
    }

    @AfterAll
    static void tearDown() {
        ArtifactoryHome.unbind();
    }

    @ParameterizedTest
    @MethodSource("checksumsInfoSupplier")
    void itemUpdated(ChecksumsInfo checksumsInfo, boolean eventCreated) {
        DbMutableFile item = mock(DbMutableFile.class);
        FileInfo itemInfo = mock(FileInfo.class);
        when(itemInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        when(itemInfo.getRelPath()).thenReturn("/var/tmp");
        when(item.getOriginalInfo()).thenReturn(itemInfo);
        // test
        StorageEventsSessionResource x = new StorageEventsSessionResource();
        x.itemUpdated(item);
        // verify
        verify(x, checksumsInfo, eventCreated);
    }

    @ParameterizedTest
    @MethodSource("checksumsInfoSupplier")
    void itemDeleted(ChecksumsInfo checksumsInfo, boolean eventCreated) {
        VfsItem<MutableFileInfo> item = mock(DbMutableFile.class);
        FileInfo itemInfo = mock(FileInfo.class);
        when(itemInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        when(itemInfo.getRelPath()).thenReturn("/var/tmp");
        when(item.getInfo()).thenReturn(itemInfo);
        // test
        StorageEventsSessionResource x = new StorageEventsSessionResource();
        x.itemDeleted(item);
        // verify
        verify(x, checksumsInfo, eventCreated);
    }

    @SuppressWarnings("unchecked")
    private void verify(StorageEventsSessionResource x, ChecksumsInfo checksumsInfo, boolean eventCreated) {
        Set<StorageEventInfo> storageEvents = (Set<StorageEventInfo>) ReflectionTestUtils.getField(x, "storageEvents");
        assert storageEvents != null;
        if(eventCreated) {
            assertFalse(storageEvents.isEmpty());
            storageEvents.stream().findFirst().ifPresent(event -> assertEquals(event.sha1(), checksumsInfo.getSha1()));
        } else {
            assertTrue(storageEvents.isEmpty());
        }
    }

    private static Stream<Arguments> checksumsInfoSupplier() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of(withSha1(null), false),
                Arguments.of(withSha1(""), true),
                Arguments.of(withSha1("SHA1"), true),
                Arguments.of(withSha1(new ZeroBytesBinaryElement().getSha1()), false)
        );
    }

    private static ChecksumsInfo withSha1(String sha1) {
        ChecksumsInfo res = mock(ChecksumsInfo.class);
        when(res.getSha1()).thenReturn(sha1);
        return res;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void afterConnectionReleased(boolean committed) {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        StorageEventsTriggerService triggerService = mock(StorageEventsTriggerService.class);
        when(context.beanForType(StorageEventsTriggerService.class)).thenReturn(triggerService);
        ArtifactoryContextThreadBinder.bind(context);

        try {
            StorageEventsSessionResource resource = new StorageEventsSessionResource();
            ReflectionTestUtils.setField(resource, "shouldTriggerQ", true);
            resource.afterConnectionReleased(committed);

            if (committed) {
                Mockito.verify(triggerService).scheduleTrigger();
            } else {
                Mockito.verify(triggerService, never()).scheduleTrigger();
            }
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void afterConnectionReleased_noTriggerNeeded() {
        StorageEventsSessionResource resource = new StorageEventsSessionResource();
        resource.afterConnectionReleased(true);
    }
}