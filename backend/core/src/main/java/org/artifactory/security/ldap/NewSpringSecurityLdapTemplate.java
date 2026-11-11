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

package org.artifactory.security.ldap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.control.PagedResultsDirContextProcessor;
import org.springframework.ldap.core.ContextExecutor;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.util.Assert;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * @author gidis
 */
@Slf4j
public class NewSpringSecurityLdapTemplate extends LdapTemplate {
    public static final String[] NO_ATTRS = new String[0];
    private static final boolean RETURN_OBJECT = true;

    //~ Instance fields ================================================================================================

    /**
     * Default search controls
     */
    private SearchControls searchControls = new SearchControls();

    //~ Constructors ===================================================================================================

    public NewSpringSecurityLdapTemplate(ContextSource contextSource) {
        super(contextSource);
        Assert.notNull(contextSource, "ContextSource cannot be null");
        setContextSource(contextSource);

        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    }

    //~ Methods ========================================================================================================

    /**
     * Internal method extracted to avoid code duplication in AD search.
     */
    public static DirContextOperations searchForSingleEntryInternal(DirContext ctx, SearchControls searchControls,
            String base, String filter, Object[] params) throws NamingException {
        final DistinguishedName ctxBaseDn = new DistinguishedName(ctx.getNameInNamespace());
        final DistinguishedName searchBaseDn = new DistinguishedName(base);
        final NamingEnumeration<SearchResult> resultsEnum =
                ctx.search(searchBaseDn, filter, params, buildControls(searchControls));

        if (log.isDebugEnabled()) {
            log.debug("Searching for entry under DN '{}', base = '{}', filter = '{}'", ctxBaseDn, searchBaseDn, filter);
        }

        Set<DirContextOperations> results = new HashSet<DirContextOperations>();
        try {
            try {
                Field entries = resultsEnum.getClass().getSuperclass().getDeclaredField("entries");
                entries.setAccessible(true);
                Vector vector = (Vector) entries.get(resultsEnum);
                for (Object o : vector) {
                    Field attributes = o.getClass().getDeclaredField("attributes");
                    attributes.setAccessible(true);
                    Attributes attributesObject = (Attributes) attributes.get(o);
                    String user = "unknown";
                    if (params != null && params.length == 1) {
                        user = params[0].toString();
                    }
                    if (attributesObject.get("javaclassname") != null) {
                        log.warn("Detected Object poisoning security vulnerability for user: '{}'", user);
                        throw new SecurityException(
                                "Detected Object poisoning security vulnerability for user: '" + user + "'");
                    }
                }
            } catch (Exception e) {
                if (e instanceof SecurityException) {
                    throw (SecurityException) e;
                } else {
                    log.warn(
                            "Cannot protect against ldap object poisoning. You can update your LDAP settings if needed",
                            e);
                }
            }
            while (resultsEnum.hasMore()) {
                SearchResult searchResult = resultsEnum.next();
                DirContextAdapter dca = (DirContextAdapter) searchResult.getObject();
                Assert.notNull(dca, "No object returned by search, DirContext is not correctly configured");

                if (log.isDebugEnabled()) {
                    log.debug("Found DN: {}", dca.getDn());
                }
                results.add(dca);
            }
        } catch (PartialResultException e) {
            LdapUtils.closeEnumeration(resultsEnum);
            log.info("Ignoring PartialResultException");
        }
        if (results.size() == 0) {
            throw new IncorrectResultSizeDataAccessException(1, 0);
        }

        if (results.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        }

        return results.iterator().next();
    }

