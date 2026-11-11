package org.artifactory.repo.validator.remote;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.artifactory.descriptor.repo.RepoType.AIEditorExtensions;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartRemoteUrlValidatorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private PackageSpecification packageSpecification;
    private SmartRemoteUrlValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new SmartRemoteUrlValidator(packageSpecification);
    }

    private void bindMockHomeWithAutoBlockFlag(boolean autoBlockEnabled) {
        ArtifactoryHome.unbind();
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(ConstantValues.autoBlockInvalidSmartRepoUrl))
                .thenReturn(autoBlockEnabled);
        ArtifactoryHome mockHome = mock(ArtifactoryHome.class);
        when(mockHome.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(mockHome);
    }

    @Test
    void validate_doesNotThrow_whenUrlIsNull() {
        // url is null by default
        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-null-url", Generic);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_throwsWhenArtifactoryUrlRequiresPackagePrefixButNotSet_andAutoBlockEnabled() {
        bindMockHomeWithAutoBlockFlag(true);

        String url = "https://artifactory.example.com/artifactory/remote-repo";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-npm-missing-prefix", Npm);
        remote.getRepoTypeConfig().setUrl(url);

        when(packageSpecification.getPrefixForSmartUrl(Npm)).thenReturn("npm");

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        ConfigurationException ex =
                assertThrows(ConfigurationException.class, () -> validator.validate(changes));
        assertTrue(ex.getMessage().contains(url));
        assertTrue(ex.getMessage().toLowerCase().contains("npm"));
        assertTrue(ex.getMessage().contains("must contain the package-specific URL prefix"));
    }

    @Test
    void validate_doesNotThrow_whenAutoBlockInvalidSmartRepoUrlIsFalse_andUrlMissingPrefix() {
        bindMockHomeWithAutoBlockFlag(false);

        String url = "https://artifactory.example.com/artifactory/remote-repo";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-npm-missing-prefix", Npm);
        remote.getRepoTypeConfig().setUrl(url);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_doesNotThrow_whenPackageSpecificUrlIsSet() {
        String url = "https://artifactory.example.com/artifactory/api/npm/remote-repo";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-npm-with-prefix", Npm);
        remote.getRepoTypeConfig().setUrl(url);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_doesNotThrow_whenAutoBlockInvalidSmartRepoUrlIsTrue_andUrlHasPrefix() {
        bindMockHomeWithAutoBlockFlag(true);

        String url = "https://artifactory.example.com/artifactory/api/npm/remote-repo";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-npm-with-prefix", Npm);
        remote.getRepoTypeConfig().setUrl(url);

        when(packageSpecification.getPrefixForSmartUrl(Npm)).thenReturn("npm");

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_doesNotThrow_whenPackageTypeDoesNotRequirePrefix() {
        String url = "https://artifactory.example.com/artifactory/remote-repo";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-maven", Maven);
        remote.getRepoTypeConfig().setUrl(url);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_DoesNotThrow_invalidUrl() {
        String url = "ht!tp://in valid-url";

        RemoteRepoConfig remote = RemoteRepoConfig.create("repo-npm-invalid-url", Npm);
        remote.getRepoTypeConfig().setUrl(url);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void validate_nullNewRepoConfig() {
        bindMockHomeWithAutoBlockFlag(true);

        RepoConfigChange repoConfigChange =
                RepoConfigChange.deleteOf(RemoteRepoConfig.create("repo-to-delete", Generic));
        RepoConfigChanges repoConfigChanges = mock(RepoConfigChanges.class);
        when(repoConfigChanges.filterBy(REMOTE, REPO_CREATE, REPO_UPDATE)).thenReturn(List.of(repoConfigChange));

        assertDoesNotThrow(() -> validator.validate(repoConfigChanges));
    }

    @Test
    void validate_doesNotThrow_whenUrlDoesNotMatchStandardOrSmartFormat() {
        // URL doesn't match standard format (has additional path segments) or smart format
        // Should skip validation and not throw exception
        String url =
                "https://artifactory.example.com/artifactory/aieditorextensions-remote" +
                        "-nu0wruxo54/_apis/public/gallery";

        RemoteRepoConfig remote = RemoteRepoConfig.create("vscode-remote", AIEditorExtensions);
        remote.getRepoTypeConfig().setUrl(url);

        RepoConfigChange change = RepoConfigChange.createOf(remote);
        RepoConfigChanges changes = new RepoConfigChanges(change);
        // Should not throw exception - URL doesn't match either format, so validation is skipped
        assertDoesNotThrow(() -> validator.validate(changes));
    }
}