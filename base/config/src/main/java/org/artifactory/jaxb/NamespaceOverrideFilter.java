package org.artifactory.jaxb;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * @author igoru
 */
public class NamespaceOverrideFilter extends XMLFilterImpl {

    private final String namespaceUri;
    private boolean addedNamespace;

    public NamespaceOverrideFilter(String namespaceUri) {
        super();
        this.namespaceUri = namespaceUri;
        this.addedNamespace = false;
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        startControlledPrefixMapping();
    }

    @Override
    public void startElement(String arg0, String arg1, String arg2,
            Attributes arg3) throws SAXException {
        super.startElement(this.namespaceUri, arg1, arg2, arg3);
    }

    @Override
    public void endElement(String arg0, String arg1, String arg2)
            throws SAXException {
        super.endElement(this.namespaceUri, arg1, arg2);
    }

    @Override
    public void startPrefixMapping(String prefix, String url)
            throws SAXException {
        this.startControlledPrefixMapping();
    }

    private void startControlledPrefixMapping() throws SAXException {
        if (!this.addedNamespace) {
            super.startPrefixMapping("", this.namespaceUri);
            this.addedNamespace = true;
        }
    }

}