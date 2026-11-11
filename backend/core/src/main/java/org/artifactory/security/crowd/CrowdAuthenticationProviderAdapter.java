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

package org.artifactory.security.crowd;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.security.ExternalProviderBase;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.model.Realm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * An authentication provider adapter that delegates the calls to the SSO addon.<br> Needed since a provider cannot be
 * added to the spring authentication manager while in an addon library.
 *
 * @author Noam Y. Tenne
 */
@Component
public class CrowdAuthenticationProviderAdapter extends ExternalProviderBase
        implements RealmAwareAuthenticationProvider {

    private final AddonsManager addonsManager;
    private final AccessService accessService;

    @Autowired
    public CrowdAuthenticationProviderAdapter(AddonsManager addonsManager,
            AccessService accessService) {
        super(addonsManager);
        this.addonsManager = addonsManager;
        this.accessService = accessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (shouldNotAuthenticate(authentication)) {
            return null;
        }
        return addonsManager.addonByType(CrowdAddon.class).authenticateCrowd(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return addonsManager.addonByType(CrowdAddon.class).isCrowdAuthenticationSupported(authentication) &&
                (!accessService.isRealmAuthenticationEnabled(Realm.CROWD) &&
                !accessService.isRealmAuthenticationEnabled(Realm.JIRA));
    }

    @Override
    public String getRealm() {
        return CrowdAddon.REALM;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        addonsManager.addonByType(CrowdAddon.class).addExternalGroups(username, groups);
    }

    @Override
    public boolean userExists(String username) {
        return addonsManager.addonByType(CrowdAddon.class).findUser(username);
    }
}
