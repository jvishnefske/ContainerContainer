package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jfrog.commons.continues.ContinueIndexPage;

/**
 * @author Hussein Alshqerat
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class ArtifactFilter extends ContinueIndexPage {

    String repoKey;
    String path;
    String repoType;
    String repoPkgType;
    boolean isFolder;
    boolean isArchive;

    public ArtifactFilter(ContinueIndexPage orig) {
        super(orig);
    }

    public ArtifactFilter(ArtifactFilter orig) {
        super(orig);
        this.repoKey = orig.repoKey;
        this.path = orig.path;
        this.repoType = orig.repoType;
        this.repoPkgType = orig.repoPkgType;
        this.isFolder = orig.isFolder;
        this.isArchive = orig.isArchive;
    }
}