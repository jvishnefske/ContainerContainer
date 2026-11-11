package org.artifactory.version.converter.v330;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jfrog.access.rest.system.LdapConfig;
import org.jfrog.access.rest.system.LdapGroupConfig;
import org.jfrog.access.rest.system.LdapGroupSearchConfig;
import org.jfrog.access.rest.system.LdapPagingConfig;

import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.ldapApplyHeadlessRealmValidation;
import static org.artifactory.common.ConstantValues.ldapCleanGroupOnFail;
import static org.artifactory.common.ConstantValues.ldapConnectTimeoutMillis;
import static org.artifactory.common.ConstantValues.ldapDisableGroupSearchAttributesLimitation;
import static org.artifactory.common.ConstantValues.ldapForceGroupMemberAttFullDN;
import static org.artifactory.common.ConstantValues.ldapGroupNamesSearchFilterThreshold;
import static org.artifactory.common.ConstantValues.ldapMaxPageSize;
import static org.artifactory.common.ConstantValues.ldapPoolTimeoutMillis;
import static org.artifactory.common.ConstantValues.ldapReferralStrategy;
import static org.artifactory.common.ConstantValues.ldapSocketTimeoutMillis;
import static org.artifactory.common.ConstantValues.loginBlockDelayMillis;
import static org.artifactory.common.ConstantValues.loginMaxBlockDelayMillis;

@Slf4j
public class MigrateContantsToAccessConverter implements XmlConverter {
    @Override
    public void convert(Document config) {
        updateAccessPropertiesLdapConfig();
        updateAccessPropertiesLoginBlockConfig();
    }

    private void updateAccessPropertiesLdapConfig() {
        LdapConfig.LdapConfigBuilder lcb = LdapConfig.builder();
        lcb.referralStrategy(ldapReferralStrategy.getString());
        lcb.readTimeoutMillis(ldapSocketTimeoutMillis.getLong());
        lcb.connectTimeoutMillis(ldapConnectTimeoutMillis.getLong());
        lcb.poolTimeoutMillis(ldapPoolTimeoutMillis.getLong());
        lcb.applyHeadlessValidation(ldapApplyHeadlessRealmValidation.getBoolean());
        lcb.forceGroupMemberAttFullDn(ldapForceGroupMemberAttFullDN.getBoolean());

        LdapGroupConfig.LdapGroupConfigBuilder lgcb = LdapGroupConfig.builder();
        LdapGroupSearchConfig.LdapGroupSearchConfigBuilder lgsc = LdapGroupSearchConfig.builder();
        lgsc.filterThreshold(ldapGroupNamesSearchFilterThreshold.getInt());
        lgsc.disableAttributesLimitation(ldapDisableGroupSearchAttributesLimitation.getBoolean());
        LdapGroupSearchConfig lgcs = lgsc.build();
        lgcb.search(lgcs);
        lgcb.cleanOnFail(ldapCleanGroupOnFail.getBoolean());
        LdapGroupConfig lgc = lgcb.build();
        lcb.group(lgc);
        LdapPagingConfig.LdapPagingConfigBuilder lpcb = LdapPagingConfig.builder();
        lpcb.maxPageSize(ldapMaxPageSize.getInt());
        lpcb.blacklistExpireAfterWriteMinutes((int) TimeUnit.MINUTES.convert(1, TimeUnit.DAYS));
        LdapPagingConfig lpc = lpcb.build();
        lcb.paging(lpc);
        LdapConfig ldapConfig = lcb.build();
        AccessConfigHolder.getInstance().setLdapConfig(ldapConfig);
        log.info("Successfully copied ldapConfig: {}", ldapConfig);
    }

    private void updateAccessPropertiesLoginBlockConfig() {
        AccessConfigHolder.getInstance().addLoginBlockDelayMillis(loginBlockDelayMillis.getLong());
        AccessConfigHolder.getInstance().addLoginMaxBlockDelayMillis(loginMaxBlockDelayMillis.getLong());
    }
}
