package org.artifactory.sh;

import lombok.RequiredArgsConstructor;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.jfrog.storage.binstore.manager.BinaryProviderManager;

@RequiredArgsConstructor
public class ArtifactoryBinaryProviderTestServiceImpl implements ArtifactoryBinaryProviderTestService {

    private final InternalBinaryService internalBinaryService;

    @Override
    public BinaryProviderManager getBinaryProviderManager() {
        return internalBinaryService.getBinaryProviderManager();
    }

}
