package org.artifactory.aql.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.result.Row;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.stream.Stream;

import static org.artifactory.api.context.ArtifactoryContextThreadBinder.bind;
import static org.artifactory.api.context.ArtifactoryContextThreadBinder.unbind;
import static org.artifactory.common.ConstantValues.securityPersonalInfoNonAdminExposure;
import static org.artifactory.utils.ObfuscationUtils.UNKNOWN_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class AqlSecUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String COOL_EMAIL = "cool@cool.com";
    private final ArtifactoryHomeStub homeStub = getOrCreateArtifactoryHomeStub();
    private static Row row;
    private AutoCloseable mocks;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityService;

    @BeforeEach
    public void beforeEach() {
        mocks = openMocks(this);
        doReturn(securityService).when(artifactoryContext).beanForType(SecurityService.class);
        bind(artifactoryContext);

        row = new Row();
        row.buildCreatedBy = COOL_EMAIL;
        row.buildModifiedBy = COOL_EMAIL;
        row.buildPromotionCreatedBy = COOL_EMAIL;
        row.statDownloadedBy = COOL_EMAIL;
        row.statRemoteDownloadedBy = COOL_EMAIL;
        row.itemCreatedBy = COOL_EMAIL;
        row.itemModifiedBy = COOL_EMAIL;
        row.itemInfosPropsModifiedBy = COOL_EMAIL;
    }

    @AfterEach
    public void afterEach() throws Exception {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(),
                securityPersonalInfoNonAdminExposure.getDefValue());
        unbind();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("testHideUserRelatedInfoIfNeededDataProvider")
    void testHideUserRelatedInfoIfNeeded(boolean enabled, boolean admin, String expected) {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(), String.valueOf(enabled));
        doReturn(admin).when(securityService).isAdmin();
        Row actual = AqlSecUtils.hideUserRelatedInfoIfNeeded(row);
        assertEquals(expected, actual.buildCreatedBy);
        assertEquals(expected, actual.buildModifiedBy);
        assertEquals(expected, actual.buildPromotionCreatedBy);
        assertEquals(expected, actual.statDownloadedBy);
        assertEquals(expected, actual.statRemoteDownloadedBy);
        assertEquals(expected, actual.itemCreatedBy);
        assertEquals(expected, actual.itemModifiedBy);
        assertEquals(expected, actual.itemInfosPropsModifiedBy);
    }

    private static Stream<Arguments> testHideUserRelatedInfoIfNeededDataProvider() {
        return Stream.of(
                Arguments.of(true, true, COOL_EMAIL),
                Arguments.of(true, false, COOL_EMAIL),
                Arguments.of(false, true, COOL_EMAIL),
                Arguments.of(false, false, UNKNOWN_STRING)
        );
    }
}
