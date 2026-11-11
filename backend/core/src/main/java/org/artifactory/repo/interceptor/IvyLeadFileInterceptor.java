package org.artifactory.repo.interceptor;

import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.ivy.IvyNaming;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * An interceptor that creates package created event for Ivy lead files.
 *
 * @author Barak Hacham
 */
@Component
public class IvyLeadFileInterceptor extends StorageInterceptorAdapter {

    private InternalRepositoryService repositoryService;
    private MetadataServiceIndexHandler metadataServiceIndexHandler;

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setMetadataServiceIndexHandler(MetadataServiceIndexHandler metadataServiceIndexHandler) {
        this.metadataServiceIndexHandler = metadataServiceIndexHandler;
    }

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
                            InterceptorCreateContext ctx) {
        createPackageEventIfNeeded(fsItem);
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
                          MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        createPackageEventIfNeeded(targetItem);
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
                          MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        createPackageEventIfNeeded(targetItem);
    }

    private void createPackageEventIfNeeded(VfsItem<? extends ItemInfo> fsItem) {
        if (!fsItem.isFile()) {
            return;
        }
        RepoConfig realRepoConfig = repositoryService.getRepoConfigByKey(fsItem.getRepoKey());
        if (realRepoConfig == null || !realRepoConfig.isReal()) {
            return;
        }
        RepoType packageType = realRepoConfig.getPackageType();
        if (packageType != RepoType.Ivy && packageType != RepoType.SBT) {
            return;
        }
        String name = fsItem.getName();
        if (IvyNaming.isIvyFileName(name) && !name.endsWith(MavenArtifactInfo.POM)) {
            metadataServiceIndexHandler.indexPackageAsync(fsItem.getInfo(), fsItem.getProperties());
        }
    }
}
