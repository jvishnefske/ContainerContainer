package org.artifactory.ui.rest.service.admin.security.user.userprofile;


import org.apache.http.HttpStatus;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.md.Properties;
import org.artifactory.model.security.UserImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.UserInfo;
import org.artifactory.security.ssh.PublicKeyAuthenticator;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.jfrog.common.ResourceUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class UnlockUserProfileServiceTest {
    private UnlockUserProfileService unlockUserProfileService;
    @Mock
    private SecurityService securityService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private UserProfileHelperService userProfileHelperService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private ArtifactoryRestResponse response;

    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .initMocks(this)
                .startMocking();
        unlockUserProfileService =
                new UnlockUserProfileService(securityService, userGroupService, userProfileHelperService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void UserProfileServiceTest() {
        UserLogin user = new UserLogin("admin");
        user.setPassword("password");
        Mockito.when(request.getImodel()).thenReturn(user);

        UserInfo userInfo = new UserImpl("admin");
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        Properties props = new PropertiesImpl();
        props.put(PublicKeyAuthenticator.SSH_PUBLIC_KEY_PROPERTY,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);

        response = new ArtifactoryRestResponse<>();
        unlockUserProfileService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }
}