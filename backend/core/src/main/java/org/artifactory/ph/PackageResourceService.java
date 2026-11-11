package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.resource.PackageApiEndPoint;
import lombok.NonNull;
import org.artifactory.common.repo.PackageType;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.model.RepoConfig;

import java.util.List;
import java.util.Map;

/**
 * Provides the access to package-handler's PackageResource functionality from artifactory-oss/artifactory-pro.
 *
 * @author Volodymyr Shulga
 */
public interface PackageResourceService {
    /**
     * @param packageType type of the package
     * @param repoKey     repository key
     * @param async       indicates if reindex should be executed asynchronously. If null, default implementation
     *                    for the package_type value will be used.
     * @return response from reindex
     */
    Response reindex(@NonNull PackageType packageType, @NonNull String repoKey, @Nullable Boolean async)
            throws PackageException;

    /**
     * @param packageType like nuget/npm.
     * @param version     package api version if available (like v2/v3) or null.
     * @param repoKey     repository key.
     * @return sub resource for specified repoType/version from package-handler. Null if implementation not found.
     */
    @Nullable
    Object getVersionedSubResource(@NonNull PackageType packageType, String version, @NonNull String repoKey,
            HttpServletRequest request) throws PackageException;

    /**
     * @param packageType like nuget/npm.
     * @param context     uri path
     * @return sub resource for specified repoType/context from package-handler.
     * Null if implementation not found.
     */
    @Nullable
    Object getNonVersionedSubResource(@NonNull PackageType packageType, @NonNull String context,
            HttpServletRequest request) throws PackageException;

    /**
     * @param packageType  like nuget/npm.
     * @param endpointName the endpoint function name as string, in pascal case. Example: GetDistribution of Pypi
     * @param context      The context variable represents the first variable after the packageType in the API. It
     *                     typically corresponds to the repository key.
     * @param request      the request to be handled by the endpoint
     * @return an interface for the endpoint, capable of executing its code
     * @throws PackageException if there is not such endpoint
     *                          Null if no such endpoint
     */
    @Nullable
    PackageApiEndPoint getPackageEndPoint(@NonNull PackageType packageType, @NonNull String endpointName,
            @NonNull String context, HttpServletRequest request) throws PackageException;

    /**
     * The method is used by Artifactory UI in order to retrieve a package info.
     *
     * @param repoKey repository key.
     * @param path    package path.
     * @return Response with package info for UI.
     */
    Response getPackageInfo(String repoKey, String path) throws PackageException;

    /**
     * Support displaying of a README file in the UI under the "packages" tab for a specific version.
     *
     * @param fullRepoPath from pattern <repoKey>/<pathToFile>
     * @return The content of the README file if exists.
     */
    Response getVersionNativeReadme(String type, String fullRepoPath) throws PackageException;

    /**
     * The method is used by the Artifactory UI to retrieve lists of allowed virtual repositories based on the
     * provided repository configuration parameters.
     *
     * @param repoConfig repository configuration.
     * @param repoLists  a map of repository list types and their corresponding lists of repository keys.
     * @return a map of repoList types and a list of filtered repos keys.
     */
    Map<RepoListType, List<String>> getVirtualAllowedRepos(RepoConfig repoConfig,
            Map<RepoListType, List<String>> repoLists);
}
