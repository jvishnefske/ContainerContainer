package org.artifactory.model;

/**
 * @author Mor Merhav
 */
public interface ProxyRepoConfig {

    String getKey();

    void setProxyRef(String proxyKey);

    String getProxyRef();

    void setDisableProxy(boolean disable);

    boolean isProxyDisabled();
}
