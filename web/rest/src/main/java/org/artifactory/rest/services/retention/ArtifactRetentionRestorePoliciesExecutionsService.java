package org.artifactory.rest.services.retention;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionRestoreExecutionStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.RestUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_IN_RANGE_FROM;
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_IN_RANGE_TO;

/**
 * @author Aviv Anidjar
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionRestorePoliciesExecutionsService
        implements RestService<ArtifactRetentionRestoreExecutionStatus> {
    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionRestoreExecutionStatus> request,
            RestResponse response) {
        String fromDate = request.getQueryParamByKey(PARAM_IN_RANGE_FROM);
        String toDate = request.getQueryParamByKey(PARAM_IN_RANGE_TO);
        if (fromDate.isEmpty()) {
            response.error("Cannot get monitoring restore executions, missing param " + PARAM_IN_RANGE_FROM)
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        if (toDate.isEmpty()) {
            response.error("Cannot get monitoring restore executions, missing param " + PARAM_IN_RANGE_TO)
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        List<ArtifactRetentionRestoreExecutionStatus> policyModels =
                addonsManager.addonByType(ArtifactRetentionAddon.class)
                        .getRestorePoliciesExecutions(RestUtils.extractLongEpoch(fromDate),
                                RestUtils.extractLongEpoch(toDate));
        response.iModel(policyModels);
    }
}