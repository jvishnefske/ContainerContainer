package org.artifactory.storage.db.aql.service.decorator;

import com.jfrog.sysconf.SysConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_AQL_FILTER_IN_DATABASE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceDecoratorTest {

    private SysConfig sysConfig;

    private EvidenceDecorator evidenceDecorator;

    @BeforeEach
    void setup() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
        evidenceDecorator = new EvidenceDecorator();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        RequestThreadLocal.unbind();
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDecorateEvidenceAsyncThread() {
        EventLogHintManager.put(AsyncThread, "true");
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        try {
            evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
            verify(aqlQuery, times(0)).getAqlElements();
        } finally {
            EventLogHintManager.remove(AsyncThread);
        }
    }

    @Test
    void testDecorateEvidenceAqlFilterInDataBasedDisabled() {
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(false);
        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        verify(aqlQuery, times(0)).getAqlElements();
    }

    @Test
    void testDecorateEvidenceAqlFilterInDataBasedEnabled() {
        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        AqlQueryElement aqlQueryElement = mock(AqlQueryElement.class);
        aqlQueryElements.add(aqlQueryElement);
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        verify(aqlQuery, times(3)).getAqlElements();
    }

    @Test
    void testDecorateEvidenceAqlFilterInDataBasedEnabledWithWrongDomain() {
        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        AqlQueryElement aqlQueryElement = mock(AqlQueryElement.class);
        aqlQueryElements.add(aqlQueryElement);
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.builds);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);
        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        verify(aqlQuery, times(0)).getAqlElements();
    }

    @Test
    void testDecorateSkipsFilterForFederationServiceUserAgent() {
        // Setup mock request with federation service user agent
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("artifactory-federation-service");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);
        // Genuine federation service token (bare ServiceId subject).
        setAuthenticatedTokenSubject("jfrtfs@01instanceid");

        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        // Should skip filtering, so getAqlElements should not be called
        verify(aqlQuery, times(0)).getAqlElements();
    }

    @Test
    void testDecorateSkipsFilterForEvidenceServiceUserAgent() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("JFrog Evidence/7.98.0");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);
        // Genuine evidence service token (bare ServiceId subject).
        setAuthenticatedTokenSubject("jfevd@01instanceid");

        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        verify(aqlQuery, times(0)).getAqlElements();
    }

    /**
     * An authenticated end user cannot bypass the evidence-hiding AQL filter by spoofing the internal
     * {@code User-Agent}. Their token subject is user-scoped (not a ServiceId), so the filter still applies.
     */
    @Test
    void testDecorateAppliesFilterWhenInternalUserAgentSpoofedByUser() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("jfrog evidence/1.0");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);
        // User-scoped subject - even a user named "jfds@attacker" is not a ServiceId.
        setAuthenticatedTokenSubject("jfac@01instanceid/users/jfds@attacker");

        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        aqlQueryElements.add(mock(AqlQueryElement.class));
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        // Filter applied: read + and-element + criterion-element
        verify(aqlQuery, times(3)).getAqlElements();
    }

    /**
     * A different, unrelated platform service (here Artifactory, {@code jfrt}) that presents a valid
     * service token but spoofs the evidence {@code User-Agent} must not skip the filter: the token
     * service type has to match the recognized User-Agent flow.
     */
    @Test
    void testDecorateAppliesFilterForMismatchedServiceToken() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("jfrog evidence/1.0");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);
        // Authenticated service token, but not the evidence service type (jfevd).
        setAuthenticatedTokenSubject("jfrt@01instanceid");

        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        aqlQueryElements.add(mock(AqlQueryElement.class));
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        // Filter applied: read + and-element + criterion-element
        verify(aqlQuery, times(3)).getAqlElements();
    }

    /**
     * A user token whose bare subject is an evidence-service-shaped ServiceId ({@code jfevd@attacker})
     * must not skip the filter: Access stamps every user token with the {@code applied-permissions/user}
     * scope that a genuine service token never carries, so the filter still applies.
     */
    @Test
    void testDecorateAppliesFilterForServiceNamedUserToken() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("jfrog evidence/1.0");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);
        // Service-shaped subject, but a user token (carries the applied-permissions/user scope).
        setAuthenticatedTokenSubject("jfevd@attacker", List.of("applied-permissions/user"));

        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        aqlQueryElements.add(mock(AqlQueryElement.class));
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        // Filter applied: read + and-element + criterion-element
        verify(aqlQuery, times(3)).getAqlElements();
    }

    private void setAuthenticatedTokenSubject(String subject) {
        setAuthenticatedTokenSubject(subject, null);
    }

    private void setAuthenticatedTokenSubject(String subject, List<String> scope) {
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(accessToken.getSubject()).thenReturn(subject);
        when(accessToken.getScope()).thenReturn(scope);
        Authentication authentication = new AccessTokenAuthentication(accessToken, null, null);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void decorate_shouldApplyFilter_whenRequestThreadLocalReturnsNull() {
        try (MockedStatic<RequestThreadLocal> mocked =
                     mockStatic(RequestThreadLocal.class)) {
            // Arrange
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn(null);
            List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
            aqlQueryElements.add(mock(AqlQueryElement.class));
            AqlQuery<?> aqlQuery = mock(AqlQuery.class);
            AqlQueryDecoratorContext ctx = mock(AqlQueryDecoratorContext.class);
            when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
            when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
            when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true))
                    .thenReturn(true);

            // Act
            evidenceDecorator.decorate(aqlQuery, ctx);

            // Assert - filter applied: read + and-element + criterion-element
            verify(aqlQuery, times(3)).getAqlElements();
        }
    }

    @Test
    void testDecorateAppliesFilterWithDifferentUserAgent() {
        // Setup mock request with different user agent
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("User-Agent")).thenReturn("some-other-agent");
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        when(requestWrapper.getRequest()).thenReturn(mockRequest);
        RequestThreadLocal.bind(requestWrapper);

        List<AqlQueryElement> aqlQueryElements = new ArrayList<>();
        AqlQueryElement aqlQueryElement = mock(AqlQueryElement.class);
        aqlQueryElements.add(aqlQueryElement);
        AqlQuery<?> aqlQuery = mock(AqlQuery.class);
        AqlQueryDecoratorContext aqlQueryDecoratorContext = mock(AqlQueryDecoratorContext.class);
        when(aqlQuery.getAqlElements()).thenReturn(aqlQueryElements);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        when(sysConfig.getBoolean(EVIDENCE_AQL_FILTER_IN_DATABASE, true)).thenReturn(true);

        evidenceDecorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        // Should apply filter because user agent is not federation service
        verify(aqlQuery, times(3)).getAqlElements();
    }
}