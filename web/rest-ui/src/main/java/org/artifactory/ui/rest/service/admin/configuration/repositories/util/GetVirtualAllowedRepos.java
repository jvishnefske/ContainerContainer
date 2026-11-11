package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.ws.rs.core.Response;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ph.RepoListType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAllowedReposModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Filter the list of repos to be shown in the dialog when creating a virtual repo.
 *
 * @author Aishwarya Singh
 */
@Component
public class GetVirtualAllowedRepos<T extends VirtualAllowedReposModel> implements RestService<T> {

    @Setter(onMethod_ = {@Autowired})
    private RepoConfigValidator repoValidator;
    @Setter(onMethod_ = {@Autowired})
    private RepoConfigDescriptorBuilder repoBuilder;
    @Setter(onMethod_ = {@Autowired})
    private CentralConfigService configService;
    @Setter(onMethod_ = {@Autowired})
    private PackageResourceService packageResourceService;

    private static final String DUMMY_REPO_KEY = "dummyRepoKey";

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        try {
            VirtualRepositoryConfigModel virtualConfigModel = request.getImodel().getVirtualConfigModel();

            // if "repoKey" is not present ... then "toRepoConfig" errors out ... so "DUMMY_REPO_KEY" is set
            if (StringUtils.isBlank(virtualConfigModel.getGeneral().getRepoKey())) {
                virtualConfigModel.getGeneral().setRepoKey(DUMMY_REPO_KEY);
            }

            VirtualRepoConfig repoConfig = virtualConfigModel.toRepoConfig(repoValidator, repoBuilder,
                    configService.getMutableDescriptor());

            if (repoConfig.getRepoType() != RepoDetailsType.VIRTUAL) {
                response.error("Request model was not of virtual repository type.")
                        .responseCode(Response.Status.BAD_REQUEST.getStatusCode());
            } else {
                Map<RepoListType, List<String>> result = packageResourceService.getVirtualAllowedRepos(repoConfig,
                        request.getImodel().getRepoLists());
                response.entity(result);
            }
        } catch (Exception e) {
            response.error(e.getMessage()).responseCode(Response.Status.BAD_REQUEST.getStatusCode());
        }
    }
}
