package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties;

import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.EmojiNotAllowedInDbException;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertiesArtifactInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class CreatePropertyServiceTest {

    @Mock
    private PropertiesService propertiesService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private MetadataServiceIndexHandler metadataServiceIndexHandler;
    @InjectMocks
    private CreatePropertyService createPropertyService;

    @Test
    void testValidationException() {
        ArtifactoryRestRequest<PropertiesArtifactInfo> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = mock(RestResponse.class);
        PropertiesArtifactInfo propertiesArtifactInfo = mock(PropertiesArtifactInfo.class);
        Property property = mock(Property.class);
        when(request.getImodel()).thenReturn(propertiesArtifactInfo);
        when(propertiesArtifactInfo.getProperty()).thenReturn(property);
        when(property.getName()).thenReturn("<img src onerror=alert(133337)>");
        assertThrows(BadRequestException.class,
                () -> createPropertyService.doExecute(request, response, mock(RepoPath.class)));
    }

    @Test
    void testDoExecuteSuccess() {
        ArtifactoryRestRequest<PropertiesArtifactInfo> request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        PropertiesArtifactInfo propertiesArtifactInfo = mock(PropertiesArtifactInfo.class);
        Property property = mock(Property.class);
        when(request.getImodel()).thenReturn(propertiesArtifactInfo);
        when(propertiesArtifactInfo.getProperty()).thenReturn(property);
        when(property.getName()).thenReturn("validPropertyName");
        createPropertyService.doExecute(request, response, mock(RepoPath.class));
        assertNull(response.getFeedbackMsg().getError());
        assertEquals("Successfully created property 'validPropertyName'", response.getFeedbackMsg().getInfo());
    }

    @Test
    void testSavePropertyAndUpdateResponseThrowingEmojiNotAllowedException() {
        PropertiesArtifactInfo propertiesArtifactInfo = mock(PropertiesArtifactInfo.class);
        Property property = mock(Property.class);
        when(propertiesArtifactInfo.getProperty()).thenReturn(property);
        RepoPath repoPath = mock(RepoPath.class);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();

        SQLException sqlException = new SQLException("Emojis are not allowed in the database");
        EmojiNotAllowedInDbException emojiEx =
                new EmojiNotAllowedInDbException("Emojis are not allowed", sqlException);
        doThrow(emojiEx).when(propertiesService).addProperty(
                repoPath, null, property, true, null);

        createPropertyService.savePropertyAndUpdateResponse(response, propertiesArtifactInfo, repoPath, false);
        assertEquals("Emojis are not allowed", response.getFeedbackMsg().getError());
        assertEquals(422, response.getResponseCode());
    }
}