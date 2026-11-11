package org.artifactory.storage.db.dao.locks;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;


public class LockInfoTest {

    @Test
    public void lockInfoConstructorCategoryMoreThan75() {
        try {
            new LockInfo("1234567890123456789012345678901234567890123456789012345678901234567890", null,
                    "1234",
                    0, null, 0);
        } catch (IllegalArgumentException e) {
            fail("Expected to pass: " + e.getMessage());
        }
    }

    @Test
    public void lockInfoConstructorCategoryMoreThan128() {
        try {
            new LockInfo(
                    "12345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                            "123456789012345678901234567890123456789012345678901234567890",
                    null,
                    "1234",
                    0, null, 0);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    "Category 12345678901234567890123456789012345678901234567890123456789012345678901234567890123" +
                            "456789012345678901234567890123456789012345678901234567890 exceeds " +
                            "maximum allowed length of 128.");
        }
    }

    @Test
    public void lockInfoConstructorOwnerMoreThan75ExpectException() {
        try {
            new LockInfo("1234567890123456789012345678901234567890123456789012345678901234567890", null,
                    "1234567890123456789012345678901234567890123456789012345678901234567890",
                    0, null, 0);
        } catch (IllegalArgumentException e) {
            assertEquals("Owner 1234567890123456789012345678901234567890123456789012345678901234567890" +
                    " exceeds maximum allowed length of 64.", e.getMessage());
        }
    }

    @Test
    public void lockInfoConstructorOwnerAndCategoryNullOrCorrectExpectNoException() {
        try {
            new LockInfo(null, null, null, 0, null, 0);
            new LockInfo("null", null, "null", 0, null, 0);
            new LockInfo(null, null, "null", 0, null, 0);
        } catch (IllegalArgumentException e) {
            fail();
        }
    }
}
