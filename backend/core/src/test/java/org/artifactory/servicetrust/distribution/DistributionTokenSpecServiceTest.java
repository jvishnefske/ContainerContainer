package org.artifactory.servicetrust.distribution;

import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.servicetrust.ServiceTrust;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.ScopeContainer;
import org.jfrog.access.common.ServiceId;
import org.jfrog.common.JsonUtils;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class DistributionTokenSpecServiceTest extends ArtifactoryHomeBoundTest {

    private static final ServiceId SERVICE_ID = new ServiceId("artifactory", "serviceId");
    private static final String SUBJECT = "a@b/users/username";
    private static final String BASE_URL = "baseUrl";
    private static final String EXCHANGE_URL = "exchangeUrl";
    private static final String PAIRING_URL = "paringUrl";

    private static final String PAIRING_SCOPE = "internal:service-trust/pairing/distribution:x";

    private static final List<String> MASTER_SCOPES = List.of(
            "internal:applied-permissions/service/distribution:*",
            "internal:service-trust/pairing/distribution:x"
    );

    private static final List<String> EXCHANGE_SCOPES = List.of(
            ScopeContainer.ADMIN_PERMISSION,
            DistributionTokenSpecService.INTRANSIT_SCOPE_TOKEN.toString()
    );

    private final DistributionTokenSpecService service = DistributionTokenSpecService.DISTRIBUTION_TOKEN_SPEC_SERVICE;

    @Test
    public void testCreatePairingTokenSpec() {
        TokenSpec<GenericTokenSpec> tokenSpec = service.createPairingTokenSpec(
                new ServiceTrustTokenData(
                        SERVICE_ID,
                        SUBJECT,
                        PAIRING_SCOPE),
                ServiceTrust.ONE_WAY,
                new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL)
        );

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(Long.valueOf(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS), tokenSpec.getExpiresIn());
        assertEquals(List.of(SERVICE_ID.getFormattedName()), tokenSpec.getAudience());
        assertEquals(tokenSpec.getScope().size(), 1);
        assertEquals(tokenSpec.getScope().stream().findFirst().get(), PAIRING_SCOPE);

        TokenExtension tokenExtension =
                JsonUtils.getInstance().readValue(tokenSpec.getExtension(), TokenExtension.class);

        assertEquals(tokenExtension.getExchangeUrl(), EXCHANGE_URL);
    }

    @Test
    public void testCreateMasterTokenSpec() {
        TokenSpec<GenericTokenSpec> tokenSpec = service.createMasterTokenSpec(
                new ServiceTrustTokenData(SERVICE_ID, SERVICE_ID.getFormattedName(), PAIRING_SCOPE),
                ServiceTrustUseCase.DISTRIBUTION.getName(),
                new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL)
        );

        assertTrue(tokenSpec.getRefreshable());
        assertEquals(Long.valueOf(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS), tokenSpec.getExpiresIn());
        assertEquals(List.of(SERVICE_ID.getFormattedName()), tokenSpec.getAudience());
        assertEquals(2, tokenSpec.getScope().size());
        assertTrue(tokenSpec.getScope().containsAll(MASTER_SCOPES));
    }

    @Test
    public void testCreateExchangeTokenSpec() {
        TokenSpec<GenericTokenSpec> tokenSpec = service.createExchangeTokenSpec(
                SERVICE_ID,
                "distribute/rb1/1.0.0"
        );

        assertNotEquals(tokenSpec.getRefreshable(), Boolean.TRUE);
        assertEquals(Long.valueOf(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS), tokenSpec.getExpiresIn());
        assertEquals(List.of(SERVICE_ID.getFormattedName()), tokenSpec.getAudience());
        assertEquals(tokenSpec.getScope().size(), 3);
        assertTrue(tokenSpec.getScope().containsAll(EXCHANGE_SCOPES));
    }

    @Test
    public void testCreateExchangeTokenSpecForSpecificScopeTokenRequest() {
        TokenSpec<GenericTokenSpec> tokenSpec = service.createExchangeTokenSpec(
                SERVICE_ID,
                "distribution/actions/scrape"
        );

        assertNotEquals(tokenSpec.getRefreshable(), Boolean.TRUE);
        assertEquals(Long.valueOf(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS), tokenSpec.getExpiresIn());
        assertEquals(List.of(SERVICE_ID.getFormattedName()), tokenSpec.getAudience());
        assertEquals(tokenSpec.getScope().size(), 1);
        assertTrue(tokenSpec.getScope().contains("internal:distribution/actions/scrape:a"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unsupported useCasePath specified: distributionBad/actions/scrape")
    public void testCreateExchangeTokenSpecForSpecificScopeTokenRequestFailureScenario() {
        service.createExchangeTokenSpec(
                SERVICE_ID,
                "distributionBad/actions/scrape"
        );
    }
}
