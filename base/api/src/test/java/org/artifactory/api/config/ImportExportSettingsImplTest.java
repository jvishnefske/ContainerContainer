package org.artifactory.api.config;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class ImportExportSettingsImplTest {

    @Test(dataProvider = "actOnMissionControlProvider")
    public void testConstructor(boolean shouldActOnMissionControl) {
        File mock = mock(File.class);
        ImportSettingsImpl settings = new ImportSettingsImpl(mock);
        settings.setShouldActOnMissionControl(shouldActOnMissionControl);
        ImportExportSettingsImpl importExportSettings = new ImportExportSettingsImpl(mock, settings, null);
        Assert.assertEquals(importExportSettings.shouldActOnMissionControl(), shouldActOnMissionControl);

        importExportSettings.setShouldActOnMissionControl(!shouldActOnMissionControl);
        assertEquals(importExportSettings.shouldActOnMissionControl(), !shouldActOnMissionControl);
    }

    @DataProvider
    public Object[][] actOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}