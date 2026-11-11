package org.artifactory.system.load.monitor.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.system.load.monitor.SystemLoadMonitorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLoadMonitorJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SystemLoadMonitorService systemLoadMonitorService;
    private SystemLoadMonitorJob systemLoadMonitorJob;

    @BeforeEach
    void setUp() {
        when(artifactoryContext.beanForType(SystemLoadMonitorService.class)).thenReturn(systemLoadMonitorService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        systemLoadMonitorJob = new SystemLoadMonitorJob();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testOnExecute() throws Exception {
        systemLoadMonitorJob.onExecute(null);
        verify(systemLoadMonitorService).monitorSystemLoad();
    }
}