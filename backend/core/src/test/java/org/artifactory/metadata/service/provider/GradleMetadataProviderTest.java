package org.artifactory.metadata.service.provider;

import org.artifactory.api.module.ModuleInfo;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GradleMetadataProviderTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepoPath repoPath;
    @Mock
    private Properties properties;
    @Mock
    private Repo<?, ?> repo;

    private GradleMetadataProvider.GradleTypeSpecificMetadataTemplate template;
    private GradleMetadataProvider gradleMetadataProvider;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        gradleMetadataProvider = new GradleMetadataProvider(repositoryService);
        template = gradleMetadataProvider.new GradleTypeSpecificMetadataTemplate();
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void isLeadArtifact() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        String path = "stam/path/to/file.pom";
        String repoKey = "repoKey";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        GradleMetadataProvider provider = new GradleMetadataProvider(repositoryService);

        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);

        when(repoDescriptor.isMavenRepoLayout()).thenReturn(true);
        when(repoDescriptor.getType()).thenReturn(RepoType.Gradle);
        assertTrue(provider.isLeadArtifact(repoPath));

        when(repoDescriptor.isMavenRepoLayout()).thenReturn(false);
        when(repoDescriptor.getType()).thenReturn(RepoType.Generic);
        assertTrue(provider.isLeadArtifact(repoPath));

        when(repoDescriptor.isMavenRepoLayout()).thenReturn(false);
        when(repoDescriptor.getType()).thenReturn(RepoType.Gradle);
        assertTrue(provider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @CsvSource({
            "'org/example/my-app/1.0/my-app-1.0.pom', true, 'POM file should be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0.jar', false, 'JAR file should not be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0-build.pom', false, " +
                    "'Build POM file should not be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0', false, " +
                    "'File without extension should not be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0.xml', false, 'XML file should not be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0.war', false, 'WAR file should not be identified as a lead artifact'",
            "'org/example/my-app/1.0/my-app-1.0.gradle', false, " +
                    "'Gradle file should not be identified as a lead artifact'"
    })
    void testIsLeadArtifact(String filePath, boolean expectedResult, String description) {
        // Given
        when(repoPath.getPath()).thenReturn(filePath);

        // When
        boolean result = gradleMetadataProvider.isLeadArtifact(repoPath);

        // Then
        assertEquals(expectedResult, result, description);
    }

    @ParameterizedTest
    @ValueSource(strings = {"example-module", "EXAMPLE-MODULE"})
    void testGetName(String artifactName) {
        ModuleInfo moduleInfo = new ModuleInfo(
                "org.example",
                artifactName,
                "2.17",
                "SNAPSHOT",
                "20200402.123134-8", null, "pom", null, Collections.emptyMap());
        when(repo.getItemModuleInfo(any())).thenReturn(moduleInfo);
        String name = template.getName(properties, repoPath);
        assertEquals("org.example:" + artifactName, name);
    }
}