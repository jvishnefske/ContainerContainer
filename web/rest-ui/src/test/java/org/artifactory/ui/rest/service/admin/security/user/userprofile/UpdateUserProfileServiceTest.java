package org.artifactory.ui.rest.service.admin.security.user.userprofile;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.security.UserImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.UserInfo;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;

public class UpdateUserProfileServiceTest extends ArtifactoryHomeBoundTest {

    private UpdateUserProfileService updateUserProfileService;
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
        updateUserProfileService = new UpdateUserProfileService();
        updateUserProfileService.setUserGroupService(userGroupService);
        updateUserProfileService.setSecurityService(securityService);
        updateUserProfileService.setUserProfileHelperService(userProfileHelperService);
        updateUserProfileService.setSecurityService(securityService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void updateUserProfileFullFlowTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        User user = new User(userInfo);
        user.setEmail("natalie.berni@jfrog.com");
        userProfile.setUser(user);

        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        response = new ArtifactoryRestResponse<>();

        updateUserProfileService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }

    @Test
    void updateUserProfileFullFlowMultiSshTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        User user = new User(userInfo);
        user.setEmail("natalie.berni@jfrog.com");
        userProfile.setUser(user);

        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        response = new ArtifactoryRestResponse<>();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.sshUserMultiSupportEnabled.getPropertyName(), "true");
        updateUserProfileService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }
}