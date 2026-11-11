package org.artifactory.storage.db.aql.service.decorator;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.jfrog.access.token.JwtAccessToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.request.RequestThreadLocal.FEDERATION_SERVICE_USER_AGENT;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.EVIDENCE_DIR;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceAQLFilterInDatabase;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isInternalServiceToken;

@Slf4j
public class EvidenceDecorator implements DecorationStrategy {

    private static final String EVIDENCE_SERVICE_USER_AGENT_PREFIX = "jfrog evidence/";

    /** Access service types expected behind each recognized internal User-Agent. */
    private static final String FEDERATION_SERVICE_TYPE = "jfrtfs";
    private static final String EVIDENCE_SERVICE_TYPE = "jfevd";

    private static final String EVIDENCE_DIR_PATTERN = EVIDENCE_DIR + "/*";

    /**
     * Decorates the given AqlQuery with a criterion that filters out .evidence directories.
     * <p>
     * Do not restrict .evidence inclusion in the case of:
     * 1. The async thread (legacy federation full-sync)
     * 2. The federation service user agent (new RTFS full-sync)
     * 3. The evidence service user agent (reconciliation)
     * unless force filtering is enabled.
     */
    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        if (shouldSkipEvidenceFilter()) {
            log.trace("Skip AQL evidence filter");
            return;
        }
        if (isEvidenceAQLFilterInDatabase() && AqlDomainEnum.items.equals(aqlQuery.getDomain())) {
            if (aqlQuery.getAqlElements().isEmpty()) {
                aqlQuery.getAqlElements().add(buildCriterion());
            } else {
                aqlQuery.getAqlElements().add(AqlAdapter.and);
                aqlQuery.getAqlElements().add(buildCriterion());
            }
        }
    }

    private boolean shouldSkipEvidenceFilter() {
        if (EventLogHintManager.contains(AsyncThread)) {
            log.trace("Skip AQL evidence filter for the async thread");
            return true;
        }

        String userAgent = RequestThreadLocal.getUserAgent();

        if (isEmpty(userAgent)) {
            return false;
        }

        String expectedServiceType = expectedServiceTypeFor(userAgent);
        if (expectedServiceType == null) {
            return false;
        }
        // User-Agent is client-controlled; only skip the filter with a matching service token.
        if (!isAuthenticatedAs(expectedServiceType)) {
            log.trace("Internal evidence User-Agent without a matching service token; applying AQL evidence filter");
            return false;
        }
        return true;
    }

    private static String expectedServiceTypeFor(String userAgent) {
        if (FEDERATION_SERVICE_USER_AGENT.equalsIgnoreCase(userAgent)) {
            return FEDERATION_SERVICE_TYPE;
        }
        if (userAgent.toLowerCase().startsWith(EVIDENCE_SERVICE_USER_AGENT_PREFIX)) {
            return EVIDENCE_SERVICE_TYPE;
        }
        return null;
    }

    private boolean isAuthenticatedAs(String expectedServiceType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AccessTokenAuthentication accessTokenAuthentication)) {
            return false;
        }
        JwtAccessToken accessToken = accessTokenAuthentication.getAccessToken();
        if (accessToken == null) {
            return false;
        }
        return isInternalServiceToken(accessToken.getSubject(), accessToken.getScope(), expectedServiceType);
    }

    @Nonnull
    private Criterion buildCriterion() {
        return new SimpleCriterion(
                Collections.singletonList(AqlDomainEnum.items),
                new AqlField(AqlPhysicalFieldEnum.itemPath),
                new SqlTable(SqlTableEnum.nodes),
                AqlComparatorEnum.notMatches.getSignature(),
                new AqlValue(AqlVariableTypeEnum.string, EVIDENCE_DIR_PATTERN),
                new SqlTable(SqlTableEnum.nodes), false);
    }
}