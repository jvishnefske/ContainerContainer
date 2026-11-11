package org.artifactory.security.ssh;

import org.apache.sshd.server.session.ServerSession;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.UserInfo;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.jfrog.security.util.BCProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import static org.artifactory.security.ssh.PublicKeyAuthenticator.DEFAULT;
import static org.artifactory.security.ssh.PublicKeyAuthenticator.SSH_PUBLIC_KEY_DATE_POSTFIX;
import static org.artifactory.security.ssh.PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Noam Y. Tenne
 */
class PublicKeyAuthenticatorTest {
    private static final Provider BC_PROVIDER = BCProviderFactory.getProvider();
    private static final ArtifactoryHome HOME = mock(ArtifactoryHome.class);
    private static final ArtifactoryContext CONTEXT = mock(ArtifactoryContext.class);

    @Test
    void AuthenticatePublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        UserGroupStoreService userGroupStoreService = mock(UserGroupStoreService.class);
        ServerSession serverSession = mock(ServerSession.class);
        PublicKeyAuthenticator authenticator = new PublicKeyAuthenticator(userGroupStoreService, HOME, CONTEXT);
        RSAPublicKeySpec spec = createNewPublicKeySpec();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", BC_PROVIDER);
        PublicKey publicKey = keyFactory.generatePublic(spec);
        String key = authenticator.decodedPublicKey(publicKey);

        when(userGroupStoreService
                .findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, key, false))
                .thenReturn(new UserInfoBuilder("jim").build());
        boolean result = authenticator.authenticate("git", publicKey, serverSession);

        Assertions.assertTrue(result);
        verify(userGroupStoreService, times(1))
                .findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, key, false);
        verify(serverSession, times(1)).setAttribute(Mockito.any(), eq("jim"));
        verifyNoMoreInteractions(userGroupStoreService, serverSession);
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 384, 521})
    void AuthenticateEcPublicKey(int num) throws NoSuchAlgorithmException, NoSuchProviderException {
        UserGroupStoreService userGroupStoreService = mock(UserGroupStoreService.class);
        ServerSession serverSession = mock(ServerSession.class);
        PublicKeyAuthenticator authenticator = new PublicKeyAuthenticator(userGroupStoreService, HOME, CONTEXT);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");
        keyPairGenerator.initialize(num);
        java.security.KeyPair pair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();

        when(userGroupStoreService.findUserByProperty(eq(SSH_PUBLIC_KEY_POSTFIX),
                ArgumentMatchers.matches(String.format("^ecdsa-sha2-nistp%s .+", num)), eq(false)))
                .thenReturn(new UserInfoBuilder("jim").build());
        boolean result = authenticator.authenticate("git", publicKey, serverSession);

        Assertions.assertTrue(result);
        verify(userGroupStoreService, times(1)).findUserByProperty(eq(SSH_PUBLIC_KEY_POSTFIX),
                ArgumentMatchers.matches(String.format("^ecdsa-sha2-nistp%s .+", num)), eq(false));
        verify(serverSession, times(1)).setAttribute(Mockito.any(), eq("jim"));
        verifyNoMoreInteractions(userGroupStoreService, serverSession);
    }

    @Test
    void AuthenticatePublicKeyAndFindNoUser() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        UserGroupStoreService userGroupStoreService = mock(UserGroupStoreService.class);
        ServerSession serverSession = mock(ServerSession.class);
        PublicKeyAuthenticator authenticator = new PublicKeyAuthenticator(userGroupStoreService, HOME, CONTEXT);
        RSAPublicKeySpec spec = createNewPublicKeySpec();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", BC_PROVIDER);
        PublicKey publicKey = keyFactory.generatePublic(spec);
        String key = authenticator.decodedPublicKey(publicKey);
        when(userGroupStoreService
                .findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, key, false))
                .thenReturn(null);
        when(userGroupStoreService
                .findUserByProperty(PublicKeyAuthenticator.SSH_PUBLIC_KEY_PROPERTY, key, true))
                .thenReturn(null);

        boolean result = authenticator.authenticate("git", publicKey, serverSession);
        Assertions.assertFalse(result);

        verify(userGroupStoreService, times(1))
                .findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, key, false);
        verify(userGroupStoreService, times(1))
                .findUserByProperty(PublicKeyAuthenticator.SSH_PUBLIC_KEY_PROPERTY, key, true);

        verifyNoMoreInteractions(userGroupStoreService, serverSession);
    }

    @Test
    void AuthenticatePublicKeyAndConvertUser() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        UserGroupStoreService userGroupStoreService = mock(UserGroupStoreService.class);
        ServerSession serverSession = mock(ServerSession.class);
        PublicKeyAuthenticator authenticator = new PublicKeyAuthenticator(userGroupStoreService, HOME, CONTEXT);
        RSAPublicKeySpec spec = createNewPublicKeySpec();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", BC_PROVIDER);
        PublicKey publicKey = keyFactory.generatePublic(spec);
        String key = authenticator.decodedPublicKey(publicKey);

        when(userGroupStoreService
                .findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, key, false))
                .thenReturn(null);
        UserInfo userInfo = new UserImpl("Natalie");
        when(userGroupStoreService
                .findUserByProperty(PublicKeyAuthenticator.SSH_PUBLIC_KEY_PROPERTY, key, true))
                .thenReturn(userInfo);

        boolean result = authenticator.authenticate("git", publicKey, serverSession);
        Assertions.assertTrue(result);

        verify(userGroupStoreService, times(2))
                .findUserByProperty(anyString(), anyString(), anyBoolean());

        verify(userGroupStoreService, times(1))
                .addUserProperty(anyString(), eq(DEFAULT + SSH_PUBLIC_KEY_POSTFIX), anyString());
        verify(userGroupStoreService, times(1))
                .addUserProperty(anyString(), eq( DEFAULT + SSH_PUBLIC_KEY_DATE_POSTFIX), anyString());
    }

    private RSAPublicKeySpec createNewPublicKeySpec(){
        int bitLength = 2048;
        BigInteger p = BigInteger.probablePrime(bitLength / 2, new SecureRandom());
        BigInteger q = BigInteger.probablePrime(bitLength / 2, new SecureRandom());
        BigInteger modulus = p.multiply(q);
        BigInteger publicExponent = new BigInteger("3", 16);
        return new RSAPublicKeySpec(modulus, publicExponent);
    }
}