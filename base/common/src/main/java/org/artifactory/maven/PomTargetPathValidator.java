/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.maven;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.mime.MavenNaming;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * @author Noam Y. Tenne
 */
public class PomTargetPathValidator {
    private static final Logger log = LoggerFactory.getLogger(PomTargetPathValidator.class);

    private final String relPath;
    private final ModuleInfo moduleInfo;
    private Model model;

    public PomTargetPathValidator(String relPath, ModuleInfo moduleInfo) {
        this.relPath = relPath;
        this.moduleInfo = moduleInfo;
    }

    public void validate(InputStream in, boolean suppressPomConsistencyChecks) throws IOException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String suppressedCheckMessage =
                "{} POM consistency checks are suppressed. Broken artifacts might have been stored in the repository " +
                        "- please resolve this manually.";
        try {
            model = parseModel(in, suppressPomConsistencyChecks, reader);
            validateModelConsistency(suppressPomConsistencyChecks, suppressedCheckMessage);
        } catch (XmlPullParserException e) {
            handleXmlPullException(in, suppressPomConsistencyChecks, e, suppressedCheckMessage);
        }
    }

    private Model parseModel(InputStream in, boolean suppressPomConsistencyChecks, MavenXpp3Reader reader) 
            throws XmlPullParserException, IOException {
        // Only use CleanedXmlInputStream when we need to validate POM structure
        // If consistency checks are suppressed, use the original stream directly
        if (suppressPomConsistencyChecks) {
            return reader.read(new XmlStreamReader(in));
        } else {
            // Use CleanedXmlInputStream to handle POM files with leading whitespace or empty lines
            return reader.read(new XmlStreamReader(new CleanedXmlInputStream(in, relPath)));
        }
    }

    private void validateModelConsistency(boolean suppressPomConsistencyChecks, String suppressedCheckMessage) 
            throws BadPomException {
        String groupId = getGroupId(model);

        if (StringUtils.isNotBlank(groupId)) {
            validateModelVersion(suppressPomConsistencyChecks, suppressedCheckMessage);
            validatePathPrefix(groupId, suppressPomConsistencyChecks, suppressedCheckMessage);
        }
    }

    private void validateModelVersion(boolean suppressPomConsistencyChecks, String suppressedCheckMessage) 
            throws BadPomException {
        String modelVersion = getModelVersion(model);
        if (StringUtils.isBlank(modelVersion)) {
            String msg = String.format(
                    "The Pom version of '%s' does not exists. Please verify your POM content for correctness",
                    relPath);
            handlePomConsistencyCheckFailure(suppressPomConsistencyChecks, suppressedCheckMessage, msg);
        }
    }

    private void validatePathPrefix(String groupId, boolean suppressPomConsistencyChecks, 
            String suppressedCheckMessage) throws BadPomException {
        String modelVersion = getModelVersion(model);
        if (StringUtils.isBlank(modelVersion)) {
            return; // Already handled in validateModelVersion
        }

        String pathPrefix = buildPathPrefix(groupId, modelVersion);

        //Do not validate paths that contain property references
        if (pathPrefix != null && !pathPrefix.contains("${")
                && !StringUtils.startsWithIgnoreCase(relPath, pathPrefix)) {
            String msg = String.format(
                    "The target deployment path '%s' does not match the POM's expected path " +
                            "prefix '%s'. Please verify your POM content for correctness and make sure the " +
                            "source path is a valid Maven repository root path.", relPath, pathPrefix);
            handlePomConsistencyCheckFailure(suppressPomConsistencyChecks, suppressedCheckMessage, msg);
        }
    }

    private String buildPathPrefix(String groupId, String modelVersion) {
        //For snapshots with unique snapshot version, do not include the model version in the path
        boolean snapshot = moduleInfo.isIntegration();
        boolean versionSnapshot = MavenNaming.isNonUniqueSnapshotVersion(modelVersion);

        if (snapshot && !versionSnapshot) {
            return groupId.replace('.', '/') + "/" + model.getArtifactId() + "/";
        } else if (StringUtils.isNotBlank(modelVersion)) {
            return groupId.replace('.', '/') + "/" + model.getArtifactId() + "/" + modelVersion;
        }
        return null;
    }

    private void handleXmlPullException(InputStream in, boolean suppressPomConsistencyChecks, XmlPullParserException e,
            String suppressedCheckMessage) throws BadPomException {
        if (log.isDebugEnabled()) {
            try {
                // Only use CleanedXmlInputStream for error reporting if consistency checks are not suppressed
                Reader isr;
                if (suppressPomConsistencyChecks) {
                    isr = new XmlStreamReader(in);
                } else {
                    // Try to clean the input stream for better error reporting using CleanedXmlInputStream
                    isr = new XmlStreamReader(new CleanedXmlInputStream(in, relPath));
                }
                String s = readString(isr);
                log.debug("Could not parse bad POM for '{}'. Bad POM content:\n{}\n", relPath, s);
            } catch (Exception ex) {
                log.trace("Could not extract bad POM content for '{}': {}.", relPath, e.getMessage());
            }
        }
        String message = "Failed to read POM for '" + relPath + "': " + e.getMessage() + ".";
        handlePomConsistencyCheckFailure(suppressPomConsistencyChecks, suppressedCheckMessage, message);
    }

    private void handlePomConsistencyCheckFailure(boolean suppressPomConsistencyChecks, String suppressedCheckMessage,
            String msg) throws BadPomException {
        if (suppressPomConsistencyChecks) {
            log.warn(suppressedCheckMessage, msg);
        } else {
            throw new BadPomException(msg);
        }
    }

    private String getGroupId(Model model) {
        String groupId = model.getGroupId();
        if (StringUtils.isBlank(groupId)) {
            Parent parent = model.getParent();
            if (parent != null) {
                groupId = parent.getGroupId();
            }
        }
        return groupId;
    }

    private String getModelVersion(Model model) {
        String modelVersion = model.getVersion();
        //Version may come from the parent
        if (StringUtils.isBlank(modelVersion)) {
            Parent parent = model.getParent();
            if (parent != null) {
                modelVersion = parent.getVersion();
            }
        }
        return modelVersion;
    }

    String readString(Reader reader) throws IOException {
        StringBuilder buffer = new StringBuilder(2048);
        int value;
        while ((value = reader.read()) != -1) {
            buffer.append((char) value);
        }
        return buffer.toString();
    }

    /**
     * @return True if the processed pom represents a maven plugin.
     */
    public boolean isMavenPlugin() {
        return model != null && "maven-plugin".equals(model.getPackaging());
    }
}
