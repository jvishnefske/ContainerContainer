package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.springframework.stereotype.Component;

/**
 * @author Package Generator
 */
@Component
public class AgentPackagesMetadataProvider extends AbstractSimplePrefixMetadataProvider {

    private static final String AGENT_PACKAGE_PREFIX = "agentpackages";
    private static final String NAME_KEY = AGENT_PACKAGE_PREFIX + ".gitrepo";
    private static final String LEAD_FILE_EXTENSION = "zip";

    AgentPackagesMetadataProvider() {
        super(RepoType.AgentPackages, AGENT_PACKAGE_PREFIX, NAME_KEY, LEAD_FILE_EXTENSION);
    }
}
