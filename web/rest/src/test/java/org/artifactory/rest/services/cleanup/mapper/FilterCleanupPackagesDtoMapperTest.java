package org.artifactory.rest.services.cleanup.mapper;

import org.artifactory.policy.cleanup.packages.FilterCleanupPackages;
import org.artifactory.policy.cleanup.packages.FilterPackage;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.resource.cleanup.dto.PackagesCleanupFilterDto;
import org.artifactory.rest.resource.cleanup.dto.PackagesDto;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testng.Assert.assertEquals;

public class FilterCleanupPackagesDtoMapperTest {
    @Test
    void fromDtoSuccessCase() throws JSONException {
        PackagesCleanupFilterDto dto = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .propertyThreshold(1714475979530L)
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        dto.validate();

        FilterCleanupPackages filterCleanupPackages = FilterCleanupPackagesDtoMapper.fromDto(dto);


        JSONAssert.assertEquals("""
                        {
                            "property": "LAST_DOWNLOADED",
                            "propertyThreshold": 1714475979530,
                            "packages": [
                                {
                                    "path": "com/jfrog/manifestFile"
                                }
                            ]
                        }
                        """, JsonUtils.getInstance().writeValueAsString(filterCleanupPackages),
                JSONCompareMode.STRICT);
    }

    @Test
    void fromFilterPackageDtoSuccessCase() throws JSONException {
        PackagesDto dto = PackagesDto.builder()
                .path("com/jfrog/manifestFile")
                .build();

        FilterPackage filterPackage = FilterCleanupPackagesDtoMapper.fromFilterPackageDto(dto);

        JSONAssert.assertEquals("""
                        {
                            "path": "com/jfrog/manifestFile"   \s
                        }
                        """, JsonUtils.getInstance().writeValueAsString(filterPackage),
                JSONCompareMode.STRICT);
    }

    @Test
    void fromDtoNullPropertyCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .propertyThreshold(1714475979530L)
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Property is required");
    }

    @Test
    void fromDtoEmptyPropertyCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("")
                .propertyThreshold(1714475979530L)
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Property is required");
    }

    @Test
    void fromDtoFailPropertyEnumCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("random_test")
                .propertyThreshold(1714475979530L)
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Property 'random_test' is not a valid property");
    }

    @Test
    void fromDtoNegativeThresholdCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .propertyThreshold(-123L)
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Threshold must be a positive number");
    }

    @Test
    void fromDtoNullThresholdCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Threshold must be a positive number");
    }

    @Test
    void fromDtoNullPackagesCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .propertyThreshold(12323442343L)
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Packages list is required");
    }

    @Test
    void fromDtoEmptyPackagesCase() {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .packages(List.of())
                .propertyThreshold(12323442343L)
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Packages list must not be empty");
    }

    @Test
    void fromDtoMaxPackagesLimitCase() {
        List<PackagesDto> packages = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            packages.add(PackagesDto.builder().path("com/jfrog/manifestFile").build());
        }
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .packages(packages)
                .propertyThreshold(12323442343L)
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                packagesCleanupFilter::validate
        );

        assertEquals(exception.getMessage(),
                "Packages list must not exceed 1000");
    }

    @Test
    void testPackagesCleanupFilterDtoToJSONString() throws JSONException {
        PackagesCleanupFilterDto packagesCleanupFilter = PackagesCleanupFilterDto.builder()
                .property("LAST_DOWNLOADED")
                .packages(List.of(PackagesDto.builder().path("com/jfrog/manifestFile").build()))
                .propertyThreshold(12323442343L)
                .build();
        JSONAssert.assertEquals("""
                {
                  "property": "LAST_DOWNLOADED",
                  "propertyThreshold": 12323442343,
                  "packages": [
                    {
                      "path": "com/jfrog/manifestFile"
                    }
                  ]
                }
                """, packagesCleanupFilter.toJSONString(), true);
    }
}
