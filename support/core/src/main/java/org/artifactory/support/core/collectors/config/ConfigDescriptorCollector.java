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

package org.artifactory.support.core.collectors.config;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.config.CentralConfigService;
import org.jfrog.support.common.config.ConfigFilesConfiguration;
import org.jfrog.support.common.core.collectors.ConfigFilesCollector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jfrog.support.common.core.collectors.scrubbing.LogScrubber.scrubIPsInLine;

/**
 * Config descriptor collector
 *
 * @author Michael Pasternak
 */

@Slf4j
public class ConfigDescriptorCollector extends ConfigFilesCollector {

    private final CentralConfigService centralConfigService;

    private static final List<String> SCRUB_STRINGS = Arrays.asList(
            "<password>", "<keyStorePassword>", "<passphrase>", "<gpgPassPhrase>", "<refreshToken>", "<secret>",
                    "<adminToken>", "<managerDn>", "<managerPassword>", "<privateKey>");

    private static final String USERNAME_SCRUB = "<username>";

    private static final String LINE_SEPARATOR = System.getProperty("line.separator") != null ?
            System.getProperty("line.separator") : "\n";

    public ConfigDescriptorCollector(CentralConfigService centralConfigService) {
        super();
        this.centralConfigService = centralConfigService;
    }

    /**
     * Collects ConfigDescriptor
     *
     * @param configuration {@link ConfigFilesConfiguration}
     * @param tmpDir        output directory for produced content
     * @return operation result
     */
    @Override
    protected boolean doCollect(ConfigFilesConfiguration configuration, File tmpDir) {

        File outputFile = new File(tmpDir.getPath() + File.separator + getFileName());
        String configDescriptor = centralConfigService.getConfigXml();

        configDescriptor = scrubDescriptorIfNeeded(configDescriptor, configuration.isHideUserDetails(),
                configuration.isScrubUserNames(), configuration.isScrubIPs());
        if (!Strings.isNullOrEmpty(configDescriptor)) {
            try {
                log.debug("Starting to write Config Descriptor to {}", tmpDir.getAbsolutePath());
                FileUtils.writeStringToFile(outputFile, configDescriptor, StandardCharsets.UTF_8);
                log.info("Collection of {} was successfully accomplished", getContentName());
                return true;
            } catch (IOException e) {
                log.error("Collecting {} has failed, Cause: {}", getContentName(), e.getMessage(), e);
                return failure(e);
            }
        }
        return false;
    }

    private String scrubDescriptorIfNeeded(String configDescriptor, boolean hideUserDetails, boolean scrubUserNames,
            boolean scrubIPs) {
        if (hideUserDetails || scrubUserNames || scrubIPs) {
            return String.join(LINE_SEPARATOR,
                    scrubConfigDescriptor(configDescriptor, hideUserDetails, scrubUserNames, scrubIPs));
        }
        return configDescriptor;
    }

    private List<String> scrubConfigDescriptor(String configDescriptor, boolean hideUserDetails, boolean scrubUserNames,
            boolean scrubIPs) {
        log.debug("Initiating Config Descriptor scrubbing");
        List<String> scrubbedDescriptor = Lists.newArrayList();
        List<String> stringToScrub = getStringsToScrub(hideUserDetails, scrubUserNames);
        String[] lines = configDescriptor.split(LINE_SEPARATOR);
        for (String line : lines) {
            boolean writeLine = true;
            for (String scrub : stringToScrub) {
                if (line.contains(scrub)) {
                    writeLine = false;
                    break;
                }
            }
            if (writeLine) {
                if (scrubIPs) {
                    line = scrubIPsInLine(line, scrubbedValuesCache);
                }
                scrubbedDescriptor.add(line);
            }
        }
        return scrubbedDescriptor;
    }

    private List<String> getStringsToScrub(boolean hideUserDetails, boolean scrubUserNames) {
        List<String> stringToScrub = Lists.newArrayList();
        if (hideUserDetails) {
            stringToScrub.addAll(SCRUB_STRINGS);
        }
        if (scrubUserNames) {
            stringToScrub.add(USERNAME_SCRUB);
        }
        return stringToScrub;
    }

    @Override
    protected List<File> getConfigFiles() {
        return Collections.emptyList();
    }

    @Override
    protected List<String> getScrubStrings() {
        return SCRUB_STRINGS;
    }

    /**
     * @return The filename to be used
     */
    @Override
    protected String getFileName() {
        return "artifactory.config.xml";
    }
}
