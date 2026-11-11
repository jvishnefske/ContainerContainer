package org.artifactory.metadata.service.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class MetadataIndexingInterceptorImpl extends StorageInterceptorAdapter {

    private static final String PROPS_NOT_FOUND = "No original properties found {}";
    private MetadataServiceIndexHandler packageMetadataServiceIndexHandler;
    private LeadArtifactFacade leadArtifactFacade;

    @Autowired
    public void setPackageMetadataServiceIndexHandler(MetadataServiceIndexHandler packageMetadataServiceIndexHandler) {
        this.packageMetadataServiceIndexHandler = packageMetadataServiceIndexHandler;
    }

    @Autowired
    public void setLeadArtifactFacade(LeadArtifactFacade leadArtifactFacade) {
        this.leadArtifactFacade = leadArtifactFacade;
    }

    @Override
    public void beforeDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
                             DeleteContext ctx) {
        if (fsItem.isFolder() || !leadArtifactFacade.isLeadArtifact(fsItem.getRepoPath())) {
            return;
        }

        if (fsItem instanceof MutableVfsFile mutableVfsFile && !mutableVfsFile.isOriginallyNew()) {
            log.info("Marking original properties for item: {}", fsItem.getRepoPath());
            mutableVfsFile.markOriginalProperties();
        } else {
            log.info("Not marking original properties, {}, {}", fsItem.getRepoPath(),
                    fsItem instanceof MutableVfsFile mutableVfsFile1 && mutableVfsFile1.isOriginallyNew() ?
                            "item is new" : "item is not a MutableVfsFile");
        }
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        RepoPath path = fsItem.getRepoPath();
        if (fsItem.isFolder() || !leadArtifactFacade.isLeadArtifact(path)) {
            return;
        }
        if (fsItem instanceof MutableVfsFile mutableVfsFile && mutableVfsFile.isOriginalPropertiesAvailable()) {
            try {
                PropertiesInfo originalProperties = mutableVfsFile.getOriginalProperties();
                packageMetadataServiceIndexHandler.deletePackageAsync(fsItem.getInfo(), originalProperties.asMap());
            } catch (Exception e) {
                log.debug(PROPS_NOT_FOUND, path);
                packageMetadataServiceIndexHandler.deletePackageAsync(fsItem.getInfo(), null);
            }
        }
    }
}
