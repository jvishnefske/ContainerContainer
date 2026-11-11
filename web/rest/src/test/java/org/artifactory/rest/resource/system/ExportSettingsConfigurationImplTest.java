package org.artifactory.rest.resource.system;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class ExportSettingsConfigurationImplTest {

    @Test(dataProvider = "actOnMissionControlProvider")
    public void testSetExportMissionControl(boolean shouldActOnMissionControl) {
        ExportSettingsConfigurationImpl exportSettingsConfiguration = new ExportSettingsConfigurationImpl();
        exportSettingsConfiguration.setExportMissionControl(shouldActOnMissionControl);
        Assert.assertEquals(exportSettingsConfiguration.isExportMissionControl(), shouldActOnMissionControl);
    }

    @DataProvider
    public Object[][] actOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}