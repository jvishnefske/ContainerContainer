package org.artifactory.metrics.framework.providers;

import com.google.common.collect.ImmutableList;
import org.artifactory.projects.ProjectsService;
import org.jfrog.metrics.model.Metric;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ProjectsAmountMetricProviderTest {

    private final static String PROJECTX = "projectx";
    private final static String PROJECTY = "projecty";
    private final static String DEFAULT = ProjectsService.DEFAULT_PROJECT;
    @Mock
    ProjectsService projectsService;
    ProjectsAmountMetricProvider projectsAmountMetricProvider;

    @Test
    public void testGetName() {
        projectsAmountMetricProvider = new ProjectsAmountMetricProvider(projectsService);
        assertEquals("jfrt_projects", projectsAmountMetricProvider.getName());
    }

    @Test
    public void testGetDescription() {
        projectsAmountMetricProvider = new ProjectsAmountMetricProvider(projectsService);
        assertEquals("Artifactory Projects Amount Metric Provider", projectsAmountMetricProvider.getDescription());
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        projectsAmountMetricProvider = new ProjectsAmountMetricProvider(projectsService);
        assertEquals(0, projectsAmountMetricProvider.getRefreshIntervalMillis());
    }

    @Test
    public void testCollectProjectMetrics() {
        projectsService = Mockito.mock(ProjectsService.class);
        projectsAmountMetricProvider = new ProjectsAmountMetricProvider(projectsService);
        when(projectsService.getAllProjects()).thenReturn(ImmutableList.of(PROJECTX, PROJECTY));
        List<Metric> metrics = projectsAmountMetricProvider.collect();

        assertEquals(metrics.get(0).getName(), "active");
        assertEquals(metrics.get(0).getValue().intValue(), 2);

    }
    @Test
    public void testFilterDefaultProjectFromMetrics() {
        projectsService = Mockito.mock(ProjectsService.class);
        projectsAmountMetricProvider = new ProjectsAmountMetricProvider(projectsService);
        when(projectsService.getAllProjects()).thenReturn(ImmutableList.of(DEFAULT,PROJECTX));
        List<Metric> metrics = projectsAmountMetricProvider.collect();

        assertEquals(metrics.get(0).getName(), "active");
        assertEquals(metrics.get(0).getValue().intValue(), 1);

    }

}