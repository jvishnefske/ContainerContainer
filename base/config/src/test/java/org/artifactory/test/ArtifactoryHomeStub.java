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

package org.artifactory.test;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.mime.MimeTypes;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.security.crypto.JFrogMasterKeyEncrypter;

import jakarta.annotation.Nullable;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A stub of {@link ArtifactoryHome} for testing.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryHomeStub extends ArtifactoryHome {
    private JFrogMasterKeyEncrypter jFrogMasterKeyEncrypter;
    private File binaryStoreXmlFile;

    /**
     * @deprecated You must not use the shared home stub folder. Instead, specify a unique identifier for your home
     * stub.
     */
    @Deprecated(forRemoval = true)
    public ArtifactoryHomeStub() {
        this("testhome");
    }

    public ArtifactoryHomeStub(String homeIdentifier) {
        super("./target/test/" + homeIdentifier);
        setArtifactorySystemProperties(new ArtifactorySystemProperties());
    }

    @Override
    public void setMimeTypes(MimeTypes mimeTypes) {
        super.setMimeTypes(mimeTypes);
    }

    /**
     * Load artifactory system properties from java system properties and set on this configuration.
     */
    public ArtifactoryHomeStub loadSystemProperties() {
        ArtifactorySystemProperties props = new ArtifactorySystemProperties();
        props.loadArtifactorySystemProperties(null, null, null);
        setArtifactorySystemProperties(props);
        return this;
    }

    @Override
    public void initArtifactorySystemProperties() {
        super.initArtifactorySystemPropertiesInternal();
    }

    public ArtifactoryHomeStub bind() {
        ArtifactoryHome.bind(this);
        return this;
    }

    public ArtifactoryHomeStub setProperty(ConstantValues constant, String value) {
        return setProperty(constant.getPropertyName(), value);
    }

    public ArtifactoryHomeStub setProperty(String key, String value) {
        getArtifactoryProperties().setProperty(key, value);
        return this;
    }

    public ArtifactoryHomeStub setHaEnabled(boolean haEnabled) {
        getHaNodeProperties().getProperties().setProperty("enabled", Boolean.toString(haEnabled));
        return this;
    }

    @Override
    public File getBinaryStoreXmlFile() {
        if (binaryStoreXmlFile != null) {
            return binaryStoreXmlFile;
        }
        return super.getBinaryStoreXmlFile();
    }

    public void setBinaryStoreXmlFile(File file) {
        // Use to override binarystore.xml for testing
        binaryStoreXmlFile = file;
    }

    @Override
    @Nullable
    public JFrogMasterKeyEncrypter getJFrogMasterKeyEncrypter() {
        return jFrogMasterKeyEncrypter;
    }

    public void initializeJFrogMasterKeyEncrypter() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        SecretKey secretKey = keyGen.generateKey();
        jFrogMasterKeyEncrypter = new JFrogMasterKeyEncrypter(HexFormat.of().formatHex(secretKey.getEncoded()));
    }

    private void setArtifactorySystemProperties(ArtifactorySystemProperties props) {
        TestUtils.setField(this, "artifactorySystemProperties", props);
    }

    /**
     * Overriding this in order to silence the ERROR log produced by this method.
     */
    @Override
    public CompoundVersionDetails getRunningArtifactoryProductVersion() {
        return new CompoundVersionDetails(ArtifactoryServiceVersion.valueOf("7.0.0"), "0", System.currentTimeMillis());
    }
}
