package org.artifactory.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author vovam
 */
@Data
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Policy<C extends PolicyConfig> {
    private String key;
    private PolicyType type;
    private PolicyItemType itemType;
    private boolean enabled;
    private long created;
    private String createdBy;
    private Long modified;
    private String modifiedBy;
    private String cronExp;
    private String description;
    private C config;
}