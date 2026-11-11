package org.artifactory.security.ldap.search;

import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;

public class ArtifactoryFilterBasedLdapUserSearch extends FilterBasedLdapUserSearch implements ConfigurableLdapFilter {
    public ArtifactoryFilterBasedLdapUserSearch(String searchBase, String searchFilter,
            BaseLdapPathContextSource contextSource) {
        super(searchBase, searchFilter, contextSource);
    }
}
