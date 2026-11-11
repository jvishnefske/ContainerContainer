package org.artifactory.metadata.service.provider;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MavenMetadataProviderTest {
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepoPath repoPath;
    @Mock
    private Properties properties;
    @Mock
    private Repo<?, ?> repo;
    @Mock
    private AddonsManager addonsManager;

    private MavenMetadataProvider.MavenTypeSpecificMetadataTemplate template;
    private MavenMetadataProvider mavenMetadataProvider;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        mavenMetadataProvider = new MavenMetadataProvider(repositoryService, addonsManager);
        template = mavenMetadataProvider.new MavenTypeSpecificMetadataTemplate();
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
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

    @ParameterizedTest
    @CsvSource({
        "'com/example/my-app/1.0/my-app-1.0.pom', true, 'POM file should be identified as a lead artifact'",
        "'com/example/my-app/1.0/my-app-1.0.jar', false, 'JAR file should not be identified as a lead artifact'",
        "'com/example/my-app/1.0/my-app-1.0-build.pom', false, " +
                "'Build POM file should not be identified as a lead artifact'",
        "'com/example/my-app/1.0/my-app-1.0', false, " +
                "'File without extension should not be identified as a lead artifact'",
        "'com/example/my-app/1.0/my-app-1.0.xml', false, 'XML file should not be identified as a lead artifact'",
        "'com/example/my-app/1.0/my-app-1.0.war', false, 'WAR file should not be identified as a lead artifact'"
    })
    void testIsLeadArtifact(String filePath, boolean expectedResult, String description) {
        // Given
        when(repoPath.getPath()).thenReturn(filePath);
        
        // When
        boolean result = mavenMetadataProvider.isLeadArtifact(repoPath);
        
        // Then
        assertEquals(expectedResult, result, description);
    }
}
