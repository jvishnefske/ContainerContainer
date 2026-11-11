package org.artifactory.factory.jaxb;

import org.artifactory.factory.JaxbAdapter;

/**
 * @author igoru
 */
public final class JaxbAdapterFactory {

    public static JaxbAdapter create(Class... annotatedClassesToProcess) {
        return new JaxbAdapterImpl(annotatedClassesToProcess);
    } // not used in nuget and gems

    private JaxbAdapterFactory() {
        // utility class
    }
}
