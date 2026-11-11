package org.artifactory.ui.rest.resource.utils.repositories;

import com.google.common.annotations.VisibleForTesting;
import lombok.Setter;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.service.utils.UtilsServiceFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_CONTINUE_STATE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_DEFAULT_VALUE;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PACKAGE_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_STAGES_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PROJECTS_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_REPO_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_SEARCH_PARAM;
import static org.artifactory.ui.rest.resource.utils.UtilsResourcesConstants.PATH_AUTO_COMPLETE_REPODATA;

/**
 *
 */
@Setter
@Component
@Path(PATH_AUTO_COMPLETE_REPODATA)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AutoCompleteRepositoriesDataResource extends BaseResource {

    @Resource
    private UtilsServiceFactory utilsServiceFactory;
    /**
     * Gets the list of repos according to the prefix, sorted by length of keys.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAutoCompleteRepoData(
                    @QueryParam(AUTO_COMPLETE_SEARCH_PARAM) String search,
                    @QueryParam(AUTO_COMPLETE_REPO_TYPE_PARAM) List<String> repoType,
                    @QueryParam(AUTO_COMPLETE_PACKAGE_TYPE_PARAM) List<String> packageType,
                    @DefaultValue(AUTO_COMPLETE_LIMIT_DEFAULT_VALUE)
                    @QueryParam(AUTO_COMPLETE_LIMIT_PARAM) String limit,
                    @QueryParam(AUTO_COMPLETE_CONTINUE_STATE_PARAM) String continueState,
                    @QueryParam(AUTO_COMPLETE_PROJECTS_PARAM) String projectKeys,
                    @QueryParam(AUTO_COMPLETE_STAGES_PARAM) String stages) {
        return runService(utilsServiceFactory.getAutoCompleteRepositoriesService());
    }

    @Override
    @VisibleForTesting
    protected <Y> Response runService(RestService<Y> service) {
        return super.runService(service);
    }
}
