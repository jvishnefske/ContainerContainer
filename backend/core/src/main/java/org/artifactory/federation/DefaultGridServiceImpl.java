package org.artifactory.federation;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Default OSS implementation of {@link GridService}. OSS JPDs are never grid
 * members — every method returns the "not a member" answer. Pro provides a
 * JFConfig-backed implementation that overrides via {@code @Primary}.
 *
 * @author Tomer Ben Kereth
 */
@Service
public class DefaultGridServiceImpl implements GridService {

    @Override
    public boolean isPartOfGrid() {
        return false;
    }

    @Override
    public Optional<String> getTopologyKey() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getJpdId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getGridMainSiteUrl() {
        return Optional.empty();
    }
}
