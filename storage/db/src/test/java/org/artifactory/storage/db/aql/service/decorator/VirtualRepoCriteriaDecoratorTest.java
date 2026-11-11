/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.aql.service.decorator;

import com.beust.jcommander.internal.Maps;
import com.google.common.collect.Lists;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.itest.service.AqlAbstractServiceTest.AdminPermissions;
import org.artifactory.storage.db.aql.itest.service.AqlAbstractServiceTest.EmptyRepoProvider;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * @author Yinon Avraham
 */
public class VirtualRepoCriteriaDecoratorTest extends BaseDecorator {

    @Test
    public void testRepoEqualsNonVirtual() {
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        Criterion originalCriterion = createRepoCriteria(AqlComparatorEnum.equals, "repo1");
        aqlQuery.getAqlElements().add(originalCriterion);
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(new EmptyRepoProvider(), new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()), "item.repo $eq repo1");
    }

    @Test
    public void testRepoEqualsVirtualWithSingleLocal() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("v-repo", "repo1");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createEqualsPathCriteria("path1"));
        aqlQuery.getAqlElements().add(AqlAdapter.and);
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.equals, "v-repo"));
        aqlQuery.getAqlElements().add(AqlAdapter.and);
        aqlQuery.getAqlElements().add(createEqualsPathCriteria("path2"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "item.path $eq path1 $and item.repo $eq repo1 $and item.path $eq path2");
    }

    @Test
    public void testRepoEqualsVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("v-repo", "repo1", "repo2", "repo3");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createEqualsPathCriteria("path1"));
        aqlQuery.getAqlElements().add(AqlAdapter.and);
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.equals, "v-repo"));
        aqlQuery.getAqlElements().add(AqlAdapter.and);
        aqlQuery.getAqlElements().add(createEqualsPathCriteria("path2"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "item.path $eq path1 $and ( item.repo $eq repo1 $or item.repo $eq repo2 $or item.repo $eq repo3 ) " +
                        "$and item.path $eq path2");
    }

    @Test
    public void testRepoEqualsMultipleVirtuals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("v-repo", "repo1", "repo2", "repo3");
        repoProvider.addVirtual("v-repo2", "repo4", "repo5");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(new OpenParenthesisAqlElement());
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.equals, "v-repo"));
        aqlQuery.getAqlElements().add(AqlAdapter.or);
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.equals, "v-repo2"));
        aqlQuery.getAqlElements().add(new CloseParenthesisAqlElement());
        aqlQuery.getAqlElements().add(AqlAdapter.and);
        aqlQuery.getAqlElements().add(createEqualsPathCriteria("path1"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "( ( item.repo $eq repo1 $or item.repo $eq repo2 $or item.repo $eq repo3 ) $or ( item.repo $eq repo4 " +
                        "$or item.repo $eq repo5 ) ) $and item.path $eq path1");
    }

    @Test
    public void testRepoMatchesVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("other-v-repo", "repo3");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.matches, "v*"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "( item.repo $match v* $or item.repo $eq repo1 $or item.repo $eq repo2 )");
    }

    @Test
    public void testRepoMatchesStar() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("other-v-repo", "repo3");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.matches, "*"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "item.repo $match *");
    }

    @Test
    public void testRepoGreaterVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.greater, "v-repo"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()), "( item.repo $gt v-repo $or item.repo $eq repo4 )");
    }

    @Test
    public void testRepoGreaterEqualsVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.greaterEquals, "v-repo"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "( item.repo $gte v-repo $or item.repo $eq repo1 $or item.repo $eq repo2 $or item.repo $eq repo4 )");
    }

    @Test
    public void testRepoLessVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.less, "v-repo"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()), "( item.repo $lt v-repo $or item.repo $eq repo3 )");
    }

    @Test
    public void testRepoLessEqualsVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.lessEquals, "v-repo"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "( item.repo $lte v-repo $or item.repo $eq repo3 $or item.repo $eq repo1 $or item.repo $eq repo2 )");
    }

    @Test
    public void testRepoNotEqualsVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.notEquals, "v-repo"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()), "( item.repo $ne repo1 $and item.repo $ne repo2 )");
    }

    @Test
    public void testRepoNotMatchesVirtualWithMultiLocals() {
        DummyRepoProvider repoProvider = new DummyRepoProvider();
        repoProvider.addVirtual("u-repo", "repo3");
        repoProvider.addVirtual("v-repo", "repo1", "repo2");
        repoProvider.addVirtual("w-repo", "repo4");
        VirtualRepoCriteriaDecorator decorator = new VirtualRepoCriteriaDecorator();
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(createRepoCriteria(AqlComparatorEnum.notMatches, "v*"));
        decorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, new AdminPermissions()));
        assertEquals(toString(aqlQuery.getAqlElements()),
                "( item.repo $nmatch v* $and item.repo $ne repo1 $and item.repo $ne repo2 )");
    }

    public static class DummyRepoProvider extends EmptyRepoProvider {
        private Map<String, List<String>> virtual2locals = Maps.newLinkedHashMap();

        void addVirtual(String virtualRepoKey, String... localRepoKeys) {
            virtual2locals.put(virtualRepoKey, Lists.newArrayList(localRepoKeys));
        }

        @Override
        public boolean existsVirtualRepoKey(String repoKey) {
            return virtual2locals.get(repoKey) != null;
        }

        @Override
        public List<String> getVirtualRepoKeys() {
            return Lists.newArrayList(virtual2locals.keySet());
        }

        @Override
        public List<String> getVirtualResolvedLocalFederatedAndCacheRepoKeys(String virtualRepoKey) {
            return Collections.unmodifiableList(virtual2locals.get(virtualRepoKey));
        }
    }
}