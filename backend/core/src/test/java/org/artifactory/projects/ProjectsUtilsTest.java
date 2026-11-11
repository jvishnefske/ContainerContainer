package org.artifactory.projects;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author BarakH
 */
public class ProjectsUtilsTest {

    @Mock
    private ArtifactoryContext artifactoryContext;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterMethod
    void teardown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test(dataProvider = "provideProjectNames")
    public void testIsDefaultProject(String project, boolean isDefault) {
        boolean result = ProjectsUtils.isDefaultProject(project);
        assertEquals(result, isDefault);
    }

    @Test
    public void testGetProjectKeyFromRepoKey() {
        assertEquals(ProjectsUtils.getProjectKeyFromRepoKey("project-repo-key"), "project");
        assertEquals(ProjectsUtils.getProjectKeyFromRepoKey("project-build"), "project");
        Assert.assertNull(ProjectsUtils.getProjectKeyFromRepoKey("project"));
        Assert.assertNull(ProjectsUtils.getProjectKeyFromRepoKey(""));
    }

    @Test
    public void testIsStorageQuotaSoftLimit() {
        AolService aolService = mock(AolService.class);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(true);

        assertTrue(ProjectsUtils.isStorageQuotaSoftLimit(false));
        assertTrue(ProjectsUtils.isStorageQuotaSoftLimit(true));
        verify(aolService).isAol();
        clearInvocations(aolService);

        when(aolService.isAol()).thenReturn(false);
        assertFalse(ProjectsUtils.isStorageQuotaSoftLimit(false));
        assertTrue(ProjectsUtils.isStorageQuotaSoftLimit(true));
        verify(aolService).isAol();
    }

    @DataProvider
    public static Object[][] provideProjectNames() {
        return new Object[][] {
                {null, true},
                {"", true},
                {DEFAULT_PROJECT, true},
                {"DEFAULT", true},
                {"project", false},
                {"default-project", false},
        };
    }

    @DataProvider
    public static Object[][] provideProjectNameAndRepo() {
        return new Object[][] {
                {null, ""},
                {"", "project"},
                {"B", ProjectsService.DEFAULT_PROJECT},
                {"B", "project1"},
        };
    }
}
