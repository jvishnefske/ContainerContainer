package org.artifactory.storage.db.conversion;

/**
 * @author AndreiK.
 */
public class DbConversionException extends RuntimeException {

    public DbConversionException(String message, Exception e) {
        super(message, e);
    }

    public DbConversionException(String message) { super(message); }
}