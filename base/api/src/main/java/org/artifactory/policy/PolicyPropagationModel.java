package org.artifactory.policy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author vovam
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PolicyPropagationModel {
    private String key;
    private long modification;
    private ChangeType changeType;
    private PolicyType policyType;
    private PolicyItemType policyItemType;

    public enum ChangeType {
        UPDATE,
        DELETE
    }
}