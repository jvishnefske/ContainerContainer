package org.artifactory.metadata.service.provider;

import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;

/**
 * @author Uriah Levy
 */
interface MetadataProvider {

    void supplement(MutableMetadataEntityBOM metadataBOM, FileInfo fileInfo, Properties properties,
            @Nullable StatsInfo stats);

    RepoType getRepoType();

    /**
     * Returns an AQL query used to collect version files adjacent to the lead artifact
     */
    default Optional<AqlApiItem> buildVersionFilesQuery(@Nonnull RepoPath leadArtifactRepoPath) {
        return Optional.empty();
    }

    String getPkgid(RepoPath repoPath, Properties properties);
}