    /**
     * Internal method extracted to avoid code duplication in AD search.
     */
    public static Set<DirContextOperations> searchForMultipleEntriesInternal(DirContext ctx,
            SearchControls searchControls,
            String base, String filter, PagedResultsDirContextProcessor pagedResultsDirContextProcessor)
            throws NamingException {
        final DistinguishedName ctxBaseDn = new DistinguishedName(ctx.getNameInNamespace());
        final DistinguishedName searchBaseDn = new DistinguishedName(base);

        Set<DirContextOperations> results = new HashSet<>();
        NamingEnumeration<SearchResult> resultsEnum = null;
        try {
            if (pagedResultsDirContextProcessor != null) {
                pagedResultsDirContextProcessor.preProcess(ctx);
            }
            resultsEnum = ctx.search(searchBaseDn, filter, buildControls(searchControls));

            if (log.isDebugEnabled()) {
                log.debug("Searching for entry under DN '{}', base = '{}', filter = '{}'", ctxBaseDn, searchBaseDn,
                        filter);
            }
            try {
                Field entries = resultsEnum.getClass().getSuperclass().getDeclaredField("entries");
                entries.setAccessible(true);
                Vector vector = (Vector) entries.get(resultsEnum);
                for (Object o : vector) {
                    Field attributes = o.getClass().getDeclaredField("attributes");
                    attributes.setAccessible(true);
                    Attributes attributesObject = (Attributes) attributes.get(o);
                    if (attributesObject.get("javaclassname") != null) {
                        log.warn("Detected Object poisoning security vulnerability for group");
                        throw new SecurityException(
                                "Detected Object poisoning security vulnerability for group");
                    }
                }
            } catch (Exception e) {
                if (e instanceof SecurityException) {
                    throw (SecurityException) e;
                } else {
                    log.warn(
                            "Cannot protect against ldap object poisoning. You can update your LDAP settings if needed",
                            e);
                }
            }
            while (resultsEnum.hasMore()) {
                SearchResult searchResult = resultsEnum.next();
                DirContextAdapter dca = (DirContextAdapter) searchResult.getObject();
                Assert.notNull(dca, "No object returned by search, DirContext is not correctly configured");

                if (log.isDebugEnabled()) {
                    log.debug("Found DN: {}", dca.getDn());
                }
                results.add(dca);
            }

        } catch (PartialResultException | SizeLimitExceededException e) {
            LdapUtils.closeEnumeration(resultsEnum);
            log.info("Ignoring PartialResultException/SizeLimitExceededException");
        } finally {
            if (pagedResultsDirContextProcessor != null) {
                pagedResultsDirContextProcessor.postProcess(ctx);
            }
        }
        return results;
    }

    /**
     * We need to make sure the search controls has the return object flag set to true, in order for
     * the search to return DirContextAdapter instances.
     */
    private static SearchControls buildControls(SearchControls originalControls) {
        return new SearchControls(originalControls.getSearchScope(),
                originalControls.getCountLimit(),
                originalControls.getTimeLimit(),
                originalControls.getReturningAttributes(),
                RETURN_OBJECT,
                originalControls.getDerefLinkFlag());
    }

    /**
     * Performs an LDAP compare operation of the value of an attribute for a particular directory entry.
     *
     * @param dn            the entry who's attribute is to be used
     * @param attributeName the attribute who's value we want to compare
     * @param value         the value to be checked against the directory value
     * @return true if the supplied value matches that in the directory
     */
    public boolean compare(final String dn, final String attributeName, final Object value) {
        final String comparisonFilter = "(" + attributeName + "={0})";

        class LdapCompareCallback implements ContextExecutor {

            public Object executeWithContext(DirContext ctx) throws NamingException {
                SearchControls ctls = new SearchControls();
                ctls.setReturningAttributes(NO_ATTRS);
                ctls.setSearchScope(SearchControls.OBJECT_SCOPE);

                NamingEnumeration<SearchResult> results = ctx.search(dn, comparisonFilter, new Object[] {value}, ctls);

                Boolean match = Boolean.valueOf(results.hasMore());
                LdapUtils.closeEnumeration(results);

                return match;
            }
        }

        Boolean matches = (Boolean) executeReadOnly(new LdapCompareCallback());

        return matches.booleanValue();
    }

