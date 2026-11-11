package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionArchiveExecutionStatus;
import org.artifactory.addon.retention.ArtifactRetentionExecutionStatus;
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
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_SEARCH_STRING;

/**
 * @author Aviv Anidjar
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionArchivePoliciesExecutionsService
        implements RestService<List<ArtifactRetentionExecutionStatus>> {

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String fromDate = request.getQueryParamByKey(PARAM_IN_RANGE_FROM);
        String toDate = request.getQueryParamByKey(PARAM_IN_RANGE_TO);
        String searchStr = request.getQueryParamByKey(PARAM_SEARCH_STRING);

        List<ArtifactRetentionArchiveExecutionStatus> policyModels =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactRetentionAddon.class)
                        .getArchivePoliciesExecutions(RestUtils.extractLongEpoch(fromDate),
                                RestUtils.extractLongEpoch(toDate), searchStr);
        response.iModel(policyModels);
    }
}