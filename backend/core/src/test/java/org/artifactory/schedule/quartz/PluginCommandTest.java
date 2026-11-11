package org.artifactory.schedule.quartz;

import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginCommandTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private ArtifactoryStorageContext storageContext;

    private MockedStatic<StorageContextHelper> storageContextHelper;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        storageContextHelper = Mockito.mockStatic(StorageContextHelper.class);
        storageContextHelper.when(StorageContextHelper::get).thenReturn(storageContext);
        when(storageContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storageContextHelper != null) {
            storageContextHelper.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testOnExecuteSkipsWhenDisablePluginInfraEntitled() throws JobExecutionException {
        when(addonsManager.isEntitled(eq(EntitlementType.DISABLE_PLUGIN_INFRA))).thenReturn(true);
        ExecuteAction action = mock(ExecuteAction.class);
        TestablePluginCommand cmd = new TestablePluginCommand();
        cmd.callOnExecute(buildJobContext(action));
        verify(action, never()).execute();
    }

    @Test
    void testOnExecuteSkipsWhenHostInfraFlagDisabled() throws JobExecutionException {
        homeStub.setProperty(ConstantValues.pluginInfrastructureEnabled.getPropertyName(), "false");
        ExecuteAction action = mock(ExecuteAction.class);
        TestablePluginCommand cmd = new TestablePluginCommand();
        cmd.callOnExecute(buildJobContext(action));
        verify(action, never()).execute();
        verify(addonsManager, never()).isEntitled(any());
    }

    @Test
    void testOnExecuteRunsActionWhenNotDisabled() throws JobExecutionException {
        when(addonsManager.isEntitled(eq(EntitlementType.DISABLE_PLUGIN_INFRA))).thenReturn(false);
        ExecuteAction action = mock(ExecuteAction.class);
        TestablePluginCommand cmd = new TestablePluginCommand();
        cmd.callOnExecute(buildJobContext(action));
        verify(action).execute();
    }

    private static JobExecutionContext buildJobContext(ExecuteAction action) {
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class, RETURNS_DEEP_STUBS);
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(PluginCommand.ACTION_CLOSURE, action);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);
        when(jobDetail.getKey().toString()).thenReturn("testGroup.testJob");
        when(jobContext.getJobDetail()).thenReturn(jobDetail);
        return jobContext;
    }

    private static class TestablePluginCommand extends PluginCommand {
        void callOnExecute(JobExecutionContext ctx) throws JobExecutionException {
            onExecute(ctx);
        }
    }
}
