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
/*
 * Additional contributors:
 *    JFrog Ltd.
 */

package org.artifactory.maven.index.creator;

import org.apache.maven.index.ArtifactAvailability;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.locator.Locator;
import org.apache.maven.model.Model;
import org.artifactory.maven.index.locator.ExtensionBasedLocator;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.fs.tree.file.JavaIOFileAdapter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.jfrog.common.archive.ArchiveType.TARGZ;

public class VfsMinimalArtifactInfoIndexCreator extends MinimalArtifactInfoIndexCreator {
    private static final Logger log = LoggerFactory.getLogger(VfsMinimalArtifactInfoIndexCreator.class);

    private final Locator jl;
    private final Locator sl;
    private final Locator sigl;

    public VfsMinimalArtifactInfoIndexCreator() {
        this.jl = new ExtensionBasedLocator("-javadoc.jar");
        this.sl = new ExtensionBasedLocator("-sources.jar");
        this.sigl = new ExtensionBasedLocator(".jar.asc");
    }

    @SuppressWarnings({"OverlyComplexMethod"})
    @Override
    public void populateArtifactInfo(ArtifactContext ac) {
        //Test whether the indexer needs to be stopped or paused
        //Check if we need to stop/suspend
        TaskService taskService = InternalContextHelper.get().getTaskService();
        boolean stop = taskService.pauseOrBreak();
        if (stop) {
            return;
        }
        //Populating the artifact info
        File artifact = ac.getArtifact();
        File pom = ac.getPom();
        ArtifactInfo ai = ac.getArtifactInfo();

        if (pom != null) {
            ai.setLastModified(pom.lastModified());

            ai.setFileExtension("pom");
        }

        // TODO handle artifacts without poms
        if (pom != null) {
            if (ai.getClassifier() != null) {
                ai.setSourcesExists(ArtifactAvailability.NOT_AVAILABLE);

                ai.setJavadocExists(ArtifactAvailability.NOT_AVAILABLE);
            } else {
                File sources = sl.locate(pom);
                if (!sources.exists()) {
                    ai.setSourcesExists(ArtifactAvailability.NOT_PRESENT);
                } else {
                    ai.setSourcesExists(ArtifactAvailability.PRESENT);
                }

                File javadoc = jl.locate(pom);
                if (!javadoc.exists()) {
                    ai.setJavadocExists(ArtifactAvailability.NOT_PRESENT);
                } else {
                    ai.setJavadocExists(ArtifactAvailability.PRESENT);
                }
            }
        }

        if (artifact != null) {
            File signature = sigl.locate(artifact);

            ai.setSignatureExists(signature.exists() ? ArtifactAvailability.PRESENT : ArtifactAvailability.NOT_PRESENT);

            try {
                String sha1 = ((JavaIOFileAdapter) artifact).getFileInfo().getSha1();
                if (sha1 != null) {
                    ai.setSha1(StringUtils.chomp(sha1).trim().split(" ")[0]);
                }
            } catch (Exception e) {
                ac.addError(e);
                if (log.isDebugEnabled()) {
                    log.debug("Could not retrieve artifact checksum.", e);
                } else {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    log.warn("Could not retrieve artifact checksum" + (msg != null ? ": " + msg : "") + ".");
                }
            }

            ai.setLastModified(artifact.lastModified());

            ai.setSize(artifact.length());

            ai.setFileExtension(getExtension(artifact, ac.getGav()));

            if (ai.getPackaging() == null) {
                ai.setPackaging(ai.getFileExtension());
            }
        }

        Model model = ac.getPomModel();

        if (model != null) {
            ai.setName(model.getName());

            ai.setDescription(model.getDescription());

            if (model.getPackaging() != null && ai.getClassifier() == null) {
                // only when this is not a classified artifact
                ai.setPackaging(model.getPackaging());
            }
        }
    }

    private String getExtension(File artifact, Gav gav) {
        if (gav != null && StringUtils.isNotBlank(gav.getExtension())) {
            return gav.getExtension();
        }

        // last resort, the extension of the file
        String artifactFileName = artifact.getName().toLowerCase();

        // tar.gz? and other "special" combinations
        if (artifactFileName.endsWith(TARGZ.value())) {
            return TARGZ.value();
        } else if (artifactFileName.equals("tar.bz2")) {
            return "tar.bz2";
        }

        // get the part after the last dot
        return FileUtils.getExtension(artifactFileName);
    }

}