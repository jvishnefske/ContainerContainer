package org.artifactory.storage.db.aql.service.decorator;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.AqlFieldResolver;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.links.TableLink;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.artifactory.addon.retention.ArtifactRetentionConstants.PACKAGE_MANIFESTS_REPO_NAME;
import static org.artifactory.addon.retention.ArtifactRetentionConstants.RESTORE_IN_TRANSIT_REPO_KEY;

/**
 * @author Omri Ziv
 */
public class ArtifactRetentionColdSearchDecorator implements DecorationStrategy {

    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        if (!isColdSearch(decoratorContext)) {
            return;
        }
        if (AqlDomainEnum.items != aqlQuery.getDomain()) {
            throw new AqlException("Retention Aql search is not supported non items search");
        }

        List<AqlQueryElement> nativeQueryElements = aqlQuery.getElementsForNative();
        int index = findNextFieldCriteria(0, AqlPhysicalFieldEnum.itemRepo, nativeQueryElements);
        boolean requestForManifestRepo = false;
        while (index > -1) {
            Criterion currCriterion = (Criterion) nativeQueryElements.get(index);
            AqlValue variable2 = (AqlValue) currCriterion.getVariable2();
            String repoKey = variable2.getValue();
            if (PACKAGE_MANIFESTS_REPO_NAME.equals(repoKey)) {
                requestForManifestRepo = true;
            } else {
                variable2.setValue(addNamespacePrefix(repoKey));
            }
            index = findNextFieldCriteria(index + 1, AqlPhysicalFieldEnum.itemRepo, nativeQueryElements);
        }

        addExcludeRestoreInTransitRepoDecoration(nativeQueryElements);
        if (!requestForManifestRepo) {
            addExcludePackageManifestRepoDecoration(nativeQueryElements);
        }
    }

    private <T extends AqlRowResult> void addExcludePackageManifestRepoDecoration(
            List<AqlQueryElement> nativeQueryElements) {
        SimpleCriterion criteria = buildRepoCriteria(PACKAGE_MANIFESTS_REPO_NAME);
        addCriteria(nativeQueryElements, criteria);
    }

    private <T extends AqlRowResult> void addExcludeRestoreInTransitRepoDecoration(
            List<AqlQueryElement> nativeQueryElements) {
        SimpleCriterion criteria = buildRepoCriteria(RESTORE_IN_TRANSIT_REPO_KEY);
        addCriteria(nativeQueryElements, criteria);
    }

    @Nonnull
    private static SimpleCriterion buildRepoCriteria(String repo) {
        AqlField itemRepo = AqlFieldResolver.resolve(AqlPhysicalFieldEnum.itemRepo);
        TableLink nodesTable = AqlTableGraph.tablesLinksMap.get(SqlTableEnum.nodes);
        SimpleCriterion criteria = new SimpleCriterion(Lists.newArrayList(AqlDomainEnum.items), itemRepo,
                nodesTable.getTable(), AqlComparatorEnum.notEquals.getSignature(),
                AqlFieldResolver.resolve(repo, AqlVariableTypeEnum.string),
                nodesTable.getTable(),
                false);
        return criteria;
    }

    private static void addCriteria(List<AqlQueryElement> nativeQueryElements, SimpleCriterion criteria) {
        if (isNotEmpty(nativeQueryElements)) {
            nativeQueryElements.add(AqlAdapter.and);
        }
        nativeQueryElements.add(criteria);
    }

    private static boolean isColdSearch(AqlQueryDecoratorContext decoratorContext) {
        return Optional.ofNullable(decoratorContext)
                .map(AqlQueryDecoratorContext::getAqlOverloadSettings)
                .map(AqlOverloadSettings::getStreamFunc)
                .isPresent();
    }

    private static String addNamespacePrefix(String repo) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        return artifactRetentionAddon.getJfrogColdStorageArchivingNamespace() + "_" + repo;
    }
}
