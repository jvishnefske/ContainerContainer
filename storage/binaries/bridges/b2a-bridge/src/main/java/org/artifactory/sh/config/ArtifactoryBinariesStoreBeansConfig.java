package org.artifactory.sh.config;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnProperty;
import org.jfrog.storage.beans.BinariesStoreBeansConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * This class enhances artifactory's spring namespace scanning to storage-handler modules
 *
 * @author TamirH
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "artifactory.use.storage.service", havingValue = "false", matchIfMissing = true)
@Import(BinariesStoreBeansConfig.class)
public class ArtifactoryBinariesStoreBeansConfig {

    public ArtifactoryBinariesStoreBeansConfig() {
        log.info("injecting spring beans for storage-handler-b2a-bridge module");
    }
}
