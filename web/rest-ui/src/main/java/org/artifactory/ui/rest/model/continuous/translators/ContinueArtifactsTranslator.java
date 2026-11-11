package org.artifactory.ui.rest.model.continuous.translators;

import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.continuous.dtos.ContinueArtifactsDto;
import org.jfrog.commons.continues.ContinueIndexPage;

/**
 * @author Hussein Alshqerat
 */
public class ContinueArtifactsTranslator {

    private ContinueArtifactsTranslator(){

    }

    public static ArtifactFilter toContinueArtifactFilter(ContinueArtifactsDto continueArtifactsDto) {
        ContinueIndexPage continueIndexPage = ContinuePageTranslator.toContinueIndex(continueArtifactsDto);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setPath(continueArtifactsDto.getPath());
        artifactFilter.setRepoKey(continueArtifactsDto.getRepoKey());
        artifactFilter.setRepoType(continueArtifactsDto.getRepoType());
        artifactFilter.setRepoPkgType(continueArtifactsDto.getRepoPkgType());
        artifactFilter.setArchive(continueArtifactsDto.isArchive());
        artifactFilter.setFolder(continueArtifactsDto.isFolder());
        return artifactFilter;
    }
}