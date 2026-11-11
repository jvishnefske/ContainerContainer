package org.artifactory.ph.repo.interceptor;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.repo.provider.PackageRemoteRepoSettingsProvider;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PackageRemoteRepoConfigurationInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    private List<PackageRemoteRepoSettingsProvider> settingsProviders;
    private List<RepoConfigChange> configChanges;

    @Mock
    private RepoConfigChanges repoConfigChanges;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        settingsProviders = new ArrayList<>();
        configChanges = new ArrayList<>();
        mocks = MockitoAnnotations.openMocks(this);
        when(repoConfigChanges.filterBy(RepoDetailsType.REMOTE)).thenReturn(configChanges);
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocks.close();
    }

    static Stream<Object[]> configActionTestCases() {
        return Stream.of(new Object[][]{
                {RepoConfigChange.ConfigAction.REPO_CREATE, true},
                {RepoConfigChange.ConfigAction.REPO_UPDATE, false},
                {RepoConfigChange.ConfigAction.REPO_DELETE, false}
        });
    }

    @ParameterizedTest
    @MethodSource("configActionTestCases")
    public void testOnBeforeSaveShouldApplySettingsWhenApplicable(
            RepoConfigChange.ConfigAction configAction, boolean shouldApplySettings) {
        // Given
        PackageRemoteRepoSettingsProvider provider = mock(PackageRemoteRepoSettingsProvider.class);
        settingsProviders.add(provider);
        PackageRemoteRepoConfigurationInterceptor interceptor =
                new PackageRemoteRepoConfigurationInterceptor(settingsProviders);
        RepoConfigChange configChange = mock(RepoConfigChange.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

        when(repoConfigChanges.filterBy(configAction ,RepoDetailsType.REMOTE))
                .thenReturn((List.of(configChange)));
        when(configChange.getPackageType()).thenReturn(RepoType.Npm);
        when(configChange.getNewRepoConfig()).thenReturn(remoteRepoConfig);
        when(provider.isPackageTypeSupported(remoteRepoConfig)).thenReturn(true);
        when(provider.isApplicable(remoteRepoConfig)).thenReturn(true);
        configChanges.add(configChange);

        // When
        interceptor.onBeforeSave(repoConfigChanges);

        // Then
        if (shouldApplySettings) {
            verify(provider, times(1)).isPackageTypeSupported(remoteRepoConfig);
            verify(provider, times(1)).isApplicable(remoteRepoConfig);
            verify(provider, times(1)).applySettings(remoteRepoConfig);
        } else {
            verify(provider, never()).isPackageTypeSupported(any());
            verify(provider, never()).isApplicable(any());
            verify(provider, never()).applySettings(any());
        }
    }

    @Test
    public void testOnBeforeSaveShouldNotApplySettingsWhenPackageTypeNotSupported() {
        // Given
        PackageRemoteRepoSettingsProvider provider = mock(PackageRemoteRepoSettingsProvider.class);
        settingsProviders.add(provider);
        PackageRemoteRepoConfigurationInterceptor interceptor =
                new PackageRemoteRepoConfigurationInterceptor(settingsProviders);
        RepoConfigChange configChange = mock(RepoConfigChange.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

        when(repoConfigChanges.filterBy(RepoConfigChange.ConfigAction.REPO_CREATE ,RepoDetailsType.REMOTE))
                .thenReturn((List.of(configChange)));
        when(configChange.getPackageType()).thenReturn(RepoType.Npm);
        when(configChange.getNewRepoConfig()).thenReturn(remoteRepoConfig);
        when(provider.isPackageTypeSupported(remoteRepoConfig)).thenReturn(false);
        configChanges.add(configChange);

        // When
        interceptor.onBeforeSave(repoConfigChanges);

        // Then
        verify(provider, times(1)).isPackageTypeSupported(remoteRepoConfig);
        verify(provider, never()).isApplicable(remoteRepoConfig);
        verify(provider, never()).applySettings(any());
    }

    @Test
    public void testOnBeforeSaveShouldNotApplySettingsWhenNotApplicable() {
        // Given
        PackageRemoteRepoSettingsProvider provider = mock(PackageRemoteRepoSettingsProvider.class);
        settingsProviders.add(provider);
        PackageRemoteRepoConfigurationInterceptor interceptor = 
                new PackageRemoteRepoConfigurationInterceptor(settingsProviders);
        RepoConfigChange configChange = mock(RepoConfigChange.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

        when(repoConfigChanges.filterBy(RepoConfigChange.ConfigAction.REPO_CREATE ,RepoDetailsType.REMOTE))
                .thenReturn((List.of(configChange)));
        when(configChange.getPackageType()).thenReturn(RepoType.Npm);
        when(configChange.getNewRepoConfig()).thenReturn(remoteRepoConfig);
        when(provider.isPackageTypeSupported(remoteRepoConfig)).thenReturn(true);
        when(provider.isApplicable(remoteRepoConfig)).thenReturn(false);
        configChanges.add(configChange);

        // When
        interceptor.onBeforeSave(repoConfigChanges);

        // Then
        verify(provider, times(1)).isPackageTypeSupported(remoteRepoConfig);
        verify(provider, times(1)).isApplicable(remoteRepoConfig);
        verify(provider, never()).applySettings(any());
    }

}