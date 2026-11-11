package org.artifactory.storage.db.devopsagent.requestlog.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.spring.ContextCreationListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RequestLogServiceContextListener implements ContextCreationListener {

    private final ArtifactoryDbProperties dbProperties;

    @Autowired
    public RequestLogServiceContextListener(ArtifactoryDbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    @Override
    public void onContextCreated() {
        log.info("agentic_api.request_logs.ready dbType={}", dbProperties.getDbType());
    }
}
