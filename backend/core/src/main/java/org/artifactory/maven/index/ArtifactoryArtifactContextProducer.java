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

package org.artifactory.maven.index;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactContextProducer;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.ArtifactPackagingMapper;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.locator.GavHelpedLocator;
import org.apache.maven.index.locator.Locator;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.artifactory.maven.index.locator.MainArtifactLocator;
import org.artifactory.maven.index.locator.MetadataLocator;
import org.artifactory.maven.index.locator.PomLocator;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.artifactory.storage.fs.tree.file.JavaIOFileAdapter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Yossi Shaul
 */
@Slf4j
public class ArtifactoryArtifactContextProducer implements ArtifactContextProducer {

    private final ArtifactPackagingMapper mapper = new SimpleArtifactPackagingMapper();
    private final GavHelpedLocator pl = new PomLocator();
    private final Locator ml = new MetadataLocator();

    /**
     * Get ArtifactContext for given pom or artifact (jar, war, etc). A file can be
     */
    @Override
    public ArtifactContext getArtifactContext(IndexingContext context, File file) {
        String repositoryPath = context.getRepository().getAbsolutePath();
        String artifactPath = file.getAbsolutePath();

        // protection from IndexOutOfBounds
        if (artifactPath.length() <= repositoryPath.length()) {
            return null; // not an artifact
        }

        if (!isIndexable(file)) {
            return null; // skipped
        }

        Gav gav = getGavFromPath(context, repositoryPath, artifactPath);

        if (gav == null) {
            return null; // not an artifact
        }

        File pom;
        File artifact;

        if (file.getName().endsWith(".pom")) {
            MainArtifactLocator al = new MainArtifactLocator(mapper);
            artifact = al.locate(file, context.getGavCalculator(), gav);

            // If we found the matching artifact, switch over to indexing that, instead of the pom
            if (artifact != null) {
                gav = getGavFromPath(context, repositoryPath, artifact.getAbsolutePath());
            }

            pom = file;
        } else {
            artifact = file;
            pom = pl.locate(file, context.getGavCalculator(), gav);
        }

        String groupId = gav.getGroupId();

        String artifactId = gav.getArtifactId();

        String version = gav.getBaseVersion();

        String classifier = gav.getClassifier();

        String extension = gav.getExtension();

        ArtifactInfo ai = new ArtifactInfo(context.getRepositoryId(), groupId, artifactId,
                version, classifier, extension);

        // store extension if classifier is not empty
        if (!StringUtils.isEmpty(ai.getClassifier())) {
            ai.setPackaging(gav.getExtension());
        }

        if (pom != null && pom.exists() && ai.getClassifier() == null) {
            try (InputStream pomInputStream = new BufferedInputStream(((JavaIOFileAdapter) pom).getStream())) {
                // need to read the pom model to get packaging
                Model model = new MavenXpp3Reader().read(pomInputStream, false);
                if (model != null && model.getPackaging() != null) {
                    // only when this is not a classified artifact
                    ai.setPackaging(model.getPackaging());
                }
            } catch (RepositoryRuntimeException | IOException | XmlPullParserException e) {
                log.warn("Could not create Maven model from file {} due to {}", pom.getName(), e.getMessage());
                log.debug("Could not create Maven model", e);
            }// auto-closed in ReaderTrackingInputStream
        }

        ai.setFileName(file.getName());
        ai.setFileExtension(gav.getExtension());

        File metadata = ml.locate(pom);

        return new ArtifactContext(pom, artifact, metadata, ai, gav);
    }

    private boolean isIndexable(File file) {
        if (file == null) {
            return false;
        }

        String filename = file.getName();

        return !filename.equals("maven-metadata.xml")
                && !filename.endsWith(".properties")
                && !filename.endsWith(".asc") && !filename.endsWith(".md5") && !filename.endsWith(".sha1");
    }

    private Gav getGavFromPath(IndexingContext context, String repositoryPath, String artifactPath) {
        String path = artifactPath.substring(repositoryPath.length() + 1).replace('\\', '/');

        return context.getGavCalculator().pathToGav(path);
    }

}
