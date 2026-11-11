package org.artifactory.version.converter.v336;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.security.file.PemHelper;

import java.io.File;

import static org.jfrog.security.file.PemHelper.readCertificate;

@Slf4j
public class MigrateSamlEncryptionToAccessConverter implements XmlConverter {
    @Override
    public void convert(Document config) {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        File publicKeyFile = artifactoryHome.getArtifactorySAMLEncryptedAssertionPublicKeyFile();
        File privateKeyFile = artifactoryHome.getArtifactorySAMLEncryptedAssertionPrivateKeyFile();
        if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                AccessConfigHolder.getInstance().setSamlEncryption(
                        SamlEncryptedAssertion.builder()
                                .override(false)
                                .certificate(readCertificate(publicKeyFile))
                                .privateKey(PemHelper.readPrivateKey(privateKeyFile))
                                .build());
            } catch (Exception e) {
                log.debug("failed to read Saml Encryption files");
            }
        }
    }
}
