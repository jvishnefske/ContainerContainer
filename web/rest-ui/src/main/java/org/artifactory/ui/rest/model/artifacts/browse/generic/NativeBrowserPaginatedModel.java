package org.artifactory.ui.rest.model.artifacts.browse.generic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jfrog.commons.continues.ContinueResult;

import java.util.List;

/**
 * @author Alshqerat Hussein
 */
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@NoArgsConstructor
public class NativeBrowserPaginatedModel extends ContinueResult<NativeBrowserChild> {

    String repo;
    String path;
    String footer;
    boolean root;
    boolean folder;

    public static NativeBrowserPaginatedModel createInstance(String repo, String path, String footer, boolean root,
            boolean folder, String continueState, List<NativeBrowserChild> nativeItems) {
        NativeBrowserPaginatedModel nativeBrowserPaginatedModel = new NativeBrowserPaginatedModel();
        nativeBrowserPaginatedModel.setRepo(repo);
        nativeBrowserPaginatedModel.setPath(path);
        nativeBrowserPaginatedModel.setFooter(footer);
        nativeBrowserPaginatedModel.setRoot(root);
        nativeBrowserPaginatedModel.setFolder(folder);
        nativeBrowserPaginatedModel.setContinueState(continueState);
        nativeBrowserPaginatedModel.setData(nativeItems);
        return nativeBrowserPaginatedModel;
    }
}
