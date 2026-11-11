package org.artifactory.storage.db.federated.mirrors;

import lombok.Data;

import java.util.Objects;

/**
 * @author Alexei Vainshtein
 */
@Data
public class MirrorsMetadataModule {

    private String type;
    private String url;
    private long cacheTime;
    private byte[] blob;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MirrorsMetadataModule that = (MirrorsMetadataModule) o;
        return Objects.equals(type, that.type) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, url);
    }
}
