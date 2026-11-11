package org.artifactory.version.converter.v336;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.jfrog.security.file.PemHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.cert.Certificate;

import static org.artifactory.test.TestUtils.createCertificate;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MigrateSamlEncryptionToAccessConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH =
            "/config/test/config.3.2.7_migrate_crowd_fields.xml";
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;
    private static AccessConfigHolder accessConfigHolder;

    @BeforeAll
    static void beforeAll() {
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        accessConfigHolder = Mockito.spy(AccessConfigHolder.class);
        //noinspection ResultOfMethodCallIgnored
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(accessConfigHolder);
    }

    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Test
    void testConvert() throws Exception {
        KeyPair keyPair = JFrogCryptoHelper.generateKeyPair();
        Certificate certificate = createCertificate(keyPair);
        SamlEncryptedAssertion expected = SamlEncryptedAssertion.builder()
                .override(false)
                .certificate(certificate)
                .privateKey(keyPair.getPrivate())
                .build();
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        PemHelper.saveCertificate(ArtifactoryHome.get().getArtifactorySAMLEncryptedAssertionPublicKeyFile(),
                certificate);
        PemHelper.savePrivateKey(ArtifactoryHome.get().getArtifactorySAMLEncryptedAssertionPrivateKeyFile(),
                keyPair.getPrivate());
        convertXml(RESOURCE_PATH, new MigrateSamlEncryptionToAccessConverter());
        SamlEncryptedAssertion actual = AccessConfigHolder.getInstance().drainSamlEncryptedAssertion();
        assertEquals(expected, actual);
        ArtifactoryHome.unbind();
    }
}
