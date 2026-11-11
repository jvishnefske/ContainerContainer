package org.artifactory.engine;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RequestClientType;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.traffic.context.TrafficContext;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.ifc.ManualRedirectType;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.security.utils.AuthenticationUtils.isXrayTokenAuthentication;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ARTIFACT_LAST_MODIFIED;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ARTIFACT_NAME;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ARTIFACT_PATH;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CONTENT_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.MANUAL_REDIRECT_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_PACKAGE_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_PROJECT_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_REPOSITORY_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ORIGIN_REPO_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.PACKAGE_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.PROJECT_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REDIRECT_GROUP;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPO_PATH_ID;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPO_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.SIGN_URL_TYPE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.SKIP_CONTENT_DISPOSITION;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.SUBJECT;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.USERNAME;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinaryElementFactory {

    @Autowired
    private final SecurityService securityService;
    @Autowired
    private final ProjectsService projectsService;
    @Autowired
    private final CentralConfigService centralConfigService;

    @NonNull
    public BinaryElement getBinaryElement(@NonNull InternalRequestContext requestContext, @NonNull Repo responseRepo,
            @NonNull RepoResource resource, @NonNull ManualRedirectType manualRedirectType,
            @Nullable RedirectType redirectType) {
        String xrayUserName = null;
        if (ConstantValues.isAddUserNameToSignUrlForRedirect.getBoolean()) {
            boolean useOnlyTokenForIdentifyingXrayUser =
                    centralConfigService.getDescriptor().isUseOnlyTokenForIdentifyingXrayUser();
            log.debug("Use only token for identifying Xray user is [{}]", useOnlyTokenForIdentifyingXrayUser);
            if (useOnlyTokenForIdentifyingXrayUser) {
                xrayUserName = getXrayUserNameFromToken();
            } else {
                xrayUserName =
                        getXrayUserName(requestContext, SecurityContextHolder.getContext().getAuthentication());
            }
            log.debug("Xray user name is [{}]", xrayUserName);
        }
        RequestClientType requestClient =
                RequestResponseHelper.extractRequestClientType(requestContext.getRequest());

        String projectKey = projectsService.getProjectKeyOfRepository(responseRepo.getKey());
        RepoConfig repoConfig = responseRepo.getRepoConfig();
        String repoType = repoConfig.getRepoType().typeNameLowercase();
        String packageType = repoConfig.getPackageType().getType();
        String redirectGroupName = getRedirectGroupName(requestContext);
        Map<String, String> headers =
                getHeaderMapForRedirect(resource.getInfo(), xrayUserName, requestClient,
                        redirectGroupName, manualRedirectType, projectKey, repoType, packageType, redirectType);

        return new BinaryElementImpl(
                resource.getInfo().getSha1(),
                resource.getInfo().getSha2(),
                resource.getInfo().getMd5(),
                resource.getInfo().getSize(),
                headers);
    }

    private String getXrayUserNameFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isXrayTokenAuthentication = isXrayTokenAuthentication(authentication);
        return isXrayTokenAuthentication ?
                ((AccessTokenAuthentication) authentication).getAccessToken().getSubject() :
                null;
    }

    @VisibleForTesting
    String getXrayUserName(InternalRequestContext requestContext, Authentication authentication) {
        String userName = null;
        String xrayUserAgent = RequestResponseHelper
                .getUserAgentIfXrayUser(requestContext.getRequest(),
                        securityService.currentUsername(), authentication);
        if (StringUtils.isNotEmpty(xrayUserAgent) && xrayUserAgent.toLowerCase().contains("xray")) {
            if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
                return accessTokenAuthentication.getAccessToken().getSubject();
            }
            return securityService.currentUsername();
        }
        return userName;
    }

    @VisibleForTesting
    @NonNull
    Map<String, String> getHeaderMapForRedirect(@NonNull RepoResourceInfo resourceInfo,
            @Nullable String xrayUserName, @NonNull RequestClientType requestClient,
            String redirectGroupName, ManualRedirectType manualRedirectType, String projectKey, String repoType,
            String packageType, @Nullable RedirectType redirectType) {
        RepoPath resourceRepoPath = resourceInfo.getRepoPath();
        Map<String, String> headers = new HashMap<>();
        if (resourceRepoPath != null) {
            headers.put(ARTIFACT_NAME.getHeaderName(), resourceRepoPath.getName());
            headers.put(REPO_PATH_ID.getHeaderName(), resourceRepoPath.getId());
            headers.put(CONTENT_TYPE.getHeaderName(),
                    NamingUtils.getMimeType(resourceInfo.getRepoPath().getPath()).getType());
            headers.put(REPOSITORY_KEY.getHeaderName(), resourceRepoPath.getRepoKey());
            headers.put(PROJECT_KEY.getHeaderName(), projectKey);
            headers.put(ARTIFACT_PATH.getHeaderName(), resourceRepoPath.getPath());
            headers.put(USERNAME.getHeaderName(), securityService.currentUsername());
            headers.put(REPO_TYPE.getHeaderName(), repoType);
            headers.put(PACKAGE_TYPE.getHeaderName(), packageType);
            headers.put(ARTIFACT_LAST_MODIFIED.getHeaderName(), String.valueOf(resourceInfo.getLastModified()));
            setOriginInfo(projectKey, repoType, packageType, headers, resourceRepoPath);
        }
        if (StringUtils.isNotEmpty(xrayUserName)) {
            headers.put(SUBJECT.getHeaderName(), xrayUserName);
        }
        if (redirectGroupName != null) {
            headers.put(REDIRECT_GROUP.getHeaderName(), redirectGroupName);
        }

        if (!manualRedirectType.equals(ManualRedirectType.DEFAULT)) {
            headers.put(MANUAL_REDIRECT_TYPE.getHeaderName(), manualRedirectType.name());
        }

        if (redirectType != null) {
            headers.put(SIGN_URL_TYPE.getHeaderName(), redirectType.name());
        }

        // there is a bug in apt-get that makes the cdn redirect link to not work if content-disposition header is
        // present. see RTFACT-23307 and https://github.com/Debian/apt/issues/113
        if (requestClient == RequestClientType.DEBIAN) {
            headers.put(SKIP_CONTENT_DISPOSITION.getHeaderName(), String.valueOf(true));
        } else {
            headers.put(SKIP_CONTENT_DISPOSITION.getHeaderName(), String.valueOf(false));
        }

        return headers;
    }

    private void setOriginInfo(String projectKey, String repoType, String packageType, Map<String, String> headers,
            RepoPath resourceRepoPath) {
        TrafficContext trafficContext = TrafficContextThreadLocal.getTrafficContext();
        if (trafficContext != null) {
            headers.put(ORIGIN_REPOSITORY_KEY.getHeaderName(), trafficContext.getOriginRepoKey());
            headers.put(ORIGIN_PROJECT_KEY.getHeaderName(), trafficContext.getOriginProjectKey());
            RepoConfig repoConfig = trafficContext.getOriginRepoConfig();
            headers.put(ORIGIN_REPO_TYPE.getHeaderName(), repoConfig.getRepoType().typeNameLowercase());
            headers.put(ORIGIN_PACKAGE_TYPE.getHeaderName(), repoConfig.getPackageType().getType());
        } else {
            headers.put(ORIGIN_REPOSITORY_KEY.getHeaderName(), resourceRepoPath.getRepoKey());
            headers.put(ORIGIN_PROJECT_KEY.getHeaderName(), projectKey);
            headers.put(ORIGIN_REPO_TYPE.getHeaderName(), repoType);
            headers.put(ORIGIN_PACKAGE_TYPE.getHeaderName(), packageType);
        }
    }

    @VisibleForTesting
    String getRedirectGroupName(InternalRequestContext requestContext) {
        return DownloadServiceImpl.getHeaderByPriority(requestContext, REDIRECT_GROUP)
                .orElse(null);
    }
}
