package org.artifactory.utils;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
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
import static org.artifactory.utils.ObfuscationUtils.obfuscateIfNeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class ObfuscationUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String COOL_EMAIL = "cool@cool.com";
    private final ArtifactoryHomeStub homeStub = getOrCreateArtifactoryHomeStub();
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
    }

    @AfterEach
    public void afterEach() throws Exception {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(),
                securityPersonalInfoNonAdminExposure.getDefValue());
        unbind();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("testObfuscateIfNeededDataProvider")
    void testObfuscateIfNeeded(boolean enabled, boolean admin, String data, String expected) {
        homeStub.setProperty(securityPersonalInfoNonAdminExposure.getPropertyName(), String.valueOf(enabled));
        doReturn(admin).when(securityService).isAdmin();
        String actual = obfuscateIfNeeded(data);
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> testObfuscateIfNeededDataProvider() {
        return Stream.of(
                Arguments.of(true, true, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(true, true, null, null),
                Arguments.of(true, false, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(true, false, null, null),
                Arguments.of(false, true, COOL_EMAIL, COOL_EMAIL),
                Arguments.of(false, true, null, null),
                Arguments.of(false, false, COOL_EMAIL, UNKNOWN_STRING),
                Arguments.of(false, false, null, null)
        );
    }
}
