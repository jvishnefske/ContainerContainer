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

package org.artifactory.mime;

import com.google.common.collect.ImmutableSet;

import jakarta.annotation.Nullable;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds a list of mime entries.
 *
 * @author Yossi Shaul
 */
@XmlRootElement(name = "mimetypes")
public class MimeTypes implements Serializable {
    /**
     * The xml version (controlled by the converters only)
     */
    @XmlAttribute
    private String version;

    @XmlElement(name = "mimetype")
    private Set<MimeType> mimeTypes;

    // Both caches are derived from mimeTypes and rebuilt by initDerivedCaches() immediately after the object is
    // fully populated (construction, JAXB unmarshal, or deserialization). They are transient: never part of the
    // serialized form, written once and never mutated; safe publication is guaranteed by single-threaded startup.
    private transient Map<String, MimeType> typeByExtension;
    private transient Set<String> archivesExtensions;

    public MimeTypes() {
    }

    protected MimeTypes(ImmutableSet<MimeType> mimeTypes) {
        this.mimeTypes = mimeTypes;
        initDerivedCaches();
    }

    private void initDerivedCaches() {
        Map<String, MimeType> byExt = new HashMap<>(mimeTypes.size());
        Set<String> archives = new HashSet<>();
        for (MimeType type : mimeTypes) {
            for (String ext : type.getExtensions()) {
                byExt.put(ext, type);
            }
            if (type.isArchive()) {
                archives.addAll(type.getExtensions());
            }
        }
        this.typeByExtension = Map.copyOf(byExt);
        this.archivesExtensions = Set.copyOf(archives);
    }

    public Set<MimeType> getMimeTypes() {
        return mimeTypes;
    }

    @Nullable
    public MimeType getByMime(String type) {
        for (MimeType mimeType : mimeTypes) {
            if (mimeType.getType().equals(type)) {
                return mimeType;
            }
        }
        return null;
    }

    @Nullable
    public MimeType getByExtension(String extension) {
        if (extension == null) {
            return null;
        }
        return typeByExtension.get(extension.toLowerCase());
    }

    public Set<String> getArchiveExtensions() {
        return archivesExtensions;
    }

    @SuppressWarnings("unused") // invoked reflectively by the JAXB runtime after unmarshalling
    private void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
        initDerivedCaches();
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initDerivedCaches();
    }
}
