package org.artifactory.rest.services.retention;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.util.ResponseUtils;
import org.artifactory.retention.exceptions.ArtifactRetentionWrongInstanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.StreamingOutput;

/**
 * @author Aviv Anidjar
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionSearchService implements RestService<String> {
    private static final Logger log = LoggerFactory.getLogger(ArtifactRetentionSearchService.class);

    @Override
    public void execute(ArtifactoryRestRequest<String> searchRequest, RestResponse response) {
        try {
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            AqlRestResult restResult =
                    ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactRetentionAddon.class)
                            .searchOnColdInstance(searchRequest.getImodel(), statusHolder);
            if (statusHolder.hasErrors()) {
                response.responseCode(statusHolder.getStatusCode()).error(statusHolder.getStatusMsg());
                return;
            }
            StreamingOutput stream = ResponseUtils.aqlRestResultToStreamingOutput(restResult);
            response.iModel(stream);
        } catch (AqlException e) {
            handleAqlSearchException(searchRequest.getImodel(), e, response);
        } catch (ArtifactRetentionWrongInstanceException e) {
            response.error(e.getMessage()).responseCode(HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            log.error("Failed to invoke search on Cold Artifact Storage instance. Caught exception: ", e);
            response.responseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleAqlSearchException(String aqlStr, AqlException e, RestResponse response) {
        log.error("Failed to execute query: {}: {}", aqlStr, e.getMessage());
        log.debug("Failed to execute query: {}: ", aqlStr, e);
        if (e.getCause() == null) {
            response.error(e.getMessage()).responseCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.error(e.getMessage() + "\ncause: " + e.getCause().getMessage())
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
