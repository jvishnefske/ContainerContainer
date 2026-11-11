package org.artifactory.properties;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class PropertiesImplAdapter extends XmlAdapter<PropertiesImplWrapper, PropertiesImpl> {

    @Override
    public PropertiesImpl unmarshal(PropertiesImplWrapper wrapper) throws Exception {
        return wrapper.toProperties();
    }

    @Override
    public PropertiesImplWrapper marshal(PropertiesImpl properties) throws Exception {
        return properties.toWrapper();
    }
}
