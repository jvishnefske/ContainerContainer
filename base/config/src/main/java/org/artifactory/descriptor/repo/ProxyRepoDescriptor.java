package org.artifactory.descriptor.repo;

/**
 * @author Alexei Vainshtein
 */
public interface ProxyRepoDescriptor {

    void setProxy(ProxyDescriptor proxy);

    void setDisableProxy(boolean disable);
}
