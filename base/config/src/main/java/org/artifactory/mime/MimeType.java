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
import org.apache.commons.lang3.StringUtils;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.Serializable;

/**
 * Represents a mime type with additional properties.
 *
 * @author Yossi Shaul
 */
@XmlRootElement(name = "mimetype")
public class MimeType implements Serializable {
    // commonly used mime types
    public static final String applicationXml = "application/xml";
    public static final String checksum = "application/x-checksum";
    public static final String javaSource = "text/x-java-source";
    public static final String javaArchive = "application/java-archive";

    public static final MimeType def = new MimeType("application/octet-stream", ImmutableSet.<String>of(),
            false, false, false, null, "doc");

    /**
     * The unique string representation of the mime type (eg, plain/text)
     */
    @XmlAttribute
    private String type;
    /**
     * List of file extensions mapped to this mime type
     */
    @XmlAttribute
    @XmlJavaTypeAdapter(StringToListAttributeAdapter.class)
    private ImmutableSet<String> extensions;
    /**
     * True if this mime type can be viewed as text file
     */
    @XmlAttribute
    private boolean viewable;
    /**
     * True if this mime type should be indexed (applies to xml and archive files)
     */
    @XmlAttribute
    private boolean index;
    /**
     * True if this mime type is a browsable archive (currently zip/jar variants are supported)
     */
    @XmlAttribute
    private boolean archive;
    /**
     * The UI highlighter syntax to for this mime type (only relevant if this is a viewable type)
     */
    @XmlAttribute
    private String syntax;
    /**
     * The css class of a display icon for this mime type
     */
    @XmlAttribute
    private String css;

    public MimeType() {
    }

    protected MimeType(String type, ImmutableSet<String> extensions, boolean viewable, boolean index, boolean archive,
            String syntax, String css) {
        this.type = type;
        this.extensions = extensions;
        this.viewable = viewable;
        this.index = index;
        this.archive = archive;
        this.syntax = syntax;
        this.css = css;
    }

    public String getType() {
        return type;
    }

    public ImmutableSet<String> getExtensions() {
        return extensions;
    }

    public boolean isViewable() {
        return viewable;
    }

    public boolean isIndex() {
        return index;
    }

    public boolean isArchive() {
        return archive;
    }

    public String getSyntax() {
        return syntax;
    }

    public String getCss() {
        return css;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MimeType mimeType = (MimeType) o;

        return type.equals(mimeType.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return type;
    }

    private static class StringToListAttributeAdapter extends XmlAdapter<String, ImmutableSet<String>> {
        @Override
        public ImmutableSet<String> unmarshal(String str) throws Exception {
            if (StringUtils.isBlank(str)) {
                return ImmutableSet.of();
            }

            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            String[] extensions = str.split(",");
            for (String extension : extensions) {
                extension = StringUtils.trim(extension);
                if (StringUtils.isNotBlank(extension)) {
                    builder.add(extension);
                }
            }
            return builder.build();
        }

        @Override
        public String marshal(ImmutableSet<String> strings) throws Exception {
            throw new UnsupportedOperationException("Marshaling is not supported");
        }
    }
}
