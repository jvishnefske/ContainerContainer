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

package org.artifactory.factory.xstream;

import org.artifactory.factory.JaxbAdapter;
import org.artifactory.factory.jaxb.JaxbAdapterFactory;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.properties.PropertiesImplAdapter;
import org.artifactory.properties.PropertiesImplNullElement;
import org.artifactory.properties.PropertiesImplWrapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * @author Yoav Landman
 */
public class PropertiesConverterTest {
    @Test
    public void testConversion() {
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("a", "aye");
        properties.put("a", "aye2");
        properties.put("b", "bee");
        properties.put("c", "cee");
        properties.put("empty", "");
        properties.put("null", null);
        properties.put("xml", "<xml></xml>");
        TestWrapper wrapper = new TestWrapper();
        wrapper.properties = properties;
        JaxbAdapter jaxbAdapter = JaxbAdapterFactory
                .create(TestWrapper.class, PropertiesImplWrapper.class,
                        PropertiesImplNullElement.class);
        String xml = jaxbAdapter.toXML(wrapper);
        TestWrapper deserializedMap = (TestWrapper) jaxbAdapter.fromXML(xml);
        Assert.assertEquals(deserializedMap.properties, properties);
    }

    @XmlRootElement
    private static class TestWrapper {

        @XmlJavaTypeAdapter(PropertiesImplAdapter.class)
        PropertiesImpl properties;

        public TestWrapper() {
            // for JAXB
        }
    }
}
