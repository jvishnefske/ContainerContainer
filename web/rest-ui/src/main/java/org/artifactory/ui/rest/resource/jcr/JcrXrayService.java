package org.artifactory.ui.rest.resource.jcr;

import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.util.HttpClientConfigurator;
import org.springframework.stereotype.Component;

/**
 * @author dudim
 */
@Component
public class JcrXrayService {

    private final CloseableHttpClient client = createClient();

    public CloseableHttpClient getClient() {
        return client;
    }

    private CloseableHttpClient createClient() {
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        return configurator.connectionTimeout(ConstantValues.xrayClientNormalSocketTimeoutMillis.getInt())
                .connectionTimeout(ConstantValues.xrayClientConnectionTimeoutMillis.getInt())
                .connectionRequestTimeout(ConstantValues.xrayClientConnectionRequestTimeoutMillis.getInt())
                .socketTimeout(ConstantValues.xrayClientNormalSocketTimeoutMillis.getInt())
                .staleCheckingEnabled(true)
                .retry(1, false)
                .build();
    }
}
