package org.artifactory.ph;

import com.jfrog.ph.acl.model.sso.PackageSsoLoginModel;
import com.jfrog.ph.acl.service.PackageOAuthHandler;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.addon.oauth.SsoLoginModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;

class PackageOAuthHandlerImplTest {

    private PackageOAuthHandler packageOAuthHandler;
    private OAuthHandler oAuthHandler;

    @BeforeEach
    void setUp() {
        oAuthHandler = Mockito.mock(OAuthHandler.class);
        packageOAuthHandler = Mockito.spy(new PackageOAuthHandlerImpl(oAuthHandler));
    }

    @Test
    void testHandleLogin() {
        PackageSsoLoginModel packageSsoLoginModel = new PackageSsoLoginModel("name", "password");
        packageOAuthHandler.handleLogin("method", "name", "path", null, packageSsoLoginModel);
        SsoLoginModel ssoLoginModel = new SsoLoginModel();
        ssoLoginModel.setName("name");
        ssoLoginModel.setPassword("password");

        Mockito.verify(oAuthHandler).handleLogin(eq("method"), eq("name"), eq("path"), eq(null), refEq(ssoLoginModel));
    }

    @Test
    void testHandleLoginWithNull() {
        assertDoesNotThrow(() -> {
            packageOAuthHandler.handleLogin("method", "name", "path", null, null);
        });
        Mockito.verify(oAuthHandler).handleLogin(eq("method"), eq("name"), eq("path"), eq(null), eq(null));
    }

    @Test
    void testGetNpmLoginHandler() {
        packageOAuthHandler.getNpmLoginHandler();
        Mockito.verify(oAuthHandler).getNpmLoginHandler();
    }
}