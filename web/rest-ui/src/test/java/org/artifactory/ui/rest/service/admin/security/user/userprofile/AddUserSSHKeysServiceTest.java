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
import org.artifactory.ui.rest.model.admin.configuration.ssh.SshClientUIModel;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh.AddUserSSHKeysService;
import org.jfrog.common.ResourceUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;


public class AddUserSSHKeysServiceTest {

    private AddUserSSHKeysService addUserSSHKeysService;
    @Mock
    private SecurityService securityService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private UserProfileHelperService userProfileHelperService;
    @Mock
    private ArtifactoryRestRequest<UserProfile> request;
    @Mock
    private ArtifactoryRestResponse response;

    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .initMocks(this)
                .startMocking();
        addUserSSHKeysService = new AddUserSSHKeysService();
        addUserSSHKeysService.setUserGroupService(userGroupService);
        addUserSSHKeysService.setSecurityService(securityService);
        addUserSSHKeysService.setUserProfileHelperService(userProfileHelperService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void addSshKeyFullFlowTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Properties props = new PropertiesImpl();
        props.put("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX, "ssh-rsa stamkey");
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }
    @Test
    void notAllowedSetPasswordTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl() {
            {
                setUsername("NatalieBerni");
                setPasswordDisabled(true);
            }
        };

        User user = new User(userInfo);
        user.setPassword("test");
        userProfile.setUser(user);
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void addSshKeyWithExistingKeysTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        Properties props = new PropertiesImpl();
        props.put("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void allowAddSshKeyWhenDBHoldsAKeyWithoutContent(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Properties props = new PropertiesImpl();
        props.put("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX, null);
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }

    @Test
    void addSshKeyWithTheSameNameTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "old",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Properties props = new PropertiesImpl();
        props.put("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void useAddSshKeyWithoutNewSshKey(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Properties props = new PropertiesImpl();
        props.put("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void nullUserTest(){
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(null);
        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void notUpdatableUserTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(false);

        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void addSshKeyWithAnonymousUserTest(){
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl(UserInfo.ANONYMOUS);
        userProfile.setUser(new User(userInfo));
        setNewSshPublicKeyToAdd(userProfile, "new",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        addUserSSHKeysService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    private void setNewSshPublicKeyToAdd(UserProfile userProfile, String sshKeyName, String sshKeyContent){
        SshClientUIModel sshClientUIModel = new SshClientUIModel(sshKeyName, sshKeyContent);
        userProfile.setSsh(sshClientUIModel);
    }
}