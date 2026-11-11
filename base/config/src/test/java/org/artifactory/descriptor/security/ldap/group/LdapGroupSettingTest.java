package org.artifactory.descriptor.security.ldap.group;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
@Test
public class LdapGroupSettingTest {

    @Test
    public void testIdenticalConfiguration() {
        LdapGroupSetting o1 = createGroupSetting();
        LdapGroupSetting o2 = createGroupSetting();

        assertTrue(o1.identicalConfiguration(o1));
        assertFalse(o1.identicalConfiguration(null));
        assertTrue(o1.identicalConfiguration(o2));

        o2.setName("test2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setName("test");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setGroupBaseDn("someDn2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setGroupBaseDn("someDn");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setGroupNameAttribute("someNameAttr2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setGroupNameAttribute("someNameAttr");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setSubTree(false);
        assertFalse(o1.identicalConfiguration(o2));
        o2.setSubTree(true);
        assertTrue(o1.identicalConfiguration(o2));

        o2.setForceAttributeSearch(true);
        assertFalse(o1.identicalConfiguration(o2));
        o2.setForceAttributeSearch(false);
        assertTrue(o1.identicalConfiguration(o2));

        o2.setFilter("someFilter2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setFilter("someFilter");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setDescriptionAttribute("someDescriptionAttr2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setDescriptionAttribute("someDescriptionAttr");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setEnabledLdap("someSetting2");
        assertFalse(o1.identicalConfiguration(o2));
        o2.setEnabledLdap("someSetting");
        assertTrue(o1.identicalConfiguration(o2));

        o2.setStrategy(LdapGroupPopulatorStrategies.DYNAMIC);
        assertFalse(o1.identicalConfiguration(o2));
        o2.setStrategy(LdapGroupPopulatorStrategies.STATIC);
        assertTrue(o1.identicalConfiguration(o2));
    }

    private LdapGroupSetting createGroupSetting() {
        LdapGroupSetting setting = new LdapGroupSetting();
        setting.setName("test");
        setting.setGroupBaseDn("someDn");
        setting.setGroupNameAttribute("someNameAttr");
        setting.setSubTree(true);
        setting.setForceAttributeSearch(false);
        setting.setFilter("someFilter");
        setting.setDescriptionAttribute("someDescriptionAttr");
        setting.setEnabledLdap("someSetting");
        setting.setStrategy(LdapGroupPopulatorStrategies.STATIC);
        return setting;
    }
}
