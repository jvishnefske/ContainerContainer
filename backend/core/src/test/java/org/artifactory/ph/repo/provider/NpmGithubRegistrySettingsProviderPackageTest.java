package org.artifactory.ph.repo.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.npmRemoteEnforceDefaultConfigurationEnabled;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NpmGithubRegistrySettingsProviderPackageTest extends ArtifactoryHomeBoundJUnitTest {

    @InjectMocks
    private NpmGithubRegistrySettingsProviderPackage provider;

    @Mock
    private RemoteRepoConfig config;

    @Mock
    private RemoteRepositoryRepoTypeConfig repoTypeConfig;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocks.close();
    }

    static Stream<Object[]> isPackageSupportedTestCases() {
        return Stream.of(new Object[][]{
                {RepoType.Npm, "TRUE", true},
                {RepoType.Npm, "FALSE", false},
                {RepoType.Maven, "TRUE", false},
                {RepoType.Maven, "FALSE", false},
                {null, "TRUE", false},
                {null, "FALSE", false}
        });
    }

    @ParameterizedTest
    @MethodSource("isPackageSupportedTestCases")
    public void testIsPackageTypeSupported(RepoType repoType, String isPackageTypeSupported, boolean expected) {
        this.setStringSystemProperty(npmRemoteEnforceDefaultConfigurationEnabled, isPackageTypeSupported);
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        boolean result = provider.isPackageTypeSupported(repoConfig);
        Assertions.assertEquals(
                expected,
                result,
                "isPackageTypeSupported should return " + expected + " for repoType: " + repoType +
                        " when feature flag is " + isPackageTypeSupported);
    }

    static Stream<Object[]> isApplicableTestCases() {
        return Stream.of(new Object[][]{
                // url, expected result
                {"https://npm.pkg.github.com/", true},
                {"https://npm.pkg.github.com/repo-name", true},
                {"https://dummy.npm.pkg.github.com/", false},
                {"https://dummy.pkg.github.com/", false},
                {"https://registry.npmjs.org", false},
                {null, false}
        });
    }

    @ParameterizedTest
    @MethodSource("isApplicableTestCases")
    public void testIsApplicable(String url, boolean expected) {
        when(repoTypeConfig.getUrl()).thenReturn(url);
        boolean result = provider.isApplicable(config);
        Assertions.assertEquals(expected, result, "isApplicable should return " + expected + " for URL: " + url);
    }

    @Test
    public void testApplySettings() {
        provider.applySettings(config);
        verify(repoTypeConfig).setBypassHeadRequests(true);
    }
}