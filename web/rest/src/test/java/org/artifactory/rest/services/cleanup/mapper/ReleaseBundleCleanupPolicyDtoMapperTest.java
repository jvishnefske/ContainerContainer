package org.artifactory.rest.services.cleanup.mapper;

import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicyConfig;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicySearchCriteria;
import org.artifactory.rest.resource.cleanup.dto.ReleaseBundleCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.ReleaseBundleCleanupPolicySearchCriteriaDto;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.List;

class ReleaseBundleCleanupPolicyDtoMapperTest {

    @Test
    void toDto() throws JSONException {
        BundleCleanupPolicy releaseBundleCleanupPolicy = BundleCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.RELEASE_BUNDLE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(BundleCleanupPolicyConfig.builder()
                        .durationInMinutes(1)
                        .searchCriteria(BundleCleanupPolicySearchCriteria.builder()
                                .releaseBundles(
                                        List.of(BundleCleanupPolicySearchCriteria.ReleaseBundle.builder()
                                                .projectKey("projectKey").name("name").build()))
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        ReleaseBundleCleanupPolicyDto dto = ReleaseBundleCleanupPolicyDtoMapper.toDto(releaseBundleCleanupPolicy);
        JSONAssert.assertEquals("""
                                {
                                  "key": "key",
                                  "itemType": "releaseBundle",
                                  "description": "desc",
                                  "cronExp": "1 1 * * * ?",
                                  "durationInMinutes": 1,
                                  "enabled": true,
                                  "searchCriteria": {
                                    "createdBeforeInMonths": 1,
                                    "releaseBundles": [
                                        {
                                          "projectKey": "projectKey",
                                          "name": "name"
                                        }
                                    ]
                                  }
                                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromDto() throws JSONException {
        ReleaseBundleCleanupPolicyDto dto = ReleaseBundleCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(ReleaseBundleCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInMonths(1)
                        .releaseBundles(List.of(ReleaseBundleCleanupPolicySearchCriteriaDto.ReleaseBundle.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BundleCleanupPolicy releaseBundleCleanupPolicy = ReleaseBundleCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "RELEASE_BUNDLE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "timeBasedConditions":[1],
                              "createdBeforeInMonths": 1,
                              "releaseBundles": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(releaseBundleCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** UI - This unit test case covers when a DTO received from frontend */
    @Test
    void fromDtoWithOnlyDays() throws JSONException {
        ReleaseBundleCleanupPolicyDto dto = ReleaseBundleCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(ReleaseBundleCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInDays(2)
                        .releaseBundles(List.of(ReleaseBundleCleanupPolicySearchCriteriaDto.ReleaseBundle.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BundleCleanupPolicy releaseBundleCleanupPolicy = ReleaseBundleCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "RELEASE_BUNDLE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "timeBasedConditions":[2],
                              "createdBeforeInDays": 2,
                              "releaseBundles": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(releaseBundleCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** Passed from API DTO should have both time base value as this is required while validation */
    @Test
    void fromDtoWithBothMonthsAndDays() throws JSONException {
        ReleaseBundleCleanupPolicyDto dto = ReleaseBundleCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(ReleaseBundleCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInMonths(1)
                        .createdBeforeInDays(30)
                        .releaseBundles(List.of(ReleaseBundleCleanupPolicySearchCriteriaDto.ReleaseBundle.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BundleCleanupPolicy releaseBundleCleanupPolicy = ReleaseBundleCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "RELEASE_BUNDLE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "timeBasedConditions":[1, 30],
                              "createdBeforeInMonths": 1,
                              "createdBeforeInDays": 30,
                              "releaseBundles": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(releaseBundleCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** Set default time based condition */
    @Test
    void toBaseDtoWhenNoTimeBasedConditionPassed() throws JSONException {
        ReleaseBundleCleanupPolicyDto dto = ReleaseBundleCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(ReleaseBundleCleanupPolicySearchCriteriaDto
                        .builder()
                        .releaseBundles(List.of(ReleaseBundleCleanupPolicySearchCriteriaDto.ReleaseBundle.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BundleCleanupPolicy releaseBundleCleanupPolicy = ReleaseBundleCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "RELEASE_BUNDLE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "timeBasedConditions":[730],
                              "createdBeforeInDays": 730,
                              "releaseBundles": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(releaseBundleCleanupPolicy),
                JSONCompareMode.STRICT);
    }
}