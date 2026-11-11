package org.artifactory.rest.common.service;

import org.artifactory.util.HttpClientConfigurator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class HttpClientConfiguratorProvider {
    public HttpClientConfigurator getHttpClientConfigurator() {
        return new HttpClientConfigurator();
    }
}
