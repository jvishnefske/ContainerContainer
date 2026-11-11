package org.artifactory.rest.common.service.admin.xray;

import org.apache.commons.codec.binary.Base64;
import org.artifactory.addon.AddonsManager;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Author: Ivan Perepelytsya
 * Date: 5/12/20.
 */
@Test
public class GetXrayLicenseServiceTest {
    private RestResponse restResponse;

    @BeforeMethod
    private void prepareMocks() {
        restResponse = mock(RestResponse.class);
    }

    @Test(dataProvider = "licenseProvider")
    public void testGetXrayLicense(String xrayLicense, boolean error) {
        GetXrayLicenseService getXrayLicenseService = new GetXrayLicenseService();
        ArtifactoryRestResponse artifactoryRestResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.error(anyString())).thenReturn(artifactoryRestResponse);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.getLicensePerProduct("xray")).thenReturn(xrayLicense);
        getXrayLicenseService.setAddonsManager(addonsManager);
        getXrayLicenseService.execute(null, restResponse);
        if (error) {
            verify(artifactoryRestResponse).responseCode(400);
        }
    }

    @DataProvider
    public static Object[][] licenseProvider() {
        return new Object[][] {
                {"", true},
                {"   ", true},
                {null, true},
                {"some string", false},
                {"q1W@e3R$T%6757U%^*&*(^*)^&$%^#$%@#$!@#±!@±!#!@$#@#$@#%#$^%$%&%^&*&*(*()", false},
                {new String(Base64.encodeBase64("some string".getBytes())), false},
                {new String(Base64.decodeBase64("some string")), true}
        };
    }
}