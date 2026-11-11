package org.artifactory.repo;

import java.util.List;

public interface FederatedRepositoryConfiguration extends LocalRepositoryConfiguration, ProxyRepositoryConfiguration {

    List<FederatedMemberConfiguration> getMembers();
    void setMembers(List<FederatedMemberConfiguration> members);
}
