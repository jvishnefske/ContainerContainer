package org.artifactory.storage.db.devopsagent.errorcatalog.model;

/**
 * Keyset cursor for stable pagination. {@code null} cursor means "first page".
 * Pairs the time column with {@code id} as the tiebreaker for rows sharing the same timestamp.
 */
public record KeysetCursor(long timeValue, long id) {

    public static KeysetCursor of(long timeValue, long id) {
        return new KeysetCursor(timeValue, id);
    }
}
