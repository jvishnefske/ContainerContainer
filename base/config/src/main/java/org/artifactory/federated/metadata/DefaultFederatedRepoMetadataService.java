package org.artifactory.federated.metadata;

import org.springframework.stereotype.Service;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;

/**
 * @author Alexei Vainshtein
 */
@Service
public class DefaultFederatedRepoMetadataService implements FederatedRepoMetadataService {

    @Override
    public String getPackageMetadata(String packageType, String url, String proxyRef) {
        return EMPTY_STRING;
    }

    @Override
    public String getPackageMetadataForLocal(String packageType, String baseUrl) {
        return EMPTY_STRING;
    }

    @Override
    public String getFederatedRepoConfigMetadata() {
        return EMPTY_STRING;
    }

}
