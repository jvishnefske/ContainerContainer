package org.artifactory.repo.interceptor;

import org.artifactory.api.config.BaseUrlModel;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.validation.RepoUrlValidator;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class SmartRepoSelfPointingUrlInterceptorTest {

    private CentralConfigService centralConfigService;
    private SmartRepoSelfPointingUrlInterceptor interceptor;

    @BeforeEach
    void setUp() {
        centralConfigService = Mockito.mock(CentralConfigService.class);
        interceptor = new SmartRepoSelfPointingUrlInterceptor(centralConfigService);
        MutableCentralConfigDescriptor centralConfigDescriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
    }

    @Test
    void onBeforeSave_skipsValidationIfBaseUrlIsNull() {
        RepoConfigChanges changes = Mockito.mock(RepoConfigChanges.class);
        when(centralConfigService.getPlatformBaseUrl()).thenReturn(null);

        try (MockedStatic<RepoUrlValidator> mocked = Mockito.mockStatic(RepoUrlValidator.class)) {
            interceptor.onBeforeSave(changes);

            mocked.verify(() ->
                    RepoUrlValidator.validateNoSelfPointingUrl(any(), any(), any()), never());
        }
    }

    @Test
    void onBeforeSave_skipsValidationIfBaseUrlIsInvalid() {
        RepoConfigChanges changes = Mockito.mock(RepoConfigChanges.class);
        when(centralConfigService.getPlatformBaseUrl()).thenReturn(new BaseUrlModel("invalid-base-url"));

        try (MockedStatic<RepoUrlValidator> mocked = Mockito.mockStatic(RepoUrlValidator.class)) {
            interceptor.onBeforeSave(changes);

            mocked.verify(() ->
                    RepoUrlValidator.validateNoSelfPointingUrl(any(), any(), any()), never());
        }
    }

    @Test
    void onBeforeSave_skipsValidationIfBaseUrlStringIsNull() {
        RepoConfigChanges changes = Mockito.mock(RepoConfigChanges.class);
        BaseUrlModel baseUrlModel = Mockito.mock(BaseUrlModel.class);
        when(centralConfigService.getPlatformBaseUrl()).thenReturn(baseUrlModel);
        when(baseUrlModel.getBaseUrl()).thenReturn(null);

        try (MockedStatic<RepoUrlValidator> mocked = Mockito.mockStatic(RepoUrlValidator.class)) {
            interceptor.onBeforeSave(changes);

            mocked.verify(() ->
                    RepoUrlValidator.validateNoSelfPointingUrl(any(), any(), any()), never());
        }
    }

    @Test
    void onBeforeSave_skipsValidationForLocalRepo() {
        String baseServerHost = "localhost";
        String repoKey = "my-repo";
        RepoType packageType = RepoType.Maven;
        String url = "http://localhost:8081/artifactory/my-repo";

        BaseUrlModel baseUrlModel = Mockito.mock(BaseUrlModel.class);
        when(baseUrlModel.getBaseUrl()).thenReturn("http://" + baseServerHost + ":8081/artifactory");

        when(centralConfigService.getPlatformBaseUrl()).thenReturn(baseUrlModel);

        RemoteRepoConfig repoConfig = Mockito.mock(RemoteRepoConfig.class);
        Mockito.when(repoConfig.getUrl()).thenReturn(url);
        Mockito.when(repoConfig.getKey()).thenReturn(repoKey);
        Mockito.when(repoConfig.getPackageType()).thenReturn(packageType);

        RepoConfigChange change = Mockito.mock(RepoConfigChange.class);
        Mockito.when(change.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_CREATE);
        Mockito.when(change.getNewRepoConfig()).thenReturn(repoConfig);
        Mockito.when(change.getRepoType()).thenReturn(RepoDetailsType.LOCAL);

        RepoConfigChanges changes = new RepoConfigChanges(change);

        try (MockedStatic<RepoUrlValidator> mocked = Mockito.mockStatic(RepoUrlValidator.class)) {
            interceptor.onBeforeSave(changes);

            mocked.verify(() ->
                    RepoUrlValidator.validateNoSelfPointingUrl(baseServerHost, repoKey, url), never());
        }
    }

    @Test
    void validateUrl_invokesValidateNoSelfPointingUrl() {
        String baseServerHost = "localhost";
        String repoKey = "my-repo";
        RepoType packageType = RepoType.Maven;
        String url = "http://localhost:8081/artifactory/my-repo";

        BaseUrlModel baseUrlModel = Mockito.mock(BaseUrlModel.class);
        when(baseUrlModel.getBaseUrl()).thenReturn("http://" + baseServerHost);
        when(centralConfigService.getPlatformBaseUrl()).thenReturn(baseUrlModel);

        RemoteRepoConfig repoConfig = Mockito.mock(RemoteRepoConfig.class);
        Mockito.when(repoConfig.getUrl()).thenReturn(url);
        Mockito.when(repoConfig.getKey()).thenReturn(repoKey);
        Mockito.when(repoConfig.getPackageType()).thenReturn(packageType);

        RepoConfigChange change = Mockito.mock(RepoConfigChange.class);
        Mockito.when(change.getConfigAction()).thenReturn(RepoConfigChange.ConfigAction.REPO_CREATE);
        Mockito.when(change.getNewRepoConfig()).thenReturn(repoConfig);
        Mockito.when(change.getRepoType()).thenReturn(RepoDetailsType.REMOTE);

        RepoConfigChanges changes = new RepoConfigChanges(change);

        try (MockedStatic<RepoUrlValidator> mocked = Mockito.mockStatic(RepoUrlValidator.class)) {
            interceptor.onBeforeSave(changes);

            mocked.verify(() ->
                    RepoUrlValidator.validateNoSelfPointingUrl(baseServerHost, repoKey, url)
            );
        }
    }

    @Test
    void validateUrl_doesNothingIfRepoConfigIsNull() {
        RepoConfigChange change = Mockito.mock(RepoConfigChange.class);
        when(change.getNewRepoConfig()).thenReturn(null);

        interceptor.onBeforeSave(Mockito.mock(RepoConfigChanges.class));
    }
}
