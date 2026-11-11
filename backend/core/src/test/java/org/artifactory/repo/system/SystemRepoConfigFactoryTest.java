package org.artifactory.repo.system;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class SystemRepoConfigFactoryTest {

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private CentralConfigService centralConfigService;
    @InjectMocks
    private SystemRepoConfigFactory systemRepoConfigFactory;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            systemRepoConfig,   centralConfigInvocations,   repoConfigInvocations
            BUILDINFO,          1,                          1
            EDGE_UPLOADS,       1,                          1
            IN_TRANSIT,         1,                          1
            PIPEINFO,           1,                          1
            RELEASE_BUNDLE,     1,                          1
            SUPPORT_BUNDLE,     0,                          0
            LOG_SHIPPING,       1,                          1
            USAGE,              1,                          1
            BILLING,            1,                          1
            TRASHCAN,           0,                          0
            BILLING,            1,                          1
            FULL_SYNC,          1,                          1
            """
    )
    void testRepoCreated(SystemRepoConfig systemRepoConfig, int centralConfigInvocations, int repoConfigInvocations) {
        systemRepoConfigFactory.buildSystemRepo(systemRepoConfig);
        verify(centralConfigService, times(centralConfigInvocations)).getDescriptor();
        verify(repositoryService, times(repoConfigInvocations)).upsertRepoConfig(any());
    }
}
