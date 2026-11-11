package org.artifactory.sh.config;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.sh.ArtifactoryBinaryProviderTestService;
import org.artifactory.sh.ArtifactoryBinaryProviderTestServiceImpl;
import org.artifactory.sh.ArtifactoryTestBinariesTaskDao;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ArtifactoryStorageHandlerITestBridgeConfig {

    public ArtifactoryStorageHandlerITestBridgeConfig() {
        log.info("injecting spring beans for storage-handler-itest-bridge test module");
    }

    @Bean
    ArtifactoryBinaryProviderTestService artifactoryBinaryProviderTestService(
            InternalBinaryService internalBinaryService) {
        return new ArtifactoryBinaryProviderTestServiceImpl(internalBinaryService);
    }

    @Bean
    ArtifactoryTestBinariesTaskDao artifactoryTestBinariesTaskDao(JdbcHelper jdbcHelper) {
        return new ArtifactoryTestBinariesTaskDao(jdbcHelper);
    }

}
