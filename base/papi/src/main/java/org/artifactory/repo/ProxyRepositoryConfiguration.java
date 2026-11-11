package org.artifactory.repo;

/**
 * @author Alexei Vainshtein
 */
public interface ProxyRepositoryConfiguration {

    String getProxy();

    /**
     * @return <code>true</code> - in case proxy should be ignored (includes default and {@link #getProxy()})
     */
    boolean isDisableProxy();
}
