package org.artifactory.ui.rest.model.admin.configuration.repository;

public interface ProxyAwareConfigModel {

    String getProxy();

    void setProxy(String proxy);

    Boolean isDisableProxy();

    void setDisableProxy(Boolean disableProxy);
}
