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

package org.artifactory.repo.virtual.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.RepoResource;
import org.artifactory.maven.MavenModelUtils;
import org.artifactory.md.MutableMetadataInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.PathTranslator;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.resource.MetadataResource;
import org.artifactory.resource.ResolvedResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.storage.StorageException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Merges maven metadata from all the repositories that are part of this virtual repository.
 *
 * @author Yossi Shaul
 */
@Component
@Slf4j
public class MavenMetadataInterceptor extends VirtualRepoInterceptorBase {
    private CentralConfigService centralConfig;
    private SecurityService securityService;
    private InternalRepositoryService repoService;
    private PathTranslator pathTranslator;
    private static final String JFROG_CURATED_PREFIX = ".jfrog/curated/";

    @Override
    public boolean shouldReturnCachedResource(VirtualRepo virtualRepo, String path, boolean fromAnotherArtifactory) {
        if (MavenNaming.isIndex(path)) {
            // for index files just return the result from the cache (we don't want to process it or to return index
            // from other repositories)
            RepoRequests.logToContext("Requested resource is a Maven index - returning the cached resource");
            return true;
        }
        return false;
    }

    @Override
    public RepoResource interceptGetInfo(VirtualRepo virtualRepo, InternalRequestContext context, RepoPath repoPath,
            List<RealRepo<?, ?>> repositories) {
        if (!MavenNaming.isMavenMetadata(repoPath.getPath())) {
            return null;
        }
        return processMavenMetadata(virtualRepo, context, repoPath, repositories);
    }

    private RepoResource processMavenMetadata(VirtualRepo virtualRepo, InternalRequestContext context,
            RepoPath repoPath, List<RealRepo<?, ?>> repositories) {
        boolean isSnapshotMavenMetadata = MavenNaming.isSnapshotMavenMetadata(context.getResourcePath());
        MergeableMavenMetadata mergedMavenMetadata = new MergeableMavenMetadata(context);
        // save forbidden unfound response
        UnfoundRepoResource forbidden = null;
        boolean foundByPriority = false;

        for (RealRepo repo : repositories) {

            if (repo.isCache()) {
                //  Skip cache repos - we search in remote repos directly which will handle the cache retrieval
                // and expiry
                continue;
            }
            if (isSnapshotMavenMetadata && !repo.isHandleSnapshots()) {
                // request for snapshot maven metadata but repo doesn't support snapshots - just skip it
                continue;
            }

            if (foundByPriority && !repo.isPriorityResolution()) {
                break;
            }
            pathTranslator = ContextHelper.get().beanForType(PathTranslator.class);
            InternalRequestContext translatedContext = pathTranslator
                    .translateRepoRequestContext(virtualRepo, repo, context);

            RepoResource res = repo.getInfo(translatedContext);
            if (res.isBlocked()) {
                return res;
            }
            if (!res.isFound()) {
                if (forbidden == null) {
                    forbidden = checkIfForbidden(res);
                }
                continue;
            }
            try {
                if (findAndMergeMavenMetadata(mergedMavenMetadata, repo, translatedContext, res)) {
                    foundByPriority = repo.isPriorityResolution();
                }
            } catch (RepoRejectException | BinaryRejectedException rre) {
                log.debug("", rre);
                int status = rre.getErrorCode();
                if (status == HttpStatus.SC_FORBIDDEN && securityService.isAnonymous()) {
                    // Create forbidden repo resource to allow 401 challenge in case it is required
                    forbidden = new UnfoundRepoResource(res.getRepoPath(), rre.getMessage(), HttpStatus.SC_FORBIDDEN);
                } else {
                    log.info("Metadata retrieval failed on repo '{}': {}", repo, rre.getMessage());
                }
            }
        }   // end repositories iteration

        String path = repoPath.getPath();
        if (mergedMavenMetadata.getMetadata() == null) {
            if (forbidden != null) {
                return new UnfoundRepoResource(repoPath, forbidden.getDetail(), forbidden.getStatusCode());
            } else {
                return new UnfoundRepoResource(repoPath, "Maven metadata not found for '" + path + "'.");
            }
        } else {
            log.debug("Maven artifact metadata found for '{}'.", path);
            try {
                return createMavenMetadataFoundResource(repoPath, mergedMavenMetadata);
            } catch (IOException e) {
                log.error("Failed creating merged maven metadata", e);
                return new UnfoundRepoResource(repoPath, "IOException: " + e.getMessage());
            }
        }
    }


    /**
     * Check for maven metadata on the given resource and merge it
     *
     * @param mergedMavenMetadata Collected maven metadata
     * @param repo                Repo to check for metadata
     * @param context             Request context
     * @param res                 Resource info
     */
    private boolean findAndMergeMavenMetadata(MergeableMavenMetadata mergedMavenMetadata, RealRepo repo,
            InternalRequestContext context, RepoResource res) throws RepoRejectException, BinaryRejectedException {
        String resourcePath = context.getResourcePath();
        Metadata metadata = getMavenMetadataContent(context, repo, res);
        if (metadata != null) {
            if (log.isDebugEnabled()) {
                log.debug("{}: found maven metadata res: {}", repo, resourcePath);
                log.debug("{}: last modified {}", res.getRepoPath(), centralConfig.format(res.getLastModified()));
            }
            mergedMavenMetadata.merge(metadata, res);
        }
        return metadata != null;
    }

    private Metadata getMavenMetadataContent(InternalRequestContext requestContext, Repo repo, RepoResource res)
            throws RepoRejectException, BinaryRejectedException {
        if (res.getPath().startsWith(JFROG_CURATED_PREFIX)) {
            repo = repoService.repositoryByKey(res.getRepoKey());
        }
        ResourceStreamHandle handle = null;
        try {
            handle = repoService.getResourceStreamHandle(requestContext, repo, res);
            //Create metadata
            InputStream metadataInputStream;
            //Hold on to the original metadata string since regenerating it could result in
            //minor differences from the original, which will cause checksum errors
            metadataInputStream = handle.getInputStream();
            String metadataContent = IOUtils.toString(metadataInputStream, StandardCharsets.UTF_8);
            return MavenModelUtils.toMavenMetadata(metadataContent);
        } catch (IOException ioe) {
            log.warn("IO exception retrieving maven metadata content from repo '{}': {}.", repo, ioe.getMessage());
        } catch (StorageException se) {
            log.error("Metadata retrieval failed on repo '{}': {}", repo, se.getMessage());
        } finally {
            IOUtils.closeQuietly(handle);
        }
        return null;
    }

    private RepoResource createMavenMetadataFoundResource(RepoPath mavenMetadataRepoPath,
            MergeableMavenMetadata mergedMavenMetadata) throws IOException {
        String metadataContent = MavenModelUtils.mavenMetadataToString(mergedMavenMetadata.getMetadata());
        MutableMetadataInfo metadataInfo = InfoFactoryHolder.get().createMetadata(mavenMetadataRepoPath);
        metadataInfo.setLastModified(mergedMavenMetadata.getLastModified());
        metadataInfo.setSize(metadataContent.length());
        MetadataResource wrappedResource = new MetadataResource(metadataInfo);
        ResolvedResource resolvedResource = new ResolvedResource(wrappedResource, metadataContent);
        return resolvedResource;
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setRepoService(InternalRepositoryService repoService) {
        this.repoService = repoService;
    }
}