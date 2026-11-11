package org.artifactory.metadata.service.provider;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.Properties;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.springframework.stereotype.Component;


/**
 * @author Igor Gumush
 */
@Component
public class OciMetadataProvider extends DockerMetadataProvider {

    private static final RepoType type = RepoType.OCI;

    public OciMetadataProvider() {
        this(type);
    }

    protected OciMetadataProvider(RepoType type) {
        super(type);
    }

    @Override
    MetadataResolver pkgIdResolver() {
        return (MutableMetadataEntityBOM bom, Properties properties, FileInfo fileInfo, StatsInfo statsInfo) -> {
            String pkgidSuffix = typeSpecificMetadataTemplate.getPkgidSuffix(fileInfo.getRepoPath(), properties);
            if (StringUtils.isNotBlank(pkgidSuffix)) {
                bom.setPkgid(trimToSize(RepoType.Docker.getType() + PACKAGE_ID_SEPARATOR + pkgidSuffix,
                        MAX_PKGID_VERSION_LENGTH));
            }
        };
    }
}
