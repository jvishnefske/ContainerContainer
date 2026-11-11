package org.artifactory.backup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.mail.MailService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.EmailException;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author batelt
 */
@Slf4j
class BackupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private final List<String> emailAddresses = Arrays.asList("a@a.com", "b@b.com", "c@c.com");
    private BasicStatusHolder statusHolder;
    private BackupServiceImpl backupService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManagerMock;
    @Mock
    private MailService mailService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor configDescriptor;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private ArtifactoryHome artifactoryHome;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeEach() {
        autoCloseable = openMocks(this);
        backupService = new BackupServiceImpl(
                centralConfigService, repositoryService, mailService, addonsManagerMock, null);
        mockCoreAddons(addonsManagerMock);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getServerUrlForEmail()).thenReturn("URLXYZ");
        statusHolder = new BasicStatusHolder();
        statusHolder.error("status", log);
        when(context.getArtifactoryHome()).thenReturn(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testSendMailToMalformedAdminMailAddress() throws Exception {
        doThrow(new EmailException("Test message: b@b.com is Malformed")).when(mailService)
                .sendMail(eq(new String[] {"b@b.com"}), any(), any());
        backupService.sendBackupErrorNotification("not_important", statusHolder);
        verifySendBackupErrorMailCalled();
    }

    @Test
    void testSendMailToAdmins() throws Exception {
        mockCoreAddons(addonsManagerMock);
        doNothing().when(mailService).sendMail(any(), any(), any());
        backupService.sendBackupErrorNotification("not_important", statusHolder);
        verifySendBackupErrorMailCalled();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBackupSystem(boolean shouldActOnMissionControl) {
        InternalArtifactoryContext ctx = mock(InternalArtifactoryContext.class);
        BackupDescriptor backup = new BackupDescriptor();
        backup.setEnabled(true);
        backup.setExportMissionControl(shouldActOnMissionControl);
        BackupServiceImpl backupServiceSpy = spy(new BackupServiceImpl(
                centralConfigService, repositoryService, mailService, addonsManagerMock, null));
        doAnswer(ans -> new HashSet<>()).when(backupServiceSpy).getBackedUpRepos(any());
        doAnswer(answer -> {
            ExportSettingsImpl argument = answer.getArgument(0, ExportSettingsImpl.class);
            assertEquals(argument.shouldActOnMissionControl(), shouldActOnMissionControl);
            return null;
        }).when(ctx).exportTo(any(ExportSettingsImpl.class));
        doAnswer(ans -> refEq(mock(File.class))).
                when(backupServiceSpy).getBackupDir(any());
        backupServiceSpy.backupSystem(ctx, backup);
        verify(ctx, times(1)).exportTo(any(ExportSettingsImpl.class));
    }

    @ParameterizedTest
    @MethodSource("localReposProvider")
    void testGetBackedUpRepos(Set<String> excludeRepos, Set<String> expectedResult) {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", RepoType.Generic);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", RepoType.Generic);
        LocalCacheRepoConfig localCacheRepoConfig = LocalCacheRepoConfig.create(remoteRepoConfig);
        ReleaseBundleRepoConfig releaseBundleRepoConfig = ReleaseBundleRepoConfig.create("release-bundle-repo");
        when(repositoryService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(
                new ArrayList<>(List.of(localRepoConfig, federatedRepoConfig, localCacheRepoConfig)));
        when(repositoryService.getReleaseBundleRepoConfigs()).thenReturn(List.of(releaseBundleRepoConfig));
        Set<String> backedUpRepos = backupService.getBackedUpRepos(excludeRepos);
        assertEquals(JsonUtils.getInstance().valueToString(expectedResult.stream().sorted()),
                JsonUtils.getInstance().valueToString(backedUpRepos.stream().sorted()));

    }

    @Test
    void testBackupSystemHomePath() {
        InternalArtifactoryContext ctx = mock(InternalArtifactoryContext.class);
        BackupDescriptor backup = new BackupDescriptor();
        backup.setEnabled(true);
        backup.setDir(getOrCreateArtifactoryHomeStub().getHomeDir());
        BackupServiceImpl backupServiceMock = mock(BackupServiceImpl.class, InvocationOnMock::callRealMethod);
        BasicStatusHolder basicStatusHolder = backupServiceMock.backupSystem(ctx, backup);
        assertTrue(basicStatusHolder.hasErrors());
    }

    @Test
    void testScheduleImmediateSystemBackupHomePath() {
        BackupDescriptor backup = new BackupDescriptor();
        backup.setEnabled(true);
        backup.setDir(getOrCreateArtifactoryHomeStub().getHomeDir());
        BackupServiceImpl backupServiceMock = mock(BackupServiceImpl.class, InvocationOnMock::callRealMethod);
        ImportExportStatusHolder importExportStatusHolder = new ImportExportStatusHolder();
        backupServiceMock.scheduleImmediateSystemBackup(backup, importExportStatusHolder);
        assertTrue(importExportStatusHolder.hasErrors());
    }

    @Test
    void testBackupSystemOutsideAllowList() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.backupAllowedPaths, "/tmp/jfrog-backup-allowed/");
        InternalArtifactoryContext ctx = mock(InternalArtifactoryContext.class);
        BackupDescriptor backup = new BackupDescriptor();
        backup.setEnabled(true);
        backup.setDir(new File("/var/log"));
        BackupServiceImpl backupServiceMock = mock(BackupServiceImpl.class, InvocationOnMock::callRealMethod);
        BasicStatusHolder status = backupServiceMock.backupSystem(ctx, backup);
        assertTrue(status.hasErrors());
        assertTrue(status.getStatusMsg().contains(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST),
                "Expected status to mention BACKUP_PATH_NOT_IN_ALLOWLIST, got: " + status.getStatusMsg());
    }

    @Test
    void testScheduleImmediateSystemBackupOutsideAllowList() {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.backupAllowedPaths, "/tmp/jfrog-backup-allowed/");
        BackupDescriptor backup = new BackupDescriptor();
        backup.setEnabled(true);
        backup.setDir(new File("/var/log"));
        BackupServiceImpl backupServiceMock = mock(BackupServiceImpl.class, InvocationOnMock::callRealMethod);
        ImportExportStatusHolder importExportStatusHolder = new ImportExportStatusHolder();
        backupServiceMock.scheduleImmediateSystemBackup(backup, importExportStatusHolder);
        assertTrue(importExportStatusHolder.hasErrors());
        String statusMsg = importExportStatusHolder.getStatusMsg();
        assertTrue(statusMsg.contains(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST),
                "Expected status to mention BACKUP_PATH_NOT_IN_ALLOWLIST, got: " + statusMsg);
    }


    @Test
    void isEnoughFreeSpace() {
        ExportSettingsImpl settings = mock(ExportSettingsImpl.class);
        assertTrue(backupService.isEnoughFreeSpace(settings));
    }

    private void mockCoreAddons(AddonsManager manager) {
        CoreAddons coreAddonsMock = mock(CoreAddons.class);
        when(manager.addonByType(CoreAddons.class)).thenReturn(coreAddonsMock);
        when(coreAddonsMock.getPlatformAdminUsersEmails()).thenReturn(emailAddresses);
    }

    private void verifySendBackupErrorMailCalled() {
        verify(mailService, times(emailAddresses.size())).sendMail(any(), any(), any());
        emailAddresses.forEach(emailAddress -> verify(mailService, times(1))
                .sendMail(eq(new String[] {"b@b.com"}), any(), any()));
    }

    private static Stream<Arguments> localReposProvider() {
        return Stream.of(
                Arguments.of(Set.of(),
                        Set.of("local-repo", "remote-repo-cache", "federated-repo", "release-bundle-repo")),
                Arguments.of(Set.of("local-repo"),
                        Set.of("remote-repo-cache", "federated-repo", "release-bundle-repo")),
                Arguments.of(Set.of("remote-repo"), Set.of("local-repo", "federated-repo", "release-bundle-repo")),
                Arguments.of(Set.of("federated-repo"),
                        Set.of("local-repo", "remote-repo-cache", "release-bundle-repo")),
                Arguments.of(Set.of("release-bundle-repo"),
                        Set.of("local-repo", "remote-repo-cache", "federated-repo")),
                Arguments.of(Set.of("local-repo", "remote-repo"), Set.of("federated-repo", "release-bundle-repo")),
                Arguments.of(Set.of("local-repo", "federated-repo"),
                        Set.of("remote-repo-cache", "release-bundle-repo")),
                Arguments.of(Set.of("local-repo", "release-bundle-repo"),
                        Set.of("remote-repo-cache", "federated-repo")),
                Arguments.of(Set.of("remote-repo", "federated-repo"), Set.of("local-repo", "release-bundle-repo")),
                Arguments.of(Set.of("remote-repo", "release-bundle-repo"), Set.of("local-repo", "federated-repo")),
                Arguments.of(Set.of("federated-repo", "release-bundle-repo"),
                        Set.of("local-repo", "remote-repo-cache")),
                Arguments.of(Set.of("local-repo", "remote-repo", "federated-repo", "release-bundle-repo"), Set.of())
        );
    }
}