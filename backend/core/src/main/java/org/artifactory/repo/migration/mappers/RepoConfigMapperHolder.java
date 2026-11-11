package org.artifactory.repo.migration.mappers;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.converter.RepositoriesConverterManager;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.jfrog.common.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RepoConfigMapperHolder {
    private final Map<Class<?>, RepoConfigMapper<?>> mappers;
    private final RepositoriesConverterManager repoConverterManager;

    public RepoConfigMapperHolder(Set<RepositoriesConfigSubConverter> subConverters) {
        mappers = new HashMap<>();
        mappers.put(LocalRepoDescriptor.class, new LocalRepositoryDescriptorMapper());
        mappers.put(RemoteRepoDescriptor.class, new RemoteRepositoryDescriptorMapper());
        mappers.put(VirtualRepoDescriptor.class, new VirtualRepositoryDescriptorMapper());
        mappers.put(FederatedRepoDescriptor.class, new FederatedRepositoryDescriptorMapper());
        mappers.put(ReleaseBundlesRepoDescriptor.class, new ReleaseBundleRepositoryDescriptorMapper());
        repoConverterManager = new RepositoriesConverterManager(subConverters);
    }

    @Nonnull
    public <E extends RepoDescriptor> List<DbRepositoryConfig> map(Class<E> clazz, Map<String, E> descriptors) {
        if (Objects.isNull(descriptors) || CollectionUtils.isEmpty(descriptors.values())) {
            return List.of();
        }
        if (mappers.containsKey(clazz)) {
            final RepoConfigMapper<E> mapper = (RepoConfigMapper<E>) mappers.get(clazz);
            return descriptors.values().stream()
                    .map(descriptor -> convertMapOfFieldsToDbEntity(descriptor, mapper))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("Repository class " + clazz.getSimpleName() + " does not have mapper");
    }

    @VisibleForTesting
    @Nonnull
    <E extends RepoDescriptor> DbRepositoryConfig convertMapOfFieldsToDbEntity(@Nonnull E descriptor,
            @Nonnull RepoConfigMapper<E> mapper) {
        Map<String, Object> originalFields = mapper.map(descriptor);
        byte[] originalFieldsAsBytes = JsonUtils.getInstance().valueToByteArray(originalFields);
        log.debug("Repo config json before applying sub converters {}",
                new String(originalFieldsAsBytes));

        byte[] convertedFieldsAsBytes = repoConverterManager.convertAsBytes(originalFieldsAsBytes);
        log.debug("Repo config json after applying sub converters {}",
                new String(convertedFieldsAsBytes));

        return RepoConfigMigrationMapperUtils.createDbRepoConfig(descriptor, mapper.getRepoType(),
                convertedFieldsAsBytes, System.currentTimeMillis());
    }
}
