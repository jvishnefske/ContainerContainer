/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ldap;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.security.ldap.search.ArtifactoryFilterBasedLdapUserSearch;
import org.artifactory.security.ldap.search.ConfigurableLdapFilter;
import org.artifactory.security.ldap.search.InjectionProtectionLdapFilter;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author gidis
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LdapUserSearchesHelper {
    public static List<ConfigurableLdapFilter> getLdapUserSearches(ContextSource ctx, LdapSetting settings,
            boolean aol) {
        SearchPattern searchPattern = settings.getSearch();
        String[] searchBases;
        if (searchPattern.getSearchBase() == null) {
            searchBases = new String[] {""};
        } else {
            searchBases = searchPattern.getSearchBase().split(Pattern.quote("|"));
        }
        boolean useObjectInjectionProtection = isObjectInjectionProtection(settings, aol);
        ArrayList<ConfigurableLdapFilter> result = new ArrayList<>();
        for (String base : searchBases) {
            ConfigurableLdapFilter userSearch;
            String filter = searchPattern.getSearchFilter();
            BaseLdapPathContextSource baseLdapCtx = (BaseLdapPathContextSource) ctx;
            if (useObjectInjectionProtection) {
                InjectionProtectionLdapFilter
                        search = new InjectionProtectionLdapFilter(base, filter, baseLdapCtx);
                search.setSearchSubtree(searchPattern.isSearchSubTree());
                userSearch = search;
            } else {
                ArtifactoryFilterBasedLdapUserSearch search =
                        new ArtifactoryFilterBasedLdapUserSearch(base, filter, baseLdapCtx);
                search.setSearchSubtree(searchPattern.isSearchSubTree());
                userSearch = search;
            }
            result.add(userSearch);
        }
        return result;
    }

    public static boolean isObjectInjectionProtection(LdapSetting settings, boolean isAol) {
        if (isAol) {
            return true;
        }
        Boolean ldapPoisoningProtection = settings.getLdapPoisoningProtection();
        return ldapPoisoningProtection == null ? true : ldapPoisoningProtection;
    }
}
