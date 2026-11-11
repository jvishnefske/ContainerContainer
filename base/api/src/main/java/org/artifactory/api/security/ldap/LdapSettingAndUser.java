package org.artifactory.api.security.ldap;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.descriptor.security.ldap.LdapSetting;

/**
 * @author Ariel Kabov
 */

@Data
@AllArgsConstructor
public class LdapSettingAndUser {

    private LdapSetting setting;
    private LdapUserAttributes ldapUser;
}
