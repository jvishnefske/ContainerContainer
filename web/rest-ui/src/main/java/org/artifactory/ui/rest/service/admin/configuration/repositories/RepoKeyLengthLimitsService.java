package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.validation.RepoKeyLengthLimitsHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;

/**
 * @author Oleksandr Legenkiy
 */
@Service
public class RepoKeyLengthLimitsService implements RestService<RepoKeyLengthLimits> {

    public static final String REPO_TYPE_QUERY_PARAM_KEY = "repoType";
    public static final String REPO_PACKAGE_TYPE_QUERY_PARAM_KEY = "repoPackageType";

    private final RepoKeyLengthLimitsHelper repoKeyLengthLimitsHelper;

    @Autowired
    public RepoKeyLengthLimitsService(RepoKeyLengthLimitsHelper repoKeyLengthLimitsHelper) {
        this.repoKeyLengthLimitsHelper = repoKeyLengthLimitsHelper;
    }

    @Override
    public void execute(ArtifactoryRestRequest<RepoKeyLengthLimits> request, RestResponse response) {
        String repoTypeParam = request.getQueryParamByKey(REPO_TYPE_QUERY_PARAM_KEY);
        String repoPackageTypeParam = request.getQueryParamByKey(REPO_PACKAGE_TYPE_QUERY_PARAM_KEY);
        if (StringUtils.isAnyBlank(repoTypeParam, repoPackageTypeParam)) {
            response.error("Query params '%s' and '%s' are required".formatted(REPO_TYPE_QUERY_PARAM_KEY,
                    REPO_PACKAGE_TYPE_QUERY_PARAM_KEY)).responseCode(SC_BAD_REQUEST);
            return;
        }
        RepoDetailsType repoType = RepoDetailsType.fromString(repoTypeParam);
        if (repoType == null) {
            response.error("Invalid '%s' param value".formatted(REPO_TYPE_QUERY_PARAM_KEY))
                    .responseCode(SC_BAD_REQUEST);
            return;
        }
        RepoType repoPackageType = RepoType.fromString(repoPackageTypeParam);
        if (repoPackageType == null) {
            response.error("Invalid '%s' param value".formatted(REPO_PACKAGE_TYPE_QUERY_PARAM_KEY))
                    .responseCode(SC_BAD_REQUEST);
            return;
        }
        int hardLimit = repoKeyLengthLimitsHelper.hardLimit(repoType, repoPackageType);
        Integer softLimit = repoKeyLengthLimitsHelper.softLimit(repoPackageType); // ok to be null
        response.iModel(new RepoKeyLengthLimits(hardLimit, softLimit));
    }
}
