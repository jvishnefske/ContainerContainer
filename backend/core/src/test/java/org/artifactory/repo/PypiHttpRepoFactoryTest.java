package org.artifactory.repo;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class PypiHttpRepoFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ResearchService researchService;
    @Mock
    private OnlineMonitorService onlineMonitorService;
    @Mock
    private CentralConfigService configService;
    @Mock
    private IRequestRateLimiterService rateLimiterService;

    private PypiHttpRepoFactory factory;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        factory = new PypiHttpRepoFactory(repositoryService, addonsManager,
                researchService, onlineMonitorService, configService, rateLimiterService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void build_returnsPypiHttpRepoInstance() {
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        HttpRepoDescriptor repoDescriptor = mock(HttpRepoDescriptor.class);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        ChecksumPolicyType checksumPolicyType = mock(ChecksumPolicyType.class);
        BaseConfig baseConfig = mock(BaseConfig.class);

        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getChecksumPolicyType()).thenReturn(checksumPolicyType);
        when(checksumPolicyType.name()).thenReturn("GEN_IF_ABSENT");
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        when(baseConfig.getIncludesPattern()).thenReturn("**/*");
        when(baseConfig.getExcludesPattern()).thenReturn("");

        HttpRepo result = factory.build(repoConfig, repoDescriptor, null);

        assertNotNull(result);
        assertInstanceOf(PypiHttpRepo.class, result);
    }

    @Test
    void getRepoType_returnsPypi() {
        assertEquals(RepoType.Pypi, factory.getRepoType());
    }
}
