package com.jfrog.build.command;

import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildRetentionService;
import com.jfrog.build.service.BuildSysConfigService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.exception.CancelException;
import org.jfrog.build.api.BuildRetention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildRetainCommandTest {

    private BuildRetainCommand command;

    private BuildRepoService repoService;
    private BuildRetentionService retentionService;
    private BuildSysConfigService sysConfigService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        retentionService = mock(BuildRetentionService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        command = new BuildRetainCommand(repoService, retentionService, sysConfigService);
    }

    @Test
    void retainBuilds_asyncDelegatesToDiscardOldBuildsAsync() {
        BuildRetention retention = mock(BuildRetention.class);
        when(repoService.getPreferredBuildRepo("br", "phoenix")).thenReturn("phoenix-build-info");

        command.retainBuilds(retention, "my-build", "br", "phoenix", true);

        verify(retentionService).discardOldBuilds("my-build", "phoenix-build-info", retention, true);
    }

    @Test
    void retainBuilds_alwaysAsyncDelegatesToDiscardOldBuildsAsync() {
        BuildRetention retention = mock(BuildRetention.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(sysConfigService.isBuildRetentionAlwaysAsync()).thenReturn(true);

        command.retainBuilds(retention, "my-build", null, null, false);

        verify(retentionService).discardOldBuilds("my-build", "artifactory-build-info", retention, true);
    }

    @Test
    void retainBuilds_syncThrowsCancelExceptionWhenErrorsOccurred() {
        BuildRetention retention = mock(BuildRetention.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(sysConfigService.isBuildRetentionAlwaysAsync()).thenReturn(false);

        MutableStatusHolder status = mock(MutableStatusHolder.class);
        StatusEntry error = mock(StatusEntry.class);
        when(error.getStatusCode()).thenReturn(500);
        when(status.getMostImportantErrorStatusCode()).thenReturn(error);
        when(retentionService.discardOldBuilds(eq("my-build"), eq("artifactory-build-info"), same(retention), eq(false)))
                .thenReturn(status);

        assertThrows(CancelException.class, () -> command.retainBuilds(retention, "my-build", null, null, false));
    }

    @Test
    void retainBuilds_syncThrowsCancelExceptionWhenWarningsOccurred() {
        BuildRetention retention = mock(BuildRetention.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(sysConfigService.isBuildRetentionAlwaysAsync()).thenReturn(false);

        MutableStatusHolder status = mock(MutableStatusHolder.class);
        StatusEntry warning = mock(StatusEntry.class);
        when(warning.getStatusCode()).thenReturn(409);
        when(status.getMostImportantErrorStatusCode()).thenReturn(null);
        when(status.getMostImportantWarningsStatusCode()).thenReturn(warning);
        when(retentionService.discardOldBuilds(any(), any(), any(), eq(false))).thenReturn(status);

        assertThrows(CancelException.class, () -> command.retainBuilds(retention, "my-build", null, null, false));
    }
}

