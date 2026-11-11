package org.artifactory.rest.resource.system;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.rest.common.model.artifactorylicense.BaseLicenseDetails;
import org.artifactory.rest.resource.system.providers.SubscriptionProviders;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

/**
 * @author Alexei Vainshtein
 */
public class ArtifactoryLicenseResourceTest {

    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(dataProviderClass = SubscriptionProviders.class, dataProvider = "subscriptionProvider")
    public void testGetLicenseInfo(SubscriptionType type) {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        ArtifactoryBaseLicenseDetails baseLicenseDetails =
                new ArtifactoryBaseLicenseDetails("", "", "", type);
        Mockito.when(addonsManager.getProAndAolLicenseDetails()).thenReturn(baseLicenseDetails);
        Mockito.when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactoryLicenseResource artifactoryLicensesResource = new ArtifactoryLicenseResource();
        Response licenseInfo = artifactoryLicensesResource.getLicenseInfo();
        Assert.assertEquals(licenseInfo.getStatus(), HttpStatus.SC_OK);
        BaseLicenseDetails entity = (BaseLicenseDetails) licenseInfo.getEntity();
        Assert.assertEquals(entity.getSubscriptionType(), type.getString());
    }
}