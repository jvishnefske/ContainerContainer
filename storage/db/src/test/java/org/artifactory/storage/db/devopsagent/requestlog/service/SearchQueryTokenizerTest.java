package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Locks the documented tokeniser contract byte-for-byte (api-documentation-v1.md §8.2).
 */
public class SearchQueryTokenizerTest {

    @DataProvider(name = "orMode")
    public Object[][] orCases() {
        return new Object[][]{
                {"npm registry", "npm|registry"},
                {"abc-123", "abc-123"},
                {"404, /api/repos", "404|api|repos"},
                {"/axios", "axios"},
                {"registry.npmjs.org/axios", "registry.npmjs.org|axios"},
                {"foo,bar|baz:qux;quux", "foo|bar|baz|qux|quux"},
                {"& | ! ( )", ""},
                {"a&b|c!(d)", "ab|cd"},
                {"oneToken", "oneToken"},
                {"", ""},
                {null, ""},
                {"   ", ""},
        };
    }

    @DataProvider(name = "andMode")
    public Object[][] andCases() {
        return new Object[][]{
                {"npm registry", "npm&registry"},
                {"api repositories", "api&repositories"},
                {"axios/1.0.0", "axios&1.0.0"},
                {"single", "single"},
                {"", ""},
        };
    }

    @Test(dataProvider = "orMode")
    public void tokenizeOrMode(String input, String expected) {
        assertEquals(SearchQueryTokenizer.tokenize(input, false), expected);
    }

    @Test(dataProvider = "andMode")
    public void tokenizeAndMode(String input, String expected) {
        assertEquals(SearchQueryTokenizer.tokenize(input, true), expected);
    }
}
