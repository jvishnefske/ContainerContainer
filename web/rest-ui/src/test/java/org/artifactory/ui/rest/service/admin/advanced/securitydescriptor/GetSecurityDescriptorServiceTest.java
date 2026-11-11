package org.artifactory.ui.rest.service.admin.advanced.securitydescriptor;

import java.util.Collections;
import java.util.List;

import org.artifactory.model.security.SecurityDataImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetSecurityDescriptorServiceTest {

    private GetSecurityDescriptorService service;

    @BeforeEach
    void setUp() {
        service = new GetSecurityDescriptorService();
    }

    @Test
    void redactPasswordsNullsOutPasswordAndSalt() {
        UserImpl user1 = new UserImpl("alice");
        user1.setPassword(new SaltedPassword(
                "$argon2id$v=19$m=512,t=16,p=1$someSaltValue$someHashValue", "someSaltValue"));
        UserImpl user2 = new UserImpl("bob");
        user2.setPassword(new SaltedPassword(
                "$argon2id$v=19$m=512,t=16,p=1$otherSalt$otherHash", "otherSalt"));

        SecurityInfo securityInfo = new SecurityDataImpl(
                List.of(user1, user2), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        service.redactPasswords(securityInfo);

        for (UserInfo user : securityInfo.getUsers()) {
            assertNull(user.getPassword(), "Password should be redacted for user " + user.getUsername());
            assertNull(user.getSalt(), "Salt should be redacted for user " + user.getUsername());
        }
    }

    @Test
    void redactPasswordsHandlesNullUserList() {
        SecurityInfo securityInfo = new SecurityDataImpl(
                null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertDoesNotThrow(() -> service.redactPasswords(securityInfo));
        assertNull(securityInfo.getUsers());
    }

    @Test
    void redactPasswordsHandlesEmptyUserList() {
        SecurityInfo securityInfo = new SecurityDataImpl(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertDoesNotThrow(() -> service.redactPasswords(securityInfo));
        assertTrue(securityInfo.getUsers().isEmpty());
    }

    @Test
    void redactPasswordsHandlesUserWithNullPassword() {
        UserImpl user = new UserImpl("charlie");

        SecurityInfo securityInfo = new SecurityDataImpl(
                List.of(user), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        service.redactPasswords(securityInfo);

        assertNull(securityInfo.getUsers().get(0).getPassword());
        assertNull(securityInfo.getUsers().get(0).getSalt());
    }
}
