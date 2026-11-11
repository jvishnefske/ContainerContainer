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
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertiesArtifactInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class UpdatePropertyServiceTest {

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
    private UpdatePropertyService updatePropertyService;

    @Test
    void testDoExecute() {
        ArtifactoryRestRequest<PropertiesArtifactInfo> request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        PropertiesArtifactInfo propertiesArtifactInfo = mock(PropertiesArtifactInfo.class);
        Property property = mock(Property.class);
        when(request.getImodel()).thenReturn(propertiesArtifactInfo);
        when(propertiesArtifactInfo.getProperty()).thenReturn(property);
        when(property.getName()).thenReturn("<img src onerror=alert(133337)>");
        updatePropertyService.doExecute(request, response, mock(RepoPath.class));
        Assertions.assertEquals("Failed to update property '<img src onerror=alert(133337)>'",
                response.getFeedbackMsg().getError());
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
        updatePropertyService.doExecute(request, response, mock(RepoPath.class));
        Assertions.assertEquals("Successfully updated property 'validPropertyName'",
                response.getFeedbackMsg().getInfo());
        Assertions.assertNull(response.getFeedbackMsg().getError());
    }
}