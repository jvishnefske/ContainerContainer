package org.artifactory.ui.rest.model.admin.importexport;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class ImportExportSettingsTest {

    @Test(dataProvider = "actOnMissionControlProvider")
    public void testSetActOnMissionControl(boolean shouldActOnMissionControl) {
        ImportExportSettings settings = new ImportExportSettings("path");
        settings.setActOnMissionControl(shouldActOnMissionControl);
        assertEquals(settings.isActOnMissionControl(), shouldActOnMissionControl);
    }

    @DataProvider
    public Object[][] actOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}