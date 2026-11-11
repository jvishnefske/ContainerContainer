package org.artifactory.ui.rest.resource.admin.configuration.projects;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
/**
 * @author Hussein Alshqerat
 */

class ProjectsResourceTest {

    private AutoCloseable autoCloseable;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactoryContext context;
    @Spy
    private ProjectsResource projectsResource;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @Test
    void testGetUnassignedProjectsUnavailableWithEdgeLicence() {
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> projectsResource.getUnassignedResourcesStats());
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }
}