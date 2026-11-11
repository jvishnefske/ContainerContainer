package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventListener;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventType;
import com.jfrog.ph.acl.listener.config.PackageRepoConfigListenerContext;
import com.jfrog.ph.acl.listener.config.RepoConfigChangeType;
import com.jfrog.ph.acl.service.PackageRepoService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PackageConfigurationChangesInterceptorImplTest {

    @Mock
    PackageConfigChangedEventListener packageConfigChangedEventListener;
    @Mock
    PackageRepoService packageRepoService;
    @Mock
    CentralConfigDescriptor oldDescriptor;
    @Mock
    CentralConfigDescriptor newDescriptor;
    PackageConfigurationChangesInterceptorImpl packageConfigurationChangesInterceptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        packageConfigurationChangesInterceptor =
                spy(new PackageConfigurationChangesInterceptorImpl(packageConfigChangedEventListener,
                        packageRepoService));
    }

    @AfterEach
    void cleanup() throws Exception {
        verifyNoMoreInteractions(packageConfigChangedEventListener);
        mocks.close();
    }


    @Test
    void testAfterGlobalSaveWithoutChanges() {
        packageConfigurationChangesInterceptor.onAfterSave(newDescriptor, null);
        verify(this.packageConfigChangedEventListener, never()).onGlobalConfigChange(any());
    }

    @Test
    void testAfterGlobalConfigSave() {
        when(oldDescriptor.getUrlBase()).thenReturn("http://localhost:8082");
        PackageConfigChangedEventListener packageConfigChangedEventListener =
                mock(PackageConfigChangedEventListener.class);
        doAnswer(invocationOnMock -> {
            assertEquals(PackageConfigChangedEventType.BASE_URL, invocationOnMock.getArgument(0));
            return null;
        }).when(packageConfigChangedEventListener).onGlobalConfigChange(any());

        packageConfigurationChangesInterceptor.onAfterSave(newDescriptor, oldDescriptor);
        verify(this.packageConfigChangedEventListener, times(1)).onGlobalConfigChange(any());
        verifyNoMoreInteractions(packageConfigChangedEventListener);
    }

    @Test
    void testAfterRepoConfigSave() throws PackageException {
        PackageConfigChangedEventListener packageConfigChangedEventListener =
                mock(PackageConfigChangedEventListener.class);
        doAnswer(invocationOnMock -> {
            assertEquals(PackageConfigChangedEventType.BASE_URL, invocationOnMock.getArgument(0));
            return null;
        }).when(packageConfigChangedEventListener).onRepoConfigChange(any());
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        RepoConfigChange repoConfigChange = new RepoConfigChange("test",
                RepoConfigChange.ConfigAction.REPO_CREATE, null, null);
        repoConfigChanges.put(RepoDetailsType.LOCAL, List.of(repoConfigChange));
        packageConfigurationChangesInterceptor.onAfterSave(repoConfigChanges);
        verify(this.packageConfigChangedEventListener, times(1)).onRepoConfigChange(any());
        verifyNoMoreInteractions(packageConfigChangedEventListener);
    }

    @ParameterizedTest
    @MethodSource("testAfterRepoSaveWithoutChangesProvider")
    void testAfterRepoSaveWithoutChanges(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges)
            throws PackageException {
        packageConfigurationChangesInterceptor.onAfterSave(repoConfigChanges);
        verify(this.packageConfigChangedEventListener, never()).onRepoConfigChange(any());
    }

    static Stream<Arguments> testAfterRepoSaveWithoutChangesProvider() {
        Map<RepoDetailsType, List<RepoConfigChange>> emptyConfig = new HashMap<>();
        Map<RepoDetailsType, List<RepoConfigChange>> emptyRepoConfigChange = new HashMap<>();
        RepoConfigChange repoConfigChange = new RepoConfigChange("test", null,
                null, null);
        emptyRepoConfigChange.put(RepoDetailsType.LOCAL, List.of(repoConfigChange));

        return Stream.of(Arguments.of(emptyConfig),
                Arguments.of(emptyRepoConfigChange));
    }

    @Test
    void testAfterRemoteRepoChangeUrlConfigSave() throws PackageException {
        RemoteRepoConfig newRepoConfig = mockRemoteRepoConfig("https://new-url.com");
        RemoteRepoConfig oldRepoConfig = mockRemoteRepoConfig("https://old-url.com");
        doAnswer(invocationOnMock -> {
            PackageRepoConfigListenerContext context = invocationOnMock.getArgument(0);
            assertEquals(PackageConfigChangedEventType.REPO_UPDATE, context.packageConfigChangedEventType());
            assertEquals(1, context.configChanges().size());
            assertTrue(context.configChanges().contains(RepoConfigChangeType.REMOTE_URL));
            return null;
        }).when(packageConfigChangedEventListener).onRepoConfigChange(any());
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        RepoConfigChange repoConfigChange = new RepoConfigChange("test",
                RepoConfigChange.ConfigAction.REPO_UPDATE, newRepoConfig, oldRepoConfig);
        repoConfigChanges.put(RepoDetailsType.REMOTE, List.of(repoConfigChange));
        packageConfigurationChangesInterceptor.onAfterSave(repoConfigChanges);
        verify(packageConfigChangedEventListener, times(1)).onRepoConfigChange(any());
        verifyNoMoreInteractions(packageConfigChangedEventListener);
    }

    @Test
    void testAfterRepoChangeEncryptStates() throws PackageException {
        LocalRepoConfig newRepoConfig = mock(LocalRepoConfig.class);
        LocalRepoConfig oldRepoConfig = mock(LocalRepoConfig.class);
        Map<String, String> newPackageTypeConfig = Map.of("encryptStates", "true");
        Map<String, String> oldPackageTypeConfig = Map.of("encryptStates", "false");
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(newRepoConfig.getPackageType()).thenReturn(RepoType.TerraformBackend);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);

        doAnswer(invocationOnMock -> {
            PackageRepoConfigListenerContext context = invocationOnMock.getArgument(0);
            assertEquals(PackageConfigChangedEventType.REPO_UPDATE, context.packageConfigChangedEventType());
            assertEquals(1, context.configChanges().size());
            assertTrue(context.configChanges().contains(RepoConfigChangeType.TERRAFORM_BACKEND_ENCRYPT_STATE));
            return null;
        }).when(packageConfigChangedEventListener).onRepoConfigChange(any());
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        RepoConfigChange repoConfigChange = new RepoConfigChange("test",
                RepoConfigChange.ConfigAction.REPO_UPDATE, newRepoConfig, oldRepoConfig);
        repoConfigChanges.put(RepoDetailsType.LOCAL, List.of(repoConfigChange));
        packageConfigurationChangesInterceptor.onAfterSave(repoConfigChanges);
        verify(packageConfigChangedEventListener, times(1)).onRepoConfigChange(any());
        verifyNoMoreInteractions(packageConfigChangedEventListener);
    }

    private RemoteRepoConfig mockRemoteRepoConfig(String url) {
        RemoteRepositoryRepoTypeConfig oldRepoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(oldRepoTypeConfig.getUrl()).thenReturn(url);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(oldRepoTypeConfig);
        return remoteRepoConfig;
    }
}