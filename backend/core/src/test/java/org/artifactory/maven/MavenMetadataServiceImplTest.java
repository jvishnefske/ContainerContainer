package org.artifactory.maven;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoLayoutBuilder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.util.RepoLayoutUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

@Test
public class MavenMetadataServiceImplTest {

    @Mock
    private InternalArtifactoryContext artifactoryContext;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private MavenMetadataService mavenMetadataService;
    @Mock
    private CentralConfigService configService;

    private MavenMetadataServiceImpl service;
    private AutoCloseable mocks;

    @BeforeMethod
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        service = spy(new MavenMetadataServiceImpl());
        service.setAddonsManager(addonsManager);
        service.setRepoService(repositoryService);

        doReturn(mavenMetadataService).when(artifactoryContext).beanForType(MavenMetadataService.class);
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getListProperty(ConstantValues.caseInsensitivePackages))
                .thenReturn(new ArrayList<>(List.of("nuget")));
        when(home.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testCalculateMavenPluginMetadata() {
        // given
        RepoPath repoPath = new RepoPathImpl("local_repo",
                "org/codehaus/mojo/maven-buildnumber-plugin/0.9.4/maven-buildnumber-plugin-0.9.4.pom", false);
        MavenMetadataWorkItem workItem = new MavenMetadataWorkItem(repoPath, false, true);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Maven);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Maven);
        LocalRepo localRepo = new DbLocalRepo(localRepoDescriptor, localRepoConfig, repositoryService, null,
                configService);


        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        when(repositoryService.localOrFederatedRepositoryByKey(repoPath.getRepoKey())).thenReturn(localRepo);

        MavenMetadataCalculator mavenMetadataCalculator = mock(MavenMetadataCalculator.class);
        when(service.getMavenMetadataCalculator(workItem, RepoType.Maven)).thenReturn(mavenMetadataCalculator);

        // when
        service.calculateMavenMetadata(workItem);

        // then
        verify(addonsManager).getArtifactoryRunningMode();
        verify(repositoryService).localOrFederatedRepositoryByKey(repoPath.getRepoKey());
        verify(service).getMavenMetadataCalculator(workItem, RepoType.Maven);
        verify(mavenMetadataCalculator).calculate();
        verify(mavenMetadataService).calculateMavenPluginsMetadataAsync(any());

        // given
        clearInvocations(addonsManager, repositoryService, service, mavenMetadataCalculator, mavenMetadataService);
        workItem = new MavenMetadataWorkItem(repoPath, false, false);
        when(service.getMavenMetadataCalculator(workItem, RepoType.Maven)).thenReturn(mavenMetadataCalculator);

        // when
        service.calculateMavenMetadata(workItem);

        // then
        verify(addonsManager).getArtifactoryRunningMode();
        verify(repositoryService).localOrFederatedRepositoryByKey(repoPath.getRepoKey());
        verify(service).getMavenMetadataCalculator(workItem, RepoType.Maven);
        verify(mavenMetadataCalculator).calculate();
        verifyNoInteractions(mavenMetadataService);
    }

    @Test(dataProvider = "skipCalculationDataProvider")
    public void TestSkipCalculation(RepoType type, RepoLayout repoLayout, ArtifactoryRunningMode runningModel,
            boolean expected) {
        assertEquals(service.skipCalculation(type, repoLayout, runningModel), expected,
                "Layout = " + repoLayout.getName());
    }

    @DataProvider(name = "skipCalculationDataProvider")
    public Object[][] skipCalculationDataProvider() {
        return new Object[][] {
                // For backward comparability Maven indexing is supported on repositories with the right layout
                // even if they are not Maven family
                {RepoType.Generic, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.OSS, false},
                {RepoType.Generic, RepoLayoutUtils.MAVEN_1_DEFAULT, ArtifactoryRunningMode.OSS, true},
                {RepoType.Gradle, RepoLayoutUtils.MAVEN_1_DEFAULT, ArtifactoryRunningMode.OSS, false},

                // Repos with null type should always be indexed - as used to be in the old code.
                {null, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.PRO, false},
                {null, RepoLayoutUtils.MAVEN_1_DEFAULT, ArtifactoryRunningMode.PRO, false},
                {null, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.JCR, false},

                // Maven family should never be skipped - even at wrong layout
                {RepoType.Ivy,
                        new RepoLayoutBuilder()
                                .name("FakeLayout").build(),
                        ArtifactoryRunningMode.PRO, false},

                // Maven 2 layout should be ignored if the license is not right and the repo is not Maven family
                {RepoType.Generic, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.JCR, true},
                {RepoType.Generic, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.CONAN, true},

                // Maven family should never be skipped - even at wrong license
                {RepoType.Maven, RepoLayoutUtils.MAVEN_2_DEFAULT, ArtifactoryRunningMode.JCR, false}
        };
    }

    @Test(dataProvider = "toPackageTypeDataProvider")
    public void testToPackageType(RepoType repoType, PackageType expected) {
        assertEquals(MavenMetadataServiceImpl.toPackageType(repoType), expected);
    }

    @DataProvider(name = "toPackageTypeDataProvider")
    public Object[][] toPackageTypeDataProvider() {
        return new Object[][] {
                // Maven-group repo types map to their PackageType counterparts so metrics are tagged correctly.
                {RepoType.Maven, PackageType.MAVEN},
                {RepoType.Ivy, PackageType.IVY},
                {RepoType.Gradle, PackageType.GRADLE},
                {RepoType.SBT, PackageType.SBT},
                {RepoType.P2, PackageType.P2},

                // Non-Maven-group types must return null even when the calculator runs on them via the
                // legacy MAVEN_2_DEFAULT-layout escape hatch
                {RepoType.Generic, null},
                {RepoType.Docker, null},
                {RepoType.NuGet, null},
                {RepoType.CocoaPods, null},
                {RepoType.Debian, null},
                {RepoType.Composer, null},
                {RepoType.Npm, null},

                // Null repo type (legacy "type unknown -> always run" branch) must also suppress metrics.
                {null, null}
        };
    }
}
