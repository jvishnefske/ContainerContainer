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

package org.artifactory.version;

import lombok.experimental.UtilityClass;
import org.artifactory.util.XmlUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.output.Format;

import java.util.List;

/**
 * @author freds
 */
@UtilityClass
public final class XmlConverterUtils {
    // ATTENTION: NO LOGGER IN HERE SINCE IT IS CALLED BY LOGGER CONVERTER => STACK OVERFLOW

    public static String convert(List<XmlConverter> converters, String in) {
        return convert(converters, in, false);
    }

    public static String convertStrict(List<XmlConverter> converters, String in) {
        return convert(converters, in, true);
    }

    public static String convertPretty(List<XmlConverter> converters, String in) {
        // If no converters nothing to do
        if (converters.isEmpty()) {
            return in;
        }
        return XmlUtils.outputString(Format.getPrettyFormat(), createDoc(converters, in, false));
    }

    private static Document createDoc(List<XmlConverter> converters, String in, boolean isStrict) {
        Document doc = isStrict ? XmlUtils.parseStrict(in) : XmlUtils.parse(in);
        for (XmlConverter converter : converters) {
            converter.convert(doc);
        }
        return doc;
    }

    private static String convert(List<XmlConverter> converters, String in, boolean isStrict) {
        // If no converters nothing to do
        if (converters.isEmpty()) {
            return in;
        }
        return XmlUtils.outputString(createDoc(converters, in, isStrict));
    }
}
