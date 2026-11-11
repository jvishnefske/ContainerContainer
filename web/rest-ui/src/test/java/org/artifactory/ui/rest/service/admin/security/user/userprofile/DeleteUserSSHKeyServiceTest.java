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
import org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh.DeleteUserSSHKeyService;
import org.jfrog.common.ResourceUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;


public class DeleteUserSSHKeyServiceTest {

    private DeleteUserSSHKeyService deleteUserSSHKeyService;
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
        deleteUserSSHKeyService = new DeleteUserSSHKeyService();
        deleteUserSSHKeyService.setUserGroupService(userGroupService);
        deleteUserSSHKeyService.setSecurityService(securityService);
        deleteUserSSHKeyService.setUserProfileHelperService(userProfileHelperService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void deleteNonExistingSshKeyFullFlowTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setSshKeyToDelete(userProfile, "exist" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        SshClientUIModel sshClientUIModel = new SshClientUIModel("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Set<SshClientUIModel> existingSshKeysToDeleteFrom = new HashSet<>();
        existingSshKeysToDeleteFrom.add(sshClientUIModel);
        userProfile.setSshKeys(existingSshKeysToDeleteFrom);
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        Properties props = new PropertiesImpl();
        props.put(PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_NOT_FOUND);
        Assert.assertEquals(response.getFeedbackMsg().getInfo(),
                "Unable to delete exist-sshPublicKey SSH key from 'admin' profile - property not found");
    }

    @Test
    void deleteSshKeyFullFlowTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setSshKeyToDelete(userProfile, "exist",
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        SshClientUIModel sshClientUIModel = new SshClientUIModel("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Set<SshClientUIModel> existingSshKeysToDeleteFrom = new HashSet<>();
        existingSshKeysToDeleteFrom.add(sshClientUIModel);
        userProfile.setSshKeys(existingSshKeysToDeleteFrom);

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Properties props = new PropertiesImpl();
        props.put(PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        Mockito.when(userGroupService.getUserProperty(anyString(), anyString()))
                .thenReturn(ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK);
    }

    @Test
    void deleteSshKeyFromUserWithEmptySshKeysSetTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));
        setSshKeyToDelete(userProfile, "old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        Properties props = new PropertiesImpl();
        Mockito.when(userGroupService.findPropertiesForUser(Mockito.anyString())).thenReturn(props);

        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
        Assert.assertEquals(response.getFeedbackMsg().getError(), "The user 'admin' doesn't have SSH public keys.");
    }

    @Test
    void executeDeleteSshKeyWithoutSupplySshKeyToDeleteTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(true);
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("admin");
        userProfile.setUser(new User(userInfo));

        SshClientUIModel sshClientUIModel = new SshClientUIModel("old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));

        Set<SshClientUIModel> setSshKeys = new HashSet<>();
        setSshKeys.add(sshClientUIModel);
        userProfile.setSshKeys(setSshKeys);

        Mockito.when(request.getImodel()).thenReturn(userProfile);
        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
        Assert.assertEquals(response.getFeedbackMsg().getError(), "SSH public key name/content are empty");
    }

    @Test
    void nullUserTest(){
        UserProfile userProfile = new UserProfile();
        userProfile.setUser(null);
        Mockito.when(request.getImodel()).thenReturn(userProfile);
        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
        Assert.assertEquals(response.getFeedbackMsg().getError(), "User cannot be null");
    }

    @Test
    void notUpdatableUserTest(){
        Mockito.when(securityService.isProfileUpdatable(any())).thenReturn(false);

        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl("Natalie");
        userProfile.setUser(new User(userInfo));
        setSshKeyToDelete(userProfile, "old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
        Assert.assertEquals(response.getFeedbackMsg().getError(), "User not allowed to update profile");
    }

    @Test
    void deleteSshKeyWithAnonymousUserTest(){
        UserProfile userProfile = new UserProfile();
        UserInfo userInfo = new UserImpl(UserInfo.ANONYMOUS);
        userProfile.setUser(new User(userInfo));
        setSshKeyToDelete(userProfile, "old" + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX,
                ResourceUtils.getResourceAsString("/ssh.keys/ssh-rsa-public.key"));
        Mockito.when(request.getImodel()).thenReturn(userProfile);

        Mockito.when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);

        response = new ArtifactoryRestResponse<>();
        deleteUserSSHKeyService.execute(request, response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
        Assert.assertEquals(response.getFeedbackMsg().getError(), "Unable to edit settings for anonymous user");
    }

    private void setSshKeyToDelete(UserProfile userProfile, String sshKeyName, String sshKeyContent){
        SshClientUIModel sshClientUIModel = new SshClientUIModel(sshKeyName, sshKeyContent);
        userProfile.setSsh(sshClientUIModel);
    }
}