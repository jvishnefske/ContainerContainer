package org.artifactory.util;

import org.jfrog.common.platform.StringBasedEnum;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class ReloadablePropertiesUtilTest {

    @Test
    public void testGetEnumValueNullValue() {
        EnumTest enumValue = ReloadablePropertiesUtil.getEnumValue(EnumTest.class, "");
        Assert.assertNull(enumValue);
    }

    @Test
    public void testGetEnumValue() {
        EnumTest enumValue = ReloadablePropertiesUtil.getEnumValue(EnumTest.class, "configured_value");
        Assert.assertNotNull(enumValue);
        Assert.assertEquals(enumValue.getString(), EnumTest.name.getString());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "No enum value.*")
    public void testGetStringBasedEnumWrongValue() {
        ReloadablePropertiesUtil.getEnumValue(EnumTest.class, "configured_value_wrong");
    }

    private enum EnumTest implements StringBasedEnum {
        name("configured_value");

        private final String value;

        EnumTest(String value) {
            this.value = value.toLowerCase();
        }

        public String getString() {
            return value;
        }
    }
}