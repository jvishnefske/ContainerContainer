package org.artifactory.storage.db.policy;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyConfig;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.jfrog.common.JsonUtils;

@UtilityClass
public class PolicyMapper {

    public static DbPolicy policyToDbPolicy(Policy<? extends PolicyConfig> policy) {
        return DbPolicy.builder().key(policy.getKey())
                .type(policy.getType().getCode())
                .itemType(policy.getItemType().getCode())
                .enabled(policy.isEnabled())
                .created(policy.getCreated())
                .createdBy(policy.getCreatedBy())
                .modified(policy.getModified())
                .modifiedBy(policy.getModifiedBy())
                .cronExp(policy.getCronExp())
                .description(policy.getDescription())
                .config(JsonUtils.getInstance().valueToByteArray(policy.getConfig()))
                .build();
    }

    public static <C extends PolicyConfig, P extends Policy<C>> P dbPolicyToPolicy(DbPolicy dbPolicy,
            P policy, Class<C> blobClass) {
        if (dbPolicy == null) {
            return null;
        }

        policy.setKey(dbPolicy.key());
        policy.setType(PolicyType.findByCode(dbPolicy.type()));
        policy.setItemType(PolicyItemType.lookup(dbPolicy.itemType()));
        policy.setEnabled(dbPolicy.enabled());
        policy.setCreated(dbPolicy.created());
        policy.setCreatedBy(dbPolicy.createdBy());
        policy.setModified(dbPolicy.modified());
        policy.setModifiedBy(dbPolicy.modifiedBy());
        policy.setCronExp(dbPolicy.cronExp());
        policy.setDescription(dbPolicy.description());
        policy.setConfig(JsonUtils.getInstance().readValue(dbPolicy.config(), blobClass));
        return policy;
    }
}