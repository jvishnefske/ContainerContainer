package org.artifactory.version.converter.v319;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.ResourceUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Omri Ziv
 */
public class GpgToDefaultGpgKeyPairConverterTest extends XmlConverterTest {

    private final String passphrase = "nonono";
    private final String DEBIAN_LOCAL = "deb-local";
    private String publicKey;
    private String privateKey;
    private File publicKeyFile;
    private File privateKeyFile;
    private static final String PRIMARY_KEY_PAIR_REF_KEY = "primaryKeyPairRef";
    private static final String PASSPHRASE_KEY = "passphrase";
    private static final String PUBLIC_KEY_KEY = "publicKey";
    private static final String PRIVATE_KEY_KEY = "privateKey";
    private static final String KEY_PAIRS_KEY = "keyPairs";

    @BeforeClass
    private void setupClass() {
        privateKey = ResourceUtils.getResourceAsString("/keys/gpg-private.key");
        publicKey = ResourceUtils.getResourceAsString("/keys/gpg-public.key");
    }

    @BeforeMethod
    private void setup() {
        publicKeyFile = new File(ArtifactoryHome.get().getSecurityDir(), "artifactory.gpg.public");
        privateKeyFile = new File(ArtifactoryHome.get().getSecurityDir(), "artifactory.gpg.private");
        try {
            java.nio.file.Files.deleteIfExists(publicKeyFile.toPath());
            java.nio.file.Files.deleteIfExists(privateKeyFile.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete gpg keys from fileSystem", e);
        }
    }

    @Test
    public void testConvertGpg() throws Exception {
        FileUtils.write(publicKeyFile, publicKey, "utf8");
        FileUtils.write(privateKeyFile, privateKey, "utf8");

        Document doc = convertXml("/config/test/config.3.1.8_gpg.xml",
                new GpgToDefaultGpgKeyPairConverter());

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element keyPairs = rootElement.getChild(KEY_PAIRS_KEY, namespace);
        assertNotNull(keyPairs);
        assertEquals(keyPairs.getChildren().size(), 1);
        Element keyPair = (Element) keyPairs.getContent(0);
        assertEquals(publicKey, keyPair.getChild(PUBLIC_KEY_KEY, namespace).getText());
        assertEquals(privateKey, CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                keyPair.getChild(PRIVATE_KEY_KEY, namespace).getText()));
        assertEquals(passphrase, CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                keyPair.getChild(PASSPHRASE_KEY, namespace).getText()));
        Element repoElement = findLocalRepoByKey(rootElement, namespace, DEBIAN_LOCAL);
        assertEquals(repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace).getText(), DEFAULT_GPG_KEY);
        verifyInPlace(repoElement, repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace), namespace);
        assertNull(rootElement.getChild("security", namespace)
                .getChild("signingKeysSettings", namespace)
                .getChild(PASSPHRASE_KEY, namespace));
    }

    @Test
    public void testConvertNoGpg() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.8_no_gpg.xml",
                new GpgToDefaultGpgKeyPairConverter());

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element keyPairs = rootElement.getChild(KEY_PAIRS_KEY, namespace);
        assertEquals(keyPairs.getChildren().size(), 0);
        Element repoElement = findLocalRepoByKey(rootElement, namespace, DEBIAN_LOCAL);
        assertNull(repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace));
    }

    @Test
    public void testConvertOnlyPublicGpg() throws Exception {
        FileUtils.write(publicKeyFile, publicKey, "utf8");

        Document doc = convertXml("/config/test/config.3.1.8_no_gpg.xml",
                new GpgToDefaultGpgKeyPairConverter());

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element keyPairs = rootElement.getChild(KEY_PAIRS_KEY, namespace);
        assertNotNull(keyPairs);
        assertEquals( keyPairs.getChildren().size(), 1);
        Element keyPair = (Element) keyPairs.getContent(0);
        assertEquals(publicKey, keyPair.getChild(PUBLIC_KEY_KEY, namespace).getText());
        assertEquals(StringUtils.EMPTY, keyPair.getChild(PRIVATE_KEY_KEY, namespace).getText());
        assertNull(keyPair.getChild(PASSPHRASE_KEY, namespace));
        Element repoElement = findLocalRepoByKey(rootElement, namespace, DEBIAN_LOCAL);
        assertEquals(repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace).getText(), DEFAULT_GPG_KEY);
        verifyInPlace(repoElement, repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace), namespace);
    }

    @Test
    public void testConvertOnlyPrivateGpg() throws Exception {
        FileUtils.write(privateKeyFile, privateKey, "utf8");
        Document doc = convertXml("/config/test/config.3.1.8_gpg.xml",
                new GpgToDefaultGpgKeyPairConverter());

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element keyPairs = rootElement.getChild(KEY_PAIRS_KEY, namespace);
        assertNotNull(keyPairs);
        assertEquals(keyPairs.getChildren().size(), 1);
        Element keyPair = (Element) keyPairs.getContent(0);
        assertEquals(privateKey, CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                keyPair.getChild(PRIVATE_KEY_KEY, namespace).getText()));
        assertEquals(passphrase, CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                keyPair.getChild(PASSPHRASE_KEY, namespace).getText()));
        assertEquals(StringUtils.EMPTY, keyPair.getChild(PUBLIC_KEY_KEY, namespace).getText());
        Element repoElement = findLocalRepoByKey(rootElement, namespace, DEBIAN_LOCAL);
        assertEquals(repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace).getText(), DEFAULT_GPG_KEY);
        verifyInPlace(repoElement, repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace), namespace);
        assertNull(rootElement.getChild("security", namespace)
                .getChild("signingKeysSettings", namespace)
                .getChild(PASSPHRASE_KEY, namespace));
    }

    @Test
    public void testRenameKeyRefToPrimaryKeyRef() throws Exception {

        Document doc = convertXml("/config/test/config.3.1.8_keyRef.xml",
                new GpgToDefaultGpgKeyPairConverter());

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element keyPairs = rootElement.getChild(KEY_PAIRS_KEY, namespace);
        assertNotNull(keyPairs);
        assertEquals(keyPairs.getChildren().size(), 1);
        Element repoElement = findLocalRepoByKey(rootElement, namespace, "alpine-loco");
        assertEquals(repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace).getText(), "boom1");
        verifyInPlace(repoElement, repoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace), namespace);
        Element virtRepoElement = findVirtualRepoByKey(rootElement, namespace, "rpm-virtual");
        assertEquals(virtRepoElement.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace).getText(), "boom1");
    }

    private Element findLocalRepoByKey(Element rootElement, Namespace namespace, String repoKey) {
        return findRepoByKey(rootElement.getChild("localRepositories", namespace)
                , namespace, repoKey);
    }

    private Element findVirtualRepoByKey(Element rootElement, Namespace namespace, String repoKey) {
        return findRepoByKey(rootElement.getChild("virtualRepositories", namespace)
                , namespace, repoKey);
    }

    private Element findRepoByKey(Element repositoriesElement, Namespace namespace, String repoKey) {
        return repositoriesElement
                .getChildren()
                .stream()
                .filter(element -> element.getChild("key", namespace).getText().equals(repoKey))
                .findFirst().get();
    }

    private void verifyInPlace(Element repoElement, Element primaryKeyPairElement, Namespace namespace) {
        int primaryKeyPairIndex = repoElement.getChildren().indexOf(primaryKeyPairElement);
        assertEquals(primaryKeyPairIndex, repoElement.getChildren().indexOf(
                repoElement.getChild("forceNugetAuthentication", namespace)) + 1);
        assertEquals(primaryKeyPairIndex, repoElement.getChildren().indexOf(
                repoElement.getChild("blackedOut", namespace)) - 1);
    }

}