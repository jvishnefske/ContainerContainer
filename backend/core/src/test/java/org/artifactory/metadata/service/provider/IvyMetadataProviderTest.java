package org.artifactory.metadata.service.provider;

import org.artifactory.api.module.ModuleInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class IvyMetadataProviderTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepoPath repoPath;
    @Mock
    private Properties properties;
    @Mock
    private Repo<?, ?> repo;

    private IvyMetadataProvider.IvyTypeSpecificMetadataTemplate template;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        template = new IvyMetadataProvider(repositoryService).new IvyTypeSpecificMetadataTemplate();
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
}