package org.artifactory.storage;

/**
 * Exception thrown when an emoji is not allowed in the database.
 */
public class EmojiNotAllowedInDbException extends StorageException {

    public EmojiNotAllowedInDbException(String message, Throwable cause) {
        super(message, cause);
    }
}

