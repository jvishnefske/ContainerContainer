package org.artifactory.storage.db.repo.service.mapper;

import jakarta.annotation.Nullable;
import org.artifactory.model.RepoConfig;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.jfrog.common.JsonUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author Tamar Rosen
 */
@Mapper(uses = RepositoryConfigAttributesMapper.class)
public interface RepositoryConfigMapper {

    @Mapping(source = "repoType",target = "type",
            qualifiedByName = {"RepositoryConfigAttributesMapper", "repoDetailTypeToLowercaseRepoTypeName"})
    @Mapping(source = "repoType",target = "repoTypeV2",
            qualifiedByName = {"RepositoryConfigAttributesMapper", "repoDetailTypeToRepoTypeV2"})
    @Mapping(source = "packageType",target = "packageType",
            qualifiedByName = {"RepositoryConfigAttributesMapper", "packageTypeToTypeString"})
    @Mapping(source = "baseConfig",target = ".")
    @Mapping(source = "modification", target = "modified")
    @Mapping(source = "creation", target = "created")
    @Mapping(source = ".", target = "repositoryInfoBlob")
    DbRepositoryConfig repositoryConfigToDbRepositoryConfig(RepoConfig repositoryConfig);

    default byte[] repositoryConfigToDbInfoBlob(RepoConfig repositoryConfig) {
        if (repositoryConfig != null) {
            return JsonUtils.getInstance().valueToByteArray(repositoryConfig);
        }
        return new byte[0];
    }

    /**
     * Reads a DB row into a {@link RepoConfig}. Returns {@code null} when the row is {@code null} or when it carries
     * an unknown package type that {@link RepoConfigBlobReader} decided to skip. Callers must not materialize nulls.
     */
    @Nullable
    default RepoConfig dbInfoBlobToRepositoryConfig(DbRepositoryConfig dbRepositoryConfig) {
        if (dbRepositoryConfig == null) {
            return null;
        }
        RepoConfig repoConfig = RepoConfigBlobReader.read(dbRepositoryConfig);
        if (repoConfig == null) {
            return null;
        }
        repoConfig.setModification(dbRepositoryConfig.getModified());
        repoConfig.setCreation(dbRepositoryConfig.getCreated());
        return repoConfig;
    }
}