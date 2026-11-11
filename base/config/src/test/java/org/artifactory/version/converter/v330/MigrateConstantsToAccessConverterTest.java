package org.artifactory.version.converter.v330;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jfrog.access.rest.system.LdapConfig;
import org.jfrog.access.rest.system.LdapGroupConfig;
import org.jfrog.access.rest.system.LdapGroupSearchConfig;
import org.jfrog.access.rest.system.LdapPagingConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MigrateConstantsToAccessConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH =
            "/config/test/config.3.2.7_migrate_crowd_fields.xml";
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;

    @BeforeAll
    static void beforeAll() {
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);
        //noinspection ResultOfMethodCallIgnored
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
    }

    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
        ArtifactoryHome.unbind();
    }

    @Test
    void testConvert() throws Exception {
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        Document doc = convertXml(RESOURCE_PATH,
                new MigrateContantsToAccessConverter());
        LdapConfig actual = AccessConfigHolder.getInstance().drainLdapConfig();

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
        LdapConfig expected = lcb.build();

        assertEquals(expected, actual);
        ArtifactoryHome.unbind();
    }
}
