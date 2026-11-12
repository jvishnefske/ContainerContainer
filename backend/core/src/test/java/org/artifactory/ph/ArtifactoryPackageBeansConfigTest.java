package org.artifactory.ph;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiAuthenticationAdapter;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;

@Test
public class ArtifactoryPackageBeansConfigTest {

    @Mock
    private ArtifactoryHome home;
    @Mock
    private ArtifactorySystemProperties props;
    @Mock
    private SysConfig sysConfig;
    @Mock
    private SysConfigHelper sysConfigHelper;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(home.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void teardown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testGetConfProps() {
        assertNotNull(new ArtifactoryPackageBeansConfig().getConfProps());
    }

    @Test
    public void testPackageRepoService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageRepoService(null, null));
    }

    @Test
    public void testPackageDownloadService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageDownloadService(null, null));
    }

    @Test
    public void testPackageUploadService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageUploadService(null, null, null, null));
    }

    @Test
    public void testPackageStorageInterceptorAdapter() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageStorageInterceptorAdapter(null, null, null));
    }

    @Test
    public void testPackageConfigurationChangesInterceptor() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageConfigurationChangesInterceptor(null, null));
    }

    @Test
    public void testRepoImportInterceptor() {
        assertNotNull(new ArtifactoryPackageBeansConfig().repoImportInterceptorAdapter(null, null, null));
    }

    @Test
    public void testPackageSecurityService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageSecurityService(null, null, null, null, null, null,
                null));
    }

    @Test
    public void testPackageSearchServicePH() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageSearchArtifactService(null, null, null, null, null,
                null, null));
    }

    @Test
    public void testPackageSpecification() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageSpecification(null));
    }

    @Test
    public void testPackageArtifactEventListenerOSS() {
        assertNotNull(new ArtifactoryPackageBeansConfig.PackageOssConfig().packageArtifactEventListener());
    }

    @Test
    public void testPackageVirtualAdapterOSS() {
        assertNotNull(new ArtifactoryPackageBeansConfig.PackageOssConfig().packageVirtualAdapter());
    }

    @Test
    public void testPackageRepoIndexCalculatorServiceOSS() {
        assertNotNull(new ArtifactoryPackageBeansConfig.PackageOssConfig().packageRepoIndexCalculatorService());
    }

    @Test
    public void testPackageSpecificationAdapterOSS() {
        assertNotNull(new ArtifactoryPackageBeansConfig.PackageOssConfig().packageSpecificationAdapter());
    }

    @Test
    public void testPackageAuthenticationFilter() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageAuthenticationFilter(null));
    }

    @Test
    public void testPackageAuthenticationAdapter() {
        assertTrue(new ArtifactoryPackageBeansConfig().packageAuthenticationAdapter(
                mock(PluginPackageTypeRegistry.class), mock(ObjectProvider.class))
                instanceof PluginApiAuthenticationAdapter);
    }

    @Test
    public void testIndexerSelector() {
        assertNotNull(new ArtifactoryPackageBeansConfig().indexingSelector(null, null, null, null, null));
    }

    @Test
    public void testPackageCurationService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageCurationService(null, null));
    }

    @Test
    public void testPackageAnalyticsService() {
        assertNotNull(new ArtifactoryPackageBeansConfig().packageAnalyticsService(null));
    }
}