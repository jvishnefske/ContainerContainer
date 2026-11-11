package org.artifactory.storage.db.policy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyConfig;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author vovam
 */
class PolicyMapperTest {

    private static final String KEY = "key";
    private static final String TYPE = "cleanup";
    private static final String ITEM_TYPE = "artifact";
    private static final String DESCRIPTION = "description";
    private static final String CROP_EXP = "cropExp";
    private static final long CREATED = 1;
    private static final String CREATED_BY = "created_by";
    private static final long MODIFIED = 2;
    private static final String MODIFIED_BY = "modified_by";
    private static final String CONFIG_FIELD_VALUE = "config_field_value";
    private static final TestPolicyConfig CONFIG_OBJECT = new TestPolicyConfig(CONFIG_FIELD_VALUE);

    @Test
    void testPolicyToDbPolicy() {
        TestPolicy policy = PolicyMapper.dbPolicyToPolicy(createDbPolicy(),
                new TestPolicy(), TestPolicyConfig.class);
        assertEquals(policy.getKey(), KEY);
        assertEquals(policy.getDescription(), DESCRIPTION);
        assertEquals(policy.getCronExp(), CROP_EXP);
        assertEquals(policy.getCreated(), CREATED);
        assertEquals(policy.getCreatedBy(), CREATED_BY);
        assertEquals(policy.getItemType(), PolicyItemType.ARTIFACT);
        assertEquals(policy.getType(), PolicyType.CLEANUP);
        assertEquals(policy.getModified().longValue(), MODIFIED);
        assertEquals(policy.getModifiedBy(), MODIFIED_BY);
        assertTrue(policy.isEnabled());
        assertEquals(policy.getConfig().getConfigField(), CONFIG_FIELD_VALUE);
    }

    @Test
    void testDbPolicyToPolicy() {
        DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(createPolicy());
        assertEquals(dbPolicy.key(), KEY);
        assertEquals(dbPolicy.description(), DESCRIPTION);
        assertEquals(dbPolicy.cronExp(), CROP_EXP);
        assertEquals(dbPolicy.created(), CREATED);
        assertEquals(dbPolicy.createdBy(), CREATED_BY);
        assertEquals(dbPolicy.itemType(), PolicyItemType.ARTIFACT.getCode());
        assertEquals(dbPolicy.type(), PolicyType.CLEANUP.getCode());
        assertEquals(dbPolicy.modified().longValue(), MODIFIED);
        assertEquals(dbPolicy.modifiedBy(), MODIFIED_BY);
        assertTrue(dbPolicy.enabled());
        assertTrue(Arrays.equals(dbPolicy.config(), JsonUtils.getInstance().valueToByteArray(CONFIG_OBJECT)));
    }

    @Test
    void testPolicyToDbPolicyWithOnlyRequiredFields() {
        TestPolicy policy = PolicyMapper.dbPolicyToPolicy(createDbPolicyMandatoryFieldsOnly(),
                new TestPolicy(), TestPolicyConfig.class);
        assertEquals(policy.getKey(), KEY);
        assertEquals(policy.getCreated(), CREATED);
        assertEquals(policy.getCreatedBy(), CREATED_BY);
        assertEquals(policy.getItemType(), PolicyItemType.ARTIFACT);
        assertEquals(policy.getType(), PolicyType.CLEANUP);
        assertEquals(policy.getConfig().getConfigField(), CONFIG_FIELD_VALUE);
        assertNull(policy.getDescription());
        assertNull(policy.getCronExp());
        assertNull(policy.getModified());
        assertNull(policy.getModifiedBy());
        assertFalse(policy.isEnabled());
    }

    @Test
    void testDbPolicyToPolicyWithOnlyRequiredFields() {
        DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(createPolicyMandatoryFieldsOnly());
        assertEquals(dbPolicy.key(), KEY);
        assertEquals(dbPolicy.created(), CREATED);
        assertEquals(dbPolicy.createdBy(), CREATED_BY);
        assertEquals(dbPolicy.itemType(), PolicyItemType.ARTIFACT.getCode());
        assertEquals(dbPolicy.type(), PolicyType.CLEANUP.getCode());
        assertNull(dbPolicy.description(), DESCRIPTION);
        assertNull(dbPolicy.cronExp(), CROP_EXP);
        assertNull(dbPolicy.modified());
        assertNull(dbPolicy.modifiedBy());
        assertFalse(dbPolicy.enabled());
        assertTrue(Arrays.equals(dbPolicy.config(), JsonUtils.getInstance().valueToByteArray(CONFIG_OBJECT)));
    }

    private TestPolicy createPolicyMandatoryFieldsOnly() {
        TestPolicy policy = new TestPolicy();
        policy.setKey(KEY);
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.ARTIFACT);
        policy.setCreated(CREATED);
        policy.setCreatedBy(CREATED_BY);
        policy.setConfig(CONFIG_OBJECT);
        return policy;
    }

    private static DbPolicy createDbPolicyMandatoryFieldsOnly() {
        return DbPolicy.builder().key(KEY)
                .type(TYPE)
                .itemType(ITEM_TYPE)
                .created(CREATED)
                .createdBy(CREATED_BY)
                .config(JsonUtils.getInstance().valueToByteArray(CONFIG_OBJECT))
                .build();
    }

    private TestPolicy createPolicy() {
        TestPolicy policy = new TestPolicy();
        policy.setKey(KEY);
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.ARTIFACT);
        policy.setEnabled(true);
        policy.setDescription(DESCRIPTION);
        policy.setCronExp(CROP_EXP);
        policy.setCreated(CREATED);
        policy.setCreatedBy(CREATED_BY);
        policy.setModifiedBy(MODIFIED_BY);
        policy.setModified(MODIFIED);
        policy.setConfig(CONFIG_OBJECT);
        return policy;
    }

    private static DbPolicy createDbPolicy() {
        return DbPolicy.builder().key(KEY)
                .type(TYPE)
                .itemType(ITEM_TYPE)
                .enabled(true)
                .created(CREATED)
                .createdBy(CREATED_BY)
                .modified(MODIFIED)
                .modifiedBy(MODIFIED_BY)
                .cronExp(CROP_EXP)
                .description(DESCRIPTION)
                .config(JsonUtils.getInstance().valueToByteArray(CONFIG_OBJECT))
                .build();
    }

    private static class TestPolicy extends Policy<TestPolicyConfig> {

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private static class TestPolicyConfig implements PolicyConfig {
        private String configField;
    }
}