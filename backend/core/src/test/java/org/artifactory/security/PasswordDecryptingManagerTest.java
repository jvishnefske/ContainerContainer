/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.security;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthenticationProvider;
import org.artifactory.security.access.providers.AccessApiKeyAuthenticationProvider;
import org.artifactory.security.access.providers.AccessTokenAuthenticationProviderV2;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.security.crypto.DecodedKeyPair;
import org.jfrog.security.crypto.EncodedKeyPair;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.jfrog.security.crypto.result.DecryptionStatusHolder;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author maximy
 */
public class PasswordDecryptingManagerTest extends ArtifactoryHomeBoundTest {
    private AuthenticationManager authenticationManager;

    @BeforeClass
    public void beforeClass() {
        authenticationManager = mock(AuthenticationManager.class);
    }

    @Test(expectedExceptions = PasswordEncryptionException.class, expectedExceptionsMessageRegExp =
            "Artifactory configured to accept only encrypted passwords but received a clear text password, getting " +
                    "the encrypted password can be done via the WebUI.")
    public void testAuthenticateThrowsPasswordEncryptionExceptionForUnencryptedPassword() {
        PasswordDecryptingManager passwordDecryptingManager = buildPasswordDecryptingManager(null);

        passwordDecryptingManager
                .authenticate(new UsernamePasswordAuthenticationToken("userName", "unencryptedPassword"));
    }

    @Test(expectedExceptions = PasswordEncryptionException.class, expectedExceptionsMessageRegExp =
            "Artifactory configured to accept only encrypted passwords but received a clear text password, getting " +
                    "the encrypted password can be done via the WebUI.")
    public void testXrayBypassPasswordPolicy() {
        PasswordDecryptingManager passwordDecryptingManager = buildPasswordDecryptingManager(null);

        passwordDecryptingManager
                .authenticate(new UsernamePasswordAuthenticationToken("xray", "unencryptedPassword12"));
    }

    @Test
    public void testAuthenticateWithAnonymousUserAndEmptyPasswordDoesNotThrowPasswordEncryptionException() {
        UserGroupService userGroupService = mock(UserGroupService.class);
        PasswordDecryptingManager passwordDecryptingManager = buildPasswordDecryptingManager(userGroupService);

        passwordDecryptingManager.authenticate(new AnonymousAuthenticationToken());

        //make sure decryption not invoked - needsDecryption() returned FALSE
        verify(userGroupService, times(0)).findUser(any());
    }

    @Test
    public void testAuthenticateWithEncryptedPassword() {
        UserGroupService userGroupService = mock(UserGroupService.class);
        UserImpl user = new UserImpl("user");
        EncodedKeyPair keyPair = getKeyPair();
        user.setPrivateKey(keyPair.getEncodedPrivateKey());
        user.setPublicKey(keyPair.getEncodedPublicKey());
        when(userGroupService.findUser(any())).thenReturn(user);
        PasswordDecryptingManager passwordDecryptingManager =
                buildPasswordDecryptingManager(userGroupService);
        String encryptedPassword = createEncryptedPassword("password", keyPair);
        passwordDecryptingManager
                .authenticate(
                        new UsernamePasswordAuthenticationToken("user", encryptedPassword));
        verify(authenticationManager).authenticate(
                new EncryptedPasswordAuthenticationToken("user", "password", encryptedPassword));
    }

    /** P2: a credential a plugin realm claims is answered by the realm, before any stock token handling. */
    @Test
    public void pluginRealmClaimIsReturnedBeforeTheStockTokenHandling() {
        PasswordDecryptingManager manager = buildPasswordDecryptingManager(mock(UserGroupService.class));
        AccessTokenAuthenticationProvider tokens = mock(AccessTokenAuthenticationProvider.class);
        when(tokens.isAccessToken(any())).thenReturn(true);
        manager.setDelegateAccessToken(tokens);
        org.springframework.security.core.Authentication granted =
                new UsernamePasswordAuthenticationToken("realm-user", "", SimpleUser.USER_GAS);
        manager.setPluginRealms(authentication -> java.util.Optional.of(granted));
        org.testng.Assert.assertSame(manager.authenticate(new UsernamePasswordAuthenticationToken("x", "a.b.c")),
                granted);
        verify(tokens, times(0)).authenticate(any());
    }

    /** P2, P6: what no plugin realm claims (including every Access token) keeps the stock path. */
    @Test
    public void unclaimedTokensKeepTheStockAccessTokenPath() {
        PasswordDecryptingManager manager = buildPasswordDecryptingManager(mock(UserGroupService.class));
        AccessTokenAuthenticationProvider tokens = mock(AccessTokenAuthenticationProvider.class);
        when(tokens.isAccessToken(any())).thenReturn(true);
        manager.setDelegateAccessToken(tokens);
        manager.setPluginRealms(authentication -> java.util.Optional.empty());
        manager.authenticate(new UsernamePasswordAuthenticationToken("x", "a.b.c"));
        manager.setPluginRealms(null);
        manager.authenticate(new UsernamePasswordAuthenticationToken("x", "a.b.c"));
        verify(tokens, times(2)).authenticate(any());
    }

    private PasswordDecryptingManager buildPasswordDecryptingManager(UserGroupService userGroupService) {
        // centralConfig
        CentralConfigService centralConfig = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor security = new SecurityDescriptor();
        AccessService accessService = mock(AccessService.class);
        when(accessService.isEncryptionRequired()).thenReturn(true);
        when(accessService.isEncryptionEnabled()).thenReturn(true);
        configDescriptor.setSecurity(security);

        XrayDescriptor xrayDescriptor = new XrayDescriptor();
        xrayDescriptor.setUser("xrayConfigUser");
        configDescriptor.setXrayConfig(xrayDescriptor);
        when(centralConfig.getDescriptor()).thenReturn(configDescriptor);
        SecurityService securityService = mock(SecurityService.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        XrayAddon xray = mock(XrayAddon.class);
        when(xray.isDecryptionNeeded(Mockito.anyString())).thenReturn(true);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xray);
        when(securityService.isAnonymous()).thenReturn(true);
        //passwordDecryptingManager
        PasswordDecryptingManager passwordDecryptingManager =
                new PasswordDecryptingManager(securityService, centralConfig, userGroupService, addonsManager,
                        accessService, mock(AccessApiKeyAuthenticationProvider.class),
                        mock(AccessTokenAuthenticationProviderV2.class));
        //DelegateAccessToken
        AccessTokenAuthenticationProvider tokenAuthProvider = mock(AccessTokenAuthenticationProvider.class);
        when(tokenAuthProvider.isAccessToken(any())).thenReturn(false);
        passwordDecryptingManager.setDelegateAccessToken(tokenAuthProvider);
        //Delegate
        passwordDecryptingManager.setDelegate(authenticationManager);

        return passwordDecryptingManager;
    }

    private EncodedKeyPair getKeyPair() {
        DecodedKeyPair decodedKeyPair = new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair());
        return new EncodedKeyPair(decodedKeyPair, null);
    }

    private String createEncryptedPassword(String password, EncodedKeyPair encodedKeyPair) {
        DecryptionStatusHolder decryptionStatus = new DecryptionStatusHolder();
        DecodedKeyPair decodedKeyPair = encodedKeyPair.decode(null, decryptionStatus);
        EncryptionWrapper keyWrapper = EncryptionWrapperFactory.createKeyWrapper(decodedKeyPair);
        return keyWrapper.encryptIfNeeded(password);

    }
}