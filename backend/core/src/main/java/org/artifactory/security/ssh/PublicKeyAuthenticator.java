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

package org.artifactory.security.ssh;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.UserInfo;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import jakarta.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Noam Y. Tenne
 */

@Slf4j
public class PublicKeyAuthenticator implements PublickeyAuthenticator {
    private final UserGroupStoreService userGroupStoreService;
    private final ArtifactoryHome artifactoryHome;
    private final ArtifactoryContext artifactoryContext;
    public static final String DEFAULT = "default";
    private static final String EC = "ecdsa-sha2-nistp";
    public static final String SSH_PUBLIC_KEY_PROPERTY = "sshPublicKey";
    public static final String SSH_PUBLIC_KEY_POSTFIX = "-" + SSH_PUBLIC_KEY_PROPERTY;
    public static final String SSH_DATE_POSTFIX = "-date";
    public static final String SSH_PUBLIC_KEY_DATE_POSTFIX = "-" + SSH_PUBLIC_KEY_PROPERTY + SSH_DATE_POSTFIX;

    public PublicKeyAuthenticator(UserGroupStoreService userGroupStoreService,
            ArtifactoryHome artifactoryHome, ArtifactoryContext artifactoryContext) {
        this.userGroupStoreService = userGroupStoreService;
        this.artifactoryHome = artifactoryHome;
        this.artifactoryContext = artifactoryContext;
    }

    @Override
    public boolean authenticate(String username, PublicKey key, ServerSession session) {
        return SshThreadBinder.callBound(artifactoryHome, artifactoryContext,
                () -> authenticateInternal(username, key, session));
    }

    private boolean authenticateInternal(String username, PublicKey key, ServerSession session) {
        String sshPublicKey;
        try {
            sshPublicKey = decodedPublicKey(key);
        } catch (IOException e) {
            log.error("Failed to read public key as blob", e);
            return false;
        }
        UserInfo userInfo = userGroupStoreService.findUserByProperty(SSH_PUBLIC_KEY_POSTFIX, sshPublicKey, false);
        // Remove the entire line below when removing the deprecated method backwardCompatabilityAuthentication.
        userInfo = backwardCompatabilityAuthentication(userInfo, sshPublicKey);
        if (userInfo != null) {
            session.setAttribute(new UsernameAttributeKey(), userInfo.getUsername());
            return true;
        }
        return false;
    }

    /**
     * @deprecated Backward compatibility for users authentication bases SSH key.
     * This method is needed in case of HA. Different versions in different nodes can cause authentication issues,
     * therefore, we are keeping the old implementation, adding new user properties to all the users who pass
     * this authentication but fail in the new authentication implementation.
     * @since v7.58
     * @param userInfo
     * @param sshPublicKey
     * @return UserInfo nullable
     */
    @Deprecated(forRemoval = true)
    @Nullable
    private UserInfo backwardCompatabilityAuthentication(UserInfo userInfo, String sshPublicKey){
        if (userInfo == null){
            UserInfo userInfoOldAuthentication
                    = userGroupStoreService.findUserByProperty(SSH_PUBLIC_KEY_PROPERTY, sshPublicKey, true);
            if (userInfoOldAuthentication != null) {
                // add property with "default" prefix alias
                userGroupStoreService.addUserProperty(userInfoOldAuthentication.getUsername(),
                        DEFAULT + SSH_PUBLIC_KEY_POSTFIX, sshPublicKey);
                userGroupStoreService.addUserProperty(userInfoOldAuthentication.getUsername(),
                        DEFAULT + SSH_PUBLIC_KEY_DATE_POSTFIX,
                        String.valueOf(System.currentTimeMillis()));
            }
            return userInfoOldAuthentication;
        }
        return userInfo;
    }
    /**
     * decode public key and return it as string
     *
     * @param key - public key
     */
    @VisibleForTesting
    String decodedPublicKey(PublicKey key) throws IOException {
        String algorithm = key.getAlgorithm();
        if ("RSA".equals(algorithm)) {
            byte[] rawKey = rsaKeyToBytes((RSAPublicKey) key);
            return "ssh-rsa " + Base64.encodeBase64String(rawKey);
        } else if ("EC".equals(algorithm)) {
            int len = ((ECKey) key).getParams().getOrder().bitLength();
            byte[] rawKey = ecKeyToBytes(key, len);
            return EC + len + " " + Base64.encodeBase64String(rawKey);
        }
        return "";
    }

    /**
     * convert rsa key to bytes
     *
     * @param publicKey - rsa public key
     * @return rsa key as byte array
     */
    private static byte[] rsaKeyToBytes(RSAPublicKey publicKey) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthFirst("ssh-rsa".getBytes(), out);
        writeLengthFirst(publicKey.getPublicExponent().toByteArray(), out);
        writeLengthFirst(publicKey.getModulus().toByteArray(), out);
        return out.toByteArray();
    }

    private static byte[] ecKeyToBytes(PublicKey publicKey, int len) throws IOException {
        byte[] point = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(publicKey.getEncoded()))
                .getPublicKeyData().getOctets();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        dataOutputStream.writeInt((EC + len).getBytes().length);
        dataOutputStream.write((EC + len).getBytes());
        dataOutputStream.writeInt(("nistp" + len).getBytes().length);
        dataOutputStream.write(("nistp" + len).getBytes());
        dataOutputStream.writeInt(point.length);
        dataOutputStream.write(point);
        return byteArrayOutputStream.toByteArray();
    }

    // http://www.ietf.org/rfc/rfc4253.txt

    /**
     * write public key data to byte array output stream
     *
     * @param array - byte array
     * @param out   - byte array output stream
     */
    private static void writeLengthFirst(byte[] array, ByteArrayOutputStream out) throws IOException {
        out.write((array.length >>> 24) & 0xFF);
        out.write((array.length >>> 16) & 0xFF);
        out.write((array.length >>> 8) & 0xFF);
        out.write((array.length) & 0xFF);
        if (array.length == 1 && array[0] == (byte) 0x00) {
            out.write(new byte[0]);
        } else {
            out.write(array);
        }
    }
}
