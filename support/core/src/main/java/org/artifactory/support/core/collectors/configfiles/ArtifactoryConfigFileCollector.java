package org.artifactory.support.core.collectors.configfiles;

import com.google.common.collect.Lists;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.support.common.core.collectors.ConfigFilesCollector;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;

/**
 * @author Tamir Hadad
 */
public class ArtifactoryConfigFileCollector extends ConfigFilesCollector {

    private ArtifactoryHome home;

    public ArtifactoryConfigFileCollector(ArtifactoryHome home) {
        super();
        this.home = home;
    }

    @Override
    protected List<File> getConfigFiles() {
        List<File> configFiles = Lists.newArrayList();
        configFiles.add(home.getArtifactoryConfigBootstrapFile());
        configFiles.add(home.getArtifactoryConfigFile());
        configFiles.add(home.getArtifactoryConfigImportFile());
        configFiles.add(home.getArtifactoryConfigLatestFile());
        configFiles.add(home.getArtifactoryConfigNewBootstrapFile());
        configFiles.add(home.getLogbackConfig());
        configFiles.add(home.getMimeTypesFile());
        configFiles.add(home.getBinaryStoreXmlFile());
        configFiles.add(home.getArtifactorySystemPropertiesFile());
        // As the system.yaml is only read by the SysConfig and not actually handled by Artifactory, special handle here
        Path systemYamlPath = Paths.get(home.getSysLayout().getProductEtc().getAbsolutePath(), SYSTEM_YAML_FILENAME);
        configFiles.add(systemYamlPath.toFile());

        return configFiles;
    }

    @Override
    protected List<String> getScrubStrings() {
        return List.of(
                "password=",
                "password:",
                "<password>",
                "<refreshToken>",
                "<secret>",
                "<managerPassword>",
                "<passphrase>",
                "<gpgPassPhrase>",
                "<keyStorePassword>",
                "<identity>",
                "<credential>",
                "artifactory.bintray.system",
                "<privateKey>",
                "<accountName>",
                "<accountKey>",
                "<proxyIdentity>",
                "<proxyCredential>",
                "<clientSecret>",
                "<tenantId>",
                "<sasToken>",
                "<cloudFrontPrivateKey>",
                "<clientId>"
        );
    }
}
