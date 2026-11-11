package org.artifactory.lifecycle.converter.postinit.v118;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Arko Jyoti De
 */
public class NugetChocolateyCacheRetrievalPostInitConverterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final long MIN_CACHE_PERIOD_SECS = 14400;

    private NugetChocolateyCacheRetrievalPostInitConverter converter;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private SecurityService securityService;
    @Mock
    private CompoundVersionDetails source;
    @Mock
    private CompoundVersionDetails target;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void init() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        setStringSystemProperty(ConstantValues.nugetChocolateyMinCachePeriodEnabled, "true");
        converter = new NugetChocolateyCacheRetrievalPostInitConverter(null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testConvertUpdatesChocolateyRepoWithLowCachePeriod() {
        RemoteRepoConfig chocolateyRepo = createNugetRepo("choco-remote",
                "https://community.chocolatey.org/api/v2", 7200);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(chocolateyRepo));

        converter.convert(source, target);

        assertEquals(MIN_CACHE_PERIOD_SECS, chocolateyRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService).doAsSystem(any());
    }

    @Test
    void testConvertSkipsRepoAlreadyAtMinimum() {
        RemoteRepoConfig chocolateyRepo = createNugetRepo("choco-remote",
                "https://community.chocolatey.org/api/v2", MIN_CACHE_PERIOD_SECS);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(chocolateyRepo));

        converter.convert(source, target);

        assertEquals(MIN_CACHE_PERIOD_SECS, chocolateyRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertSkipsRepoAboveMinimum() {
        RemoteRepoConfig chocolateyRepo = createNugetRepo("choco-remote",
                "https://community.chocolatey.org/api/v2", 28800);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(chocolateyRepo));

        converter.convert(source, target);

        assertEquals(28800, chocolateyRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertSkipsNonChocolateyNugetRepo() {
        RemoteRepoConfig nonChocolatey = createNugetRepo("nuget-remote",
                "https://api.nuget.org/v3/index.json", 3600);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(nonChocolatey));

        converter.convert(source, target);

        assertEquals(3600, nonChocolatey.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertSkipsNonNugetRepoTypes() {
        RemoteRepoConfig mavenRepo = RemoteRepoConfig.create("maven-remote", RepoType.Maven);
        mavenRepo.getRepoTypeConfig().setUrl("https://community.chocolatey.org/api/v2");
        mavenRepo.getRepoTypeConfig().setRetrievalCachePeriodSecs(3600);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(mavenRepo));

        converter.convert(source, target);

        assertEquals(3600, mavenRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertUpdatesOnlyEligibleReposInMixedList() {
        RemoteRepoConfig chocolateyLow = createNugetRepo("choco-low",
                "https://community.chocolatey.org/api/v2", 3600);
        RemoteRepoConfig chocolateyHigh = createNugetRepo("choco-high",
                "https://chocolatey.org/api/v2", 28800);
        RemoteRepoConfig nugetOrg = createNugetRepo("nuget-org",
                "https://api.nuget.org/v3/index.json", 1800);
        RemoteRepoConfig mavenRepo = RemoteRepoConfig.create("maven-remote", RepoType.Maven);
        mavenRepo.getRepoTypeConfig().setUrl("https://repo1.maven.org");
        mavenRepo.getRepoTypeConfig().setRetrievalCachePeriodSecs(600);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(chocolateyLow, chocolateyHigh, nugetOrg, mavenRepo));

        converter.convert(source, target);

        assertEquals(MIN_CACHE_PERIOD_SECS, chocolateyLow.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        assertEquals(28800, chocolateyHigh.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        assertEquals(1800, nugetOrg.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        assertEquals(600, mavenRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService).doAsSystem(any());
    }

    @Test
    void testConvertHandlesEmptyRepoList() {
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(Collections.emptyList());

        converter.convert(source, target);

        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertDisabledByFeatureFlag() {
        setStringSystemProperty(ConstantValues.nugetChocolateyMinCachePeriodEnabled, "false");

        RemoteRepoConfig chocolateyRepo = createNugetRepo("choco-remote",
                "https://community.chocolatey.org/api/v2", 3600);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(chocolateyRepo));

        converter.convert(source, target);

        assertEquals(3600, chocolateyRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(repositoryConfigService, never()).getRepoConfigsByType(RepoDetailsType.REMOTE);
    }

    @Test
    void testConvertHandlesExceptionGracefully() {
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenThrow(new RuntimeException("Test exception"));

        converter.convert(source, target);

        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertChocolateyUrlCaseInsensitive() {
        RemoteRepoConfig upperCaseRepo = createNugetRepo("choco-upper",
                "https://Community.CHOCOLATEY.Org/api/v2", 1000);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(upperCaseRepo));

        converter.convert(source, target);

        assertEquals(MIN_CACHE_PERIOD_SECS, upperCaseRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
    }

    @Test
    void testConvertSkipsRepoWithNullUrl() {
        RemoteRepoConfig nullUrlRepo = RemoteRepoConfig.create("null-url", RepoType.NuGet);
        nullUrlRepo.getRepoTypeConfig().setRetrievalCachePeriodSecs(1000);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(nullUrlRepo));

        converter.convert(source, target);

        assertEquals(1000, nullUrlRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testConvertSkipsRepoWithBlankUrl() {
        RemoteRepoConfig blankUrlRepo = createNugetRepo("blank-url", "  ", 1000);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenReturn(List.of(blankUrlRepo));

        converter.convert(source, target);

        assertEquals(1000, blankUrlRepo.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        verify(securityService, never()).doAsSystem(any());
    }

    @Test
    void testAssertConversionPreconditionNoop() {
        converter.assertConversionPrecondition(null,
                null, null);
    }

    private RemoteRepoConfig createNugetRepo(String key, String url, long cachePeriod) {
        RemoteRepoConfig config = RemoteRepoConfig.create(key, RepoType.NuGet);
        config.getRepoTypeConfig().setUrl(url);
        config.getRepoTypeConfig().setRetrievalCachePeriodSecs(cachePeriod);
        return config;
    }
}
