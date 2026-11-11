package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

/**
 * @author Daniel simantov
 * this class is default sevice for federatedBindURLService
 */

@Service
public class DefaultFederatedReplaceURLServiceImpl implements FederatedReplaceURLService {

    @Override
    public void replaceUrlAndQueues(String oldUrl, String newUrl, boolean isReplaceQueues) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federated";
    }

}
