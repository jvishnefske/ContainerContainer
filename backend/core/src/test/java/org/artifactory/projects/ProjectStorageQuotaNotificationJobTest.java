package org.artifactory.projects;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.mail.MailService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.StorageService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.EmailException;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserResponse;
import org.joda.time.DateTime;
import org.mockito.Mock;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Yevdo Abramov
 * Created on 27/12/2020
 */
@Test
public class ProjectStorageQuotaNotificationJobTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ProjectsService projectsService;
    @Mock
    private StorageService storageService;
    @Mock
    private MailService mailService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CoreAddons coreAddons;
    @Mock
    private InternalArtifactoryContext artifactoryContext;

    private ProjectStorageQuotaNotificationJob projectStorageQuotaNotificationJob;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(StorageService.class)).thenReturn(storageService);
        when(artifactoryContext.beanForType(MailService.class)).thenReturn(mailService);
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);

        projectStorageQuotaNotificationJob = new ProjectStorageQuotaNotificationJob();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void testOnExecuteWithNoProjects() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        when(projectsService.getAllProjectsEntities()).thenReturn(Collections.emptyList());

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(projectsService).getAllProjectsEntities();
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
    }

    public void testOnExecuteForDefaultProject() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity projectEntity = ProjectEntity.builder()
                .name(ProjectsService.DEFAULT_PROJECT)
                .projectKey(ProjectsService.DEFAULT_PROJECT)
                .storageQuotaEmailNotification(true)
                .build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(projectEntity));

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(projectsService).getAllProjectsEntities();
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
    }

    public void testOnExecuteWithoutNotificationEnabledProjects() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity projectEntity = ProjectEntity.builder().storageQuotaEmailNotification(false).build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(projectEntity));

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(projectsService).getAllProjectsEntities();
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
    }

    public void testOnExecuteWithUnlimitedStorageProjects() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity projectEntity = ProjectEntity.builder()
                .storageQuotaBytes(ProjectsService.UNLIMITED_STORAGE)
                .storageQuotaEmailNotification(true)
                .build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(projectEntity));

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(projectsService).getAllProjectsEntities();
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
    }

    public void testOnExecuteProjectWithSoftLimit() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder().name("project")
                .projectKey("project")
                .softLimit(true)
                .storageQuotaBytes(100)
                .storageQuotaEmailNotification(true).build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(100L);

        // when 100% and soft limit
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        // then do nothing
        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(project.getProjectKey());
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
        clearInvocations(projectsService, storageService, mailService);

        // when 101% and soft limit
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(101L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        // then send a notification
        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(project.getProjectKey());
        verify(storageService).getAllProjectsNotifiedOnStorageQuota();
        verify(storageService).updateProjectStorageQuotaNotificationTimestamp(eq(Set.of(project.getName())));
    }

    public void testOnExecuteProjectWithHardLimit() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder().name("project")
                .projectKey("project")
                .softLimit(false)
                .storageQuotaBytes(100)
                .storageQuotaEmailNotification(true).build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(75L);

        // when 75% and hard limit
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        // then do nothing
        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(project.getProjectKey());
        clearInvocations(coreAddons, projectsService, storageService, mailService);

        // when 76% and hard limit
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(76L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        // then send a notification
        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(project.getProjectKey());
        verify(storageService).getAllProjectsNotifiedOnStorageQuota();
        verify(storageService).updateProjectStorageQuotaNotificationTimestamp(eq(Set.of(project.getName())));
    }

    public void testOnExecuteWithExcludingRecentlyNotifiedProject() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity projectCanNotBeNotified = ProjectEntity.builder()
                .name("projectCanNotBeNotified")
                .projectKey("projectCanNotBeNotified")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true).build();
        ProjectEntity projectCanBeNotified = ProjectEntity.builder()
                .name("projectCanBeNotified")
                .projectKey("projectCanBeNotified")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true).build();

        when(projectsService.getAllProjectsEntities())
                .thenReturn(List.of(projectCanNotBeNotified, projectCanBeNotified));
        when(storageService.getProjectStorageUsage(anyString())).thenReturn(1174000000L);

        when(storageService.getAllProjectsNotifiedOnStorageQuota())
                .thenReturn(Map.of(projectCanBeNotified.getName(), 0L,
                        projectCanNotBeNotified.getName(), DateTime.now().minusMinutes(1).getMillis()));

        when(coreAddons.getPlatformAdminUsersEmails()).thenReturn(List.of("platform_admin@jfrog.com"));
        User projectAdmin = new UserResponse("projectAdmin").email("project_admin@jfrog.com");
        when(projectsService.getProjectAdmins(projectCanBeNotified)).thenReturn(Set.of(projectAdmin));

        // when only one project can actually be notified
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        // then send a notification to both project admins and platform admins
        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(projectCanBeNotified.getProjectKey());
        verify(storageService).getProjectStorageUsage(projectCanNotBeNotified.getProjectKey());
        verify(storageService).getAllProjectsNotifiedOnStorageQuota();
        verify(storageService).updateProjectStorageQuotaNotificationTimestamp(Set.of(projectCanBeNotified.getName()));
        verify(coreAddons).getPlatformAdminUsersEmails();
        verify(projectsService).getProjectAdmins(projectCanBeNotified);
        verify(mailService).sendMail(
                argThat(recipients -> recipients.length == 2
                        && Set.of(recipients).contains("project_admin@jfrog.com")
                        && Set.of(recipients).contains("platform_admin@jfrog.com")),
                eq("The projectCanBeNotified Project has reached its storage limit"),
                eq("<html><body>This email is for information purposes only!<br>Your projectCanBeNotified project has" +
                        " a total storage usage of 1.09 GB out of 1 GB.</body></html>"));
        verifyNoMoreInteractions(coreAddons, projectsService, storageService, mailService);
    }

    @Test
    public void testOnExecuteSwallowsSendMailException() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder()
                .name("quotaProject")
                .projectKey("quotaProject")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true)
                .build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(1174000000L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        when(coreAddons.getPlatformAdminUsersEmails()).thenReturn(List.of("platform_admin@jfrog.com"));
        User projectAdmin = new UserResponse("projectAdmin").email("project_admin@jfrog.com");
        when(projectsService.getProjectAdmins(project)).thenReturn(Set.of(projectAdmin));

        doThrow(new EmailException("smtp down")).when(mailService)
                .sendMail(any(String[].class), anyString(), anyString());

        // should not throw even when sendMail fails
        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(mailService, times(1)).sendMail(
                argThat(recipients -> Set.of(recipients).contains("project_admin@jfrog.com")
                        && Set.of(recipients).contains("platform_admin@jfrog.com")),
                eq("The quotaProject Project has reached its storage limit"),
                anyString());
    }

    @Test
    public void testOnExecuteSendsOnlyToPlatformAdminsWhenNoProjectAdminEmails() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder()
                .name("noProjAdminMail")
                .projectKey("noProjAdminMail")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true)
                .build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(1174000000L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        when(coreAddons.getPlatformAdminUsersEmails()).thenReturn(List.of("platform_admin@jfrog.com"));
        when(projectsService.getProjectAdmins(project)).thenReturn(Set.of());

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(mailService).sendMail(
                argThat(recipients -> recipients.length == 1 && "platform_admin@jfrog.com".equals(recipients[0])),
                eq("The noProjAdminMail Project has reached its storage limit"),
                anyString());
        verify(mailService, times(1)).sendMail(any(String[].class), anyString(), anyString());
    }

    @Test
    public void testOnExecuteSendsOnlyToProjectAdminsWhenNoPlatformAdminEmails() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder()
                .name("noPlatformAdminMail")
                .projectKey("noPlatformAdminMail")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true)
                .build();
        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(1174000000L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        when(coreAddons.getPlatformAdminUsersEmails()).thenReturn(List.of());
        User projectAdmin = new UserResponse("projectAdmin").email("project_admin@jfrog.com");
        when(projectsService.getProjectAdmins(project)).thenReturn(Set.of(projectAdmin));

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(mailService).sendMail(
                argThat(recipients -> recipients.length == 1 && "project_admin@jfrog.com".equals(recipients[0])),
                eq("The noPlatformAdminMail Project has reached its storage limit"),
                anyString());
        verify(mailService, times(1)).sendMail(any(String[].class), anyString(), anyString());
    }

    @Test
    public void testOnExecuteWithEmptyEmail() throws JobExecutionException {
        JobExecutionContext executionContext = mock(JobExecutionContext.class);
        ProjectEntity project = ProjectEntity.builder()
                .name("projectWithEmptyEmail")
                .projectKey("projectWithEmptyEmail")
                .softLimit(false)
                .storageQuotaBytes(1074000000)
                .storageQuotaEmailNotification(true).build();

        when(projectsService.getAllProjectsEntities()).thenReturn(List.of(project));
        when(storageService.getProjectStorageUsage(project.getProjectKey())).thenReturn(1174000000L);
        when(storageService.getAllProjectsNotifiedOnStorageQuota()).thenReturn(new HashMap<>());
        when(coreAddons.getPlatformAdminUsersEmails()).thenReturn(List.of());
        User projectAdmin = new UserResponse("projectAdmin").email("");
        when(projectsService.getProjectAdmins(project)).thenReturn(Set.of(projectAdmin));

        projectStorageQuotaNotificationJob.onExecute(executionContext);

        verify(projectsService).getAllProjectsEntities();
        verify(storageService).getProjectStorageUsage(project.getProjectKey());
        verify(storageService).getAllProjectsNotifiedOnStorageQuota();
        verify(storageService).updateProjectStorageQuotaNotificationTimestamp(Set.of(project.getName()));
        verify(coreAddons).getPlatformAdminUsersEmails();
        verify(projectsService).getProjectAdmins(project);
        verify(mailService, times(0)).sendMail(any(String[].class), anyString(), anyString());
    }
}