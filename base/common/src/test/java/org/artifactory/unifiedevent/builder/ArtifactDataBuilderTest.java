package org.artifactory.unifiedevent.builder;

import org.artifactory.common.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.builder.artifact.ArtifactDataBuilder;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactDataBuilderTest {

    private static final String PATH_DELIMITER = "/";

    @Mock
    private UnifiedEventContextProvider contextProvider;

    @Mock
    private ArtifactoryRequest request;

    @Mock
    private Properties properties;

    @Test
    void testBuildDataForArtifactAction() {

        String repoKey = "test-repo";
        String name = "artifact-1.0.jar";
        RepoDetailsType repoType = RepoDetailsType.LOCAL;
        String path = "com/example/artifact/1.0/artifact-1.0.jar";
        long lastModified = 1234567890L;
        Map<String, Collection<String>> propertiesMap = new HashMap<>();
        propertiesMap.put("key1", List.of("value1", "value2"));
        propertiesMap.put("key2", List.of("value3"));

        PropertiesImpl properties = new PropertiesImpl();
        properties.put("key1", "value1");
        properties.put("key1", "value2");
        properties.put("key2", "value3");

        when(request.getPath()).thenReturn(path);
        when(request.getRepoKey()).thenReturn(repoKey);
        when(request.getName()).thenReturn(name);
        when(request.getLastModified()).thenReturn(lastModified);
        when(request.getProperties()).thenReturn(properties);
        when(contextProvider.getRepoType(repoKey)).thenReturn(repoType);

        ArtifactData result = ArtifactDataBuilder.buildDataForArtifactAction(request, contextProvider);

        assertNotNull(result);
        assertEquals(repoKey, result.repoKey());
        assertEquals(repoType, result.repoType());
        assertEquals(path, result.path());
        assertEquals(lastModified, result.lastModified());
        assertTrue(result.properties().containsKey("key1"));
        assertTrue(result.properties().get("key1").contains("value1"));
        assertTrue(result.properties().get("key1").contains("value2"));
        assertTrue(result.properties().containsKey("key2"));
        assertTrue(result.properties().get("key2").contains("value3"));
        assertEquals(name, result.name());
    }

    @Test
    void testBuildDataFormItemInfo() {

        String repoKey = "test-storage-repo";
        RepoDetailsType repoType = RepoDetailsType.REMOTE;
        String name = "artifact.jar";
        String path = "path/to/artifact.jar";
        long created = 9876543210L;
        long lastModified = 1234567890L;
        String deployedBy = "admin";
        Map<String, Collection<String>> propertiesMap = new HashMap<>();
        propertiesMap.put("build.name", List.of("test-build"));
        propertiesMap.put("build.number", List.of("123"));
        String expectedResultPath = path + PATH_DELIMITER;

        VfsItem<ItemInfo> vfsItem = mock(VfsItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        Properties itemProperties = mock(Properties.class);
        RepoPath repoPath = mock(RepoPath.class);

        when(vfsItem.getRepoKey()).thenReturn(repoKey);
        when(vfsItem.getName()).thenReturn(name);
        when(vfsItem.getCreated()).thenReturn(created);
        when(vfsItem.getInfo()).thenReturn(itemInfo);
        when(vfsItem.getProperties()).thenReturn(itemProperties);
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn(path);
        when(repoPath.isFolder()).thenReturn(true);
        when(itemInfo.getLastModified()).thenReturn(lastModified);
        when(itemInfo.getCreatedBy()).thenReturn(deployedBy);
        when(itemProperties.asMap()).thenReturn(propertiesMap);
        when(contextProvider.getRepoType(repoKey)).thenReturn(repoType);

        ArtifactData result = ArtifactDataBuilder.buildDataFormItemInfo(vfsItem, contextProvider);

        assertNotNull(result);
        assertEquals(name, result.name());
        assertEquals(repoKey, result.repoKey());
        assertEquals(expectedResultPath, result.path());
        assertEquals(repoType, result.repoType());
        assertEquals(created, result.created());
        assertEquals(lastModified, result.lastModified());
        assertEquals(deployedBy, result.deployedBy());
        assertEquals(propertiesMap, result.properties());
    }

    @Test
    void testBuildForReplicationEvent_coversAllFields() {
        RepoPath repoPath = mock(RepoPath.class);
        UserContextData userContext = UserContextData.builder()
                .actor("replicator")
                .build();
        when(repoPath.getRepoKey()).thenReturn("repoKey");
        when(repoPath.getPath()).thenReturn("path");
        when(repoPath.isFolder()).thenReturn(true);
        when(contextProvider.getRepoType("repoKey")).thenReturn(RepoDetailsType.LOCAL);

        ArtifactData data = ArtifactDataBuilder.buildDataForReplicationInfo(repoPath, contextProvider);

        assertNotNull(data);
        assertEquals(RepoType.LOCAL.ordinal(), data.repoType().ordinal());
        assertEquals("repoKey", data.repoKey());
        assertEquals("path" + PATH_DELIMITER, data.path());
    }

}
