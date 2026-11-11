package org.artifactory.repo.virtual;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.NullRequestContext;
import org.springframework.stereotype.Service;

@Service
public class PathTranslator {

    /**
     * Translates the artifact request context if the layout of the target repository is different from the source
     *
     * @param source  Source repository
     * @param target  Target repository
     * @param context Request context to translate
     * @return Translated context if needed, original if not needed or if there is insufficient info
     */
    public <S extends Repo, T extends Repo> InternalRequestContext translateRepoRequestContext(S source,
            T target, InternalRequestContext context) {
        String originalPath = context.getResourcePath();
        String translatedPath = translateRepoPath(source, target, originalPath);
        if (originalPath.equals(translatedPath)) {
            return context;
        }

        if (context instanceof NullRequestContext) {
            return new NullRequestContext(target.getRepoPath(translatedPath));
        }

        ArtifactoryRequest artifactoryRequest = ((DownloadRequestContext) context).getRequest();
        RepoPath translatedRepoPath = InternalRepoPathFactory.create(artifactoryRequest.getRepoKey(), translatedPath);
        TranslatedArtifactoryRequest translatedRequest = new TranslatedArtifactoryRequest(translatedRepoPath,
                artifactoryRequest);
        return new DownloadRequestContext(translatedRequest);
    }

    /**
     * Translates the artifact path if the layout of the target repository is different from the source
     *
     * @param source Source repository
     * @param target Target repository
     * @param path   Path to translate
     * @return Translated path if needed, original if not needed of if there is insufficient info
     */
    public  <S extends Repo, T extends Repo> String translateRepoPath(S source, T target, String path) {
        RepoLayout sourceRepoLayout = source.getDescriptor().getRepoLayout();
        RepoLayout targetRepoLayout = target.getDescriptor().getRepoLayout();
        LayoutsCoreAddon layoutsCoreAddon = loadLayoutsCoreAddon();
        return layoutsCoreAddon.translateArtifactPath(sourceRepoLayout, targetRepoLayout, path);
    }

    public String translateArtifactPath(RepoLayout sourceRepoLayout, RepoLayout targetRepoLayout, String path) {
        LayoutsCoreAddon layoutsCoreAddon = loadLayoutsCoreAddon();
        return layoutsCoreAddon.translateArtifactPath(sourceRepoLayout, targetRepoLayout, path);
    }

    private LayoutsCoreAddon loadLayoutsCoreAddon() {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(LayoutsCoreAddon.class);
    }
}
