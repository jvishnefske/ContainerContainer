package org.artifactory.addon.web;

import com.google.common.collect.Lists;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.util.HttpUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Omri Ziv
 */
public class WebAddonsImplTest {

    private final WebAddonsImpl webAddons = new WebAddonsImpl();
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService centralConfigServiceMock;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ReflectionTestUtils.setField(webAddons, "centralConfigService", centralConfigServiceMock);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateLicenseJCR() {
        webAddons.validateTargetHasDifferentLicenseKeyHash("JFrog Container Registry",
                Lists.newArrayList("docker", "helm", "generic"));
        Assert.fail();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateLicenseOSS() {
        webAddons.validateTargetHasDifferentLicenseKeyHash("Artifactory OSS",
                Lists.newArrayList("addon1"));
        Assert.fail();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateLicenseConan() {
        webAddons.validateTargetHasDifferentLicenseKeyHash("Artifactory Community Edition for C/C++",
                Lists.newArrayList("addon1"));
        Assert.fail();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateLicenseBlank() {
        webAddons.validateTargetHasDifferentLicenseKeyHash(null,
                Lists.newArrayList("not-replication-error"));
        Assert.fail();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateLicenseNullReplicationAddon() {
        webAddons.validateTargetHasDifferentLicenseKeyHash(null,
                Lists.newArrayList("replication"));
        Assert.fail();
    }

    @Test
    void testValidateTrial() {
        when(addonsManager.isLicenseInstalled()).thenReturn(true);
        ArtifactoryBaseLicenseDetails artifactoryBaseLicenseDetailsMock = mock(ArtifactoryBaseLicenseDetails.class);
        when(addonsManager.getProAndAolLicenseDetails()).thenReturn(artifactoryBaseLicenseDetailsMock);
        when(artifactoryBaseLicenseDetailsMock.getType()).thenReturn("Trial");
        webAddons.validateTargetHasDifferentLicenseKeyHash("Trial",
                Lists.newArrayList("addon1"));
    }

    @Test
    void testValidateDifferentLicense() {
        when(addonsManager.getLicenseKeyHash(false)).thenReturn("alicence");
        webAddons.validateTargetHasDifferentLicenseKeyHash("blicence",
                Lists.newArrayList("addon1"));

    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testValidateSameLicense() {
        when(addonsManager.getLicenseKeyHash(false)).thenReturn("alicence");
        webAddons.validateTargetHasDifferentLicenseKeyHash("alicence",
                Lists.newArrayList("addon1"));
        Assert.fail();
    }

    @Test
    void testValidateDifferentLicenseWhereEntitlementsEnforced() {
        when(addonsManager.isEntitlementEnforced()).thenReturn(true);
        Assert.assertTrue(webAddons.validateTargetHasDifferentLicense(null, null));
        verify(addonsManager, times(1)).isEntitlementEnforced();
        verifyNoMoreInteractions(addonsManager);
    }

    @Test
    void testGetArtifactoryServerName() {
        var centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        when(centralConfigDescriptorMock.getServerName()).thenReturn("someServerName");
        when(centralConfigServiceMock.getDescriptor()).thenReturn(centralConfigDescriptorMock);
        var result = webAddons.getArtifactoryServerName();
        assertEquals("someServerName", result);
    }

    @Test(dataProvider = "getMailConfigArtifactoryUrlTestDataProvider")
    void testGetMailConfigArtifactoryUrl(String mailServerUrl, String urlFromRequest, String baseUrl,
            String expectedResult) {
        var mailServerMock = mock(MailServerDescriptor.class);
        when(mailServerMock.getArtifactoryUrl()).thenReturn(mailServerUrl);
        var descriptorMock = mock(CentralConfigDescriptor.class);
        when(descriptorMock.getMailServer()).thenReturn(mailServerMock);
        when(descriptorMock.getUrlBase()).thenReturn(baseUrl);
        when(centralConfigServiceMock.getDescriptor()).thenReturn(descriptorMock);
        try (MockedStatic<HttpUtils> httpUtilsMockedStatic = Mockito.mockStatic(HttpUtils.class)) {
            httpUtilsMockedStatic.when(HttpUtils::getArtifactoryUrlFromRequestIfPresent).thenReturn(urlFromRequest);
            assertEquals(expectedResult, webAddons.getMailConfigArtifactoryUrl());
        }
    }

    @DataProvider(name = "getMailConfigArtifactoryUrlTestDataProvider")
    public static Object[][] provideGetMailConfigArtifactoryUrlTestData() {
        return new Object[][] {
                {null, null, null, ""},
                {" ", null, null, ""},
                {null, " ", null, ""},
                {null, null, " ", ""},
                {"someMailServerUrl", "someUrlFromRequest", "someBaseUrl", "someMailServerUrl/ui/"},
                {null, "someUrlFromRequest", "someBaseUrl", "someUrlFromRequest/ui/"},
                {null, null, "someBaseUrl", "someBaseUrl/ui/"}
        };
    }
}
