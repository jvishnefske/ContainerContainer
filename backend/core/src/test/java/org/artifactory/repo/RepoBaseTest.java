package org.artifactory.repo;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.spring.InternalArtifactoryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
public class RepoBaseTest {

    @Mock
    private InternalArtifactoryContext contextMock;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(contextMock);
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getListProperty(ConstantValues.caseInsensitivePackages))
                .thenReturn(new ArrayList<>(List.of("nuget")));
        when(home.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testGlobalExcludeForVirtualRepo() {
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("repo");
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Generic);
        VirtualRepo repo = new VirtualRepo(descriptor,
                repoConfig, Mockito.mock(InternalRepositoryService.class), null);
        Assertions.assertEquals(0, repo.excludes.size());
    }

    @Test
    void testValidPathPattern() {
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("repo");
        descriptor.setType(RepoType.NuGet);

        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.NuGet);
        repoConfig.getBaseConfig().setExcludesPattern("**/*Abc**/*");
        RepoBase<VirtualRepoDescriptor, VirtualRepoConfig> repo = new VirtualRepo(descriptor,
                repoConfig, Mockito.mock(InternalRepositoryService.class), null);
        Assertions.assertTrue(repo.isPathPatternValid(repo.getRepoPath("repo"), "testpackage"));
    }

    @Test
    void testInValidPathPattern() {
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("repo");
        descriptor.setType(RepoType.NuGet);
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.NuGet);
        repoConfig.getBaseConfig().setExcludesPattern("**/*TestPackage*/**");
        RepoBase<VirtualRepoDescriptor, VirtualRepoConfig> repo = new VirtualRepo(descriptor,
                repoConfig, Mockito.mock(InternalRepositoryService.class), null);
        Assertions.assertFalse(repo.isPathPatternValid(repo.getRepoPath("repo"), "testpackage"));
    }
}
