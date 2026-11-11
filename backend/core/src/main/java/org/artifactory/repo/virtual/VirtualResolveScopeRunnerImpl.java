package org.artifactory.repo.virtual;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.threadlocal.cache.SupplierWithError;
import org.artifactory.threadlocal.cache.VirtualResolveScopeRunner;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
public class VirtualResolveScopeRunnerImpl implements VirtualResolveScopeRunner {

    private final InternalRepositoryConfigService repositoryConfigService;

    @Override
    public <T, Y extends Exception> T run(@NonNull String virtualRepoKey,
            @NonNull SupplierWithError<T, Y> supplier) throws Y {
        return repositoryConfigService.runWithVirtualResolveConfigCache(virtualRepoKey, supplier);
    }
}
