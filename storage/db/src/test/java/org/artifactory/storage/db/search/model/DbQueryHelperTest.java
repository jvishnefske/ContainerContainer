package org.artifactory.storage.db.search.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DbQueryHelperTest {

    @ParameterizedTest
    @CsvSource({
            "test, test",
            "test*, test%",
            "test?, test_",
            "test/*, test/%",
            "test^%*, test^%",
            "te*st?/, te%st_/"  ,
            "test_^*, test_^%"
    })
    void testConvertWildcards(String input, String expected) {
        assertEquals(expected, DbQueryHelper.convertWildcards(input));
    }

    @ParameterizedTest
    @CsvSource({
            "test*, ^, test%",
            "test?, ^, test_",
            "test/*, ^, test/%",
            "test^%*, ^, test^%%",
            "te*st?/, ^, te%st_/",
            "test_^*, ^, test_^%",
            "test*, #, test%",
            "test?, #, test_",
            "test/*, #, test/%",
            "test#%*, #, test#%%",
            "te*st?/, #, te%st_/",
            "test_#*, #, test_#%"
    })
    void testConvertWildcardsWithEscapeSymbol(String input, String escapeSymbol, String expected) {
        assertEquals(expected, DbQueryHelper.convertWildcards(input, escapeSymbol));
    }

    @ParameterizedTest
    @CsvSource({
            "test, /test",
            "test*, /test%",
            "test?, /test_",
            "test/*, /test/%",
            "test^%*, /test^%",
            "te*st?/, /te%st_/",
            "test_^*, /test_^%"
    })
    void testAddPathValue(String input, String expected) {
        StringBuilder builder = new StringBuilder("/");
        DbQueryHelper.addPathValue(builder, input);
        assertEquals(expected, builder.toString());
    }

    @ParameterizedTest
    @CsvSource({
            "test*, ^, /test%",
            "test?, ^, /test_",
            "test/*, ^, /test/%",
            "test^%*, ^, /test^%%",
            "te*st?/, ^, /te%st_/",
            "test_^*, ^, /test_^%",
            "test*, #, /test%",
            "test?, #, /test_",
            "test/*, #, /test/%",
            "test#%*, #, /test#%%",
            "te*st?/, #, /te%st_/",
            "test_#*, #, /test_#%"
    })
    void testAddPathValueWithEscapeSymbol(String input, String escapeSymbol, String expected) {
        StringBuilder builder = new StringBuilder("/");
        DbQueryHelper.addPathValue(builder, input, escapeSymbol);
        assertEquals(expected, builder.toString());
    }
}