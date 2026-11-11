package org.artifactory.ui.rest.service.admin.security.user.userprofile;

import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author Oleksandr Legenkiy
 */
@Test
public class UserProfileHelperServiceImplTest {

    private final UserProfileHelperService service = new UserProfileHelperServiceImpl();

    @Test
    public void testGetUserPasswordFromHeader() {
        assertEquals(service.getUserPasswordFromHeader(createReqMock("admin:password")), "password");
        assertEquals(service.getUserPasswordFromHeader(createReqMock("admin:passPart1:passPart2")),
                "passPart1:passPart2");
        assertNull(service.getUserPasswordFromHeader(createReqMock("password")));
        assertNull(service.getUserPasswordFromHeader(createReqMock(":password")));
        assertNull(service.getUserPasswordFromHeader(createReqMock("admin:")));
        assertEquals(service.getUserPasswordFromHeader(createReqMock("admin:password:")), "password:");
        // RTDEV-2341
        assertEquals(service.getUserPasswordFromHeader(createReqMock("admin::password")), ":password");
    }

    private HttpServletRequest createReqMock(String userAndPassword) {
        HttpServletRequest reqMock = mock(HttpServletRequest.class);
        when(reqMock.getHeader(UserProfileHelperService.RE_AUTH_HEADER))
                .thenReturn("Basic " +
                        Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8)));
        return reqMock;
    }
}
