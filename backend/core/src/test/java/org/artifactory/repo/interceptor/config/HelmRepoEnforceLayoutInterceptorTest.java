package org.artifactory.repo.interceptor.config;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.HelmGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelmRepoEnforceLayoutInterceptorTest extends PackageRepoEnforceLayoutInterceptorTest {

    @BeforeEach
    void setUp() {
        super.setUp();
        mockHelmGeneralConfig(new HelmGeneralConfig());
    }


    @Override
    protected RepoType getRepoType() {
        return RepoType.Helm;
    }

    @Override
    protected Map<String, String> getEnforceLayoutDefaultSettings() {
        HelmGeneralConfig helmGeneralConfig = new HelmGeneralConfig();
        Map<String, String> helmGeneralConfigMap = new HashMap<>();
        helmGeneralConfigMap.put(
                FORCE_METADATA_NAME_VERSION, String.valueOf(helmGeneralConfig.isForceMetadataNameVersion()));
        helmGeneralConfigMap.put(
                FORCE_NON_DUPLICATE_CHART, String.valueOf(helmGeneralConfig.isForceNonDuplicateChart())
        );
        return helmGeneralConfigMap;
    }

    @Override
    protected void enableEnforceLayoutSettings() {
        HelmGeneralConfig helmGeneralConfig = new HelmGeneralConfig();
        helmGeneralConfig.setForceMetadataNameVersion(true);
        helmGeneralConfig.setForceNonDuplicateChart(true);
        mockHelmGeneralConfig(helmGeneralConfig);
    }

    private void mockHelmGeneralConfig(HelmGeneralConfig helmGeneralConfig) {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor =
                mock(RepositoriesGeneralConfigDescriptor.class);
        when(descriptor.getRepositoriesGeneralConfig()).thenReturn(repositoriesGeneralConfigDescriptor);
        when(repositoriesGeneralConfigDescriptor.getHelm()).thenReturn(helmGeneralConfig);
    }
}