package org.artifactory.storage.db.devopsagent.requestlog.model;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Covers verification checklist item #36 — cursor encode/decode round-trip and the
 * documented malformed-input contract.
 */
public class CursorTest {

    @Test
    public void roundTrip() {
        Cursor original = new Cursor(1_715_630_000_000L, 42L);
        Cursor decoded = Cursor.decode(original.encode());
        assertEquals(decoded, original);
    }

    @Test
    public void roundTripZero() {
        Cursor original = new Cursor(0L, 0L);
        assertEquals(Cursor.decode(original.encode()), original);
    }

    @DataProvider(name = "malformed")
    public Object[][] malformedCases() {
        return new Object[][]{
                {"not-base64-???"},
                {"!!!!"},
                {"YWJjMTIz"},        // base64 of "abc123" — valid b64 but not valid JSON cursor
                {""},
                {null},
        };
    }

    @Test(dataProvider = "malformed")
    public void malformedThrowsIllegalArgument(String input) {
        assertThrows(IllegalArgumentException.class, () -> Cursor.decode(input));
    }
}
