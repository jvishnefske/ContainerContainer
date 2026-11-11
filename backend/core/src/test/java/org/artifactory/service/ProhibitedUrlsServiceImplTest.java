package org.artifactory.service;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.interceptor.modle.AllowedRemoteUrlsConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.QuartzTask;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.CronExpression;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Collections;

import static org.artifactory.service.ProhibitedUrlsServiceImpl.ALLOWED_URLS_SOURCE_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProhibitedUrlsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    TaskService taskService;
    @Mock
    InternalRepositoryService repositoryService;
    @Mock
    ConfigsService configsService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    HaAddon haAddon;
    @Captor
    ArgumentCaptor<Task> taskCaptor;

    ProhibitedUrlsServiceImpl prohibitedUrlsService;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    private void setupService(String enable) {
        setStringSystemProperty(ConstantValues.enableRemoteProhibitedURLs, enable);
        prohibitedUrlsService = spy(new ProhibitedUrlsServiceImpl());
        prohibitedUrlsService.setTaskService(taskService);
        prohibitedUrlsService.setRepositoryService(repositoryService);
        prohibitedUrlsService.setConfigsService(configsService);
        prohibitedUrlsService.setAddonsManager(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        setStringSystemProperty(ConstantValues.remoteProhibitedURLsCheckCron, "");
    }

    @Test
    void testOnContextCreatedWithEmptyDb() {
        setupService("true");
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(null);
        prohibitedUrlsService.onContextCreated();

        verify(taskService, times(2)).startTask(taskCaptor.capture(), eq(false));
        taskCaptor.getAllValues().forEach(task -> assertInstanceOf(QuartzTask.class, task));
        assertNull(prohibitedUrlsService.getAllowedRemoteUrlsConfig());
        verify(configsService, never()).addOrUpdateConfig(any(ConfigsServiceKey.class), any(), anyLong());
    }

    @Test
    void testDisabledDoesNothing() {
        setupService("false");
        prohibitedUrlsService.fetchAllowedRemoteUrls();
        assertNull(prohibitedUrlsService.getAllowedRemoteUrlsConfig());
    }

    @Test
    void testLoadsFromDbWhenPresent() {
        setupService("true");
        AllowedRemoteUrlsConfig dbConfig = new AllowedRemoteUrlsConfig();
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(dbConfig);
        prohibitedUrlsService.onContextCreated();

        assertEquals(dbConfig, prohibitedUrlsService.getAllowedRemoteUrlsConfig());
        verify(configsService, never()).addOrUpdateConfig(any(ConfigsServiceKey.class), any(), anyLong());
    }

    @Test
    void testBuildRandomQuartzExp() {
        setupService("true");
        String cronExp = prohibitedUrlsService.buildRandomQuartzExp();
        assertNotNull(cronExp);
        assertFalse(cronExp.isEmpty());
        assertTrue(CronExpression.isValidExpression(cronExp));

        setStringSystemProperty(ConstantValues.remoteProhibitedURLsCheckCron, "0 32 1 ? * *");
        cronExp = prohibitedUrlsService.buildRandomQuartzExp();
        assertEquals("0 32 1 ? * *", cronExp);
    }

    @Test
    void testFetchUpdatesConfigWhenChanged() throws IOException, InterruptedException {
        setupService("true");
        AllowedRemoteUrlsConfig initialConfig = new AllowedRemoteUrlsConfig();
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(initialConfig);
        prohibitedUrlsService.onContextCreated();

        String newJson = """
                {
                    "globallyAllowedUrls": {
                        "https://new-registry.io": ["Npm"]
                    }
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.body()).thenReturn(newJson);
        doReturn(response).when(prohibitedUrlsService).getResponseFromUrl(ALLOWED_URLS_SOURCE_URL);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(Collections.emptyList());

        prohibitedUrlsService.fetchAllowedRemoteUrls();

        AllowedRemoteUrlsConfig config = prohibitedUrlsService.getAllowedRemoteUrlsConfig();
        assertNotNull(config);
        assertTrue(config.getGloballyAllowedUrls().containsKey("https://new-registry.io"));
    }

    @Test
    void testFetchDoesNotUpdateWhenSameChecksum() throws IOException, InterruptedException {
        setupService("true");
        String json = """
                {"globallyAllowedUrls": {}}
                """;
        AllowedRemoteUrlsConfig initialConfig = new AllowedRemoteUrlsConfig();
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(initialConfig);
        prohibitedUrlsService.onContextCreated();

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.body()).thenReturn(json);
        doReturn(response).when(prohibitedUrlsService).getResponseFromUrl(ALLOWED_URLS_SOURCE_URL);

        prohibitedUrlsService.fetchAllowedRemoteUrls();

        assertEquals(initialConfig, prohibitedUrlsService.getAllowedRemoteUrlsConfig());
    }

    @Test
    void testFetchHandlesErrorGracefully() throws IOException, InterruptedException {
        setupService("true");
        AllowedRemoteUrlsConfig existingConfig = new AllowedRemoteUrlsConfig();
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(existingConfig);
        prohibitedUrlsService.onContextCreated();

        doThrow(new IOException("network error"))
                .when(prohibitedUrlsService).getResponseFromUrl(ALLOWED_URLS_SOURCE_URL);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(Collections.emptyList());

        prohibitedUrlsService.fetchAllowedRemoteUrls();

        assertEquals(existingConfig, prohibitedUrlsService.getAllowedRemoteUrlsConfig());
    }

    @Test
    void testOfflineEnforcementOnInvalidRepo() throws IOException, InterruptedException {
        setupService("true");
        AllowedRemoteUrlsConfig initialConfig = new AllowedRemoteUrlsConfig();
        initialConfig.getGloballyAllowedUrls().put("https://registry.npmjs.org",
                new java.util.HashSet<>(java.util.Set.of("Npm")));
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(initialConfig);
        prohibitedUrlsService.onContextCreated();

        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(typeConfig.getUrl()).thenReturn("https://registry.npmjs.org");
        when(typeConfig.isOffline()).thenReturn(false);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repoConfig.getKey()).thenReturn("docker-remote");
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(Collections.singletonList(repoConfig));

        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        doReturn(resp).when(prohibitedUrlsService).getResponseFromUrl(ALLOWED_URLS_SOURCE_URL);

        prohibitedUrlsService.fetchAllowedRemoteUrls();

        verify(typeConfig).setOffline(true);
        verify(repositoryService).updateRepoConfig(repoConfig);
    }

    @Test
    void testSkipsAlreadyOfflineRepos() {
        setupService("true");
        AllowedRemoteUrlsConfig initialConfig = new AllowedRemoteUrlsConfig();
        initialConfig.getGloballyAllowedUrls().put("https://registry.npmjs.org",
                new java.util.HashSet<>(java.util.Set.of("Npm")));
        when(configsService.getConfig(
                eq(ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS),
                eq(AllowedRemoteUrlsConfig.class)))
                .thenReturn(initialConfig);
        prohibitedUrlsService.onContextCreated();

        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(typeConfig.getUrl()).thenReturn("https://registry.npmjs.org");
        when(typeConfig.isOffline()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(Collections.singletonList(repoConfig));

        prohibitedUrlsService.fetchAllowedRemoteUrls();

        verify(typeConfig, never()).setOffline(true);
    }
}
