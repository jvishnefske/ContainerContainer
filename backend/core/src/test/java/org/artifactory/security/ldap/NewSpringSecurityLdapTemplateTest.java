package org.artifactory.security.ldap;

import org.mockito.Mockito;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Set;
import java.util.Vector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

public class NewSpringSecurityLdapTemplateTest {
    private SearchControls searchControls;
    private DirContext dirContext;

    @BeforeMethod
    private void setup() {
        searchControls = Mockito.mock(SearchControls.class);
        dirContext = Mockito.mock(DirContext.class);
    }

    @Test(expectedExceptions = SecurityException.class,
            expectedExceptionsMessageRegExp = ".*Detected Object poisoning security vulnerability for group")
    public void testThrowExceptionOnObjectPoisoningUsingGroupSearch() throws NamingException {
        final String CLASSNAME_ATTRIBUTE = "javaclassname";
        Set<DirContextOperations> result = invokeSearch(CLASSNAME_ATTRIBUTE);
        assertNull(result);
    }

    @Test(dataProvider = "getAttributes")
    public void testAllowedAttributesWhenUsingGroupSearch(String attribute) throws NamingException {
        Set<DirContextOperations> result = invokeSearch(attribute);
        assertFalse(result.isEmpty());
    }

    @DataProvider
    private static Object[][] getAttributes() {
        return new Object[][] {
                {"objectclass"},
                {"javacerializedcata"},
                {"javafactory"},
                {"javafodebase"},
                {"javareferenceaddress"},
                {"javaclassnames"},
                {"javaremotelocation"}
        };
    }

    private Set<DirContextOperations> invokeSearch(String attribute) throws NamingException {
        when(dirContext.getNameInNamespace()).thenReturn("");
        Vector<VectorElement> vector = new Vector<>();
        vector.add(new VectorElement(attribute));
        when(dirContext.search(any(DistinguishedName.class), any(String.class), any(SearchControls.class))).
                thenReturn(new SubCustomEnumeration<>(vector));

        return NewSpringSecurityLdapTemplate.
                searchForMultipleEntriesInternal(
                        dirContext, searchControls, "", "", null);
    }

    static class CustomEnumeration<SearchResult> implements NamingEnumeration<SearchResult> {
        public Vector<VectorElement> entries;
        int iterator = 0;

        CustomEnumeration(Vector<VectorElement> entries) {
            this.entries = entries;
        }

        @Override
        public SearchResult next() throws NamingException {
            SearchResult result;
            if (this.hasMore()) {
                result = (SearchResult) entries.get(iterator);
                iterator++;
            } else {
                throw new IndexOutOfBoundsException("no more elements in entries");
            }
            return result;
        }

        @Override
        public boolean hasMore() {
            return iterator != entries.size();
        }

        @Override
        public void close() throws NamingException {

        }

        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public SearchResult nextElement() {
            return null;
        }
    }

    static class SubCustomEnumeration<SearchResult> extends CustomEnumeration<SearchResult> {
        SubCustomEnumeration(Vector<VectorElement> entries) {
            super(entries);
        }
    }

    static class VectorElement extends SearchResult {
        public Attributes attributes;

        VectorElement(String attributeName) {
            super(attributeName, new Object(), null);
            this.attributes = new BasicAttributes();
            attributes.put(new BasicAttribute(attributeName));
        }

        @Override
        public Object getObject() {
            return new DirContextAdapter();
        }
    }
}