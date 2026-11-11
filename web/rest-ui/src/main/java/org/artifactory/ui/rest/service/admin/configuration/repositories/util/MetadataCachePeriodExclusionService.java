package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionRequestModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionResponseModel;
import org.artifactory.util.RemoteRepoCachePeriodValidator;
import org.springframework.stereotype.Component;

/**
 * Pre-validates whether a remote repository metadata retrieval cache period value satisfies the enforced minimum
 * for the given URL and package type so the UI can block invalid values during creation (RTDEV-88235).
 *
 * @author Ron Malka
 */
@Component
public class MetadataCachePeriodExclusionService
        implements RestService<MetadataCachePeriodExclusionRequestModel> {

    @Override
    public void execute(ArtifactoryRestRequest<MetadataCachePeriodExclusionRequestModel> request,
            RestResponse response) {
        MetadataCachePeriodExclusionRequestModel model = request.getImodel();
        if (model == null) {
            throw new BadRequestException("Request body is required");
        }

        String packageType = model.getPackageType();
        String url = model.getUrl();
        if (StringUtils.isBlank(packageType)) {
            throw new BadRequestException("packageType must be provided");
        }
        if (StringUtils.isBlank(url)) {
            throw new BadRequestException("url must be provided");
        }
        if (model.getRetrievalCachePeriodSecs() == null) {
            throw new BadRequestException("retrievalCachePeriodSecs must be provided");
        }

        RepoType repoType = parsePackageType(packageType);
        long retrievalCachePeriodSecs = model.getRetrievalCachePeriodSecs();
        boolean allowed = isRetrievalCachePeriodAllowed(url, retrievalCachePeriodSecs,
                model.isContentSynchronisationEnabled(), repoType);

        response.iModel(new MetadataCachePeriodExclusionResponseModel(allowed));
        response.responseCode(HttpStatus.SC_OK);
    }

    private static boolean isRetrievalCachePeriodAllowed(String url, long retrievalCachePeriodSecs,
            boolean contentSynchronisationEnabled, RepoType repoType) {
        try {
            RemoteRepoCachePeriodValidator.assertValidValue(url, retrievalCachePeriodSecs,
                    contentSynchronisationEnabled, repoType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static RepoType parsePackageType(String packageType) {
        try {
            return RepoType.fromTypeOrThrow(packageType);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown packageType: " + packageType);
        }
    }
}
