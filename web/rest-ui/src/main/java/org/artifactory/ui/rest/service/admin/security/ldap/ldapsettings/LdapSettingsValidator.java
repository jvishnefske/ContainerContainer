package org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.security.access.AccessService;
import org.jdom2.Verifier;

import java.util.Objects;

import static org.artifactory.ui.utils.RequestUtils.PASSWORD_PLACEHOLDER;

/**
 * @author Dor Solomon
 */
@Slf4j
@UtilityClass
public class LdapSettingsValidator {

    @VisibleForTesting
    static final String INVALID_PASSWORD_MSG = "LDAP password needs to be re-entered to allow changes in LDAP settings";
    @VisibleForTesting
    static final String KEYS_ARE_DIFFERENT_MSG = "Key in path and object are different";


    public static void validateCreate(LdapSetting ldapSetting, BasicStatusHolder statusHolder,
            AccessService accessService) {
        validateKey(ldapSetting, statusHolder);
        validateDuplicatedKey(ldapSetting, statusHolder, accessService);
    }

    public static void validateUpdate(String ldapKey, LdapSetting ldapSetting, BasicStatusHolder statusHolder) {
        validateKey(ldapSetting, statusHolder);
        validateAlignedEntityAndPathKeys(ldapKey, ldapSetting, statusHolder);
        validatePassword(ldapSetting, statusHolder);
    }

    private static void validateAlignedEntityAndPathKeys(String ldapKey, LdapSetting ldapSetting,
            BasicStatusHolder statusHolder) {
        if (!Objects.equals(ldapKey, ldapSetting.getKey())) {
            statusHolder.error(KEYS_ARE_DIFFERENT_MSG, log);
        }
    }

    private static void validateDuplicatedKey(LdapSetting ldapSetting,
            BasicStatusHolder statusHolder, AccessService accessService) {
        if (accessService.isLdapSettingsExist(ldapSetting.getKey())) {
            statusHolder.error("Ldap with key " + ldapSetting.getKey() + " already exists", log);
        }
    }

    private static void validateKey(LdapSetting ldapSetting, BasicStatusHolder statusHolder) {
        String error = Verifier.checkXMLName(ldapSetting.getKey());
        if (StringUtils.isNotBlank(error)) {
            statusHolder.error(error, log);
        }
    }

    private static void validatePassword(LdapSetting ldapSetting, BasicStatusHolder statusHolder) {
        if (ldapSetting.getSearch() != null &&
                PASSWORD_PLACEHOLDER.equals(ldapSetting.getSearch().getManagerPassword())) {
            statusHolder.error(INVALID_PASSWORD_MSG, log);
        }
    }
}
