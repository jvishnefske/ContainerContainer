package org.artifactory.storage.db.repo.service.mapper;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.Locale;

/**
 * @author Tamar Rosen
 */
@Mapper
@Named("RepositoryConfigAttributesMapper")
public interface RepositoryConfigAttributesMapper {
    @Named("repoDetailTypeToLowercaseRepoTypeName")
    default String repoDetailTypeToLowercaseRepoTypeName(RepoDetailsType repoDetailsType) {
        return repoDetailsType.typeNameLowercase();
    }

    @Named("repoDetailTypeToRepoTypeV2")
    default String repoDetailTypeToRepoTypeV2(RepoDetailsType repoDetailsType) {
        return typeStringToRepoTypeV2(repoDetailsType.typeNameLowercase());
    }

    /**
     * Single source of truth for the {@code repo_type_v2} shadow-column value: a federated repo shadows as
     * {@code local}, every other type shadows as itself. Mirrors the {@code v284} backfill SQL
     * ({@code CASE WHEN type = 'federated' THEN 'local' ELSE type END}).
     * <p>
     * Callers that cannot inject the mapper (e.g. non-Spring components) should use the static
     * {@link #repoTypeV2ForType(String)} overload directly.
     */
    @Named("typeStringToRepoTypeV2")
    default String typeStringToRepoTypeV2(String type) {
        return repoTypeV2ForType(type);
    }

    /**
     * Static helper so callers outside the MapStruct injection context can compute the
     * {@code repo_type_v2} value without holding a mapper instance.
     */
    static String repoTypeV2ForType(String type) {
        if (RepoDetailsType.FEDERATED.typeNameLowercase().equals(type)) {
            return RepoDetailsType.LOCAL.typeNameLowercase();
        }
        return type;
    }

    @Named("packageTypeToTypeString")
    default String packageTypeToTypeString(RepoType packageType) {
        return packageType.getType();
    }

    @Named("typeStringToPackageType")
    default RepoType typeStringToPackageType(String packageType) {
        return RepoType.fromType(packageType);
    }

    @Named("repoDetailTypeStringToRepoDetailType")
    default RepoDetailsType repoTypeNameToRepoDetailsType(String repoDetailsType) {
        return repoDetailsType == null ? null : RepoDetailsType.byNativeName(repoDetailsType.toLowerCase(Locale.ROOT));
    }
}
