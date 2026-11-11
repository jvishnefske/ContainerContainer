package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.TransitiveMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This decorator is used to identify a remote transitive request
 *
 * @author Tomer Slutsky
 */
public class RemoteRepoDecorator implements DecorationStrategy {
    private static final Logger log = LoggerFactory.getLogger(RemoteRepoDecorator.class);

    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        TransitiveMetadata details = aqlQuery.getTransitiveMetadata();
        if (details.isTransitive()) {
            AqlRepoProvider repoProvider = decoratorContext.getRepoProvider();

            List<AqlQueryElement> queryElements = aqlQuery.getElementsForNative();
            int index = findNextFieldCriteria(0, AqlPhysicalFieldEnum.itemRepo, queryElements);
            Criterion currCriterion = (Criterion) queryElements.get(index);
            String repoKey = ((AqlValue) currCriterion.getVariable2()).getValue();

            // This means the repo is not of Remote type and there is no need to perform this decorator
            // Or that it exists but is offline
            log.debug("Checking if the repoKey: {}, is a remote repo or offline", repoKey);
            if (repoProvider.getRemoteRepoDescriptor(repoKey) == null || repoProvider.isRemoteRepoOffline(repoKey)) {
                log.debug("The repoKey: {}, is either offline or not a remote repo", repoKey);
                return;
            }

            // Makes sure the remote repo is a smart repo or an artifactory
            log.debug("Checking if the repoKey: {}, is transitive valid", repoKey);
            if (!repoProvider.isRepoTransitiveValid(repoKey)) {
                log.debug("The repoKey: {}, is not transitive valid", repoKey);
                log.error("When including the transitive property, and requesting a remote repository," +
                        "the remote repository must be an Artifactory");
                throw new AqlException("Transitive Aql search is not supported for this repo");
            }

            // Reduce the depth of the transitive search by 1
            log.debug("Reducing the transitive depth by 1, the current depth is: {}", details.getTransitiveDepth());
            details.setTransitiveDepth(details.getTransitiveDepth() - 1);

            // Set the repo name
            log.debug("Setting the base repo name: {}", repoKey);
            details.setBaseRepoName(repoKey);

            // Change the query repo type to remote
            log.debug("Changing the queryRepoType, previous type: {}, new type: {}",
                    aqlQuery.getQueryRepoType().getName(), AqlQueryRepoType.remote.getName());
            aqlQuery.setQueryRepoType(AqlQueryRepoType.remote);
        }
    }
}

