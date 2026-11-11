package org.artifactory.sh.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ConditionalOnProperty;
import org.jfrog.security.crypto.JFrogMasterKeyEncrypter;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.jfrog.storage.binstore.config.BinaryProviderConfigDecrypter;
import org.jfrog.storage.binstore.config.BinaryProviderConfigEncrypterImpl;
import org.jfrog.storage.binstore.ifc.BinaryProviderConfigEncrypter;
import org.jfrog.storage.config.BinaryStoreConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author TamirH
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "artifactory.use.storage.service", havingValue = "true")
public class StorageServiceConfig {

    {
        log.info("Injecting spring beans for MICROSERVICE binary-store-code module");
    }

    @Bean
    public StorageClientMetricsProvider storageClientMetricsProvider() {
        return new StorageClientMetricsProvider();
    }

    @Bean
    public InternalBinaryService binaryInternalService(AccessService accessService, ArtifactoryTracer tracer,
            StorageClientMetricsProvider storageClientMetricsProvider, BinaryStoreConfigService configService) {
        return new StorageBinaryServiceImpl(accessService, tracer, storageClientMetricsProvider, configService);
    }

    @Bean
    public BinaryProviderConfigEncrypter binaryProviderConfigEncrypter(BinaryStoreConfigService configService) {
        return getProviderConfigDecEnc(configService);
    }

    @Bean
    public BinaryProviderConfigDecrypter binaryProviderConfigDecrypter(BinaryStoreConfigService configService) {
        return getProviderConfigDecEnc(configService);
    }

    private BinaryProviderConfigEncrypterImpl getProviderConfigDecEnc(BinaryStoreConfigService configService) {
        JFrogMasterKeyEncrypter jfrogMasterKey = configService.getJfrogMasterKey();
        return new BinaryProviderConfigEncrypterImpl(jfrogMasterKey);
    }
}
