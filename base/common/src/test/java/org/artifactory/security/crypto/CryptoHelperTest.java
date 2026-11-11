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

package org.artifactory.security.crypto;

import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.security.common.KeyUtils;
import org.jfrog.security.crypto.CipherAlg;
import org.jfrog.security.crypto.DecodedKeyPair;
import org.jfrog.security.crypto.EncodedKeyPair;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.testng.annotations.Test;

import java.io.File;

import static org.jfrog.security.crypto.EncodingType.MASTER_LEVEL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the CryptoHelper.
 *
 * @author Yossi Shaul
 * @author Noam Tenne
 */
@Test
public class CryptoHelperTest extends ArtifactoryHomeBoundTest {

    public void isNotEncrypted() {
        assertFalse(CryptoHelper.isEncryptedUserPassword("blabla"));
        assertFalse(CryptoHelper.isArtifactoryKeyEncrypted("blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{RSA}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{ENC}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{DESede}blabla"));
        assertFalse(CryptoHelper.isArtifactoryKeyEncrypted("{DESede}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("\\{DESede\\}blabla"), "Escaped maven encryption prefix");
        assertFalse(CryptoHelper.isArtifactoryKeyEncrypted("\\{DESede\\}blabla"),
                "Escaped maven encryption prefix not master");
        assertFalse(CryptoHelper.isEncryptedUserPassword("\\{DESede}blabla"));
        assertFalse(CryptoHelper.isEncryptedUserPassword("{DESede\\}blabla"));
    }

    public void encryptDecryptMasterKeyNoArtifactoryKey() {
        // make sure there is no artifactory key so we work with master only.
        if (CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        }

        String pass = "mySuper34Hard42Password";
        String encryptPass = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), pass);
        assertNotEquals(encryptPass, pass, "Master key is always present encryption should always run");
        assertTrue(encryptPass.startsWith("JE"), "Encrypted password should start with JE");
        assertTrue(CryptoHelper.isMasterKeyEncrypted(encryptPass),
                "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPass),
                "Encrypted password should not be password encrypted");
        String encryptPass2 = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        assertEquals(encryptPass2, encryptPass, "Encrypting twice should not do anything");

        // Encrypting password then encrypt in master
        EncryptionWrapper artifactoryKeyWrapper = ArtifactoryHome.get().getArtifactoryEncryptionWrapper();
        EncryptionWrapper keyWrapper = EncryptionWrapperFactory
                .createKeyWrapper(artifactoryKeyWrapper, new EncodedKeyPair(
                        new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair()), artifactoryKeyWrapper));
        String passEncrypt = keyWrapper.encryptIfNeeded(pass);
        assertNotEquals(passEncrypt, encryptPass, "Encrypting pass should be different");
        assertFalse(CryptoHelper.isArtifactoryKeyEncrypted(passEncrypt),
                "Encrypted password should not be master encrypted");
        assertTrue(CryptoHelper.isEncryptedUserPassword(passEncrypt),
                "Encrypted password should be password encrypted");
        String encryptPassEncrypt = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), passEncrypt);
        assertNotEquals(encryptPassEncrypt, passEncrypt, "Encrypting pass should be different");
        assertTrue(CryptoHelper.isMasterKeyEncrypted(encryptPassEncrypt),
                "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPassEncrypt),
                "Encrypted password should not be password encrypted");

        String decrypted = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        assertEquals(decrypted, pass, "decrypted password should go back to origin");

        String decryptedPass = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPassEncrypt);
        assertEquals(decryptedPass, passEncrypt, "decrypted password encrypted should go back to pass encrypt");
        assertEquals(keyWrapper.decryptIfNeeded(decryptedPass).getDecryptedData(), pass,
                "decrypted password should go back to origin");
    }

    public void removeKeyFileOld() {
        if (!CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            CryptoHelper.createArtifactoryKeyFile(ArtifactoryHome.get(), CipherAlg.DESede);
        }
        File master = ArtifactoryHome.get().getArtifactoryKey();
        assertTrue(master.exists(), "Master encryption file not found at " + master.getAbsolutePath());
        CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        assertFalse(master.exists(), "Master encryption file found at " + master.getAbsolutePath());
    }

    public void testEmptyOrNullPasswordEncryptionOld() {
        if (CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        }
        CryptoHelper.createArtifactoryKeyFile(ArtifactoryHome.get(), CipherAlg.DESede);
        // First test with empty password
        String resultPassEmptyPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), "");
        assertEquals("", resultPassEmptyPassword);
        // Then test with a null password
        String resultPassNullPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), null);
        assertEquals(resultPassNullPassword, null);
    }

    public void removeKeyFile() {
        if (!CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            CryptoHelper.createArtifactoryKeyFile(ArtifactoryHome.get());
        }
        File master = ArtifactoryHome.get().getArtifactoryKey();
        assertTrue(master.exists(), "Master encryption file not found at " + master.getAbsolutePath());
        CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        assertFalse(master.exists(), "Master encryption file found at " + master.getAbsolutePath());
    }

    public void testEmptyOrNullPasswordEncryption() {
        if (CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        }
        CryptoHelper.createArtifactoryKeyFile(ArtifactoryHome.get());
        // First test with empty password
        String resultPassEmptyPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), "");
        assertEquals("", resultPassEmptyPassword);
        // Then test with a null password
        String resultPassNullPassword = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), null);
        assertEquals(resultPassNullPassword, null);
    }

    public void encryptDecryptMasterKeyWithArtifactoryKey() {
        String pass = "mySuper34Hard42Password";
        String encryptPass = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), pass);
        assertNotEquals(encryptPass, pass);
        assertTrue(encryptPass.startsWith("JE"), "Encrypted password should start with JE");
        assertTrue(CryptoHelper.isMasterKeyEncrypted(encryptPass),
                "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPass),
                "Encrypted password should not be password encrypted");
        String encryptPass2 = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        String decryptPass = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        String decryptPass2 = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPass2);
        assertEquals(decryptPass2, decryptPass, "Encrypting twice should be decryptable");

        // Encrypting password then encrypt in master
        EncryptionWrapper masterKeyWrapper = ArtifactoryHome.get().getArtifactoryEncryptionWrapper();
        EncryptionWrapper keyWrapper = EncryptionWrapperFactory.createKeyWrapper(masterKeyWrapper, new EncodedKeyPair(
                new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair()), masterKeyWrapper));
        String passEncrypt = keyWrapper.encryptIfNeeded(pass);
        assertNotEquals(passEncrypt, encryptPass, "Encrypting pass should be different");
        assertFalse(CryptoHelper.isArtifactoryKeyEncrypted(passEncrypt),
                "Encrypted password should not be master encrypted");
        assertTrue(CryptoHelper.isEncryptedUserPassword(passEncrypt),
                "Encrypted password should be password encrypted");
        String encryptPassEncrypt = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), passEncrypt);
        assertNotEquals(encryptPassEncrypt, passEncrypt, "Encrypting pass should be different");
        assertTrue(CryptoHelper.isMasterKeyEncrypted(encryptPassEncrypt),
                "Encrypted password should be master encrypted");
        assertFalse(CryptoHelper.isEncryptedUserPassword(encryptPassEncrypt),
                "Encrypted password should not be password encrypted");

        String decrypted = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPass);
        assertEquals(decrypted, pass, "decrypted password should go back to origin");

        String decryptedPass = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), encryptPassEncrypt);
        assertEquals(decryptedPass, passEncrypt, "decrypted password encrypted should go back to pass encrypt");
        assertEquals(keyWrapper.decryptIfNeeded(decryptedPass).getDecryptedData(), pass,
                "decrypted password should go back to origin");
    }

    public void decryptMasterKeyEncryption() {
        EncryptionWrapper aesKeyFile = EncryptionWrapperFactory.createAesKeyFile();
        KeyUtils.saveKeyToFile(new File(ArtifactoryHome.get().getSysConfig().helper().getMasterKeyFile()), aesKeyFile);
        String plainText = "secret";
        String secret = homeStub.getMasterEncryptionWrapper().encryptIfNeeded(plainText);
        assertTrue(MASTER_LEVEL.isEncodedByMe(secret));
        String s = CryptoHelper.decryptIfNeeded(homeStub, secret);
        assertEquals(plainText, s);
    }

    public void encryptIfNeededWithCustomKeyAfterMasterEncrypted() {
        ArtifactoryHome home = ArtifactoryHome.get();
        if (!CryptoHelper.hasArtifactoryKey(home)) {
            CryptoHelper.createArtifactoryKeyFile(home);
        }
        String pass = "rotationTargetSecret";
        String masterEncrypted = CryptoHelper.encryptIfNeeded(home, pass);
        assertTrue(CryptoHelper.isMasterKeyEncrypted(masterEncrypted));

        EncryptionWrapper artWrapper = home.getArtifactoryEncryptionWrapper();
        EncryptionWrapper customWrapper = EncryptionWrapperFactory.createKeyWrapper(artWrapper, new EncodedKeyPair(
                new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair()), artWrapper));

        String reWrapped = CryptoHelper.encryptIfNeeded(home, customWrapper, masterEncrypted);
        assertNotEquals(reWrapped, masterEncrypted);
        assertTrue(CryptoHelper.isEncryptedUserPassword(reWrapped));
        assertEquals(customWrapper.decryptIfNeeded(reWrapped).getDecryptedData(), pass);
    }
}
