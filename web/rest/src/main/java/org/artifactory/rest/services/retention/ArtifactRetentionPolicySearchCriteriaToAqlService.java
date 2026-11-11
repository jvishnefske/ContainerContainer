package org.artifactory.rest.services.retention;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionCandidateCriteria;
import org.artifactory.addon.retention.mapper.SearchCriteriaMapper;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author Tamar Rosen
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionPolicySearchCriteriaToAqlService
        implements RestService<ArtifactRetentionCandidateCriteria> {

    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionCandidateCriteria> convertRequest,
            RestResponse response) {
        ArtifactRetentionCandidateCriteria searchCriteriaModel = convertRequest.getImodel();
        if (Objects.isNull(searchCriteriaModel)) {
            response.error("Cannot preview archive policy - search criteria form is invalid")
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
        }
        SearchCriteriaMapper searchCriteriaMapper = Mappers.getMapper(SearchCriteriaMapper.class);
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        AqlBase generatedAql = addonsManager.addonByType(ArtifactRetentionAddon.class).convertSearchCriteriaFormToAql(
                searchCriteriaMapper.searchCriteriaModelToDescriptor(searchCriteriaModel));
        response.iModel(generatedAql.toNative(0));
    }
}


