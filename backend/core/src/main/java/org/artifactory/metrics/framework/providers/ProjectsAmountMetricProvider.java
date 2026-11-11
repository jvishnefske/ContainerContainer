package org.artifactory.metrics.framework.providers;

import org.artifactory.projects.ProjectsService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.metrics.MetricsUtil.addMetricToList;

@Component
public class ProjectsAmountMetricProvider implements MetricProvider {

    private final ProjectsService projectsService;

    @Autowired
    public ProjectsAmountMetricProvider(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Override
    public String getName() {
        return "jfrt_projects";
    }

    @Override
    public String getDescription() {
        return "Artifactory Projects Amount Metric Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0;
    }

    @Override
    public List<Metric> collect() {
        int projectsAmount = projectsService.getAllProjects().stream()
                .filter(project -> !project.equalsIgnoreCase(ProjectsService.DEFAULT_PROJECT))
                .collect(Collectors.toList()).size();
        List<Metric> metrics = new ArrayList<>();
        addMetricToList(metrics, "active", Metric.MetricType.COUNTER, "Projects Amount",
                projectsAmount);
        return metrics;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

}
