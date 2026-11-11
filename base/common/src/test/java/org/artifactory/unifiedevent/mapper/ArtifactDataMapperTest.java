package org.artifactory.unifiedevent.mapper;

import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactDataMapperTest {

    @Mock
    private UnifiedEventContextProvider contextProvider;

    @Mock
    private RepoResource repoResource;

    @Mock
    private RepoPath repoPath;

    @Mock
    private RepoResourceInfo repoResourceInfo;

    @Mock
    private Properties properties;

    @Mock
    private ArtifactoryRequest artifactoryRequest;

    private ArtifactDataMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ArtifactDataMapperImpl();
        setContextProvider(mapper, contextProvider);
    }

    @Test
    void toArtifactEventEnvelopeV1_mapsEnvelopeCorrectly() {
        String eventId = "event-id-123";
        String source = "artifactory://my-service@node-1";
        String timestamp = "2025-01-01T12:00:00Z";
        String subject = "libs-release/path/to/artifact.jar";
        UserContextData userContext = UserContextData.builder().actor("test-user").build();
        Map<String, java.util.Collection<String>> propsMap = Collections.emptyMap();

        when(contextProvider.generateEventId()).thenReturn(eventId);
        when(contextProvider.buildSourceHeader()).thenReturn(source);
        when(contextProvider.getCurrentTimestamp()).thenReturn(timestamp);
        when(contextProvider.getUserContext()).thenReturn(userContext);
        when(contextProvider.getRepoType("libs-release")).thenReturn(RepoDetailsType.LOCAL);

        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.getRepoKey()).thenReturn("libs-release");
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);

        when(repoPath.toPrettyPath()).thenReturn(subject);
        when(repoPath.getPath()).thenReturn("path/to/artifact.jar");

        when(repoResourceInfo.getCreated()).thenReturn(1000L);
        when(repoResourceInfo.getLastModified()).thenReturn(2000L);
        when(repoResourceInfo.getMd5()).thenReturn("md5-value");
        when(repoResourceInfo.getSha1()).thenReturn("sha1-value");
        when(repoResourceInfo.getSha2()).thenReturn("sha256-value");

        when(properties.asMap()).thenReturn(propsMap);

        ArtifactResourceContext resourceContext = ArtifactResourceContext.builder()
                .repoResource(repoResource)
                .properties(properties)
                .artifactoryRequest(artifactoryRequest)
                .build();

        EventEnvelope<ArtifactEventData> result = mapper.toArtifactEventEnvelopeV1(
                SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, resourceContext);

        assertNotNull(result);
        assertEquals("1.0", result.specVersion());
        assertEquals(eventId, result.id());
        assertEquals(SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, result.type());
        assertEquals(source, result.source());
        assertEquals(timestamp, result.time());
        assertEquals(subject, result.subject());
        assertEquals("application/json", result.dataContentType());

        ArtifactEventData data = result.data();
        assertNotNull(data);
        assertEquals(userContext, data.context());
        assertEquals(artifactoryRequest, data.request());

        ArtifactData artifact = data.artifact();
        assertNotNull(artifact);
        assertEquals("libs-release", artifact.repoKey());
        assertEquals(RepoDetailsType.LOCAL, artifact.repoType());
        assertEquals("path/to/artifact.jar", artifact.path());
        assertEquals(1000L, artifact.created());
        assertEquals(2000L, artifact.lastModified());
        assertEquals("md5-value", artifact.md5());
        assertEquals("sha1-value", artifact.sha1());
        assertEquals("sha256-value", artifact.sha256());
        assertEquals(propsMap, artifact.properties());
    }

    @Test
    void toArtifactEventEnvelopeV1_mapsNullRepoResourceWithoutThrowing() {
        when(contextProvider.generateEventId()).thenReturn("id");
        when(contextProvider.buildSourceHeader()).thenReturn("src");
        when(contextProvider.getCurrentTimestamp()).thenReturn("t");
        when(contextProvider.getUserContext()).thenReturn(UserContextData.builder().build());
        when(contextProvider.getRepoType(isNull())).thenReturn(null);

        ArtifactResourceContext resourceContext = ArtifactResourceContext.builder()
                .repoResource(null)
                .properties(null)
                .build();

        EventEnvelope<ArtifactEventData> result = mapper.toArtifactEventEnvelopeV1(
                SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, resourceContext);

        assertNotNull(result);
        assertNull(result.subject());
        ArtifactData artifact = result.data().artifact();
        assertNull(artifact.repoKey());
        assertNull(artifact.repoType());
        assertNull(artifact.path());
        assertNull(artifact.created());
        assertNull(artifact.lastModified());
        assertNull(artifact.md5());
        assertNull(artifact.sha1());
        assertNull(artifact.sha256());
        assertTrue(artifact.properties().isEmpty());
    }

    @Test
    void toArtifactEventEnvelopeV1_mapsNullRepoPathWithoutThrowing() {
        when(contextProvider.generateEventId()).thenReturn("id");
        when(contextProvider.buildSourceHeader()).thenReturn("src");
        when(contextProvider.getCurrentTimestamp()).thenReturn("t");
        when(contextProvider.getUserContext()).thenReturn(UserContextData.builder().build());
        when(contextProvider.getRepoType("libs-release")).thenReturn(RepoDetailsType.LOCAL);

        when(repoResource.getRepoPath()).thenReturn(null);
        when(repoResource.getRepoKey()).thenReturn("libs-release");
        when(repoResource.getInfo()).thenReturn(repoResourceInfo);
        when(repoResourceInfo.getCreated()).thenReturn(1L);
        when(repoResourceInfo.getLastModified()).thenReturn(2L);
        when(repoResourceInfo.getMd5()).thenReturn("m");
        when(repoResourceInfo.getSha1()).thenReturn("s1");
        when(repoResourceInfo.getSha2()).thenReturn("s256");
        when(properties.asMap()).thenReturn(Collections.emptyMap());

        ArtifactResourceContext resourceContext = ArtifactResourceContext.builder()
                .repoResource(repoResource)
                .properties(properties)
                .build();

        EventEnvelope<ArtifactEventData> result = mapper.toArtifactEventEnvelopeV1(
                SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, resourceContext);

        assertNull(result.subject());
        assertNull(result.data().artifact().path());
    }

    @Test
    void toArtifactEventEnvelopeV1_mapsNullRepoResourceInfoWithoutThrowing() {
        when(contextProvider.generateEventId()).thenReturn("id");
        when(contextProvider.buildSourceHeader()).thenReturn("src");
        when(contextProvider.getCurrentTimestamp()).thenReturn("t");
        when(contextProvider.getUserContext()).thenReturn(UserContextData.builder().build());
        when(contextProvider.getRepoType("libs-release")).thenReturn(RepoDetailsType.LOCAL);

        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoResource.getRepoKey()).thenReturn("libs-release");
        when(repoResource.getInfo()).thenReturn(null);
        when(repoPath.toPrettyPath()).thenReturn("libs-release/x");
        when(repoPath.getPath()).thenReturn("x");
        when(properties.asMap()).thenReturn(Collections.emptyMap());

        ArtifactResourceContext resourceContext = ArtifactResourceContext.builder()
                .repoResource(repoResource)
                .properties(properties)
                .build();

        EventEnvelope<ArtifactEventData> result = mapper.toArtifactEventEnvelopeV1(
                SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, resourceContext);

        ArtifactData artifact = result.data().artifact();
        assertNull(artifact.created());
        assertNull(artifact.lastModified());
        assertNull(artifact.md5());
        assertNull(artifact.sha1());
        assertNull(artifact.sha256());
    }

    private static void setContextProvider(ArtifactDataMapper mapperInstance,
            UnifiedEventContextProvider provider) throws Exception {
        Field field = ArtifactDataMapper.class.getDeclaredField("contextProvider");
        field.setAccessible(true);
        field.set(mapperInstance, provider);
    }
}
