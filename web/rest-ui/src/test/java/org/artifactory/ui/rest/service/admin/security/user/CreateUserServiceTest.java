package org.artifactory.ui.rest.service.admin.security.user;

import com.google.common.collect.ImmutableSet;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.ui.rest.model.admin.security.group.UserGroup;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class CreateUserServiceTest {

    @Mock
    private ArtifactoryRestRequest<User> requestMock;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(securityService.currentUsername()).thenReturn("testUser");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testDefaultGroupAddedToNewUser() {
        User user = new User();
        user.setName("jon");
        defaultGroupAddedToNewUser(user);
    }

    /**
     * Following the CLI invite flow, we added the source of the user creation.
     * This test checks that nothing was broken after this change.
     **/
    @Test
    public void testDefaultGroupAddedToNewUserWithSource() {
        User user = new User();
        user.setName("jon");
        user.setSource("cli");
        defaultGroupAddedToNewUser(user);
    }

    @Test
    public void testDefaultGroupRemovedInUINotAddedToNewUser() {
        User user = new User();
        user.setName("jon");

        user.setUserGroups(ImmutableSet.of());

        CreateUserService<User> createUserService = new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);
        GroupImpl readersGroup = new GroupImpl();
        readersGroup.setGroupName("readers");

        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        createUserService.execute(requestMock, mock(RestResponse.class));

        verify(userGroupService).createUser(captor.capture());

        MutableUserInfo createdUser = captor.getValue();
        assertNotNull(createdUser);
        assertEquals(createdUser.getGroups().size(), 0);
    }

    @Test
    public void testDefaultAllResourceReaderRoleNotSet() {
        User user = new User();
        user.setName("jon");

        CreateUserService<User> createUserService =
                new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);

        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        createUserService.execute(requestMock, mock(RestResponse.class));

        verify(userGroupService).createUser(captor.capture());

        MutableUserInfo createdUser = captor.getValue();
        assertNotNull(createdUser);
        assertFalse(createdUser.isReportsManager());
    }

    @Test
    public void testAllResourceReaderRoleSet() {
        User user = new User();
        user.setName("jon");
        user.setReportsManager(true);

        CreateUserService<User> createUserService = new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);

        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        createUserService.execute(requestMock, mock(RestResponse.class));

        verify(userGroupService).createUser(captor.capture());

        MutableUserInfo createdUser = captor.getValue();
        assertNotNull(createdUser);
        assertTrue(createdUser.isReportsManager());
    }

    @Test
    public void testCreateUserWithXssOnEmail() {
        User user = new User();
        user.setName("jon");
        user.setReportsManager(true);
        user.setEmail("aaa\"><img src=# onerror=\"alert(1)\">");

        CreateUserService<User> createUserService = new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);
        ArgumentCaptor<String> errorCapture = ArgumentCaptor.forClass(String.class);

        RestResponse restResponse = mock(RestResponse.class);
        createUserService.execute(requestMock, restResponse);
        verify(restResponse).error(errorCapture.capture());

        String error = errorCapture.getValue();
        assertNotNull(error);
        assertEquals(error, "Email may contains a Cross-Site Scripting expression");
    }

    @Test
    public void testCreateUserWithInvalidName() {
        User user = new User();
        user.setName("a:jon");
        user.setReportsManager(true);

        CreateUserService<User> createUserService = new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);
        ArgumentCaptor<String> errorCapture = ArgumentCaptor.forClass(String.class);

        RestResponse restResponse = mock(RestResponse.class);
        createUserService.execute(requestMock, restResponse);
        verify(restResponse).error(errorCapture.capture());

        String error = errorCapture.getValue();
        assertNotNull(error);
        assertEquals(error, "Illegal name : '/,\\,:,|,?,<,>,*,\"' is not allowed");
    }

    private void defaultGroupAddedToNewUser(User user) {
        UserGroup userGroup = new UserGroup();
        userGroup.setGroupName("readers");
        user.setUserGroups(ImmutableSet.of(userGroup));

        CreateUserService<User> createUserService = new CreateUserService<>(null, userGroupService, authService);
        when(requestMock.getImodel()).thenReturn(user);
        GroupImpl readersGroup = new GroupImpl();
        readersGroup.setGroupName("readers");

        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        createUserService.execute(requestMock, mock(RestResponse.class));

        verify(userGroupService).createUser(captor.capture());

        MutableUserInfo createdUser = captor.getValue();
        assertNotNull(createdUser);
        assertEquals(createdUser.getGroups().size(), 1);
        assertTrue(createdUser.getGroups().stream().anyMatch(group -> "readers".equals(group.getGroupName())));
    }
}