package org.artifactory.model;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.DockerApiVersion;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.BOWER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_SPECS_URL;
import static org.artifactory.util.PackageTypeConfigFields.COMPOSER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_PROJECT_ID;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_TAG_RETENTION;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REPOSITORY_SUFFIX;
import static org.artifactory.util.PackageTypeConfigFields.SPECS_REPO_DOWNLOAD_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_PROVIDERS_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_TYPE;
import static org.artifactory.util.PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS;

/**
 * @author Mor Merhav
 */
class RemoteRepoConfigTest {

    @ParameterizedTest
    @EnumSource(RepoType.class)
    void mavenGroupDefaultValues(RepoType repoType) {
        if (repoType.isMavenGroup()) {
            RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", repoType);

            Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
            Assertions.assertEquals(1, packageTypeConfig.size());
            Assertions.assertTrue(packageTypeConfig.containsKey(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey()));

            String pomConsistencyCheck = packageTypeConfig.get(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey());
            Assertions.assertTrue(Boolean.parseBoolean(pomConsistencyCheck));
        }
    }

    @Test
    void dockerDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Docker);

        Assertions.assertEquals(RepoType.Docker, remoteRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(4, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(DOCKER_API_VERSION.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(DOCKER_TAG_RETENTION.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(BLOCK_PUSHING_SCHEMA_1.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(DOCKER_PROJECT_ID.getKey()));

        String dockerApiVersion = packageTypeConfig.get(DOCKER_API_VERSION.getKey());
        String dockerTagRetention = packageTypeConfig.get(DOCKER_TAG_RETENTION.getKey());
        String blockPushingSchema1 = packageTypeConfig.get(BLOCK_PUSHING_SCHEMA_1.getKey());
        String dockerProjectId = packageTypeConfig.get(DOCKER_PROJECT_ID.getKey());

        Assertions.assertEquals(DockerApiVersion.V2.name(), dockerApiVersion);
        Assertions.assertEquals("1", dockerTagRetention);
        Assertions.assertTrue(Boolean.parseBoolean(blockPushingSchema1));
        Assertions.assertEquals("", dockerProjectId);
    }

    @Test
    void bowerDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Bower);

        Assertions.assertEquals(RepoType.Bower, remoteRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        Assertions.assertEquals(1, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(BOWER_REGISTRY_URL.getKey()));

        String bowerRegistryUrl = packageTypeConfig.get(BOWER_REGISTRY_URL.getKey());
        Assertions.assertEquals("https://registry.bower.io", bowerRegistryUrl);
    }

    @Test
    void cocoaPodsDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.CocoaPods);

        Assertions.assertEquals(RepoType.CocoaPods, remoteRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        Assertions.assertEquals(4, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(COCOA_PODS_SPECS_URL.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(SPECS_REPO_DOWNLOAD_PROVIDER.getKey()));

        String cocoaPodsSpecsUrl = packageTypeConfig.get(COCOA_PODS_SPECS_URL.getKey());
        Assertions.assertEquals("https://github.com/CocoaPods/Specs", cocoaPodsSpecsUrl);

        String specsRepoDownloadProvider = packageTypeConfig.get(SPECS_REPO_DOWNLOAD_PROVIDER.getKey());
        Assertions.assertEquals(VcsGitProvider.GITHUB.name(), specsRepoDownloadProvider);

        assertVcsPackageConfig(packageTypeConfig);
    }

    @Test
    void composerPodsDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Composer);

        Assertions.assertEquals(RepoType.Composer, remoteRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        Assertions.assertEquals(3, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(COMPOSER_REGISTRY_URL.getKey()));

        String composerRegistryUrl = packageTypeConfig.get(COMPOSER_REGISTRY_URL.getKey());
        Assertions.assertEquals("https://packagist.org", composerRegistryUrl);

        assertVcsPackageConfig(packageTypeConfig);
    }

    @Test
    void pyPiDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Pypi);

        Assertions.assertEquals(RepoType.Pypi, remoteRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        Assertions.assertEquals(2, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(PYPI_REGISTRY_URL.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(PYPI_REPOSITORY_SUFFIX.getKey()));

        String pyPiRegistryUrl = packageTypeConfig.get(PYPI_REGISTRY_URL.getKey());
        String pyPiRepositorySuffix = packageTypeConfig.get(PYPI_REPOSITORY_SUFFIX.getKey());

        Assertions.assertEquals("https://pypi.org", pyPiRegistryUrl);
        Assertions.assertEquals("simple", pyPiRepositorySuffix);
    }

    @Test
    void goDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Go);

        Assertions.assertEquals(RepoType.Go, remoteRepoConfig.getPackageType());
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(3, packageTypeConfig.size());
        assertVcsPackageConfig(packageTypeConfig);
    }

    @Test
    void vcsDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.VCS);

        Assertions.assertEquals(RepoType.VCS, remoteRepoConfig.getPackageType());
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(2, packageTypeConfig.size());
        assertVcsPackageConfig(packageTypeConfig);
    }

    @Test
    void terraformDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Terraform);

        Assertions.assertEquals(RepoType.Terraform, remoteRepoConfig.getPackageType());
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(4, packageTypeConfig.size());

        Assertions.assertTrue(packageTypeConfig.containsKey(TERRAFORM_PROVIDERS_URL.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(TERRAFORM_REGISTRY_URL.getKey()));

        String terraformProvidersUrl = packageTypeConfig.get(TERRAFORM_PROVIDERS_URL.getKey());
        String terraformRegistryUrl = packageTypeConfig.get(TERRAFORM_REGISTRY_URL.getKey());

        Assertions.assertEquals("https://releases.hashicorp.com", terraformProvidersUrl);
        Assertions.assertEquals("https://registry.terraform.io", terraformRegistryUrl);

        assertVcsPackageConfig(packageTypeConfig);
    }

    @Test
    void vscodeDefaultValues() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.AIEditorExtensions);

        Assertions.assertEquals(RepoType.AIEditorExtensions, remoteRepoConfig.getPackageType());
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        Assertions.assertTrue(packageTypeConfig.containsKey(VSCODE_EXTERNAL_DEPENDENCIES_ENABLED.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS.getKey()));

        Assertions.assertEquals(
                VSCODE_EXTERNAL_DEPENDENCIES_ENABLED.getDefaultValue(),
                packageTypeConfig.get(VSCODE_EXTERNAL_DEPENDENCIES_ENABLED.getKey())
        );
        Assertions.assertEquals(
                VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS.getDefaultValue(),
                packageTypeConfig.get(VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS.getKey())
        );
    }

    @Test
    void blankRepoKeyThrowsException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RemoteRepoConfig.create(StringUtils.EMPTY, RepoType.Generic));
    }

    @ParameterizedTest
    @MethodSource("provideUrlsForTesting")
    void extractUrlWithoutCredentials(String url, String expectedUrlWithoutCredentials) {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Generic);
        String urlWithoutCredentials = remoteRepoConfig.extractUrlWithoutCredentials(url);
        Assertions.assertEquals(expectedUrlWithoutCredentials, urlWithoutCredentials);
    }

    @Test
    void extractUrlWithoutCredentialsWhenInvalidUrlProvidedReturnsNull() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Generic);
        Assertions.assertNull(remoteRepoConfig.extractUrlWithoutCredentials("htt:invalid-url"));
    }

    @Test
    void processUrlCredentialsExtractsAndRemovesCredentialsFromUrl() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://user:password@localhost:8080/repo");

        remoteRepoConfig.processUrlCredentials();

        Assertions.assertEquals("user", remoteRepoConfig.getRepoTypeConfig().getUsername());
        Assertions.assertEquals("password", remoteRepoConfig.getRepoTypeConfig().getPassword());
        Assertions.assertEquals("http://localhost:8080/repo", remoteRepoConfig.getRepoTypeConfig().getUrl());
    }

    @Test
    void processUrlCredentialsDoesNothingWhenNoCredentialsInUrl() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost:8080/repo");

        remoteRepoConfig.processUrlCredentials();

        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getUsername());
        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getPassword());
        Assertions.assertEquals("http://localhost:8080/repo", remoteRepoConfig.getRepoTypeConfig().getUrl());
    }

    @Test
    void processUrlCredentialsHandlesInvalidUrlGracefully() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("key", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("htt:invalid-url");

        remoteRepoConfig.processUrlCredentials();

        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getUsername());
        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getPassword());
        Assertions.assertEquals("htt:invalid-url", remoteRepoConfig.getRepoTypeConfig().getUrl());
    }

    private void assertVcsPackageConfig(Map<String, String> packageTypeConfig) {
        Assertions.assertTrue(packageTypeConfig.containsKey(VCS_GIT_PROVIDER.getKey()));
        Assertions.assertTrue(packageTypeConfig.containsKey(VCS_TYPE.getKey()));

        String vcsGitProvider = packageTypeConfig.get(VCS_GIT_PROVIDER.getKey());
        String vcsType = packageTypeConfig.get(VCS_TYPE.getKey());

        Assertions.assertEquals(VcsType.GIT.name(), vcsType);
        Assertions.assertEquals(VcsGitProvider.GITHUB.name(), vcsGitProvider);
    }

    static Stream<Arguments> provideUrlsForTesting() {
        return Stream.of(
                Arguments.of("http://admin:Password1@localhost:8080/conan-smart-remote",
                        "http://localhost:8080/conan-smart-remote"),
                Arguments.of("https://user:pass%40@domain.com/path", "https://domain.com/path"),
                Arguments.of("https://user:pass@server.com/resource?id", "https://server.com/resource"),
                Arguments.of("https://user:password@domain.com/path#fragment", "https://domain.com/path")
        );
    }

    @Test
    void isFederated_shouldReturnFalse_whenNoFederationMembers() {
        RemoteRepoConfig config = RemoteRepoConfig.create("test-remote", RepoType.Generic);
        Assertions.assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenFederationMembersExist() {
        RemoteRepoConfig config = RemoteRepoConfig.create("test-remote", RepoType.Generic);
        FederatedMember member1 = new FederatedMember();
        member1.setUrl("http://site1.example.com/remote-repo");
        FederatedMember member2 = new FederatedMember();
        member2.setUrl("http://site2.example.com/remote-repo");
        config.getBaseConfig().getFederationConfig().setMembers(List.of(member1, member2));
        Assertions.assertTrue(config.isFederated());
    }
}