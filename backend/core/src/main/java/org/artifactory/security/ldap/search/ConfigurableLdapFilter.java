package org.artifactory.security.ldap.search;

import org.springframework.security.ldap.search.LdapUserSearch;

public interface ConfigurableLdapFilter extends LdapUserSearch {
    void setReturningAttributes(String[] attrs);
}
