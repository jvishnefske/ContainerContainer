/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.virtual;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.fs.RepoResource;
import org.artifactory.maven.versioning.MavenVersionComparator;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepoBase;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.threadlocal.cache.VirtualResolveScopeRunner;

import java.util.List;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.snapshot.LocalLatestVersionResolver.compareLatest;

/**
 * Default download strategy of a virtual repository.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryVirtualDownloadStrategy implements VirtualDownloadStrategy {

    private static final String SEARCHING_RESOURCE_MESSAGE = "Searching for the resource within %s";

    private final MavenVersionComparator mavenVersionComparator = new MavenVersionComparator();

    private final InternalRepositoryService repositoryService;

    private final CentralConfigService centralConfig;

    private final StatsService statsService;

    private final VirtualRepo virtualRepo;
    private final PathTranslator pathTranslator;
    private final VirtualResolveScopeRunner virtualResolveScopeRunner;

    ArtifactoryVirtualDownloadStrategy(VirtualRepo virtualRepo) {
        this.virtualRepo = virtualRepo;
        this.centralConfig = virtualRepo.getConfigService();
        this.repositoryService = virtualRepo.getRepositoryService();
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        this.statsService = artifactoryContext.beanForType(StatsServiceImpl.class);
        this.pathTranslator = artifactoryContext.beanForType(PathTranslator.class);
        this.virtualResolveScopeRunner = artifactoryContext.beanForType(VirtualResolveScopeRunner.class);
    }

    public RepoResource getInfo(InternalRequestContext context) {
        String repoName = context.getRepoKey();
        return virtualResolveScopeRunner.run(repoName,
                () -> ReposItemsThreadLocalCacheService.wrap(repoName, () -> getInfoInternal(context)));
    }

    private RepoResource getInfoInternal(InternalRequestContext context) {
        RepoRequests.logToContext("Consulting the virtual repo download strategy");
        virtualRepo.interceptPreCalculateVirtualMetadata(context);
        // first look in local storage
        RepoResource cachedResource = getInfoFromLocalStorage(context);
        if (resourceAvailable(cachedResource) && virtualRepo.interceptShouldReturnCachedResource(context)) {
            return cachedResource;
        }
        // release the read lock on the virtual repo local cache to prevent deadlock in any of the interceptors
        // (in case one of them needs to write back to the virtual repo cache)
        RepoPath localCacheRepoPath = InternalRepoPathFactory.create(virtualRepo.getKey(), context.getResourcePath());

        // not found in local virtual repository storage, look in configured repositories
        RepoResource searchableResource = getInfoFromSearchableRepositories(context);
        if (!cachedResource.isFound() && !searchableResource.isFound()) {
            // not found
            return searchableResource;
        } else if (cachedResource.isFound() && !searchableResource.isFound()) {
            // delete the local cached artifact and return the not found resource
            if (searchableResource instanceof UnfoundRepoResource &&
                    ((UnfoundRepoResource) searchableResource).getStatusCode() == 404) {
                RepoRequests.logToContext("Resource was not found but is still cached - removing from the cache");
                repositoryService.undeploy(localCacheRepoPath, false);
            }
            return searchableResource;
        } else if (cachedResource.isFound() && searchableResource.isFound()) {
            String sourceRepoKey = searchableResource.getResponseRepoPath().getRepoKey();
            // if the searchable resource is a remote repo - it is not downloaded yet, so we shouldn't
            // return the cached artifact
            if (repositoryService.remoteRepositoryByKey(sourceRepoKey) == null &&
                    cachedResource.getLastModified() >= searchableResource.getLastModified()) {
                RepoRequests.logToContext("Resource was found locally and the cached instance is the latest - " +
                        "updating the stats and returning the cached instance");
                // locally stored resource is latest, update the stats of the original resource and return our cached
                //file
                LocalRepo repo = repositoryService.localFederatedOrCachedRepositoryByKey(sourceRepoKey);
                if (repo != null) {
                    statsService
                            .updateDownloadStatsIfNeeded(searchableResource.getRepoPath(), context.getRequest(),
                                    repo.isReal());
                }
                return cachedResource;
            }
        }

        // found newer resource in the searchable repositories
        RepoRequests.logToContext("Returning resource as found in the aggregated repositories");
        return virtualRepo.interceptBeforeReturn(context, searchableResource);
    }

    private boolean resourceAvailable(RepoResource cachedResource) {
        return cachedResource != null && (cachedResource.isFound() || cachedResource.isExpired());
    }

    private RepoResource getInfoFromLocalStorage(InternalRequestContext context) {
        try {
            RepoRequests.logToContext("Trying to retrieve resource info from the local storage");
            return virtualRepo.getInfoFromVirtualCache(context);
        } catch (FileExpectedException e) {
            RepoRequests.logToContext("Unable to find resource info in the local storage - " +
                    "already exists as a directory: %s", e.getMessage());
            // see RTFACT-3721
            return new UnfoundRepoResource(
                    InternalRepoPathFactory.create(virtualRepo.getKey(), context.getResourcePath()),
                    "File expected but the cache already points to directory");
        }
    }

    private RepoResource getInfoFromSearchableRepositories(InternalRequestContext context) {
        RepoRequests.logToContext("Searching for info in aggregated repositories");
        String path = context.getResourcePath();
        RepoPath repoPath = InternalRepoPathFactory.create(virtualRepo.getKey(), path);
        RepoResource result;
        List<RealRepo<?, ?>> repositories = virtualRepo.getVirtualRepositoriesResolver()
                .assembleSearchRepositoriesList(repoPath, context.isFromAnotherArtifactory());
        if (repositories.isEmpty()) {
            RepoRequests.logToContext("Unable to find aggregated repositories to search within - returning " +
                    "unfound resource");
            return new UnfoundRepoResource(repoPath, "No repository found to serve the request for " + repoPath);
        }
        result = virtualRepo.interceptGetInfo(context, repoPath, repositories);
        if (result != null) {
            RepoRequests.logToContext("Received resource from an interceptor - returning");
            return result;
        }

        /* The repo might not define a layout, so also check specifically for a Maven snapshot if the resulting
          module info is invalid */
        boolean mavenSnapshotPath = MavenNaming.isSnapshot(path);
        boolean isSnapshotPath = mavenSnapshotPath || path.contains("[INTEGRATION]");
        ModuleInfo artifactModuleInfo = virtualRepo.getItemModuleInfo(path);
        if (artifactModuleInfo.isIntegration() || (!artifactModuleInfo.isValid() && isSnapshotPath)) {
            RepoRequests.logToContext("Processing request as a snapshot resource (Module info validity = %s, " +
                            "Module info identified as integration = %s, Path identified as Maven snapshot = %s)",
                    artifactModuleInfo.isValid(), artifactModuleInfo.isIntegration(), mavenSnapshotPath);
            result = processSnapshot(context, repoPath, repositories);
        } else if (path.contains("[RELEASE]")) {
            RepoRequests.logToContext("Processing request as a latest release resource (Module info validity = %s)",
                    artifactModuleInfo.isValid());
            result = processLatestRelease(context, repoPath, repositories, artifactModuleInfo.isValid());
        } else {
            RepoRequests.logToContext("Processing request as a release resource");
            result = processStandard(context, repoPath, repositories, artifactModuleInfo.isValid());
        }
        return result;
    }


    /**
     * Iterate over a list of repos until a resource is found in one of them, and return that resource. The first
     * resource that is found is returned. The order of searching is: local repos, cache repos and remote repos (unless
     * request originated from another Artifactory).
     *
     * @param context         Original download request context
     * @param repoPath        The repository path of the resource to find
     * @param repositories    List of repositories to look in (doesn't include virtual repos)
     * @param validModuleInfo Whether the module info is valid, if not we will pass the request to the aggregated
     *                        repos even if they do not handle releases
     */
    private RepoResource processStandard(InternalRequestContext context, RepoPath repoPath,
            List<RealRepo<?, ?>> repositories,
            boolean validModuleInfo) {
        // Save forbidden unfounded response
        UnfoundRepoResource forbidden = null;
        // Locate the resource matching the request
        RepoResource closestMatch = null;

        for (RealRepo repo : repositories) {
            RepoRequests.logToContext(SEARCHING_RESOURCE_MESSAGE, repo.getKey());
            // Since we are in process standard, repositories that does not process releases should be skipped.
            // Now, checksums are always considered standard, even if executed against a snapshot repository.
            // So, we should not skip snapshots repositories for checksums.
            String path = repoPath.getPath();
            if (validModuleInfo && !repo.isHandleReleases() && !NamingUtils.isChecksum(path)) {
                RepoRequests.logToContext("Skipping %s - doesn't handle releases", repo.getKey());
                continue;
            }

            if (closestMatch != null && isNotSynchronizeProperties(repo)) {
                continue;
            }

            RepoResource res = getInfo(repo, context);

            if (res.isFound()) {
                RepoRequests.logToContext("Resource was found in %s", repo.getKey());
                updateResponseRepoPath(repo, res);
                if (res.isExactQueryMatch()) {
                    //return the exact match
                    RepoRequests.logToContext("Resource is an exact match - returning");
                    return res;
                } else {
                    RepoRequests.logToContext("Resource is not an exact match - keeping as closest match");
                    closestMatch = res;
                }
            } else if (forbidden == null) {
                forbidden = checkIfForbidden(res);
                if (forbidden != null) {
                    RepoRequests.logToContext("Request is forbidden by %s", repo.getKey());
                }
            }
        }

        //If we didn't find an exact match return the first found resource (closest match)
        if (closestMatch != null) {
            RepoRequests.logToContext("Unable to find an exact matching resource - returning closest match");
            return closestMatch;
        }
        // Not found in any repo
        return newUnfoundResource(repoPath, forbidden, "Could not find resource");
    }

    /**
     * Handles Latest Release requests such as: maven/accent/accent/[RELEASE]/accent-[RELEASE].jar
     * Iterates over a list of repos and returns the latest matching resource.
     * The order of searching is: local repos, cache repos and remote repos (unless request originated from another
     * Artifactory).
     *
     * @param context         Original download request context
     * @param repoPath        The repository path of the resource to find
     * @param repositories    List of repositories to look in (doesn't include virtual repos)
     * @param validModuleInfo Whether the module info is valid, if not we will pass the request to the aggregated
     *                        repos even if they do not handle releases
     */
    private RepoResource processLatestRelease(InternalRequestContext context, RepoPath repoPath,
            List<RealRepo<?, ?>> repositories, boolean validModuleInfo) {
        RepoResource latestRes = null;
        ModuleInfo latestModuleInfo = null;
        UnfoundRepoResource forbidden = null;
        boolean foundByPriority = false;
        for (RealRepo repo : repositories) {
            RepoRequests.logToContext(SEARCHING_RESOURCE_MESSAGE, repo.getKey());
            if (foundByPriority && !repo.isPriorityResolution()) {
                break;
            }
            if (validModuleInfo && !repo.isHandleReleases()) {
                RepoRequests.logToContext("Skipping %s - doesn't handle releases", repo.getKey());
                continue;
            }

            RepoResource res = getInfo(repo, context);

            if (res.isFound()) {
                ModuleInfo resModuleInfo;
                if ((resModuleInfo = getUpdatedInfo(res, latestRes, latestModuleInfo, repo.getKey())) != null) {
                    updateResponseRepoPath(repo, res);
                    latestRes = res;
                    latestModuleInfo = resModuleInfo;
                    foundByPriority = repo.isPriorityResolution();
                }
            } else if (forbidden == null) {
                forbidden = checkIfForbidden(res);
                if (forbidden != null) {
                    RepoRequests.logToContext("Request is forbidden by %s", repo.getKey());
                }
            }
        }

        if (latestRes != null) {
            RepoRequests.logToContext("Returning latest release found %s", latestRes.getRepoPath().toPath());
            return latestRes;
        }
        // Not found in any repo
        return newUnfoundResource(repoPath, forbidden, "Could not find resource");
    }

    /**
     * Compares between current res and the latestRes that we have so far. If res is a better match than latestRes
     * then return resModuleInfo else return null.
     */
    private ModuleInfo getUpdatedInfo(RepoResource res, RepoResource latestRes, ModuleInfo latestModuleInfo,
            String repoKey) {
        boolean firstFoundResource = latestRes == null;
        boolean isFirstExactMatch = !firstFoundResource && !latestRes.isExactQueryMatch() && res.isExactQueryMatch();
        ModuleInfo resModuleInfo = repositoryService.getItemModuleInfo(res.getRepoPath());
        boolean isMoreRecent = !firstFoundResource &&
                compareLatest(resModuleInfo, latestModuleInfo, mavenVersionComparator) > 0;

        RepoRequests.logToContext("Resource was found in %s, is the first candidate = %s, " +
                        "is not the first candidate and is the first exact match = %s, is more recent = %s",
                repoKey, firstFoundResource, isFirstExactMatch, isMoreRecent);
        return (firstFoundResource || isFirstExactMatch || isMoreRecent) ? resModuleInfo : null;
    }

    /**
     * Iterate over the repos and return the latest resource found (content or just head information) on the response.
     */
    RepoResource processSnapshot(InternalRequestContext context, RepoPath repoPath, List<RealRepo<?, ?>> repositories) {
        //Find the latest in all repositories
        RepoResource latestRes = null;
        // save forbidden unfound response
        UnfoundRepoResource forbidden = null;
        //Traverse the local, caches and remote repositories and search for the newest snapshot
        //Make sure local repos are always searched first

        String path = repoPath.getPath();
        boolean isUniqueSnapshot = MavenNaming.isUniqueSnapshot(path) && !path.contains("[INTEGRATION]");
        boolean foundByPriority = false;
        for (RealRepo repo : repositories) {
            if (foundByPriority && !repo.isPriorityResolution()) {
                break;
            }
            RepoRequests.logToContext(SEARCHING_RESOURCE_MESSAGE, repo.getKey());
            if (shouldSkipSnapshotRepo(repo)) {
                RepoRequests.logToContext("Skipping %s", repo.getKey());
                continue;
            }
            InternalRequestContext translatedContext =
                    pathTranslator.translateRepoRequestContext(virtualRepo, repo, context);
            if (translatedContext.getRequest() instanceof TranslatedArtifactoryRequest) {
                RepoRequests.logToContext("Request path was translated to %s due to repository layout differences",
                        translatedContext.getResourcePath());
            }

            final RepoResource res = repo.getInfo(translatedContext);
            if (res.isFound()) {
                RepoRequests.logToContext("Resource last modified time - %s",
                        centralConfig.format(res.getLastModified()));

                if (isUniqueSnapshot && res.isExactQueryMatch()) {
                    RepoRequests.logToContext("Selecting a unique snapshot");
                    updateResponseRepoPath(repo, res);
                    latestRes = res;
                    break;
                }
                boolean firstFoundResource = latestRes == null;
                boolean currentResourceIsAnExactMatchAndLatterFoundIsNot = !firstFoundResource &&
                        !latestRes.isExactQueryMatch() && res.isExactQueryMatch();
                boolean currentResourceWasModifiedLater = !firstFoundResource &&
                        (res.getLastModified() > latestRes.getLastModified());

                RepoRequests.logToContext("Current found resource is the first candidate = %s, is an exact match " +
                                "query while the former candidate isn't = %s, has later modified time than former = %s",
                        firstFoundResource, currentResourceIsAnExactMatchAndLatterFoundIsNot,
                        currentResourceWasModifiedLater
                );

                //If we haven't found one yet
                if (firstFoundResource ||
                        //or this one is a better match
                        currentResourceIsAnExactMatchAndLatterFoundIsNot
                        //or newer than the one found
                        || currentResourceWasModifiedLater) {
                    RepoRequests.logToContext("Selecting current found resource as best candidate");
                    //take it
                    updateResponseRepoPath(repo, res);
                    latestRes = res;
                    foundByPriority = repo.isPriorityResolution();
                }
            } else if (forbidden == null) {
                forbidden = checkIfForbidden(res);
                if (forbidden != null) {
                    RepoRequests.logToContext("Request is forbidden by %s", repo.getKey());
                }
            }
        }

        boolean nonFoundRetrievalCacheHit = latestRes != null && !latestRes.isFound();
        if (latestRes == null || nonFoundRetrievalCacheHit) {
            String msg = "Artifact not found: " + repoPath.getPath() +
                    (nonFoundRetrievalCacheHit ? " (cached on " +
                            centralConfig.format(latestRes.getLastModified()) + ")" : "");
            return newUnfoundResource(repoPath, forbidden, msg);
        }

        final RepoPath responseRepoPath = latestRes.getResponseRepoPath();
        if (responseRepoPath != null) {
            RepoRequests.logToContext("Returning found resource from %s", responseRepoPath.getId());
        } else {
            RepoRequests.logToContext("Returning found resource with null response repo path");
        }
        return latestRes;
    }

    //Skip repos that don't handle snapshots and cache repos to allow their remotes' logic to discover newer versions
    //see RTFACT-7095
    private boolean shouldSkipSnapshotRepo(RealRepo repo) {
        //Skip if not handling
        if (!repo.isHandleSnapshots()) {
            RepoRequests.logToContext("%s doesn't handle snapshot resources", repo.getKey());
            return true;
        } else {
            return repo.isCache();
        }
    }

    private UnfoundRepoResource checkIfForbidden(RepoResource resource) {
        if (resource instanceof UnfoundRepoResource &&
                ((UnfoundRepoResource) resource).getStatusCode() == SC_FORBIDDEN) {
            return (UnfoundRepoResource) resource;
        }
        return null;
    }

    private void updateResponseRepoPath(Repo foundInRepo, RepoResource resource) {
        resource.setResponseRepoPath(
                InternalRepoPathFactory.create(foundInRepo.getKey(), resource.getRepoPath().getPath()));
    }

    private RepoResource getInfo(RealRepo repo, InternalRequestContext context) {
        InternalRequestContext translatedContext =
                pathTranslator.translateRepoRequestContext(virtualRepo, repo, context);
        boolean isTranslated = !translatedContext.getResourcePath().equals(context.getResourcePath());
        if (isTranslated) {
            RepoRequests.logToContext("Request path was translated to %s due to repository layout differences",
                    translatedContext.getResourcePath());
        }
        RepoResource res = repo.getInfo(translatedContext);

        // Retry the original path if the path was translated and failed (RTFACT-4329)
        if (!res.isFound() && isTranslated) {
            RepoRequests
                    .logToContext("Unable to find the resource in the translated path - retrying with the original");
            res = repo.getInfo(context);
        }
        return res;
    }

    private RepoResource newUnfoundResource(RepoPath repoPath, UnfoundRepoResource forbidden, String msg) {
        if (forbidden != null) {
            RepoRequests.logToContext("Returning a forbidden-unfound resource");
            return new UnfoundRepoResource(repoPath, forbidden.getDetail(), forbidden.getStatusCode());
        } else {
            RepoRequests.logToContext("Returning an unfound resource");
            return new UnfoundRepoResource(repoPath, msg);
        }
    }


    private boolean isNotSynchronizeProperties(RealRepo repo) {
        return !repo.isLocal() && !((RemoteRepoBase) repo).isSynchronizeProperties();
    }
}