    /**
     * Composes an object from the attributes of the given DN.
     *
     * @param dn                   the directory entry which will be read
     * @param attributesToRetrieve the named attributes which will be retrieved from the directory entry.
     * @return the object created by the mapper
     */
    public DirContextOperations retrieveEntry(final String dn, final String[] attributesToRetrieve) {

        return (DirContextOperations) executeReadOnly(new ContextExecutor() {
            public Object executeWithContext(DirContext ctx) throws NamingException {
                Attributes attrs = ctx.getAttributes(dn, attributesToRetrieve);

                // Object object = ctx.lookup(LdapUtils.getRelativeName(dn, ctx));

                return new DirContextAdapter(attrs, new DistinguishedName(dn),
                        new DistinguishedName(ctx.getNameInNamespace()));
            }
        });
    }

    /**
     * Performs a search using the supplied filter and returns the union of the values of the named attribute
     * found in all entries matched by the search. Note that one directory entry may have several values for the
     * attribute. Intended for role searches and similar scenarios.
     *
     * @param base          the DN to search in
     * @param filter        search filter to use
     * @param params        the parameters to substitute in the search filter
     * @param attributeName the attribute who's values are to be retrieved.
     * @return the set of String values for the attribute as a union of the values found in all the matching entries.
     */
    public Set<String> searchForSingleAttributeValues(final String base, final String filter, final Object[] params,
            final String attributeName) {
        // Escape the params acording to RFC2254
        Object[] encodedParams = new String[params.length];

        for (int i = 0; i < params.length; i++) {
            encodedParams[i] = LdapEncoder.filterEncode(params[i].toString());
        }

        String formattedFilter = MessageFormat.format(filter, encodedParams);
        log.debug("Using filter: {}", formattedFilter);

        final HashSet<String> set = new HashSet<String>();

        ContextMapper roleMapper = new ContextMapper() {
            public Object mapFromContext(Object ctx) {
                DirContextAdapter adapter = (DirContextAdapter) ctx;
                String[] values = adapter.getStringAttributes(attributeName);
                if (values == null || values.length == 0) {
                    log.debug("No attribute value found for '{}'", attributeName);
                } else {
                    set.addAll(Arrays.asList(values));
                }
                return null;
            }
        };

        SearchControls ctls = new SearchControls();
        ctls.setSearchScope(searchControls.getSearchScope());
        ctls.setReturningAttributes(new String[] {attributeName});

        search(base, formattedFilter, ctls, roleMapper);

        return set;
    }

    /**
     * Performs a search, with the requirement that the search shall return a single directory entry, and uses
     * the supplied mapper to create the object from that entry.
     * <p>
     * Ignores <code>PartialResultException</code> if thrown, for compatibility with Active Directory
     * (see {@link LdapTemplate#setIgnorePartialResultException(boolean)}).
     *
     * @param base   the search base, relative to the base context supplied by the context source.
     * @param filter the LDAP search filter
     * @param params parameters to be substituted in the search.
     * @return a DirContextOperations instance created from the matching entry.
     * @throws IncorrectResultSizeDataAccessException if no results are found or the search returns more than one
     *                                                result.
     */
    public DirContextOperations searchForSingleEntry(final String base, final String filter, final Object[] params) {

        return (DirContextOperations) executeReadOnly(new ContextExecutor() {
            public Object executeWithContext(DirContext ctx) throws NamingException {
                return searchForSingleEntryInternal(ctx, searchControls, base, filter, params);
            }
        });
    }

    public Set<DirContextOperations> searchForMultipleEntries(final String base, final String filter,
            SearchControls searchControls, PagedResultsDirContextProcessor pagedResultsDirContextProcessor) {
        return (Set<DirContextOperations>) executeReadOnly(new ContextExecutor() {
            public Object executeWithContext(DirContext ctx) throws NamingException {
                return searchForMultipleEntriesInternal(ctx, searchControls, base, filter,
                        pagedResultsDirContextProcessor);
            }
        });
    }

    /**
     * Sets the search controls which will be used for search operations by the template.
     *
     * @param searchControls the SearchControls instance which will be cached in the template.
     */
    public void setSearchControls(SearchControls searchControls) {
        this.searchControls = searchControls;
    }
}

