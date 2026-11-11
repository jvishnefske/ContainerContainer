package org.artifactory.rest.resource.artifact;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.fs.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class ArtifactMetadataResourceHelperTest {

    private final JsonNodeFactory instance = JsonNodeFactory.instance;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private StatsService statsService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private PropertiesAddon propertiesAddon;
    private ArtifactMetadataResourceHelper artifactMetadataResourceHelper;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = openMocks(this);
        artifactMetadataResourceHelper =
                new ArtifactMetadataResourceHelper(addonsManager, statsService, authorizationService, securityService,
                        repositoryService);
        when(addonsManager.addonByType(PropertiesAddon.class)).thenReturn(propertiesAddon);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocks.close();
    }

    @Test
    void testHandlePatchProperties() {
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-artifact");
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        Properties properties = new PropertiesImpl();
        properties.put("existingKey", "oldValue");
        properties.putAll("existingKeyMulti", List.of("modifiedValue1", "modifiedValue2"));
        ObjectNode requestedProps = new ObjectNode(instance);
        requestedProps.put("newKey", "newValue");
        requestedProps.put("existingKey", "modifiedValue");
        requestedProps.putNull("toBeRemovedKey");
        ArrayNode array = instance.arrayNode().add("value1").add("value2");
        requestedProps.putArray("multiValueKey").addAll(array);
        ArrayNode modifiedArray = instance.arrayNode().add("modifiedValue1").add("modifiedValue2");
        requestedProps.putArray("existingKeyMulti").addAll(modifiedArray);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        when(propertiesAddon.getProperties(itemInfo)).thenReturn(properties);
        when(propertiesAddon.patchProperties(any(), any(), any(), any(), any())).thenReturn(
                Response.noContent().build());
        artifactMetadataResourceHelper.handlePatchProperties(requestedProps, repoPath, "true");
        Map<Property, List<String>> toBeAdded = new HashMap<>();
        Map<Property, List<String>> toBeModified = new HashMap<>();
        toBeAdded.put(new Property("newKey"), List.of("newValue"));
        toBeAdded.put(new Property("multiValueKey"), List.of("value1", "value2"));
        toBeModified.put(new Property("existingKey"), List.of("modifiedValue"));
        toBeModified.put(new Property("existingKeyMulti"), List.of("modifiedValue1", "modifiedValue2"));
        verify(propertiesAddon, times(1)).getProperties(itemInfo);
        verify(propertiesAddon, times(1)).patchProperties(repoPath,
                "true", List.of("toBeRemovedKey"), toBeAdded, toBeModified);
    }

    @Test
    void testHandlePatchPropertiesFails() {
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-artifact");
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        Properties properties = new PropertiesImpl();
        properties.put("existingKey", "oldValue");
        properties.putAll("existingKeyMulti", List.of("modifiedValue1", "modifiedValue2"));
        ObjectNode requestedProps = new ObjectNode(instance);
        requestedProps.put("newKey", "newValue");
        requestedProps.put("existingKey", "modifiedValue");
        requestedProps.putNull("toBeRemovedKey");
        ArrayNode array = instance.arrayNode().add("value1").add("value2");
        requestedProps.putArray("multiValueKey").addAll(array);
        ArrayNode modifiedArray = instance.arrayNode().add("modifiedValue1").add("modifiedValue2");
        requestedProps.putArray("existingKeyMulti").addAll(modifiedArray);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        when(propertiesAddon.getProperties(itemInfo)).thenReturn(properties);
        assertThrows(Exception.class, () ->
                artifactMetadataResourceHelper.handlePatchProperties(requestedProps, repoPath, "true"));
    }

    @Test
    void testHandlePatchPropertiesWithIllegalValuesFails() {
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-artifact");
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        Properties properties = new PropertiesImpl();
        properties.put("existingKey", "oldValue");
        properties.putAll("existingKeyMulti", List.of("modifiedValue1", "modifiedValue2"));
        ObjectNode requestedProps = new ObjectNode(instance);
        requestedProps.put("int", 1908);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        when(propertiesAddon.getProperties(itemInfo)).thenReturn(properties);
        assertThrows(IllegalArgumentException.class,
                () -> artifactMetadataResourceHelper.handlePatchProperties(requestedProps, repoPath, "true"));
    }
}