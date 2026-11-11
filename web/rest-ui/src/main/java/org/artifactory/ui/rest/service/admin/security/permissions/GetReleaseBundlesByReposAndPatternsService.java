package org.artifactory.ui.rest.service.admin.security.permissions;

import com.jfrog.bh.acl.producer.bridge.BundleArtifactoryProducerBridge;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.api.rest.distribution.bundle.models.BundlesNamesResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.distribution.ReleaseBundlePermissionIModel;
import org.artifactory.util.PathMatcher;
import org.artifactory.util.UnsupportedByLicenseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.artifactory.security.ArtifactoryPermission.MANAGE;

/**
 * Used in specific Permission page to retrieve the list of Release Bundles
 * under given repositories, that match the include/exclude patterns.
 *
 * @author Inbar Tal
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetReleaseBundlesByReposAndPatternsService implements RestService<ReleaseBundlePermissionIModel> {

    private final AddonsManager addonsManager;
    private final AuthorizationService authorizationService;
    private final BundleArtifactoryProducerBridge bundleProducerBridge;

    @Override
    @SuppressWarnings("rawtypes")
    public void execute(ArtifactoryRestRequest<ReleaseBundlePermissionIModel> request, RestResponse response) {
        if (!authorizationService.hasReleaseBundlePermission(MANAGE, false, false)) {
            throw new ForbiddenException("Only admin or users with manage permissions can access this endpoint");
        }
        ReleaseBundlePermissionIModel model = request.getImodel();
        BundlesNamesResponse bundleNames = populateReleaseBundlesV1(model);
        populateReleaseBundlesV2(model, bundleNames);
        populateReleaseBundlesResponse(response, bundleNames);
    }

    @NonNull
    private BundlesNamesResponse populateReleaseBundlesV1(@NonNull ReleaseBundlePermissionIModel model) {
        try {
            ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
            return releaseBundleAddon.getBundlesByReposAndPatterns(
                    model.getRepositories(),
                    model.getIncludePatterns(),
                    model.getExcludePatterns(),
                    model.getBundleType());
        } catch (UnsupportedByLicenseException e) {
            return new BundlesNamesResponse();
        }
    }

    private void populateReleaseBundlesV2(
            @NonNull ReleaseBundlePermissionIModel model,
            @NonNull BundlesNamesResponse bundleNames) {
        List<String> include = model.getIncludePatterns();
        List<String> exclude = model.getExcludePatterns();
        if (!model.getRepositories().isEmpty()) {
            bundleProducerBridge.getBundleNames(model.getRepositories()).stream()
                    .filter(name -> PathMatcher.matches(name + "/", include, exclude, true))
                    .forEach(name -> bundleNames.getBundles().add(name));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void populateReleaseBundlesResponse(
            @NonNull RestResponse response,
            @NonNull BundlesNamesResponse bundleNames) throws IllegalArgumentException {
        try {
            response.iModel(JacksonWriter.serialize(bundleNames));
        } catch (IOException e) {
            log.error("Failed to serialize response ", e);
            throw new IllegalArgumentException(e);
        }
    }
}
