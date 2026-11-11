package org.artifactory.devopsagent.errorcatalog.service;

import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogEventBuffer;
import org.artifactory.devopsagent.errorcatalog.ingestion.SignatureToTemplateIdCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring beans for the error_catalog producer plumbing — singletons reused by the appender,
 * the flush job, and the retention job.
 */
@Configuration
public class ErrorCatalogBeansConfig {

    @Bean
    public ErrorCatalogEventBuffer errorCatalogEventBuffer() {
        return new ErrorCatalogEventBuffer();
    }

    @Bean
    public SignatureToTemplateIdCache signatureToTemplateIdCache() {
        return new SignatureToTemplateIdCache();
    }
}
