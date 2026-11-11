package org.artifactory.environment.converter.local.version.v1;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.FileUtils;
import org.testng.annotations.Test;

import java.io.File;

import static org.artifactory.environment.converter.local.version.v1.RemoveMissionControlPluginsConverter.MC_PLUGIN_FILENAMES;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class RemoveMissionControlPluginsConverterTest extends ArtifactoryHomeBoundTest {

    public void testIsInterestedTrue() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        RemoveMissionControlPluginsConverter converter = new RemoveMissionControlPluginsConverter();

        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v4120.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v700m001.get(), null, 0L);

        assertTrue(converter.isInterested(artifactoryHome, from, to));
    }

    public void testIsInterestedFalse() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        RemoveMissionControlPluginsConverter converter = new RemoveMissionControlPluginsConverter();

        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v700m001.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v700m001.get(), null, 0L);

        assertFalse(converter.isInterested(artifactoryHome, from, to));
    }

    public void testMCPluginFilesRemovedOnConvert() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        File pluginsDir = artifactoryHome.getPluginsDir();
        RemoveMissionControlPluginsConverter converter = new RemoveMissionControlPluginsConverter();

        MC_PLUGIN_FILENAMES.forEach(mcPluginName -> FileUtils
                .writeFile(pluginsDir.getAbsolutePath() + "/" + mcPluginName, "not really a groovy file".getBytes()));

        MC_PLUGIN_FILENAMES
                .forEach(mcPluginName -> assertTrue(
                        new File(pluginsDir.getAbsolutePath() + "/" + mcPluginName).exists()));

        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v4120.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v700m001.get(), null, 0L);

        converter.convert(ArtifactoryHome.get(), from, to);
        MC_PLUGIN_FILENAMES
                .forEach(mcPluginName -> assertFalse(
                        new File(pluginsDir.getAbsolutePath() + "/" + mcPluginName).exists()));
    }

    public void testNonMCPluginFilesRemovedOnConvert() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        File pluginsDir = artifactoryHome.getPluginsDir();
        RemoveMissionControlPluginsConverter converter = new RemoveMissionControlPluginsConverter();

        MC_PLUGIN_FILENAMES.forEach(mcPluginName -> FileUtils
                .writeFile(pluginsDir.getAbsolutePath() + "/" + mcPluginName, "not really a groovy file".getBytes()));

        MC_PLUGIN_FILENAMES
                .forEach(mcPluginName -> assertTrue(
                        new File(pluginsDir.getAbsolutePath() + "/" + mcPluginName).exists()));

        FileUtils.writeFile(pluginsDir.getAbsolutePath() + "/someOtherPlugin.groovy",
                "not really a groovy file".getBytes());
        File otherPluginFile = new File(pluginsDir.getAbsolutePath() + "/someOtherPlugin.groovy");
        assertTrue(otherPluginFile.exists());

        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v4120.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v700m001.get(), null, 0L);

        converter.convert(ArtifactoryHome.get(), from, to);
        // MC Plugins removed
        MC_PLUGIN_FILENAMES
                .forEach(mcPluginName -> assertFalse(
                        new File(pluginsDir.getAbsolutePath() + "/" + mcPluginName).exists()));
        // Other ones left intact
        assertTrue(otherPluginFile.exists());
    }
}