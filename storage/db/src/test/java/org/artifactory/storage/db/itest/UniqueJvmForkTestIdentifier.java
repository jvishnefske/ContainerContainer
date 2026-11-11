package org.artifactory.storage.db.itest;

import java.util.Random;

/**
 * @author Omri Ziv
 */
public abstract class UniqueJvmForkTestIdentifier {

    private static final Random random = new Random();
    private static final String UNIQUE_TEST_NAME;

    static {
        UNIQUE_TEST_NAME =
                "rtdbtest_" + (10_000_000 + random.nextInt(90_000_000)) + System.getProperty("forknumber", "0");
        System.out.println("UNIQUE_TEST_NAME: " + UNIQUE_TEST_NAME);
    }

    public static String get() {
        return UNIQUE_TEST_NAME;
    }
}
