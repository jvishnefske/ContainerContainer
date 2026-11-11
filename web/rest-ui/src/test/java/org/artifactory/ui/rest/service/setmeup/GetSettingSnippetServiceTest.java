package org.artifactory.ui.rest.service.setmeup;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Natalie Berni
 */
public class GetSettingSnippetServiceTest {

    public static final String BASE_URL = "http://www.jfrog.io";
    public static final String TEMPLATE_NAME = "${security.getCurrentUsername()}";
    public static final  String TEMPLATE_ESCAPED_CREDENTIALS =
            "${security.getEscapedEncryptedPassword()!\"*** Insert encrypted password here ***\"}";
    public static final String TEMPLATE_CREDENTIALS =
            "${security.getEncryptedPassword()!\"*** Insert encrypted password here ***\"}";
    public static final String ARTIFACTORY_URL = "http://localhost:8081/artifactory";

    protected AutoCloseable mocksClosable;
    @Mock
    protected ArtifactoryHome artifactoryHome;
    @Mock
    protected ArtifactoryContext artifactoryContext;
    @Mock
    protected SysConfig sysConfig;
    @Mock
    protected SysConfigHelper sysConfigHelper;
    @Mock
    protected CentralConfigService configService;
    @Mock
    protected AddonsManager addonsManager;
    @Mock
    protected ArtifactoryRestRequest request;
    @Mock
    protected FilteredResourcesAddon filteredResourcesWebAddon;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        initContext();
    }
    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocksClosable.close();
    }

    protected void initContext() {
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(FilteredResourcesAddon.class)).thenReturn(filteredResourcesWebAddon);
        when(filteredResourcesWebAddon.getGeneratedSettingsUsernameTemplate()).
                thenReturn(TEMPLATE_NAME);
        when(filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(true)).
                thenReturn(TEMPLATE_ESCAPED_CREDENTIALS);
        when(filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(false)).
                thenReturn(TEMPLATE_CREDENTIALS);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(configService);
        when(configService.getListeningPort()).thenReturn(8081);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    protected void mockBaseUrlFromConfig() {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getUrlBase()).thenReturn("https://baseurl/");
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
    }
}
