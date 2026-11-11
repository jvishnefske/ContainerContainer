package org.artifactory.storage.db.policy.entity;

import lombok.Builder;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author vovam
 */
@Builder(toBuilder = true)
public record DbPolicy(
        String key,
        String type,
        String itemType,
        boolean enabled,
        long created,
        String createdBy,
        Long modified,
        String modifiedBy,
        String cronExp,
        String description,
        byte[] config
) {
    @Override
    public String toString() {
        return "DbPolicy{" +
                "key='" + key + '\'' +
                ", type='" + type + '\'' +
                ", itemType='" + itemType + '\'' +
                ", enabled=" + enabled +
                ", created=" + created +
                ", createdBy='" + createdBy + '\'' +
                ", modified=" + modified +
                ", modifiedBy='" + modifiedBy + '\'' +
                ", cronExp='" + cronExp + '\'' +
                ", description='" + description + '\'' +
                ", config=" + Arrays.toString(config) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DbPolicy dbPolicy = (DbPolicy) o;
        return enabled == dbPolicy.enabled && created == dbPolicy.created && Objects.equals(key, dbPolicy.key) &&
                Objects.equals(type, dbPolicy.type) && Objects.equals(itemType, dbPolicy.itemType) &&
                Objects.equals(createdBy, dbPolicy.createdBy) && Objects.equals(modified, dbPolicy.modified) &&
                Objects.equals(modifiedBy, dbPolicy.modifiedBy) && Objects.equals(cronExp, dbPolicy.cronExp) &&
                Objects.equals(description, dbPolicy.description) && Arrays.equals(config, dbPolicy.config);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(key, type, itemType, enabled, created, createdBy, modified, modifiedBy, cronExp,
                description);
        result = 31 * result + Arrays.hashCode(config);
        return result;
    }
}