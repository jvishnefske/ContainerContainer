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

package org.artifactory.version.converter.v359;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renames the Hugging Face ML general-config XET elements that controlled remote-only XET to the unified XET flag:
 * <ul>
 *     <li>{@code xetRemoteEnabled} → {@code xetEnabled}</li>
 *     <li>{@code xetRemoteEnabledExplicit} → {@code xetEnabledExplicit}</li>
 * </ul>
 * The element values are preserved verbatim, so a customer who explicitly enabled (or disabled) remote XET keeps the
 * same effective state under the new unified flag. The rename is a no-op when the legacy elements are absent or when
 * the new elements already exist.
 *
 * @author shahafg
 */
public class HuggingFaceXetEnabledConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(HuggingFaceXetEnabledConverter.class);

    private static final String REPOSITORIES_GENERAL_CONFIG = "repositoriesGeneralConfig";
    private static final String HUGGINGFACEML = "huggingfaceml";
    private static final String XET_REMOTE_ENABLED = "xetRemoteEnabled";
    private static final String XET_REMOTE_ENABLED_EXPLICIT = "xetRemoteEnabledExplicit";
    private static final String XET_ENABLED = "xetEnabled";
    private static final String XET_ENABLED_EXPLICIT = "xetEnabledExplicit";

    @Override
    public void convert(Document doc) {
        log.info("Starting to convert Hugging Face ML XET configuration elements.");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repositoriesGeneralConfig = rootElement.getChild(REPOSITORIES_GENERAL_CONFIG, namespace);
        if (repositoriesGeneralConfig == null) {
            log.debug("No '{}' element found, skipping Hugging Face ML XET conversion.", REPOSITORIES_GENERAL_CONFIG);
            return;
        }
        Element huggingfaceml = repositoriesGeneralConfig.getChild(HUGGINGFACEML, namespace);
        if (huggingfaceml == null) {
            log.debug("No '{}' element found, skipping Hugging Face ML XET conversion.", HUGGINGFACEML);
            return;
        }

        renameElement(huggingfaceml, namespace, XET_REMOTE_ENABLED, XET_ENABLED);
        renameElement(huggingfaceml, namespace, XET_REMOTE_ENABLED_EXPLICIT, XET_ENABLED_EXPLICIT);

        log.info("Finished converting Hugging Face ML XET configuration elements.");
    }

    private void renameElement(Element parent, Namespace namespace, String legacyName, String newName) {
        Element legacyElement = parent.getChild(legacyName, namespace);
        if (legacyElement == null) {
            return;
        }
        if (parent.getChild(newName, namespace) != null) {
            // New element already present (should not happen in practice) - drop the stale legacy element
            parent.removeChild(legacyName, namespace);
            log.debug("Element '{}' already exists, removed legacy element '{}'.", newName, legacyName);
            return;
        }
        legacyElement.setName(newName);
        log.debug("Renamed Hugging Face ML element '{}' to '{}'.", legacyName, newName);
    }
}
