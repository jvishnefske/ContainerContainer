package org.artifactory.api.config;

import com.jfrog.sysconf.SysConfig;

/**
 * Artifactory system configuration. Stems from the global {@link com.jfrog.sysconf.SysConfig}.
 *
 * @author Uriah Levy
 */
public interface ArtifactorySystemConfig {

    String getRouterUrl();

    boolean isRouterEnabled();

    boolean isJFConnectEnabled();

    /**
     * Returns <code>true</code> if config properties were set to false and not just left unspecified.
     */
    boolean isJFConnectDisabledExplicitly();

    SysConfig getSysConfig();

    String getNodeId();

    String getCentralOAuthRedirectUrlOrFail();

    CentralOAuthSettings getCentralOAuthDefaultConfig();
}