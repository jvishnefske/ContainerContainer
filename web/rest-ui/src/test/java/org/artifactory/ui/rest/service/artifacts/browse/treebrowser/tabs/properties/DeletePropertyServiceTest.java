package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties;

import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.DeletePropertyModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertyWithPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Harendra Rathore
 */
@ExtendWith(MockitoExtension.class)
public class DeletePropertyServiceTest {
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private PropertiesService propService;
    @Mock
    private SecurityService securityService;
    @Mock
    private MetadataServiceIndexHandler metadataServiceIndexHandler;
    @InjectMocks
    private DeletePropertyService<DeletePropertyModel> deletePropertyService;
    @Mock
    private DeletePropertyModel model;


    @ParameterizedTest
    @MethodSource("executeProvider")
    void testExecute(boolean annotatePermission, boolean recursive, boolean deleteError, boolean single) {
        ArtifactoryRestRequest<DeletePropertyModel> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        RepoPath repoPath = InternalRepoPathFactory.create("testRepoKey", "testPath");
        List<PropertyWithPath> propertiesWithPath = new ArrayList<>();
        propertiesWithPath.add(createMockProperty("prop1", recursive));
        if (!single) {
            propertiesWithPath.add(createMockProperty("prop2", recursive));
        }

        when(request.getImodel()).thenReturn(model);
        when(model.getProperties()).thenReturn(propertiesWithPath);
        when(authorizationService.canAnnotate(repoPath)).thenReturn(annotatePermission);

        if (!annotatePermission) {
            deletePropertyService.execute(request, response);
            assertEquals("Unable to remove properties from '" + "testRepoKey:testPath" +
                    "' - user does not have annotate permissions on given path",
                    ((ArtifactoryRestResponse) response).getFeedbackMsg().getWarn());
        } else {
            when(repoService.localFederatedOrCachedRepositoryByKey("testRepoKey")).thenReturn(mock(LocalRepo.class));
            setupDeleteBehavior(deleteError, recursive, repoPath);
            deletePropertyService.execute(request, response);
            if (deleteError) {
                assertTrue(((ArtifactoryRestResponse) response).getFeedbackMsg().getError()
                        .contains("failed to delete"));
            } else {
                assertTrue(((ArtifactoryRestResponse) response).getFeedbackMsg().getInfo()
                        .contains("Successfully removed"));
            }
        }
    }

    private PropertyWithPath createMockProperty(String name, boolean recursive) {
        PropertyWithPath property = mock(PropertyWithPath.class);
        when(property.getName()).thenReturn(name);
        when(property.getRepoKey()).thenReturn("testRepoKey");
        when(property.getPath()).thenReturn("testPath");
        when(property.isRecursive()).thenReturn(recursive);
        return property;
    }

    private void setupDeleteBehavior(boolean deleteError, boolean recursive, RepoPath repoPath) {
        if (deleteError) {
            if (recursive) {
                doThrow(new RuntimeException("Cannot delete property")).when(propService)
                        .deletePropertyRecursively(repoPath, "prop1", true);
            } else {
                when(propService.deleteProperty(repoPath, "prop1", true))
                        .thenThrow(new RuntimeException("Cannot delete property"));
            }
        } else {
            if (recursive) {
                doNothing().when(propService).deletePropertyRecursively(repoPath, "prop1", true);
            } else {
                when(propService.deleteProperty(repoPath, "prop1", true)).thenReturn(true);
            }
        }
    }

    @Test
    void batchDeleteSamePathNotifiesOnce() {
        ArtifactoryRestRequest<DeletePropertyModel> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = new ArtifactoryRestResponse<>();
        RepoPath repoPath = InternalRepoPathFactory.create("repo", "path/to/artifact");
        List<PropertyWithPath> propertiesWithPath = List.of(
                createMockPropertyWithPath("prop1", "repo", "path/to/artifact", false),
                createMockPropertyWithPath("prop2", "repo", "path/to/artifact", false),
                createMockPropertyWithPath("prop3", "repo", "path/to/artifact", false)
        );

        when(request.getImodel()).thenReturn(model);
        when(model.getProperties()).thenReturn(propertiesWithPath);
        when(authorizationService.canAnnotate(repoPath)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepositoryByKey("repo")).thenReturn(mock(LocalRepo.class));
        when(propService.deleteProperty(repoPath, "prop1", true)).thenReturn(true);
        when(propService.deleteProperty(repoPath, "prop2", true)).thenReturn(true);
        when(propService.deleteProperty(repoPath, "prop3", true)).thenReturn(true);
        Properties props = mock(Properties.class);
        when(propService.getProperties(repoPath)).thenReturn(props);

        deletePropertyService.execute(request, response);

        verify(metadataServiceIndexHandler, times(1)).notifyPropertyChange(repoPath, props);
    }

    private PropertyWithPath createMockPropertyWithPath(String name, String repoKey, String path, boolean recursive) {
        PropertyWithPath p = mock(PropertyWithPath.class);
        when(p.getName()).thenReturn(name);
        when(p.getRepoKey()).thenReturn(repoKey);
        when(p.getPath()).thenReturn(path);
        when(p.isRecursive()).thenReturn(recursive);
        return p;
    }

    public static Stream<Arguments> executeProvider() {
        return Stream.of(
                Arguments.of(true, true, true, true),
                Arguments.of(true, true, false, true),
                Arguments.of(true, false, true, true),
                Arguments.of(true, false, false, true),
                Arguments.of(false, true, true, true),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, true, true),
                Arguments.of(false, false, false, true),
                Arguments.of(true, true, true, false),
                Arguments.of(true, true, false, false),
                Arguments.of(true, false, true, false),
                Arguments.of(true, false, false, false),
                Arguments.of(false, true, true, false),
                Arguments.of(false, true, false, false),
                Arguments.of(false, false, true, false),
                Arguments.of(false, false, false, false)
        );
    }
}
