package org.artifactory.file;

import org.jfrog.common.ResourceUtils;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.AssertJUnit.assertTrue;

@Test
public class ArtifactoryValidatedFileTest {

    private final static String DELIMITER = "/";

    private final String normalFilePath = "ziptest.zip";
    private final String maliciousFilePath = ".." + DELIMITER + normalFilePath;

    @Test
    public void testWithNormalFilePath() {
        ArtifactoryValidatedFile validatedFile = new ArtifactoryValidatedFile(normalFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));

        validatedFile = new ArtifactoryValidatedFile(".", normalFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));

        validatedFile = new ArtifactoryValidatedFile(new File("."), normalFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));
    }

    @Test
    public void testWithMaliciousFilePath() {
        ArtifactoryValidatedFile validatedFile = new ArtifactoryValidatedFile(maliciousFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));

        validatedFile = new ArtifactoryValidatedFile(".", maliciousFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));

        validatedFile = new ArtifactoryValidatedFile(new File("."), maliciousFilePath);
        assertTrue(ResourceUtils.resourceExists(DELIMITER + validatedFile.getPath()));
    }
}