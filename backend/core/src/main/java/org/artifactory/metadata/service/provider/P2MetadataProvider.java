package org.artifactory.metadata.service.provider;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * @author Barak Hacham
 */
@Component
public class P2MetadataProvider extends AbstractMetadataProvider {

    private static final RepoType type = RepoType.P2;
    private static final String JAR = "jar";
    private static final String JAR_PACK = "jar.pack.gz";
    private static final String JAR_PACK_EXTENSION = ".jar.pack.gz";
    private static final Set<String> LEAD_FILE_EXTENSIONS = Set.of(JAR_PACK, JAR);
    private static final String UNDERSCORE = "_";
    private CacheExpiry cacheExpiry;

    P2MetadataProvider() {
        super(type);
        setTypeSpecificMetadataTemplate(new P2TypeSpecificMetadataTemplate());
    }

    @Autowired
    public void setCacheExpiry(CacheExpiry cacheExpiry) {
        this.cacheExpiry = cacheExpiry;
    }

    @Override
    public boolean isPackageConsumptionLead(RepoPath repoPath) {
        if (cacheExpiry.isExpirable(type, repoPath.getRepoKey(), repoPath.getPath())) {
            return false;
        }
        return repoPath.getName().endsWith(JAR_PACK) || repoPath.getName().endsWith(JAR);
    }

    @Override
    protected String getType(@Nonnull Properties properties) {
        return type.getPkgidPrefix();
    }

    @Data
    static class P2TypeSpecificMetadataTemplate implements TypeSpecificMetadataTemplate {

        @Override
        public Set<String> getLeadFileExtensions() {
            return LEAD_FILE_EXTENSIONS;
        }

        @Override
        public String getName(Properties properties, RepoPath repoPath) {
            return getPackageName(repoPath);
        }

        @Nullable
        @Override
        public String getVersion(RepoPath repoPath, Properties properties) {
            return getPackageVersion(repoPath);
        }

        @Override
        public Set<String> getExcludedUserProperties() {
            return Set.of();
        }

        @Nullable
        @Override
        public String getPkgidSuffix(RepoPath repoPath, Properties properties) {
            return getPackageName(repoPath);
        }

        private static String getPackageName(RepoPath path) {
            String name = path.getName();
            return StringUtils.substringBefore(name, UNDERSCORE);
        }

        private static String getPackageVersion(RepoPath path) {
            String name = path.getName();
            name = StringUtils.substringAfter(name, UNDERSCORE);
            if (name.endsWith(JAR_PACK)) {
                return StringUtils.substringBefore(name, JAR_PACK_EXTENSION);
            }
            return PathUtils.stripExtension(name);
        }
    }

}
