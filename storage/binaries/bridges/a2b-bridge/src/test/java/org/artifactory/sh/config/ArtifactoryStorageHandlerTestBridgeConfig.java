package org.artifactory.sh.config;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.sh.ArtifactoryBinaryServiceMock;
import org.artifactory.sh.ArtifactoryTestBinariesDao;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.jfrog.storage.JdbcHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * configuration class of this test module to wire mocks for artifactory test suited (storage-db)
 */
@Slf4j
@Configuration
public class ArtifactoryStorageHandlerTestBridgeConfig {

    public ArtifactoryStorageHandlerTestBridgeConfig() {
        log.info("injecting spring beans for storage-handler-a2b-bridge test module");
    }

    @Bean
    ArtifactoryBinaryService artifactoryBinaryService(ArtifactoryTestBinariesDao binariesDao) {
        return new ArtifactoryBinaryServiceMock(binariesDao);
    }

    @Bean
    ArtifactoryTestBinariesDao binariesDao(JdbcHelper jdbcHelper) {
        return new ArtifactoryTestBinariesDao(jdbcHelper);
    }
}
