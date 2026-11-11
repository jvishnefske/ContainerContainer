package org.artifactory.rest.resource.metadata;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.metadata.service.MetadataEventServiceImpl;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.metadataServerEventsEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServerReindexResourceTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable mocks;

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;

    MetadataServerReindexResource metadataServerReindexResource;
    MetadataEventService metadataEventService;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        metadataServerReindexResource = spy(new MetadataServerReindexResource());
        metadataEventService = spy(new MetadataEventServiceImpl());
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(MetadataEventService.class)).thenReturn(metadataEventService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recreateStatsInMetadataByPathsPassing(boolean async) {
        MetadataServerReindexResource metadataServerReindexResource = new MetadataServerReindexResource();
        MetadataServerReindexModel metadataServerReindexModel = new MetadataServerReindexModel();
        metadataServerReindexModel.setPaths(List.of("path1", "path2"));
        metadataServerReindexResource.recreateStatsInMetadataByPaths(metadataServerReindexModel, async);
        if (async) {
            verify(metadataEventService, times(1)).statsRecreateAsync(metadataServerReindexModel.getPaths());
            verify(metadataEventService, times(0)).statsRecreateSync(metadataServerReindexModel.getPaths());
        } else {
            verify(metadataEventService, times(0)).statsRecreateAsync(metadataServerReindexModel.getPaths());
            verify(metadataEventService, times(1)).statsRecreateSync(metadataServerReindexModel.getPaths());
        }
    }

    @Test
    void recreateStatsInMetadataByPathsFailing() {
        MetadataServerReindexResource metadataServerReindexResource = new MetadataServerReindexResource();
        MetadataServerReindexModel metadataServerReindexModel = new MetadataServerReindexModel();
        metadataServerReindexModel.setPaths(List.of("path1", "path2"));

        homeStub.setProperty(metadataServerEventsEnabled.getPropertyName(), "false");

        Response response =
                metadataServerReindexResource.recreateStatsInMetadataByPaths(metadataServerReindexModel, false);
        assertEquals(404, response.getStatus());
        assertEquals("Metadata Server Integration is disabled", response.getEntity());

        homeStub.setProperty(metadataServerEventsEnabled.getPropertyName(), "true");
        response = metadataServerReindexResource.recreateStatsInMetadataByPaths(null, false);
        assertEquals(400, response.getStatus());
        assertEquals("Reindex paths are missing in the request. No reindexing will be done.", response.getEntity());

        metadataServerReindexModel.setPaths(List.of());

        response =
                metadataServerReindexResource.recreateStatsInMetadataByPaths(metadataServerReindexModel, false);
        assertEquals(400, response.getStatus());
        assertEquals("Reindex paths are missing in the request. No reindexing will be done.", response.getEntity());
    }

    @ParameterizedTest
    @MethodSource("reindexMetadataByPathsTestData")
    void reindexMetadataByPathsPassing(boolean deleteFirst, boolean async) {
        MetadataServerReindexResource metadataServerReindexResource = new MetadataServerReindexResource();
        MetadataServerReindexModel metadataServerReindexModel = new MetadataServerReindexModel();
        metadataServerReindexModel.setPaths(List.of("path1", "path2"));
        metadataServerReindexResource.reindexMetadataByPaths(metadataServerReindexModel, deleteFirst, async);
        if (async) {
            verify(metadataEventService, times(1)).reindexAsync(metadataServerReindexModel.getPaths(), deleteFirst);
            verify(metadataEventService, times(0)).reindexSync(metadataServerReindexModel.getPaths(), deleteFirst);
        } else {
            verify(metadataEventService, times(0)).reindexAsync(metadataServerReindexModel.getPaths(), deleteFirst);
            verify(metadataEventService, times(1)).reindexSync(metadataServerReindexModel.getPaths(), deleteFirst);
        }
    }

    @Test
    void reindexMetadataByPathsFailing() {
        MetadataServerReindexResource metadataServerReindexResource = new MetadataServerReindexResource();
        MetadataServerReindexModel metadataServerReindexModel = new MetadataServerReindexModel();
        metadataServerReindexModel.setPaths(List.of("path1", "path2"));

        homeStub.setProperty(metadataServerEventsEnabled.getPropertyName(), "false");

        Response response =
                metadataServerReindexResource.reindexMetadataByPaths(metadataServerReindexModel, false, false);
        assertEquals(404, response.getStatus());
        assertEquals("Metadata Server Integration is disabled", response.getEntity());

        homeStub.setProperty(metadataServerEventsEnabled.getPropertyName(), "true");
        response = metadataServerReindexResource.reindexMetadataByPaths(null, false, false);
        assertEquals(400, response.getStatus());
        assertEquals("Reindex paths are missing in the request. No reindexing will be done.", response.getEntity());

        metadataServerReindexModel.setPaths(List.of());

        response =
                metadataServerReindexResource.reindexMetadataByPaths(metadataServerReindexModel, false, false);
        assertEquals(400, response.getStatus());
        assertEquals("Reindex paths are missing in the request. No reindexing will be done.", response.getEntity());
    }

    public static Stream<Arguments> reindexMetadataByPathsTestData() {
        return TestUtils.booleanMatrix(2);
    }
}