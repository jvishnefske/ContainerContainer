package org.artifactory.rest.resource.system;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.model.artifactorylicense.BaseLicenseDetails;
import org.artifactory.rest.common.model.artifactorylicense.LicensesDetails;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.resource.system.providers.SubscriptionProviders;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.AccessAuthz;
import org.jfrog.access.common.Scope;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;
import java.util.Collections;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class ArtifactoryLicensesResourceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private AolService aolService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AccessTokenAuthentication authentication;
    @Mock
    private JwtAccessToken accessToken;
    @Mock
    private AddonsManager addonsManager;

    private AutoCloseable autoCloseable;


    @BeforeMethod
    private void setup() {
        this.autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        this.autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
    }

    @Test(dataProvider = "configureException", expectedExceptions = BadRequestException.class)
    public void testGetLicensesException(boolean isAol, boolean isDashboardUser, String tokenSubject, String perm) {
        this.createAolMock(isAol, isDashboardUser, tokenSubject, perm);
        ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
        artifactoryLicensesResource.getLicenseInfo();
    }

    @Test(dataProvider = "configureSuccess")
    public void testGetLicensesSuccessful(boolean isAol, boolean isDashboardUser, String tokenSubject, String perm) {
        this.createAolMock(isAol, isDashboardUser, tokenSubject, perm);
        ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
        ArtifactoryBaseLicenseDetails baseLicenseDetails =
                new ArtifactoryBaseLicenseDetails("", "", "", SubscriptionType.FREE);
        when(this.addonsManager.getProAndAolLicenseDetails()).thenReturn(baseLicenseDetails);
        Response licenseInfo = artifactoryLicensesResource.getLicenseInfo();
        Assert.assertEquals(licenseInfo.getStatus(), HttpStatus.SC_OK);
    }

    @Test
    public void testGetLicensesSuccessfulForMcScope() {
        this.createAolMockForMcScope(true);
        ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
        ArtifactoryBaseLicenseDetails baseLicenseDetails =
                new ArtifactoryBaseLicenseDetails("", "", "", SubscriptionType.FREE);
        when(this.addonsManager.getProAndAolLicenseDetails()).thenReturn(baseLicenseDetails);
        Response licenseInfo = artifactoryLicensesResource.getLicenseInfo();
        Assert.assertEquals(licenseInfo.getStatus(), HttpStatus.SC_OK);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetLicensesExceptionForNonMcScope() {
        this.createAolMockForMcScope(false);
        ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
        artifactoryLicensesResource.getLicenseInfo();
    }

    @DataProvider
    public Object[][] configureSuccess() {
        return new Object[][] {
                {true, true, "", "not-an-admin-scope"},
                {false, false, "", "not-an-admin-scope"},
                {true, false, "jffe@01cxa95mdb8t65cj33eferky0w", AccessAuthz.ADMIN},
                {true, true, "jffe@01cxa95mdb8t65cj33eferky0w", AccessAuthz.TOKEN},
        };
    }

    @DataProvider
    public Object[][] configureException() {
        return new Object[][] {
                {true, false, "jffe@01cxa95mdb8t65cj33eferky0w", AccessAuthz.TOKEN},
                {true, false, "", "not-an-admin-scope"}
        };
    }

    @Test(dataProviderClass = SubscriptionProviders.class, dataProvider = "subscriptionProvider")
    public void getLicenseInfoPro(SubscriptionType type) {
        this.createAolMock(true, true, "", "not-admin");
        ArtifactoryBaseLicenseDetails baseLicenseDetails =
                new ArtifactoryBaseLicenseDetails("", "", "", type);
        ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
        when(this.addonsManager.getProAndAolLicenseDetails()).thenReturn(baseLicenseDetails);
        Response licenseInfo = artifactoryLicensesResource.getLicenseInfo();
        Assert.assertEquals(licenseInfo.getStatus(), HttpStatus.SC_OK);
        BaseLicenseDetails entity = (BaseLicenseDetails) licenseInfo.getEntity();
        Assert.assertEquals(entity.getSubscriptionType(), type.getString());
    }

    @Test
    public void getLicenseInfoHa() {
        this.createAolMock(true, true, "", "not-admin");
        this.homeStub.getHaNodeProperties().setProperty("enabled", "true");
        try {
            ArtifactoryHaLicenseDetails artifactoryHaLicenseDetails
                    = new ArtifactoryHaLicenseDetails("", "", "", "", false, "", "", false);
            when(this.addonsManager.getClusterLicensesDetails())
                    .thenReturn(Collections.singletonList(artifactoryHaLicenseDetails));
            ArtifactoryLicensesResource artifactoryLicensesResource = new ArtifactoryLicensesResource();
            Response licenseInfo = artifactoryLicensesResource.getLicenseInfo();
            Assert.assertEquals(licenseInfo.getStatus(), HttpStatus.SC_OK);
            LicensesDetails entity = (LicensesDetails) licenseInfo.getEntity();
            entity.getLicenses().forEach(licenseFullDetails ->
                    Assert.assertNull(licenseFullDetails.getSubscriptionType()));
        } finally {
            this.homeStub.getHaNodeProperties().setProperty("enabled", "false");
        }
    }

    private void createAolMock(boolean isAol, boolean isDashboardUser, String tokenSubject, String permission) {
        when(securityService.hasMcScope()).thenReturn(false);
        when(accessToken.getSubject()).thenReturn(tokenSubject);
        when(accessToken.getScope()).thenReturn(singletonList(new Scope(tokenSubject, permission).toString()));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getAccessToken()).thenReturn(accessToken);
        when(aolService.isDashboardUser()).thenReturn(isDashboardUser);
        when(aolService.isAol()).thenReturn(isAol);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryContextThreadBinder.bind(context);
        SecurityContextHolder.setContext(securityContext);
    }

    private void createAolMockForMcScope(boolean hasMcScope) {
        when(securityService.hasMcScope()).thenReturn(hasMcScope);
        when(aolService.isDashboardUser()).thenReturn(false);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryContextThreadBinder.bind(context);
        SecurityContextHolder.setContext(securityContext);
    }
}